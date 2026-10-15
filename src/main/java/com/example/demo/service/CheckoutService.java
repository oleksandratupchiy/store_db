
package com.example.demo.service;

import com.example.demo.repository.OrderRepository;
import com.example.demo.repository.ProductRepository;
import com.example.demo.uow.UnitOfWork;
import com.example.demo.uow.UnitOfWorkFactory;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;

@Service
public class CheckoutService {

    private final UnitOfWorkFactory factory;

    public CheckoutService(UnitOfWorkFactory factory) {
        this.factory = factory;
    }

    public int processPurchase(
            int customerId,
            int employeeId,
            int productId,
            int quantity,
            BigDecimal price
    ) throws SQLException {

        if (quantity <= 0) {
            throw new IllegalArgumentException(
                    "Кількість має бути додатною"
            );
        }

        if (price == null || price.signum() < 0) {
            throw new IllegalArgumentException(
                    "Невірна ціна"
            );
        }

        try (UnitOfWork uow = factory.create()) {

            try {
                OrderRepository orders =
                        new OrderRepository(uow);

                ProductRepository products =
                        new ProductRepository(uow);

                // 1. Списуємо товар зі складу
                products.deductInventory(
                        productId,
                        quantity,
                        employeeId
                );

                // 2. Створюємо замовлення
                int orderId = orders.createOrder(
                        customerId,
                        employeeId
                );

                // 3. Додаємо позицію
                orders.addOrderItem(
                        orderId,
                        productId,
                        quantity,
                        price
                );

                // 4. Підтверджуємо транзакцію
                uow.commit();

                return orderId;

            } catch (SQLException | RuntimeException e) {

                try {
                    uow.rollback();
                } catch (SQLException ex) {
                    e.addSuppressed(ex);
                }

                throw e;
            }
        }
    }

    public List<ProductRepository.ProductStock>
    getAvailableProducts() throws SQLException {

        try (UnitOfWork uow = factory.create()) {

            ProductRepository products =
                    new ProductRepository(uow);

            return products.findActiveProducts();
        }
    }
}
