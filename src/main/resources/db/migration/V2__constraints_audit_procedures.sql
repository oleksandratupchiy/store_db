
-- Нова Flyway-міграція V2.
-- Початкову V1 після застосування не змінюємо.

-- 1. Обмеження цілісності даних

ALTER TABLE product
    ADD CONSTRAINT uq_product_sku UNIQUE (sku);

ALTER TABLE employee
    ADD CONSTRAINT uq_employee_email UNIQUE (email);

ALTER TABLE inventory
    ADD CONSTRAINT uq_inventory_product UNIQUE (product_id);

ALTER TABLE inventory
    ADD CONSTRAINT ck_inventory_quantity
        CHECK (quantity >= 0);

ALTER TABLE product
    ADD CONSTRAINT ck_product_price
        CHECK (price >= 0);

ALTER TABLE supply_order_item
    ADD CONSTRAINT ck_supply_item_quantity
        CHECK (quantity > 0);

ALTER TABLE supply_order_item
    ADD CONSTRAINT ck_supply_item_price
        CHECK (cost_price >= 0);

ALTER TABLE sales_order_item
    ADD CONSTRAINT ck_sale_item_quantity
        CHECK (quantity > 0);

ALTER TABLE sales_order_item
    ADD CONSTRAINT ck_sale_item_price
        CHECK (unit_price >= 0);

ALTER TABLE payment
    ADD CONSTRAINT ck_payment_amount
        CHECK (amount > 0);

ALTER TABLE customer
    ADD CONSTRAINT ck_customer_points
        CHECK (loyalty_points >= 0);

ALTER TABLE shift
    ADD CONSTRAINT ck_shift_times
        CHECK (end_time IS NULL OR end_time > start_time);


-- 2. Зовнішні ключі для аудиту

ALTER TABLE product
    ADD CONSTRAINT fk_product_updated_by
        FOREIGN KEY (updated_by) REFERENCES employee(id);

ALTER TABLE employee
    ADD CONSTRAINT fk_employee_updated_by
        FOREIGN KEY (updated_by) REFERENCES employee(id);

ALTER TABLE inventory
    ADD CONSTRAINT fk_inventory_updated_by
        FOREIGN KEY (updated_by) REFERENCES employee(id);

ALTER TABLE price_history
    ADD CONSTRAINT fk_price_history_changed_by
        FOREIGN KEY (changed_by) REFERENCES employee(id);


-- 3. Функція автоматичного оновлення часу

CREATE OR REPLACE FUNCTION fn_update_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at := NOW();
RETURN NEW;
END;
$$ LANGUAGE plpgsql;


-- 4. Новий тригер для inventory

CREATE TRIGGER trg_inventory_audit
    BEFORE UPDATE ON inventory
    FOR EACH ROW
    EXECUTE FUNCTION fn_update_timestamp();


-- 5. View активних товарів та залишків

CREATE OR REPLACE VIEW v_active_products_inventory AS
SELECT
    p.id,
    p.sku,
    p.name,
    p.price,
    COALESCE(i.quantity, 0) AS quantity
FROM product p
         LEFT JOIN inventory i ON i.product_id = p.id
WHERE p.is_deleted = FALSE;


-- 6. Soft delete товару

CREATE OR REPLACE PROCEDURE sp_soft_delete_product(
    p_product_id INT,
    p_user_id INT
)
LANGUAGE plpgsql AS $$
BEGIN
UPDATE product
SET
    is_deleted = TRUE,
    deleted_at = NOW(),
    updated_by = p_user_id
WHERE id = p_product_id
  AND is_deleted = FALSE;

IF NOT FOUND THEN
        RAISE EXCEPTION
            'Товар відсутній або вже видалений: %',
            p_product_id;
END IF;
END;
$$;


-- 7. Soft delete працівника

CREATE OR REPLACE PROCEDURE sp_soft_delete_employee(
    p_employee_id INT,
    p_user_id INT
)
LANGUAGE plpgsql AS $$
BEGIN
UPDATE employee
SET
    is_deleted = TRUE,
    deleted_at = NOW(),
    updated_by = p_user_id
WHERE id = p_employee_id
  AND is_deleted = FALSE;

IF NOT FOUND THEN
        RAISE EXCEPTION
            'Працівника не знайдено або видалено: %',
            p_employee_id;
END IF;
END;
$$;


-- 8. Soft delete покупця

CREATE OR REPLACE PROCEDURE sp_soft_delete_customer(
    p_customer_id INT
)
LANGUAGE plpgsql AS $$
BEGIN
UPDATE customer
SET is_deleted = TRUE
WHERE id = p_customer_id
  AND is_deleted = FALSE;

IF NOT FOUND THEN
        RAISE EXCEPTION
            'Покупця не знайдено або видалено: %',
            p_customer_id;
END IF;
END;
$$;


-- 9. Створення замовлення

CREATE OR REPLACE PROCEDURE sp_create_sales_order(
    p_customer_id INT,
    p_employee_id INT,
    OUT p_order_id INT
)
LANGUAGE plpgsql AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM customer
        WHERE id = p_customer_id
          AND is_deleted = FALSE
    ) THEN
        RAISE EXCEPTION
            'Активного покупця % не знайдено',
            p_customer_id;
END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM employee
        WHERE id = p_employee_id
          AND is_deleted = FALSE
    ) THEN
        RAISE EXCEPTION
            'Активного працівника % не знайдено',
            p_employee_id;
END IF;

INSERT INTO sales_order (
    customer_id,
    employee_id,
    order_date,
    total_amount
)
VALUES (
           p_customer_id,
           p_employee_id,
           NOW(),
           0
       )
    RETURNING id INTO p_order_id;
END;
$$;


-- 10. Списання товару зі складу

CREATE OR REPLACE PROCEDURE sp_deduct_inventory(
    p_product_id INT,
    p_quantity INT,
    p_user_id INT
)
LANGUAGE plpgsql AS $$
BEGIN
    IF p_quantity <= 0 THEN
        RAISE EXCEPTION
            'Кількість має бути додатною';
END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM product
        WHERE id = p_product_id
          AND is_deleted = FALSE
    ) THEN
        RAISE EXCEPTION
            'Товар % відсутній або видалений',
            p_product_id;
END IF;

UPDATE inventory
SET
    quantity = quantity - p_quantity,
    updated_by = p_user_id
WHERE product_id = p_product_id
  AND quantity >= p_quantity;

IF NOT FOUND THEN
        RAISE EXCEPTION
            'Недостатньо товару % на складі',
            p_product_id;
END IF;
END;
$$;


-- 11. Додавання позиції до замовлення

CREATE OR REPLACE PROCEDURE sp_add_order_item(
    p_order_id INT,
    p_product_id INT,
    p_quantity INT,
    p_price DECIMAL
)
LANGUAGE plpgsql AS $$
DECLARE
v_price DECIMAL;
BEGIN
    IF p_quantity <= 0 THEN
        RAISE EXCEPTION
            'Кількість має бути додатною';
END IF;

SELECT price INTO v_price
FROM product
WHERE id = p_product_id
  AND is_deleted = FALSE;

IF NOT FOUND THEN
        RAISE EXCEPTION
            'Активний товар % не знайдений',
            p_product_id;
END IF;

    IF v_price IS NULL
       OR p_price IS NULL
       OR v_price <> p_price THEN
        RAISE EXCEPTION
            'Ціна товару змінилася: очікується %, отримано %',
            v_price, p_price;
END IF;

INSERT INTO sales_order_item (
    sales_order_id,
    product_id,
    quantity,
    unit_price
)
VALUES (
           p_order_id,
           p_product_id,
           p_quantity,
           v_price
       );

UPDATE sales_order
SET total_amount =
        total_amount + (p_quantity * v_price)
WHERE id = p_order_id;

IF NOT FOUND THEN
        RAISE EXCEPTION
            'Замовлення % не знайдено',
            p_order_id;
END IF;
END;
$$;


-- 12. Оновлення email працівника

CREATE OR REPLACE PROCEDURE sp_update_employee_email(
    p_employee_id INT,
    p_email VARCHAR,
    p_user_id INT
)
LANGUAGE plpgsql AS $$
BEGIN
UPDATE employee
SET
    email = p_email,
    updated_by = p_user_id
WHERE id = p_employee_id
  AND is_deleted = FALSE;

IF NOT FOUND THEN
        RAISE EXCEPTION
            'Працівника не знайдено: %',
            p_employee_id;
END IF;
END;
$$;


-- 13. Зміна ціни з веденням історії

CREATE OR REPLACE PROCEDURE sp_change_product_price(
    p_product_id INT,
    p_new_price DECIMAL,
    p_user_id INT
)
LANGUAGE plpgsql AS $$
DECLARE
v_old_price DECIMAL;
BEGIN
    IF p_new_price IS NULL
       OR p_new_price < 0 THEN
        RAISE EXCEPTION 'Неправильна ціна';
END IF;

SELECT price INTO v_old_price
FROM product
WHERE id = p_product_id
  AND is_deleted = FALSE
    FOR UPDATE;

IF NOT FOUND THEN
        RAISE EXCEPTION
            'Товар не знайдено: %',
            p_product_id;
END IF;

    IF v_old_price IS DISTINCT FROM p_new_price THEN

UPDATE product
SET
    price = p_new_price,
    updated_by = p_user_id
WHERE id = p_product_id;

INSERT INTO price_history (
    product_id,
    old_price,
    new_price,
    changed_at,
    changed_by
)
VALUES (
           p_product_id,
           v_old_price,
           p_new_price,
           NOW(),
           p_user_id
       );
END IF;
END;
$$;
