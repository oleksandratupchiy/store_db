package com.example.demo.lab2;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.*;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.Decimal128;

import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;

/** The same materialized BSON-compatible value is returned by both stores. */
public final class History {
    private History() {}
    public static Decimal128 money(BigDecimal value) {
        return new Decimal128(value.stripTrailingZeros());
    }
    // Lab 1 uses TIMESTAMP WITHOUT TIME ZONE. Interpret wall-clock values as UTC.
    public static java.util.Date date(LocalDateTime value) {
        return value == null ? null : java.util.Date.from(value.toInstant(ZoneOffset.UTC));
    }
    public interface Reader {
        List<Document> byId(int id) throws SQLException;
        List<Document> latest(int customer, int limit) throws SQLException;
        List<Document> range(java.util.Date from, java.util.Date to, int limit) throws SQLException;
        Document stats(int customer) throws SQLException;
    }
    public static final class Sql implements Reader {
        private final Connection c;
        public Sql(Connection c) { this.c = c; }
        // No completion status exists in V1/V2: paid, positive, nonempty orders only.
        static final String COMPLETE = """
            o.total_amount > 0
            AND EXISTS (SELECT 1 FROM sales_order_item i WHERE i.sales_order_id=o.id)
            AND (SELECT COALESCE(SUM(p.amount),0) FROM payment p WHERE p.sales_order_id=o.id)>=o.total_amount
            """;
        private static final String HEADER = """
            SELECT o.id,o.employee_id,o.order_date,o.total_amount,c.id AS cid,c.full_name
            FROM sales_order o LEFT JOIN customer c ON c.id=o.customer_id WHERE
            """ + COMPLETE;
        private List<Document> read(String suffix, Object... args) throws SQLException {
            List<Document> orders = new ArrayList<>();
            try (var s = c.prepareStatement(HEADER + suffix)) {
                for (int i=0;i<args.length;i++) s.setObject(i+1,args[i]);
                try (var r=s.executeQuery()) {
                    while(r.next()) orders.add(new Document("_id",r.getInt("id"))
                        .append("customer",new Document("id",r.getObject("cid")).append("name",r.getString("full_name")))
                        .append("employee_id",r.getObject("employee_id"))
                        .append("order_date",date(r.getObject("order_date",LocalDateTime.class)))
                        .append("total_amount",money(r.getBigDecimal("total_amount")))
                        .append("items",new ArrayList<Document>()).append("payments",new ArrayList<Document>()));
                }
            }
            if (orders.isEmpty()) return orders;
            Map<Integer,Document> byId=new HashMap<>();
            orders.forEach(d->byId.put(d.getInteger("_id"),d));
            String slots=String.join(",",Collections.nCopies(orders.size(),"?"));
            try(var s=c.prepareStatement("SELECT i.*,p.name FROM sales_order_item i LEFT JOIN product p ON p.id=i.product_id WHERE i.sales_order_id IN ("+slots+") ORDER BY i.id")) {
                for(int i=0;i<orders.size();i++) s.setInt(i+1,orders.get(i).getInteger("_id"));
                try(var r=s.executeQuery()) { while(r.next()) byId.get(r.getInt("sales_order_id")).getList("items",Document.class).add(
                    new Document("id",r.getInt("id")).append("product_id",r.getObject("product_id"))
                    .append("name",r.getString("name")).append("quantity",r.getInt("quantity"))
                    .append("unit_price",money(r.getBigDecimal("unit_price")))); }
            }
            try(var s=c.prepareStatement("SELECT * FROM payment WHERE sales_order_id IN ("+slots+") ORDER BY id")) {
                for(int i=0;i<orders.size();i++) s.setInt(i+1,orders.get(i).getInteger("_id"));
                try(var r=s.executeQuery()) { while(r.next()) byId.get(r.getInt("sales_order_id")).getList("payments",Document.class).add(
                    new Document("id",r.getInt("id")).append("method",r.getString("payment_method"))
                    .append("amount",money(r.getBigDecimal("amount")))
                    .append("date",date(r.getObject("payment_date",LocalDateTime.class)))); }
            }
            return orders;
        }
        public List<Document> page(int after, int limit) throws SQLException {
            return read(" AND o.id>? ORDER BY o.id LIMIT ?",after,limit);
        }
        public List<Document> byId(int id) throws SQLException { return read(" AND o.id=?",id); }
        public List<Document> latest(int customer,int limit) throws SQLException {
            return read(" AND o.customer_id=? ORDER BY o.order_date DESC,o.id DESC LIMIT ?",customer,limit);
        }
        public List<Document> range(java.util.Date from,java.util.Date to,int limit) throws SQLException {
            return read(" AND o.order_date>=? AND o.order_date<? ORDER BY o.order_date DESC,o.id DESC LIMIT ?",
                LocalDateTime.ofInstant(from.toInstant(),ZoneOffset.UTC),LocalDateTime.ofInstant(to.toInstant(),ZoneOffset.UTC),limit);
        }
        public Document stats(int customer) throws SQLException {
            try(var s=c.prepareStatement("SELECT COUNT(*),COALESCE(SUM(o.total_amount),0) FROM sales_order o WHERE "+COMPLETE+" AND o.customer_id=?")) {
                s.setInt(1,customer);
                try(var r=s.executeQuery()) { r.next(); return new Document("count",r.getLong(1)).append("total",money(r.getBigDecimal(2))); }
            }
        }
        public long count() throws SQLException {
            try(var s=c.createStatement();var r=s.executeQuery("SELECT COUNT(*) FROM sales_order o WHERE "+COMPLETE)) {r.next();return r.getLong(1);}
        }
    }
    public static final class Mongo implements Reader {
        public final MongoCollection<Document> collection;
        public Mongo(MongoCollection<Document> collection) { this.collection=collection; }
        public void indexes() {
            collection.createIndex(Indexes.compoundIndex(Indexes.ascending("customer.id"),Indexes.descending("order_date","_id")));
            collection.createIndex(Indexes.descending("order_date","_id"));
        }
        public void upsert(List<Document> docs) {
            if(docs.isEmpty()) return;
            List<WriteModel<Document>> writes=new ArrayList<>();
            docs.forEach(d->writes.add(new ReplaceOneModel<>(Filters.eq("_id",d.getInteger("_id")),d,new ReplaceOptions().upsert(true))));
            collection.bulkWrite(writes,new BulkWriteOptions().ordered(false));
        }
        public List<Document> byId(int id) { return collection.find(Filters.eq("_id",id)).into(new ArrayList<>()); }
        public List<Document> latest(int customer,int limit) {
            return collection.find(Filters.eq("customer.id",customer)).sort(Sorts.descending("order_date","_id")).limit(limit).into(new ArrayList<>());
        }
        public List<Document> range(java.util.Date from,java.util.Date to,int limit) {
            return collection.find(Filters.and(Filters.gte("order_date",from),Filters.lt("order_date",to)))
                .sort(Sorts.descending("order_date","_id")).limit(limit).into(new ArrayList<>());
        }
        public Document stats(int customer) {
            Document d=collection.aggregate(List.of(Aggregates.match(Filters.eq("customer.id",customer)),
                Aggregates.group(null,Accumulators.sum("count",1),Accumulators.sum("total","$total_amount")))).first();
            if(d==null) return new Document("count",0L).append("total",money(BigDecimal.ZERO));
            return new Document("count",((Number)d.get("count")).longValue()).append("total",money(d.get("total",Decimal128.class).bigDecimalValue()));
        }
    }
}
