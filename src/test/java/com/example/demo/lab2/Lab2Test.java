package com.example.demo.lab2;

import org.junit.jupiter.api.Test;
import org.bson.Document;
import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class Lab2Test {
    @Test void percentilesUseNearestRankAndDoNotMutateSamples() {
        long[] v=new long[200];for(int i=0;i<200;i++)v[i]=(200-i)*1_000_000L;
        assertEquals(100,Benchmark.percentile(v,.5));assertEquals(190,Benchmark.percentile(v,.95));
        assertEquals(200_000_000L,v[0]);assertThrows(IllegalArgumentException.class,()->Benchmark.percentile(new long[0],.95));
    }
    @Test void canonicalMoneyAndUtcMillisecondsAreStable() {
        assertEquals(History.money(new BigDecimal("12.00")),History.money(new BigDecimal("12.0")));
        assertEquals(Instant.parse("2025-01-01T00:00:00.123Z"),History.date(LocalDateTime.parse("2025-01-01T00:00:00.123456")).toInstant());
    }
    @Test void generatorRejectsMainDatabaseBeforeAnyWrite() throws Exception {
        Connection c=mock(Connection.class);Statement s=mock(Statement.class);ResultSet r=mock(ResultSet.class);
        when(c.createStatement()).thenReturn(s);when(s.executeQuery(anyString())).thenReturn(r);
        when(r.getString(1)).thenReturn("store_db");
        assertThrows(IllegalArgumentException.class,()->Generator.generate(c,100,42));
        verify(c,never()).setAutoCommit(false);verify(s,never()).executeUpdate(anyString());
    }
    @Test void sqlMappingKeepsItemsAndPaymentsSeparate() throws Exception {
        Connection c=mock(Connection.class);
        PreparedStatement header=mock(PreparedStatement.class),items=mock(PreparedStatement.class),payments=mock(PreparedStatement.class);
        ResultSet h=mock(ResultSet.class),i=mock(ResultSet.class),p=mock(ResultSet.class);
        when(c.prepareStatement(startsWith("SELECT o.id"))).thenReturn(header);
        when(c.prepareStatement(startsWith("SELECT i.*"))).thenReturn(items);
        when(c.prepareStatement(startsWith("SELECT * FROM payment"))).thenReturn(payments);
        when(header.executeQuery()).thenReturn(h);when(items.executeQuery()).thenReturn(i);when(payments.executeQuery()).thenReturn(p);
        when(h.next()).thenReturn(true,false);when(h.getInt("id")).thenReturn(7);
        when(h.getObject("cid")).thenReturn(2);when(h.getString("full_name")).thenReturn("Customer");
        when(h.getBigDecimal("total_amount")).thenReturn(new BigDecimal("30.00"));
        when(i.next()).thenReturn(true,true,false);when(i.getInt("sales_order_id")).thenReturn(7);
        when(i.getInt("id")).thenReturn(1,2);when(i.getInt("quantity")).thenReturn(1);
        when(i.getBigDecimal("unit_price")).thenReturn(new BigDecimal("15.00"));
        when(p.next()).thenReturn(true,true,false);when(p.getInt("sales_order_id")).thenReturn(7);
        when(p.getInt("id")).thenReturn(1,2);when(p.getBigDecimal("amount")).thenReturn(new BigDecimal("15.00"));
        Document d=new History.Sql(c).byId(7).get(0);
        assertEquals(2,d.getList("items",Document.class).size());assertEquals(2,d.getList("payments",Document.class).size());
        assertEquals(History.money(new BigDecimal("30")),d.get("total_amount"));
        verify(header).setObject(1,7);verify(items).setInt(1,7);verify(payments).setInt(1,7);
    }
}
