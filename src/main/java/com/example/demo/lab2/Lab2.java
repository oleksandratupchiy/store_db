package com.example.demo.lab2;

import com.mongodb.client.*;
import org.bson.Document;
import org.flywaydb.core.Flyway;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Explicit CLI, deliberately independent of Spring startup and DB_URL writes. */
public final class Lab2 {
    private Lab2() {}
    static String env(String key,String fallback) {return System.getenv().getOrDefault(key,fallback);}
    static String schema(int count,long seed) {return "lab2_n"+count+"_s"+seed;}
    static Connection connect(boolean source) throws SQLException {
        Properties properties=new Properties();
        properties.setProperty("user",source?env("DB_USER","postgres"):env("LAB2_PG_USER","lab2"));
        properties.setProperty("password",source?env("DB_PASSWORD",""):env("LAB2_PG_PASSWORD","lab2_local_only"));
        properties.setProperty("connectTimeout","5");
        if(!source) properties.setProperty("reWriteBatchedInserts","true");
        return DriverManager.getConnection(source?env("DB_URL","jdbc:postgresql://localhost:5432/store_db"):env("LAB2_PG_URL","jdbc:postgresql://localhost:55432/store_db_benchmark"),properties);
    }
    public static void main(String[] args) throws Exception {
        if(args.length>0 && args[0].equals("suite")) {
            String sizes=args.length>1?args[1]:"1000,10000,100000";
            String seed=args.length>2?args[2]:"42",repeats=args.length>3?args[3]:"200",warmup=args.length>4?args[4]:"50";
            boolean reuse=args.length>5 && Boolean.parseBoolean(args[5]);
            for(String size:sizes.split(",")) {
                if(!reuse) main(new String[]{"generate",size,seed});
                main(new String[]{"import",size,seed});
                main(new String[]{"benchmark",size,seed,repeats,warmup});
            }
            return;
        }
        if(args.length==0) throw new IllegalArgumentException("Commands: generate|import|verify|benchmark|show|import-source [count=1000] [seed=42] [repeats=200] [warmup=50]");
        String command=args[0];
        int count=args.length>1?Integer.parseInt(args[1]):1000;
        long seed=args.length>2?Long.parseLong(args[2]):42;
        if(count<1 || count>1_000_000 || seed<0) throw new IllegalArgumentException("count=1..1000000; seed>=0");
        boolean source=command.equals("import-source");
        if(!List.of("generate","import","verify","benchmark","show","import-source").contains(command)) throw new IllegalArgumentException("Unknown command");
        String schema=schema(count,seed);
        try(Connection c=connect(source)) {
            if(!source) {
                Generator.guard(c);
                if(command.equals("generate")) {
                    Flyway.configure().dataSource(env("LAB2_PG_URL","jdbc:postgresql://localhost:55432/store_db_benchmark"),env("LAB2_PG_USER","lab2"),env("LAB2_PG_PASSWORD","lab2_local_only"))
                        .schemas(schema).defaultSchema(schema).locations("classpath:db/migration","classpath:db/lab2").load().migrate();
                    c.setSchema(schema);Generator.generate(c,count,seed);return;
                }
                c.setSchema(schema);
            }
            c.setReadOnly(true);c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);c.setAutoCommit(false);
            History.Sql sql=new History.Sql(c);
            String db=source?"lab2_import":schema;
            try(MongoClient client=MongoClients.create(env("LAB2_MONGO_URI","mongodb://localhost:27017/?serverSelectionTimeoutMS=5000"))) {
                History.Mongo mongo=new History.Mongo(client.getDatabase(db).getCollection("order_history"));
                if(command.equals("import") || source) {
                    mongo.indexes();int after=Integer.MIN_VALUE,total=0;
                    while(true) {
                        List<Document> page=sql.page(after,500);if(page.isEmpty()) break;
                        mongo.upsert(page);total+=page.size();after=page.get(page.size()-1).getInteger("_id");
                    }
                    System.out.println("Upserted completed orders="+total+" MongoDB="+db+".order_history");
                    List<Document> sample=sql.page(Integer.MIN_VALUE,10);
                    for(Document d:sample) Benchmark.equal(sql.byId(d.getInteger("_id")),mongo.byId(d.getInteger("_id")));
                    System.out.println("Import sample equivalence PASS; checked="+sample.size());
                } else if(command.equals("show")) {
                    System.out.println("SQL: "+sql.byId(1));System.out.println("Mongo: "+mongo.byId(1));
                    Benchmark.equal(sql.byId(1),mongo.byId(1));
                } else {
                    int repeats=command.equals("verify")?1:(args.length>3?Integer.parseInt(args[3]):200);
                    int warmup=command.equals("verify")?0:(args.length>4?Integer.parseInt(args[4]):50);
                    Path output=Path.of("testing/results/"+command+"_"+schema+"_"+System.currentTimeMillis()+".csv");
                    Benchmark.run(sql,mongo,count,seed,warmup,repeats,output);
                    System.out.println("CSV: "+output);
                }
            } finally {c.rollback();}
        }
    }
}
