package com.example.demo.lab2;

import org.bson.Document;
import java.nio.file.*;
import java.io.*;
import java.time.Instant;
import java.util.*;

final class Benchmark {
    private Benchmark() {}
    interface Query {Object run(History.Reader r,int iteration) throws Exception;}
    static volatile Object sink;
    static double percentile(long[] values,double p) {
        if(values.length==0 || p<=0 || p>1) throw new IllegalArgumentException("Invalid percentile");
        long[] copy=values.clone();Arrays.sort(copy);
        return copy[(int)Math.ceil(p*copy.length)-1]/1_000_000.0;
    }
    static void equal(Object sql,Object mongo) {
        if(!Objects.equals(sql,mongo)) throw new IllegalStateException("SQL/Mongo mismatch:\nSQL="+sql+"\nMongo="+mongo);
    }
    static void run(History.Sql sql,History.Mongo mongo,int count,long seed,int warmup,int repeats,Path output) throws Exception {
        if(repeats<1 || warmup<0) throw new IllegalArgumentException("Invalid repetitions");
        if(sql.count()!=count || mongo.collection.countDocuments()!=count) throw new IllegalStateException("Dataset count mismatch; generate/import first");
        Random rng=new Random(seed);
        int[] ids=new int[Math.max(repeats,Math.max(warmup,20))];
        for(int i=0;i<ids.length;i++) ids[i]=1+rng.nextInt(count);
        int customers=Math.max(10,count/100);
        List<String> names=List.of("getOrderById","getLatestOrdersForCustomer","getOrdersByDateRange","getCustomerStats");
        List<Query> queries=List.of((r,i)->r.byId(ids[i%ids.length]),
            (r,i)->r.latest(1+ids[i%ids.length]%customers,20),
            (r,i)->{Instant from=Instant.parse("2025-01-01T00:00:00Z").plusSeconds((ids[i%ids.length]%330)*86400L);
                return r.range(Date.from(from),Date.from(from.plusSeconds(30*86400L)),20);},
            (r,i)->r.stats(1+ids[i%ids.length]%customers));
        // Validate all four scenarios before writing any benchmark result.
        for(Query q:queries) for(int i=0;i<20;i++) equal(q.run(sql,i),q.run(mongo,i));
        equal(sql.byId(count+1),mongo.byId(count+1));
        equal(sql.latest(customers+1,20),mongo.latest(customers+1,20));
        equal(sql.stats(customers+1),mongo.stats(customers+1));
        System.out.println("Equivalence PASS: 80 scenario samples + missing ID/customer");
        Files.createDirectories(output.toAbsolutePath().getParent());
        try(var csv=Files.newBufferedWriter(output,StandardOpenOption.CREATE_NEW)) {
            csv.write("timestamp_utc,orders,seed,scenario,store,warmup,repetitions,median_ms,p95_ms,mean_ms\n");
            for(int scenario=0;scenario<queries.size();scenario++) {
                Query q=queries.get(scenario);
                for(int i=0;i<warmup;i++){sink=q.run(sql,i);sink=q.run(mongo,i);}
                long[][] samples=new long[2][repeats];
                for(int i=0;i<repeats;i++) for(int turn=0;turn<2;turn++) {
                    int store=(i+turn)%2;History.Reader reader=store==0?sql:mongo;
                    long start=System.nanoTime();sink=q.run(reader,i);samples[store][i]=System.nanoTime()-start;
                }
                for(int store=0;store<2;store++) {
                    long[] v=samples[store];
                    String row=String.format(Locale.ROOT,"%s,%d,%d,%s,%s,%d,%d,%.6f,%.6f,%.6f%n",Instant.now(),count,seed,names.get(scenario),
                        store==0?"postgresql":"mongodb",warmup,repeats,percentile(v,.5),percentile(v,.95),Arrays.stream(v).average().orElseThrow()/1_000_000.0);
                    csv.write(row);System.out.print(row);
                }
                csv.flush();
            }
        }
    }
}
