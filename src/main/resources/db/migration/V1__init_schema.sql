-- 1. Створення таблиць (16 сутностей) у правильному порядку для Foreign Keys
CREATE TABLE store (id SERIAL PRIMARY KEY, name VARCHAR, address VARCHAR);
CREATE TABLE department (id SERIAL PRIMARY KEY, store_id INT REFERENCES store(id), name VARCHAR);
CREATE TABLE role (id SERIAL PRIMARY KEY, name VARCHAR);
CREATE TABLE employee (id SERIAL PRIMARY KEY, department_id INT REFERENCES department(id), role_id INT REFERENCES role(id), first_name VARCHAR, last_name VARCHAR, email VARCHAR, is_deleted BOOLEAN DEFAULT FALSE, deleted_at TIMESTAMP, updated_at TIMESTAMP, updated_by INT);
CREATE TABLE category (id SERIAL PRIMARY KEY, department_id INT REFERENCES department(id), name VARCHAR);
CREATE TABLE product (id SERIAL PRIMARY KEY, category_id INT REFERENCES category(id), sku VARCHAR, name VARCHAR, price DECIMAL, is_deleted BOOLEAN DEFAULT FALSE, deleted_at TIMESTAMP, updated_at TIMESTAMP, updated_by INT);
CREATE TABLE inventory (id SERIAL PRIMARY KEY, product_id INT REFERENCES product(id), quantity INT, updated_at TIMESTAMP, updated_by INT);
CREATE TABLE supplier (id SERIAL PRIMARY KEY, name VARCHAR, contact_info VARCHAR, is_deleted BOOLEAN DEFAULT FALSE);
CREATE TABLE supply_order (id SERIAL PRIMARY KEY, supplier_id INT REFERENCES supplier(id), order_date TIMESTAMP, status VARCHAR);
CREATE TABLE supply_order_item (id SERIAL PRIMARY KEY, supply_order_id INT REFERENCES supply_order(id), product_id INT REFERENCES product(id), quantity INT, cost_price DECIMAL);
CREATE TABLE customer (id SERIAL PRIMARY KEY, phone VARCHAR, full_name VARCHAR, loyalty_points INT, is_deleted BOOLEAN DEFAULT FALSE);
CREATE TABLE sales_order (id SERIAL PRIMARY KEY, customer_id INT REFERENCES customer(id), employee_id INT REFERENCES employee(id), order_date TIMESTAMP, total_amount DECIMAL);
CREATE TABLE sales_order_item (id SERIAL PRIMARY KEY, sales_order_id INT REFERENCES sales_order(id), product_id INT REFERENCES product(id), quantity INT, unit_price DECIMAL);
CREATE TABLE payment (id SERIAL PRIMARY KEY, sales_order_id INT REFERENCES sales_order(id), payment_method VARCHAR, amount DECIMAL, payment_date TIMESTAMP);
CREATE TABLE shift (id SERIAL PRIMARY KEY, employee_id INT REFERENCES employee(id), start_time TIMESTAMP, end_time TIMESTAMP);
CREATE TABLE price_history (id SERIAL PRIMARY KEY, product_id INT REFERENCES product(id), old_price DECIMAL, new_price DECIMAL, changed_at TIMESTAMP, changed_by INT);

-- 2. Індекси
CREATE INDEX idx_product_name ON product USING btree (name);
CREATE INDEX idx_product_sku ON product USING hash (sku);
CREATE INDEX idx_employee_dept ON employee USING btree (department_id);

-- 3. Функція та Тригери для Аудиту
CREATE OR REPLACE FUNCTION fn_update_timestamp() RETURNS TRIGGER AS $$
BEGIN NEW.updated_at = NOW(); RETURN NEW; END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_employee_audit BEFORE UPDATE ON employee FOR EACH ROW EXECUTE FUNCTION fn_update_timestamp();
CREATE TRIGGER trg_product_audit BEFORE UPDATE ON product FOR EACH ROW EXECUTE FUNCTION fn_update_timestamp();

-- 4. Розрізи даних (Views)
CREATE OR REPLACE VIEW v_active_products_inventory AS
SELECT p.id, p.sku, p.name, p.price, COALESCE(i.quantity, 0) as quantity
FROM product p LEFT JOIN inventory i ON p.id = i.product_id
WHERE p.is_deleted = false;

CREATE OR REPLACE VIEW v_active_employees AS
SELECT id, first_name, last_name, email, department_id
FROM employee WHERE is_deleted = false;

CREATE OR REPLACE VIEW v_sales_details AS
SELECT so.id as order_id, so.order_date, c.full_name as customer_name, so.total_amount
FROM sales_order so JOIN customer c ON so.customer_id = c.id
WHERE c.is_deleted = false;

-- 5. Збережені процедури
CREATE OR REPLACE PROCEDURE sp_soft_delete_product(p_product_id INT, p_user_id INT)
LANGUAGE plpgsql AS $$
BEGIN
UPDATE product SET is_deleted = true, deleted_at = NOW(), updated_by = p_user_id WHERE id = p_product_id;
END;
$$;

CREATE OR REPLACE PROCEDURE sp_create_sales_order(p_customer_id INT, p_employee_id INT, OUT p_order_id INT)
LANGUAGE plpgsql AS $$
BEGIN
INSERT INTO sales_order (customer_id, employee_id, order_date, total_amount) VALUES (p_customer_id, p_employee_id, NOW(), 0) RETURNING id INTO p_order_id;
END;
$$;

CREATE OR REPLACE PROCEDURE sp_add_order_item(p_order_id INT, p_product_id INT, p_quantity INT, p_price DECIMAL)
LANGUAGE plpgsql AS $$
BEGIN
INSERT INTO sales_order_item (sales_order_id, product_id, quantity, unit_price) VALUES (p_order_id, p_product_id, p_quantity, p_price);
UPDATE sales_order SET total_amount = total_amount + (p_quantity * p_price) WHERE id = p_order_id;
END;
$$;

CREATE OR REPLACE PROCEDURE sp_deduct_inventory(p_product_id INT, p_quantity INT, p_user_id INT)
LANGUAGE plpgsql AS $$
BEGIN
UPDATE inventory SET quantity = quantity - p_quantity, updated_by = p_user_id, updated_at = NOW() WHERE product_id = p_product_id AND quantity >= p_quantity;
IF NOT FOUND THEN RAISE EXCEPTION 'Недостатньо товару на складі'; END IF;
END;
$$;