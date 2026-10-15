package com.example.demo.uow;

import org.springframework.stereotype.Component;
import javax.sql.DataSource;
import java.sql.SQLException;

@Component
public class UnitOfWorkFactory {
    private final DataSource dataSource;

    public UnitOfWorkFactory(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public UnitOfWork create() throws SQLException {
        return new UnitOfWork(dataSource.getConnection());
    }
}