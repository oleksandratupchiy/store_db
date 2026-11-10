-- Reserved regression schema in the dedicated Compose benchmark database only.
BEGIN;
DO $$ BEGIN
  IF current_database() <> 'store_db_benchmark' THEN
    RAISE EXCEPTION 'Regression fixtures require store_db_benchmark';
  END IF;
END $$;
SET LOCAL search_path TO lab2_n10_s900001;
INSERT INTO customer(id,full_name,loyalty_points)
VALUES(900001,'Test Customer',0) ON CONFLICT(id) DO NOTHING;
INSERT INTO employee(id,department_id,role_id,first_name,last_name,email)
VALUES(900001,1,1,'Test','Employee','regression@example.invalid') ON CONFLICT(id) DO NOTHING;
INSERT INTO product(id,category_id,sku,name,price)
VALUES(900001,1,'DEMO-900001','Test product',500.00) ON CONFLICT(id) DO NOTHING;
INSERT INTO inventory(id,product_id,quantity,updated_by)
VALUES(900001,900001,100,900001)
ON CONFLICT(product_id) DO UPDATE SET quantity=100;
COMMIT;
