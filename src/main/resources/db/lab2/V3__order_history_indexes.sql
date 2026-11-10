-- Applied explicitly by the CLI only to the separate benchmark database.
CREATE INDEX idx_history_customer_date ON sales_order(customer_id, order_date DESC, id DESC);
CREATE INDEX idx_history_date ON sales_order(order_date DESC, id DESC);
CREATE INDEX idx_history_item_order ON sales_order_item(sales_order_id, id);
CREATE INDEX idx_history_payment_order ON payment(sales_order_id, id);
