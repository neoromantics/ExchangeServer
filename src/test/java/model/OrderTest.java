package model;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

public class OrderTest {

    @Test
    public void testOrderConstructorAndGetters() {
        BigDecimal amount = new BigDecimal("100");
        BigDecimal limitPrice = new BigDecimal("250.00");

        Order order = new Order("ACC123", "SPY", amount, limitPrice);

        Assertions.assertEquals("ACC123", order.getAccountId());
        Assertions.assertEquals("SPY", order.getSymbol());
        Assertions.assertEquals(0, order.getAmount().compareTo(new BigDecimal("100")));
        Assertions.assertEquals(0, order.getLimitPrice().compareTo(new BigDecimal("250.00")));

        Assertions.assertEquals(OrderStatus.OPEN, order.getStatus(),
                "By default, let's assume newly constructed Order has status=OPEN");
        Assertions.assertEquals(0L, order.getCreationTime(),
                "By default, creationTime might be 0 if we haven’t set it yet");
    }

    @Test
    public void testSettersAndUpdateFields() {
        BigDecimal amt = new BigDecimal("50");
        BigDecimal price = new BigDecimal("100.50");

        Order order = new Order("A", "XYZ", amt, price);
        order.setOrderId(123L);
        order.setCreationTime(10000L);
        order.setStatus(OrderStatus.EXECUTED);

        Assertions.assertEquals(123L, order.getOrderId());
        Assertions.assertEquals(10000L, order.getCreationTime());
        Assertions.assertEquals(OrderStatus.EXECUTED, order.getStatus());
    }

    @Test
    public void testIsBuyIsSell() {
        // Buy
        Order buyOrder = new Order("ACC", "ABC", new BigDecimal("10"), new BigDecimal("20.00"));
        Assertions.assertTrue(buyOrder.isBuy());
        Assertions.assertFalse(buyOrder.isSell());

        // Sell
        Order sellOrder = new Order("ACC", "ABC", new BigDecimal("-5"), new BigDecimal("20.00"));
        Assertions.assertFalse(sellOrder.isBuy());
        Assertions.assertTrue(sellOrder.isSell());
    }
}
