
package com.example.demo.repository;

import com.example.demo.uow.UnitOfWork;

import java.math.BigDecimal;
import java.sql.*;

public class OrderRepository {

    private final Connection connection;

    public OrderRepository(UnitOfWork uow) {
        this.connection = uow.getConnection();
    }

    public int createOrder(
            int customerId,
            int employeeId
    ) throws SQLException {

        String sql = "CALL sp_create_sales_order(?, ?, NULL)";

        try (PreparedStatement stmt =
                     connection.prepareStatement(sql)) {

            stmt.setInt(1, customerId);
            stmt.setInt(2, employeeId);

            try (ResultSet rs = stmt.executeQuery()) {

                if (!rs.next()) {
                    throw new SQLException(
                            "Процедура не повернула ID замовлення"
                    );
                }

                return rs.getInt(1);
            }
        }
    }

    public void addOrderItem(
            int orderId,
            int productId,
            int quantity,
            BigDecimal price
    ) throws SQLException {

        String sql = "CALL sp_add_order_item(?, ?, ?, ?)";

        try (PreparedStatement stmt =
                     connection.prepareStatement(sql)) {

            stmt.setInt(1, orderId);
            stmt.setInt(2, productId);
            stmt.setInt(3, quantity);
            stmt.setBigDecimal(4, price);

            stmt.execute();
        }
    }
}
