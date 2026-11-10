-- psql -v schema=lab2_n100000_s42 -f testing/check-dataset.sql
BEGIN READ ONLY;
SET LOCAL search_path TO :"schema";
WITH item_totals AS (
  SELECT sales_order_id,COUNT(*) AS n,SUM(quantity*unit_price) AS amount
  FROM sales_order_item GROUP BY sales_order_id
), payment_totals AS (
  SELECT sales_order_id,COUNT(*) AS n,SUM(amount) AS amount
  FROM payment GROUP BY sales_order_id
)
SELECT current_schema() AS dataset,COUNT(*) AS orders,
       SUM(i.n) AS items,SUM(p.n) AS payments,
       ROUND(AVG(i.n),4) AS items_per_order,
       COUNT(*) FILTER (WHERE o.total_amount IS DISTINCT FROM i.amount
                         OR o.total_amount IS DISTINCT FROM p.amount) AS invalid_totals
FROM sales_order o
LEFT JOIN item_totals i ON i.sales_order_id=o.id
LEFT JOIN payment_totals p ON p.sales_order_id=o.id;
ROLLBACK;
