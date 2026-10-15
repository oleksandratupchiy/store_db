
package com.example.demo;

import com.example.demo.repository.ProductRepository;
import com.example.demo.service.CheckoutService;
import com.example.demo.uow.UnitOfWork;
import com.example.demo.uow.UnitOfWorkFactory;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class CheckoutServiceTest {

    private static final int CUSTOMER_ID = 900001;
    private static final int EMPLOYEE_ID = 900001;
    private static final int PRODUCT_ID = 900001;

    @Autowired
    private CheckoutService checkoutService;

    @Autowired
    private UnitOfWorkFactory uowFactory;

    // Отримання кількості через VIEW і Repository
    private int getStock() throws SQLException {
        List<ProductRepository.ProductStock> products =
                checkoutService.getAvailableProducts();

        return products.stream()
                .filter(p -> p.id() == PRODUCT_ID)
                .findFirst()
                .orElseThrow(() ->
                        new AssertionError(
                                "Тестовий товар не знайдено"
                        ))
                .quantity();
    }

    // Кількість замовлень читаємо через VIEW
    private long getOrderCount() throws SQLException {
        try (UnitOfWork uow = uowFactory.create()) {
            try (var stmt = uow.getConnection()
                    .prepareStatement(
                            "SELECT COUNT(*) " +
                                    "FROM v_sales_details " +
                                    "WHERE customer_name = 'Test Customer'"
                    );
                 var rs = stmt.executeQuery()) {

                rs.next();
                return rs.getLong(1);
            }
        }
    }

    @Test
    void successfulPurchase() throws SQLException {

        int stockBefore = getStock();
        long ordersBefore = getOrderCount();

        assertTrue(stockBefore >= 2,
                "Недостатньо товару для тесту");

        int orderId = checkoutService.processPurchase(
                CUSTOMER_ID,
                EMPLOYEE_ID,
                PRODUCT_ID,
                2,
                new BigDecimal("500.00")
        );

        int stockAfter = getStock();

        assertTrue(orderId > 0);

        assertEquals(
                stockBefore - 2,
                stockAfter,
                "Залишок повинен зменшитися на 2"
        );

        assertEquals(
                ordersBefore + 1,
                getOrderCount(),
                "Повинно створитися одне замовлення"
        );

        System.out.println(
                "SUCCESS: покупка створена, ID = " + orderId
        );
    }

    @Test
    void transactionRollback() throws SQLException {

        int stockBefore = getStock();
        long ordersBefore = getOrderCount();

        // Навмисно передаємо неправильну ціну.
        // Списання і створення замовлення відбудуться
        // всередині транзакції, але додавання позиції
        // має завершитися помилкою.

        assertThrows(
                SQLException.class,
                () -> checkoutService.processPurchase(
                        CUSTOMER_ID,
                        EMPLOYEE_ID,
                        PRODUCT_ID,
                        1,
                        new BigDecimal("1.00")
                )
        );

        // Зміни мають бути повністю відкочені.
        assertEquals(
                stockBefore,
                getStock(),
                "Rollback має відновити складський залишок"
        );

        assertEquals(
                ordersBefore,
                getOrderCount(),
                "Rollback не повинен залишати замовлення"
        );

        System.out.println(
                "SUCCESS: rollback виконано правильно"
        );
    }

    @Test
    void insufficientInventory() throws SQLException {

        int stockBefore = getStock();
        long ordersBefore = getOrderCount();

        assertThrows(
                SQLException.class,
                () -> checkoutService.processPurchase(
                        CUSTOMER_ID,
                        EMPLOYEE_ID,
                        PRODUCT_ID,
                        1000000,
                        new BigDecimal("500.00")
                )
        );

        assertEquals(stockBefore, getStock());
        assertEquals(ordersBefore, getOrderCount());

        System.out.println(
                "SUCCESS: продаж без запасів заблоковано"
        );
    }

    @Test
    void readProductsThroughView() throws SQLException {

        List<ProductRepository.ProductStock> products =
                checkoutService.getAvailableProducts();

        boolean found = products.stream()
                .anyMatch(p ->
                        p.id() == PRODUCT_ID
                                && p.sku().equals("DEMO-900001")
                );

        assertTrue(found,
                "Активний товар повинен відображатися у VIEW");

        System.out.println(
                "SUCCESS: Repository читає дані через VIEW"
        );
    }
}
