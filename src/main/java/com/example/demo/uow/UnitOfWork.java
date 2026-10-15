
package com.example.demo.uow;

import java.sql.Connection;
import java.sql.SQLException;

public final class UnitOfWork implements AutoCloseable {

    private final Connection connection;
    private boolean committed;

    public UnitOfWork(Connection connection) throws SQLException {
        this.connection = connection;

        try {
            connection.setAutoCommit(false);
        } catch (SQLException e) {
            try {
                connection.close();
            } catch (SQLException closeError) {
                e.addSuppressed(closeError);
            }
            throw e;
        }
    }

    public Connection getConnection() {
        return connection;
    }

    public void commit() throws SQLException {
        connection.commit();
        committed = true;
    }

    public void rollback() throws SQLException {
        connection.rollback();
    }

    @Override
    public void close() throws SQLException {
        try {
            if (!committed && !connection.isClosed()) {
                connection.rollback();
            }
        } finally {
            connection.close();
        }
    }
}
