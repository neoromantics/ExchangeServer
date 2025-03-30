package engine;

import db.DatabaseException;
import db.PostgresDBManager;
import model.Account;
import model.Order;
import model.OrderStatus;
import model.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

public class MatchingEngineIntegrationTest {

    private PostgresDBManager dbManager;
    private MatchingEngine engine;

    @BeforeEach
    public void setUp() throws DatabaseException {
        // Create a new instance of your DB manager per test and connect
        dbManager = new PostgresDBManager();
        dbManager.connect();
        clearTables();
        engine = new MatchingEngine(dbManager);
    }

    @AfterEach
    public void tearDown() throws DatabaseException {
        clearTables();
        dbManager.disconnect();
    }

    // Clear all rows from all test tables (without closing the connection)
    private void clearTables() throws DatabaseException {
        Connection conn = dbManager.getConnection();
        try (Statement stmt = conn.createStatement()) {
            // Delete in order to avoid foreign key constraint issues
            stmt.executeUpdate("DELETE FROM executions");
            stmt.executeUpdate("DELETE FROM orders");
            stmt.executeUpdate("DELETE FROM positions");
            stmt.executeUpdate("DELETE FROM accounts");
        } catch (Exception e) {
            throw new DatabaseException("Failed to clear tables: " + e.getMessage());
        }
    }

    /**
     * Integration Test 1: Fully executed BUY order.
     *
     * Scenario:
     * - Buyer account created with balance 10000.
     * - Seller account created with balance 5000 and initial position of 200 shares for symbol "TEST".
     * - Seller places a SELL order: Sell 100 shares at limit 45.
     * - Buyer places a BUY order: Buy 100 shares at limit 50.
     *
     * Expected:
     * - Withheld cost = 100 * 50 = 5000.
     * - Actual cost = 100 * 45 = 4500, so refund = 500.
     * - Final buyer balance = 10000 - 5000 + 500 = 5500.
     * - The buyer order is fully executed (openShares = 0).
     */
    @Test
    public void testFullyExecutedBuyOrderIntegration() throws DatabaseException, MatchingEngineException {
        // Create buyer account.
        String buyerId = "intBuyer1";
        dbManager.createAccount(buyerId, new BigDecimal("10000.00"));

        // Create seller account and position.
        String sellerId = "intSeller1";
        dbManager.createAccount(sellerId, new BigDecimal("5000.00"));
        dbManager.createOrAddSymbol("TEST", sellerId, new BigDecimal("200"));

        // Create seller order: Sell 100 shares at limit 45.
        Order sellerOrder = new Order(sellerId, "TEST", new BigDecimal("-100"), new BigDecimal("45.00"));
        sellerOrder.setStatus(OrderStatus.OPEN);
        sellerOrder.setCreationTime(Instant.now().getEpochSecond());
        long sellerOrderId = dbManager.createOrder(sellerOrder);
        sellerOrder.setOrderId(sellerOrderId);

        // Create buyer order: Buy 100 shares at limit 50.
        // Revised setup – let engine.openOrder() do the insertion:
        Order buyerOrder = new Order(buyerId, "TEST", new BigDecimal("100"), new BigDecimal("50.00"));
        buyerOrder.setStatus(OrderStatus.OPEN);
        buyerOrder.setCreationTime(Instant.now().getEpochSecond());
        Order placedBuyerOrder = engine.openOrder(buyerOrder);


        // Query the order.
        QueryResult qr = engine.queryOrder(placedBuyerOrder.getOrderId());
        assertEquals(0, qr.openShares.compareTo(BigDecimal.ZERO));

        // Check buyer account balance.
        Account buyerAccount = dbManager.getAccount(buyerId);
        // Expected: 10000 - (100*50) + (refund of 500) = 5500.
        assertEquals(0, buyerAccount.getBalance().compareTo(new BigDecimal("5500.00")));
    }

    /**
     * Integration Test 2: Cancel BUY order with no executions.
     *
     * Scenario:
     * - Buyer account created with balance 8000.
     * - Buyer places a BUY order: Buy 100 shares at limit 60.
     * - No executions occur.
     * - Cancellation refunds the full withheld cost.
     *
     * Expected:
     * - Withheld cost = 100 * 60 = 6000; balance becomes 8000 - 6000 = 200.
     * - On cancellation, refund 6000; final balance becomes 200 + 6000 = 6200.
     */
    @Test
    public void testCancelBuyOrderNoExecutionIntegration() throws DatabaseException, MatchingEngineException {
        String buyerId = "intBuyer2";
        dbManager.createAccount(buyerId, new BigDecimal("8000.00"));

        // Place BUY order.
        Order buyerOrder = new Order(buyerId, "TEST", new BigDecimal("100"), new BigDecimal("60.00"));
        buyerOrder.setStatus(OrderStatus.OPEN);
        buyerOrder.setCreationTime(Instant.now().getEpochSecond());
        long buyerOrderId = dbManager.createOrder(buyerOrder);
        buyerOrder.setOrderId(buyerOrderId);

        // Open the order: funds deducted.
        Order openedOrder = engine.openOrder(buyerOrder);
        Account buyerAccount = dbManager.getAccount(buyerId);
        // Expected balance = 8000 - 6000 = 2000.
        assertEquals(0, buyerAccount.getBalance().compareTo(new BigDecimal("2000.00")));

        // Simulate no executions (query returns zero executed shares).
        // Now cancel the order.
        Order canceledOrder = engine.cancelOrder(buyerOrderId);
        assertEquals(OrderStatus.CANCELED, canceledOrder.getStatus());
        // After cancellation, refund 6000; final balance should be 2000 + 6000 = 8000.
        buyerAccount = dbManager.getAccount(buyerId);
        assertEquals(0, buyerAccount.getBalance().compareTo(new BigDecimal("8000.00")));
    }

    /**
     * Integration Test 3: Cancel SELL order.
     *
     * Scenario:
     * - Seller account created with balance 0 and an initial position of 200 shares in "TEST".
     * - Seller places a SELL order: Sell 100 shares at limit 40.
     * - No matching occurs.
     * - Cancellation returns the withheld shares.
     *
     * Expected:
     * - Final seller position for "TEST" becomes: initial 200 (position restored).
     */
    @Test
    public void testCancelSellOrderIntegration() throws DatabaseException, MatchingEngineException {
        String sellerId = "intSeller2";
        dbManager.createAccount(sellerId, new BigDecimal("0.00"));
        dbManager.createOrAddSymbol("TEST", sellerId, new BigDecimal("200"));

        // Place SELL order for 100 shares at limit 40.
        Order sellOrder = new Order(sellerId, "TEST", new BigDecimal("-100"), new BigDecimal("40.00"));
        sellOrder.setStatus(OrderStatus.OPEN);
        sellOrder.setCreationTime(Instant.now().getEpochSecond());
        long sellOrderId = dbManager.createOrder(sellOrder);
        sellOrder.setOrderId(sellOrderId);

        // Open the order: this should deduct 100 shares from the position.
        Order openedSellOrder = engine.openOrder(sellOrder);
        Position pos = dbManager.getPosition(sellerId, "TEST");
        // Expected position now: 200 - 100 = 100.
        assertEquals(0, pos.getQuantity().compareTo(new BigDecimal("100")));

        // Cancel the order.
        Order canceledOrder = engine.cancelOrder(sellOrderId);
        assertEquals(OrderStatus.CANCELED, canceledOrder.getStatus());

        // After cancellation, the 100 withheld shares should be returned.
        pos = dbManager.getPosition(sellerId, "TEST");
        // Final position should be restored to 200.
        assertEquals(0, pos.getQuantity().compareTo(new BigDecimal("200")));
    }
}
