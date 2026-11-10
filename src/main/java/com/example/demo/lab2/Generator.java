package com.example.demo.lab2;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.Random;

/** Writes only into a newly created schema in store_db_benchmark. Never deletes. */
final class Generator {
    private Generator() {}
    static void guard(Connection c) throws SQLException {
        try(var s=c.createStatement();var r=s.executeQuery("SELECT current_database()")) {
            r.next(); if(!"store_db_benchmark".equals(r.getString(1)))
                throw new IllegalArgumentException("Writes allowed only in store_db_benchmark");
        }
    }
    static void generate(Connection c,int count,long seed) throws SQLException {
        guard(c);
        try(var s=c.createStatement();var r=s.executeQuery("SELECT COUNT(*) FROM sales_order")) {
            r.next(); if(r.getLong(1)!=0) throw new IllegalStateException("Dataset already exists; reuse it or choose another count/seed. No rows deleted.");
        }
        c.setAutoCommit(false);
        int customers=Math.max(10,count/100);
        try {
            try(var s=c.createStatement()) {
                s.executeUpdate("INSERT INTO store(id,name) VALUES(1,'Benchmark store')");
                s.executeUpdate("INSERT INTO department(id,store_id,name) VALUES(1,1,'Sales')");
                s.executeUpdate("INSERT INTO role(id,name) VALUES(1,'Cashier')");
                s.executeUpdate("INSERT INTO employee(id,department_id,role_id,first_name,last_name,email) VALUES(1,1,1,'Lab','Cashier','lab@example.invalid')");
                s.executeUpdate("INSERT INTO category(id,department_id,name) VALUES(1,1,'Demo products')");
            }
            try(var s=c.prepareStatement("INSERT INTO customer(id,full_name,loyalty_points) VALUES(?,?,0)")) {
                for(int i=1;i<=customers;i++){s.setInt(1,i);s.setString(2,"Customer "+i);s.addBatch();} s.executeBatch();
            }
            try(var s=c.prepareStatement("INSERT INTO product(id,category_id,sku,name,price) VALUES(?,1,?,?,?)")) {
                for(int i=1;i<=100;i++){s.setInt(1,i);s.setString(2,"LAB2-"+i);s.setString(3,"Product "+i);s.setBigDecimal(4,price(i));s.addBatch();}s.executeBatch();
            }
            Random random=new Random(seed);
            try(var orders=c.prepareStatement("INSERT INTO sales_order(id,customer_id,employee_id,order_date,total_amount) VALUES(?,?,1,?,?)");
                var items=c.prepareStatement("INSERT INTO sales_order_item(id,sales_order_id,product_id,quantity,unit_price) VALUES(?,?,?,?,?)");
                var payments=c.prepareStatement("INSERT INTO payment(id,sales_order_id,payment_method,amount,payment_date) VALUES(?,?,?,?,?)")) {
                int itemId=0,paymentId=0;
                for(int id=1;id<=count;id++) {
                    LocalDateTime date=LocalDateTime.of(2025,1,1,0,0).plusSeconds(random.nextInt(365*86400));
                    BigDecimal total=BigDecimal.ZERO;
                    int itemCount=3+random.nextInt(5);
                    for(int j=0;j<itemCount;j++) {
                        int product=1+random.nextInt(100),qty=1+random.nextInt(3);
                        BigDecimal unit=price(product);total=total.add(unit.multiply(BigDecimal.valueOf(qty)));
                        items.setInt(1,++itemId);items.setInt(2,id);items.setInt(3,product);items.setInt(4,qty);items.setBigDecimal(5,unit);items.addBatch();
                    }
                    orders.setInt(1,id);orders.setInt(2,1+random.nextInt(customers));orders.setObject(3,date);orders.setBigDecimal(4,total);orders.addBatch();
                    // Some orders have split payments: exposes accidental items x payments joins.
                    BigDecimal first=id%3==0 ? total.divide(BigDecimal.valueOf(2),2,java.math.RoundingMode.DOWN) : total;
                    int parts=id%3==0 ? 2 : 1;
                    for(int j=0;j<parts;j++) {
                        payments.setInt(1,++paymentId);payments.setInt(2,id);payments.setString(3,j==0?"CARD":"CASH");
                        payments.setBigDecimal(4,j==0?first:total.subtract(first));payments.setObject(5,date.plusMinutes(1+j));payments.addBatch();
                    }
                    if(id%500==0 || id==count){orders.executeBatch();items.executeBatch();payments.executeBatch();}
                }
            }
            // Explicit generated IDs must not leave SERIAL sequences behind.
            try(var s=c.createStatement()) {
                for(String table:new String[]{"store","department","role","employee","category","product","customer","sales_order","sales_order_item","payment"})
                    s.execute("SELECT setval(pg_get_serial_sequence('"+table+"','id'),(SELECT MAX(id) FROM "+table+"))");
            }
            c.commit();
        } catch(SQLException|RuntimeException e){c.rollback();throw e;}
        finally {c.setAutoCommit(true);}
        try(var s=c.createStatement()){s.execute("ANALYZE");}
        System.out.printf("Generated orders=%d seed=%d customers=%d%n",count,seed,customers);
    }
    static BigDecimal price(int product) {return BigDecimal.valueOf(1000L+product*37L,2);}
}
