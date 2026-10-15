
-- Тестові дані для лабораторної роботи
-- ID 900001 зарезервовано для демонстрації

-- 1. Магазин
INSERT INTO store (id, name, address)
VALUES (900001, 'Demo Store', 'Kyiv')
    ON CONFLICT (id) DO NOTHING;

-- 2. Відділ
INSERT INTO department (id, store_id, name)
VALUES (900001, 900001, 'Electronics')
    ON CONFLICT (id) DO NOTHING;

-- 3. Роль
INSERT INTO role (id, name)
VALUES (900001, 'Cashier')
    ON CONFLICT (id) DO NOTHING;

-- 4. Працівник
INSERT INTO employee (
    id, department_id, role_id,
    first_name, last_name, email, is_deleted
)
VALUES (
           900001, 900001, 900001,
           'Test', 'Cashier',
           'cashier900001@example.com', FALSE
       )
    ON CONFLICT (id) DO NOTHING;

-- 5. Категорія
INSERT INTO category (id, department_id, name)
VALUES (900001, 900001, 'Accessories')
    ON CONFLICT (id) DO NOTHING;

-- 6. Покупець
INSERT INTO customer (
    id, phone, full_name,
    loyalty_points, is_deleted
)
VALUES (
           900001, '+380000000001',
           'Test Customer', 0, FALSE
       )
    ON CONFLICT (id) DO NOTHING;

-- 7. Товар
INSERT INTO product (
    id, category_id, sku, name,
    price, is_deleted
)
VALUES (
           900001, 900001,
           'DEMO-900001', 'Demo Mouse',
           500.00, FALSE
       )
    ON CONFLICT (id) DO NOTHING;

-- 8. Складські залишки
INSERT INTO inventory (
    id, product_id, quantity
)
VALUES (900001, 900001, 20)
    ON CONFLICT (id) DO NOTHING;

-- 9. Перевірка через VIEW
SELECT *
FROM v_active_products_inventory
WHERE id = 900001;
