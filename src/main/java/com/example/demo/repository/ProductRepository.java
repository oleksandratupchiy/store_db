
package com.example.demo.repository;

import com.example.demo.uow.UnitOfWork;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ProductRepository {

    private final Connection connection;

    public ProductRepository(UnitOfWork uow) {
        this.connection = uow.getConnection();
    }

    // Читання виключно через VIEW
    public List<ProductStock> findActiveProducts()
            throws SQLException {

        String sql = """
            SELECT id, sku, name, price, quantity
            FROM v_active_products_inventory
            ORDER BY id
            """;

        List<ProductStock> products = new ArrayList<>();

        try (PreparedStatement stmt =
                     connection.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                products.add(new ProductStock(
                        rs.getInt("id"),
                        rs.getString("sku"),
                        rs.getString("name"),
                        rs.getBigDecimal("price"),
                        rs.getInt("quantity")
                ));
            }
        }

        return products;
    }

    // Списання товару через процедуру
    public void deductInventory(
            int productId,
            int quantity,
            int userId
    ) throws SQLException {

        String sql = "CALL sp_deduct_inventory(?, ?, ?)";

        try (PreparedStatement stmt =
                     connection.prepareStatement(sql)) {

            stmt.setInt(1, productId);
            stmt.setInt(2, quantity);
            stmt.setInt(3, userId);
            stmt.execute();
        }
    }

    // Soft delete через процедуру
    public void softDeleteProduct(
            int productId,
            int userId
    ) throws SQLException {

        String sql = "CALL sp_soft_delete_product(?, ?)";

        try (PreparedStatement stmt =
                     connection.prepareStatement(sql)) {

            stmt.setInt(1, productId);
            stmt.setInt(2, userId);
            stmt.execute();
        }
    }

    public record ProductStock(
            int id,
            String sku,
            String name,
            BigDecimal price,
            int quantity
    ) {}
}
