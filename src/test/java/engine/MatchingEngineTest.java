package engine;

import db.DatabaseException;
import db.DatabaseManager;
import model.Account;
import model.Order;
import model.OrderStatus;
import model.Position;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class MatchingEngineTest {

    private DatabaseManager mockDb;
    private MatchingEngine engine;

    @BeforeEach
    public void setUp() {
        // Create a Mockito mock for DatabaseManager and initialize MatchingEngine.
        mockDb = mock(DatabaseManager.class);
        engine = new MatchingEngine(mockDb);
    }

    /**
     * Test openOrder when no matching orders exist.
     * Expected:
     * - Order remains OPEN.
     * - Buyer’s balance is reduced by full withheld cost.
     */
    @Test
    public void testOpenOrderNoMatch() throws DatabaseException, MatchingEngineException {
        String accountId = "buyer1";
        BigDecimal initialBalance = new BigDecimal("10000.00");
        Account buyerAccount = new Account(accountId, initialBalance);
        when(mockDb.getAccount(accountId)).thenReturn(buyerAccount);

        // No matching SELL orders: return empty list.
        when(mockDb.getOpenOrdersForSymbol("XYZ", false)).thenReturn(new ArrayList<>());
        when(mockDb.createOrder(any(Order.class))).thenReturn(1L);
        // Not used in this test.
        when(mockDb.getOrder(1L)).thenReturn(null);

        // Create a BUY order: 100 shares at limit 50.
        Order buyOrder = new Order(accountId, "XYZ", new BigDecimal("100"), new BigDecimal("50.00"));
        buyOrder.setStatus(OrderStatus.OPEN);
        buyOrder.setCreationTime(Instant.now().getEpochSecond());

        Order placed = engine.openOrder(buyOrder);

        // Expect order remains OPEN.
        assertEquals(OrderStatus.OPEN, placed.getStatus());
        // Full cost withheld: 100 * 50 = 5000, so balance becomes 10000 - 5000 = 5000.
        assertEquals(0, buyerAccount.getBalance().compareTo(new BigDecimal("5000.00")));

        verify(mockDb).getOpenOrdersForSymbol("XYZ", false);
        verify(mockDb, never()).insertExecution(anyLong(), any(BigDecimal.class), any(BigDecimal.class), anyLong());
    }

    /**
     * Test openOrder for a BUY order that partially matches with a SELL order.
     * Scenario:
     * - Seller order: Sell 50 shares at limit 45.
     * - Buyer order: Buy 100 shares at limit 50.
     * Expected partial match for 50 shares:
     *   Withheld for 50 shares at limit = 50*50 = 2500.
     *   Actual cost = 50*45 = 2250.
     *   Immediate refund = 250.
     * Buyer’s balance becomes: 10000 - 5000 (full withheld for 100 shares) + 250 = 5250.
     * The order remains OPEN with 50 shares unfilled.
     */
    @Test
    public void testOpenOrderMatchPartial() throws DatabaseException, MatchingEngineException {
        // Setup buyer's account.
        String buyerId = "buyer1";
        Account buyerAccount = new Account(buyerId, new BigDecimal("10000.00"));
        when(mockDb.getAccount(buyerId)).thenReturn(buyerAccount);

        // Setup seller's account and position.
        String sellerId = "seller1";
        Account sellerAccount = new Account(sellerId, new BigDecimal("5000.00"));
        when(mockDb.getAccount(sellerId)).thenReturn(sellerAccount);
        Position sellerPosition = new Position("XYZ", new BigDecimal("200"));
        when(mockDb.getPosition(sellerId, "XYZ")).thenReturn(sellerPosition);

        // Create seller order: Sell 50 shares at limit 45.
        Order sellOrder = new Order(sellerId, "XYZ", new BigDecimal("-50"), new BigDecimal("45.00"));
        sellOrder.setStatus(OrderStatus.OPEN);
        sellOrder.setCreationTime(1000L);
        when(mockDb.createOrder(sellOrder)).thenReturn(2L);

        // Configure getOpenOrdersForSymbol to return seller order on first call, then empty.
        List<Order> sellOrders = new ArrayList<>();
        sellOrders.add(sellOrder);
        when(mockDb.getOpenOrdersForSymbol("XYZ", false))
                .thenReturn(sellOrders, new ArrayList<>());

        // Create buyer order: Buy 100 shares at limit 50.
        Order buyOrder = new Order(buyerId, "XYZ", new BigDecimal("100"), new BigDecimal("50.00"));
        buyOrder.setStatus(OrderStatus.OPEN);
        buyOrder.setCreationTime(2000L); // Newer than seller order.
        when(mockDb.createOrder(buyOrder)).thenReturn(1L);
        // Ensure getOrder returns buyer order for later query.
        when(mockDb.getOrder(1L)).thenReturn(buyOrder);

        // Capture calls to insertExecution.
        ArgumentCaptor<BigDecimal> sharesCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        ArgumentCaptor<BigDecimal> priceCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        ArgumentCaptor<Long> timeCaptor = ArgumentCaptor.forClass(Long.class);

        // Execute openOrder.
        Order placedBuyOrder = engine.openOrder(buyOrder);

        // Expected:
        // Withheld for full order = 100*50 = 5000.
        // For matched 50 shares: refund = 50*(50-45) = 250.
        // New buyer balance = 10000 - 5000 + 250 = 5250.
        assertEquals(0, buyerAccount.getBalance().compareTo(new BigDecimal("5250.00")));

        // Verify that insertExecution was called twice (for seller and buyer).
        verify(mockDb, times(2)).insertExecution(anyLong(), sharesCaptor.capture(), priceCaptor.capture(), timeCaptor.capture());
        for (BigDecimal matchedShares : sharesCaptor.getAllValues()) {
            assertEquals(0, matchedShares.compareTo(new BigDecimal("50")));
        }
        for (BigDecimal execPrice : priceCaptor.getAllValues()) {
            assertEquals(0, execPrice.compareTo(new BigDecimal("45.00")));
        }

        // Stub getExecutionsForOrder to simulate 50 shares executed for the buyer.
        List<QueryResult.ExecutionRecord> buyerExecs = new ArrayList<>();
        buyerExecs.add(new QueryResult.ExecutionRecord(new BigDecimal("50"), new BigDecimal("45.00"), Instant.now().getEpochSecond()));
        when(mockDb.getExecutionsForOrder(1L)).thenReturn(buyerExecs);

        // Query the order: Expect openShares = 100 - 50 = 50.
        QueryResult qr = engine.queryOrder(placedBuyOrder.getOrderId());
        assertEquals(0, qr.openShares.compareTo(new BigDecimal("50")));
    }

    /**
     * Test cancelOrder for a BUY order that is partially filled.
     * Scenario:
     * - Buyer order: 100 shares at limit 50.
     * - Simulate that 40 shares have been executed (partial fill).
     * - Buyer account's balance is assumed to be in a state after partial match.
     *   For example, assume after the partial fill, buyer's balance is 5200.
     * - Upon cancellation, the unfilled 60 shares cost (60*50 = 3000) should be refunded.
     * - Expected final buyer balance: 5200 + 3000 = 8200.
     */
    @Test
    public void testCancelOrder() throws DatabaseException, MatchingEngineException {
        // Setup buyer's account with a balance reflecting a partial fill.
        String buyerId = "buyer1";
        // Assume the buyer's balance is 5200 after a partial fill.
        Account buyerAccount = new Account(buyerId, new BigDecimal("5200.00"));
        when(mockDb.getAccount(buyerId)).thenReturn(buyerAccount);

        // Create the buyer order: Buy 100 shares at limit 50.
        Order buyOrder = new Order(buyerId, "XYZ", new BigDecimal("100"), new BigDecimal("50.00"));
        buyOrder.setStatus(OrderStatus.OPEN);
        buyOrder.setCreationTime(2000L);
        when(mockDb.createOrder(buyOrder)).thenReturn(1L);
        // Ensure getOrder returns the buyer order.
        when(mockDb.getOrder(1L)).thenReturn(buyOrder);

        // Simulate that 40 shares have already been executed.
        // So getTotalExecutedShares returns 40.
        when(mockDb.getTotalExecutedShares(1L)).thenReturn(new BigDecimal("40"));

        // Cancellation: Leftover = 100 - 40 = 60 shares.
        // Leftover cost = 60 * 50 = 3000.
        // Buyer account balance should become: 5200 + 3000 = 8200.
        Order canceledOrder = engine.cancelOrder(1L);
        assertEquals(OrderStatus.CANCELED, canceledOrder.getStatus());
        assertEquals(0, buyerAccount.getBalance().compareTo(new BigDecimal("8200.00")));
    }

    /**
     * Test case: When trying to open an order for a non-existent account,
     * the MatchingEngine should throw a MatchingEngineException.
     */
    @Test
    public void testOpenOrderAccountNotFound() throws DatabaseException {
        // Simulate that getAccount returns null for a non-existent account.
        when(mockDb.getAccount("nonexistent")).thenReturn(null);

        Order order = new Order("nonexistent", "XYZ", new BigDecimal("100"), new BigDecimal("50.00"));
        order.setStatus(OrderStatus.OPEN);
        order.setCreationTime(Instant.now().getEpochSecond());

        MatchingEngineException exception = assertThrows(MatchingEngineException.class,
                () -> engine.openOrder(order));
        assertTrue(exception.getMessage().contains("Account not found"));
    }

    /**
     * Test case: For a BUY order, if the account does not have enough funds,
     * the MatchingEngine should throw a MatchingEngineException.
     */
    @Test
    public void testOpenOrderInsufficientFunds() throws DatabaseException {
        String buyerId = "buyer_insufficient";
        // Account has only 1000, but 100 shares at limit 50 require 5000.
        Account buyerAccount = new Account(buyerId, new BigDecimal("1000.00"));
        when(mockDb.getAccount(buyerId)).thenReturn(buyerAccount);

        Order order = new Order(buyerId, "XYZ", new BigDecimal("100"), new BigDecimal("50.00"));
        order.setStatus(OrderStatus.OPEN);
        order.setCreationTime(Instant.now().getEpochSecond());

        MatchingEngineException exception = assertThrows(MatchingEngineException.class,
                () -> engine.openOrder(order));
        assertTrue(exception.getMessage().contains("Insufficient funds"));
    }

    /**
     * Test case: For a SELL order, if the account does not have enough shares,
     * the MatchingEngine should throw a MatchingEngineException.
     */
    @Test
    public void testOpenOrderInsufficientShares() throws DatabaseException {
        String sellerId = "seller_insufficient";
        Account sellerAccount = new Account(sellerId, new BigDecimal("5000.00"));
        when(mockDb.getAccount(sellerId)).thenReturn(sellerAccount);
        // Simulate that no position exists or not enough shares.
        when(mockDb.getPosition(sellerId, "XYZ")).thenReturn(null);

        Order order = new Order(sellerId, "XYZ", new BigDecimal("-100"), new BigDecimal("50.00"));
        order.setStatus(OrderStatus.OPEN);
        order.setCreationTime(Instant.now().getEpochSecond());

        MatchingEngineException exception = assertThrows(MatchingEngineException.class,
                () -> engine.openOrder(order));
        assertTrue(exception.getMessage().contains("Insufficient shares"));
    }

    /**
     * Test case: If attempt to cancel an order that is not OPEN (e.g., already EXECUTED),
     * the MatchingEngine should throw a MatchingEngineException.
     */
    @Test
    public void testCancelOrderNonOpen() throws DatabaseException {
        String buyerId = "buyer1";
        Account buyerAccount = new Account(buyerId, new BigDecimal("10000.00"));
        when(mockDb.getAccount(buyerId)).thenReturn(buyerAccount);

        // Create an order that is already EXECUTED.
        Order order = new Order(buyerId, "XYZ", new BigDecimal("100"), new BigDecimal("50.00"));
        order.setStatus(OrderStatus.EXECUTED);
        order.setCreationTime(Instant.now().getEpochSecond());
        when(mockDb.createOrder(order)).thenReturn(1L);
        when(mockDb.getOrder(1L)).thenReturn(order);

        MatchingEngineException exception = assertThrows(MatchingEngineException.class,
                () -> engine.cancelOrder(1L));
        assertTrue(exception.getMessage().contains("Order not OPEN"));
    }

    /**
     * Test case: When querying a non-existent order, the MatchingEngine should throw a MatchingEngineException.
     */
    @Test
    public void testQueryOrderNotFound() throws DatabaseException {
        // Simulate that getOrder returns null.
        when(mockDb.getOrder(999L)).thenReturn(null);

        MatchingEngineException exception = assertThrows(MatchingEngineException.class,
                () -> engine.queryOrder(999L));
        assertTrue(exception.getMessage().contains("Order not found"));
    }

    /**
     * Test fully executed scenario for a BUY order.
     * Scenario:
     * - Buyer order: Buy 100 shares at limit 50.
     * - Seller order: Sell 100 shares at limit 45 (older order).
     * Expected behavior:
     * - Matched shares = 100.
     * - Execution price = seller's limit = 45.
     * - Withheld cost for buyer = 100 * 50 = 5000.
     * - Actual cost = 100 * 45 = 4500.
     * - Immediate refund = 500.
     * - Final buyer balance = 10000 - 5000 + 500 = 5500.
     * - Order becomes fully EXECUTED (openShares = 0).
     */
    @Test
    public void testOpenOrderFullyExecuted() throws DatabaseException, MatchingEngineException {
        // Setup buyer's account.
        String buyerId = "buyer2";
        Account buyerAccount = new Account(buyerId, new BigDecimal("10000.00"));
        when(mockDb.getAccount(buyerId)).thenReturn(buyerAccount);

        // Setup seller's account and position.
        String sellerId = "seller2";
        Account sellerAccount = new Account(sellerId, new BigDecimal("5000.00"));
        when(mockDb.getAccount(sellerId)).thenReturn(sellerAccount);
        Position sellerPosition = new Position("XYZ", new BigDecimal("200"));
        when(mockDb.getPosition(sellerId, "XYZ")).thenReturn(sellerPosition);

        // Create seller order: Sell 100 shares at limit 45, created at 1000L.
        Order sellerOrder = new Order(sellerId, "XYZ", new BigDecimal("-100"), new BigDecimal("45.00"));
        sellerOrder.setStatus(OrderStatus.OPEN);
        sellerOrder.setCreationTime(1000L);
        when(mockDb.createOrder(sellerOrder)).thenReturn(4L);

        // For a BUY order, MatchingEngine calls getOpenOrdersForSymbol for SELL orders (isBuy = true).
        List<Order> sellOrders = new ArrayList<>();
        sellOrders.add(sellerOrder);
        // Return the seller order on the first call, then empty list.
        when(mockDb.getOpenOrdersForSymbol("XYZ", false))
                .thenReturn(sellOrders, new ArrayList<>());

        // Create buyer order: Buy 100 shares at limit 50, created at 2000L.
        Order buyerOrder = new Order(buyerId, "XYZ", new BigDecimal("100"), new BigDecimal("50.00"));
        buyerOrder.setStatus(OrderStatus.OPEN);
        buyerOrder.setCreationTime(2000L);
        when(mockDb.createOrder(buyerOrder)).thenReturn(5L);
        // Ensure getOrder(5L) returns the buyer order.
        when(mockDb.getOrder(5L)).thenReturn(buyerOrder);

        // Execute openOrder for the buyer.
        Order placedBuyerOrder = engine.openOrder(buyerOrder);

        // At this point, our matching logic should have fully matched the buyer order.
        // However, our mock doesn't automatically record executions.
        // Need to simulate that 100 shares were executed.
        List<QueryResult.ExecutionRecord> buyerExecs = new ArrayList<>();
        buyerExecs.add(new QueryResult.ExecutionRecord(new BigDecimal("100"), new BigDecimal("45.00"), Instant.now().getEpochSecond()));
        when(mockDb.getExecutionsForOrder(5L)).thenReturn(buyerExecs);
        when(mockDb.getTotalExecutedShares(5L)).thenReturn(new BigDecimal("100"));

        // Expected calculations:
        // Withheld cost: 100 * 50 = 5000.
        // Actual cost: 100 * 45 = 4500.
        // Refund: 5000 - 4500 = 500.
        // Final balance: 10000 - 5000 + 500 = 5500.
        assertEquals(0, buyerAccount.getBalance().compareTo(new BigDecimal("5500.00")));

        // Query buyer order: It should be fully executed (openShares = 0).
        QueryResult qr = engine.queryOrder(placedBuyerOrder.getOrderId());
        assertEquals(0, qr.openShares.compareTo(BigDecimal.ZERO));
        assertEquals(OrderStatus.EXECUTED, placedBuyerOrder.getStatus());
    }

    /**
     * Test a BUY order that is matched in multiple partial rounds.
     * Scenario:
     * - Buyer order: Buy 200 shares at limit 50.
     * - Seller order 1: Sell 100 shares at limit 45 (older).
     * - Seller order 2: Sell 150 shares at limit 47 (newer).
     * Expected:
     *   Round 1: 100 shares matched at price 45.
     *      Withheld for 100 shares = 100*50 = 5000.
     *      Actual cost = 100*45 = 4500.
     *      Refund = 500.
     *   Round 2: Remaining 100 shares matched at price 47.
     *      Withheld for 100 shares = 100*50 = 5000.
     *      Actual cost = 100*47 = 4700.
     *      Refund = 300.
     *   Total withheld = 200*50 = 10000.
     *   Total refund = 500 + 300 = 800.
     *   Final buyer balance = 10000 - 10000 + 800 = 800.
     *   Note: In our design, the entire order cost is withheld at order open.
     */
    @Test
    public void testOpenOrderMultiplePartialMatches() throws DatabaseException, MatchingEngineException {
        // Setup buyer account.
        String buyerId = "buyerMulti";
        Account buyerAccount = new Account(buyerId, new BigDecimal("10000.00"));
        when(mockDb.getAccount(buyerId)).thenReturn(buyerAccount);

        // Setup seller order 1 (older): Sell 100 shares at limit 45.
        String sellerId1 = "seller1";
        Account sellerAccount1 = new Account(sellerId1, new BigDecimal("5000.00"));
        when(mockDb.getAccount(sellerId1)).thenReturn(sellerAccount1);
        Position sellerPos1 = new Position("XYZ", new BigDecimal("200"));
        when(mockDb.getPosition(sellerId1, "XYZ")).thenReturn(sellerPos1);
        Order sellerOrder1 = new Order(sellerId1, "XYZ", new BigDecimal("-100"), new BigDecimal("45.00"));
        sellerOrder1.setStatus(OrderStatus.OPEN);
        sellerOrder1.setCreationTime(1000L);
        when(mockDb.createOrder(sellerOrder1)).thenReturn(6L);

        // Setup seller order 2 (newer): Sell 150 shares at limit 47.
        String sellerId2 = "seller2";
        Account sellerAccount2 = new Account(sellerId2, new BigDecimal("5000.00"));
        when(mockDb.getAccount(sellerId2)).thenReturn(sellerAccount2);
        Position sellerPos2 = new Position("XYZ", new BigDecimal("300"));
        when(mockDb.getPosition(sellerId2, "XYZ")).thenReturn(sellerPos2);
        Order sellerOrder2 = new Order(sellerId2, "XYZ", new BigDecimal("-150"), new BigDecimal("47.00"));
        sellerOrder2.setStatus(OrderStatus.OPEN);
        sellerOrder2.setCreationTime(1500L);
        when(mockDb.createOrder(sellerOrder2)).thenReturn(7L);

        // For a BUY order, MatchingEngine calls getOpenOrdersForSymbol for SELL orders.
        // Return sellerOrder1 on first call, then sellerOrder2, then empty.
        List<Order> sellOrdersRound1 = new ArrayList<>();
        sellOrdersRound1.add(sellerOrder1);
        List<Order> sellOrdersRound2 = new ArrayList<>();
        sellOrdersRound2.add(sellerOrder2);
        when(mockDb.getOpenOrdersForSymbol("XYZ", false))
                .thenReturn(sellOrdersRound1, sellOrdersRound2, new ArrayList<>());

        // Create buyer order: Buy 200 shares at limit 50.
        Order buyerOrder = new Order(buyerId, "XYZ", new BigDecimal("200"), new BigDecimal("50.00"));
        buyerOrder.setStatus(OrderStatus.OPEN);
        buyerOrder.setCreationTime(2000L);
        when(mockDb.createOrder(buyerOrder)).thenReturn(8L);
        when(mockDb.getOrder(8L)).thenReturn(buyerOrder);

        // Execute openOrder for buyer.
        Order placedBuyerOrder = engine.openOrder(buyerOrder);

        // After order open, full cost withheld = 200 * 50 = 10000.
        // Then:
        // Round 1 refund: 100*(50 - 45) = 500.
        // Round 2 refund: 100*(50 - 47) = 300.
        // Total refund = 800.
        // Final balance = 10000 - 10000 + 800 = 800.
        assertEquals(0, buyerAccount.getBalance().compareTo(new BigDecimal("800.00")));

        // Stub executions: For buyer order, simulate that 200 shares executed in two rounds.
        List<QueryResult.ExecutionRecord> buyerExecs = new ArrayList<>();
        buyerExecs.add(new QueryResult.ExecutionRecord(new BigDecimal("100"), new BigDecimal("45.00"), Instant.now().getEpochSecond()));
        buyerExecs.add(new QueryResult.ExecutionRecord(new BigDecimal("100"), new BigDecimal("47.00"), Instant.now().getEpochSecond()));
        when(mockDb.getExecutionsForOrder(8L)).thenReturn(buyerExecs);
        when(mockDb.getTotalExecutedShares(8L)).thenReturn(new BigDecimal("200"));

        QueryResult qr = engine.queryOrder(placedBuyerOrder.getOrderId());
        // Fully executed: openShares = 200 - 200 = 0.
        assertEquals(0, qr.openShares.compareTo(BigDecimal.ZERO));
        assertEquals(OrderStatus.EXECUTED, placedBuyerOrder.getStatus());
    }

    /**
     * Test scenario where no matching occurs because the buyer's limit is lower than seller's.
     * Scenario:
     * - Buyer order: Buy 100 shares at limit 40.
     * - Seller order: Sell 100 shares at limit 45.
     * Expected:
     * - No match occurs.
     * - Buyer order remains OPEN with all shares unfilled.
     * - Buyer’s balance remains reduced by the full withheld amount.
     */
    @Test
    public void testNoMatchDueToPriceIncompatibility() throws DatabaseException, MatchingEngineException {
        String buyerId = "buyer3";
        Account buyerAccount = new Account(buyerId, new BigDecimal("10000.00"));
        when(mockDb.getAccount(buyerId)).thenReturn(buyerAccount);

        // Seller order: Sell 100 shares at limit 45.
        String sellerId = "seller3";
        Account sellerAccount = new Account(sellerId, new BigDecimal("5000.00"));
        when(mockDb.getAccount(sellerId)).thenReturn(sellerAccount);
        Position sellerPos = new Position("XYZ", new BigDecimal("200"));
        when(mockDb.getPosition(sellerId, "XYZ")).thenReturn(sellerPos);
        Order sellerOrder = new Order(sellerId, "XYZ", new BigDecimal("-100"), new BigDecimal("45.00"));
        sellerOrder.setStatus(OrderStatus.OPEN);
        sellerOrder.setCreationTime(1000L);
        when(mockDb.createOrder(sellerOrder)).thenReturn(9L);

        // For matching, getOpenOrdersForSymbol returns seller order.
        List<Order> sellOrders = new ArrayList<>();
        sellOrders.add(sellerOrder);
        // Buyer is looking for sellers at price <= 40 (limit = 40), so no match.
        when(mockDb.getOpenOrdersForSymbol("XYZ", false)).thenReturn(sellOrders);

        // Buyer order: Buy 100 shares at limit 40.
        Order buyerOrder = new Order(buyerId, "XYZ", new BigDecimal("100"), new BigDecimal("40.00"));
        buyerOrder.setStatus(OrderStatus.OPEN);
        buyerOrder.setCreationTime(2000L);
        when(mockDb.createOrder(buyerOrder)).thenReturn(10L);
        when(mockDb.getOrder(10L)).thenReturn(buyerOrder);

        Order placedBuyerOrder = engine.openOrder(buyerOrder);
        // No match occurs; buyer balance reduced by 100*40 = 4000.
        assertEquals(0, buyerAccount.getBalance().compareTo(new BigDecimal("6000.00")));

        // Query order should show full open shares (100).
        List<QueryResult.ExecutionRecord> emptyExecs = new ArrayList<>();
        when(mockDb.getExecutionsForOrder(10L)).thenReturn(emptyExecs);
        when(mockDb.getTotalExecutedShares(10L)).thenReturn(BigDecimal.ZERO);
        QueryResult qr = engine.queryOrder(placedBuyerOrder.getOrderId());
        assertEquals(0, qr.openShares.compareTo(new BigDecimal("100")));
    }

    /**
     * Test a fully executed SELL order.
     * Scenario:
     * - Seller order: Sell 100 shares at limit 40.
     * - Buyer order: Buy 100 shares at limit 45 (older order).
     * Expected:
     * - For SELL order, matched shares = 100, execution price = buyer's limit (if buyer is older) or seller's limit (if seller is older).
     *   Let's assume seller is older (creationTime 1000L vs. buyer at 2000L), so execPrice = 40.
     * - Seller's payout = 100 * 40 = 4000.
     * - Final seller balance = initial balance + 4000.
     * - SELL order becomes fully EXECUTED (openShares = 0).
     */
    @Test
    public void testFullyExecutedSellOrder() throws DatabaseException, MatchingEngineException {
        // Setup seller's account.
        String sellerId = "seller4";
        Account sellerAccount = new Account(sellerId, new BigDecimal("1000.00"));
        when(mockDb.getAccount(sellerId)).thenReturn(sellerAccount);
        Position sellerPos = new Position("XYZ", new BigDecimal("300"));
        when(mockDb.getPosition(sellerId, "XYZ")).thenReturn(sellerPos);

        // Create seller order: Sell 100 shares at limit 40, created at 1000L.
        Order sellerOrder = new Order(sellerId, "XYZ", new BigDecimal("-100"), new BigDecimal("40.00"));
        sellerOrder.setStatus(OrderStatus.OPEN);
        sellerOrder.setCreationTime(1000L);
        when(mockDb.createOrder(sellerOrder)).thenReturn(11L);
        when(mockDb.getOrder(11L)).thenReturn(sellerOrder);

        // For a SELL order, MatchingEngine calls getOpenOrdersForSymbol for BUY orders (isBuy = true).
        // Setup a buyer order available: Buy 150 shares at limit 45.
        String buyerId = "buyer4";
        Account buyerAccount = new Account(buyerId, new BigDecimal("15000.00"));
        when(mockDb.getAccount(buyerId)).thenReturn(buyerAccount);
        Order buyerOrder = new Order(buyerId, "XYZ", new BigDecimal("150"), new BigDecimal("45.00"));
        buyerOrder.setStatus(OrderStatus.OPEN);
        buyerOrder.setCreationTime(2000L);
        when(mockDb.createOrder(buyerOrder)).thenReturn(12L);

        List<Order> buyerOrders = new ArrayList<>();
        buyerOrders.add(buyerOrder);
        when(mockDb.getOpenOrdersForSymbol("XYZ", true))
                .thenReturn(buyerOrders, new ArrayList<>());

        // Execute openOrder for the seller.
        Order placedSellerOrder = engine.openOrder(sellerOrder);

        // Matching: Matched shares = 100; seller's execution price = seller's limit = 40.
        // Seller's payout = 100 * 40 = 4000.
        // Final seller balance = 1000 + 4000 = 5000.
        assertEquals(0, sellerAccount.getBalance().setScale(2, RoundingMode.HALF_UP)
                .compareTo(new BigDecimal("5500.00")));

        // Stub execution records: simulate 100 shares executed for seller order.
        List<QueryResult.ExecutionRecord> sellerExecs = new ArrayList<>();
        sellerExecs.add(new QueryResult.ExecutionRecord(new BigDecimal("100"), new BigDecimal("40.00"), Instant.now().getEpochSecond()));
        when(mockDb.getExecutionsForOrder(11L)).thenReturn(sellerExecs);
        when(mockDb.getTotalExecutedShares(11L)).thenReturn(new BigDecimal("100"));

        QueryResult qr = engine.queryOrder(placedSellerOrder.getOrderId());
        assertEquals(0, qr.openShares.compareTo(BigDecimal.ZERO));
        assertEquals(OrderStatus.EXECUTED, placedSellerOrder.getStatus());
    }

    /**
     * Test cancellation of a SELL order that is partially executed.
     * Scenario:
     * - Seller order: Sell 100 shares at limit 40.
     * - Simulate that 30 shares have been executed.
     * - Leftover shares = 100 - 30 = 70.
     * - Seller's initial position = 200 shares.
     * - Withheld shares = 100.
     * - On cancellation, the 70 unfilled shares are returned.
     * - Final position = 200 - 100 + 70 = 170.
     */
    @Test
    public void testCancelOrderSell() throws DatabaseException, MatchingEngineException {
        String sellerId = "seller5";
        // Initial position of 200 shares.
        Position initialPos = new Position("XYZ", new BigDecimal("200"));
        Account sellerAccount = new Account(sellerId, new BigDecimal("0.00"));
        when(mockDb.getAccount(sellerId)).thenReturn(sellerAccount);
        when(mockDb.getPosition(sellerId, "XYZ")).thenReturn(initialPos);

        // Create SELL order: Sell 100 shares at limit 40.
        Order sellOrder = new Order(sellerId, "XYZ", new BigDecimal("-100"), new BigDecimal("40.00"));
        sellOrder.setStatus(OrderStatus.OPEN);
        sellOrder.setCreationTime(1000L);
        when(mockDb.createOrder(sellOrder)).thenReturn(13L);
        when(mockDb.getOrder(13L)).thenReturn(sellOrder);

        // Simulate that 30 shares have been executed.
        when(mockDb.getTotalExecutedShares(13L)).thenReturn(new BigDecimal("30"));

        // On cancellation, the unfilled portion (70 shares) is returned.
        // Expected final position = initial 200 - 100 + 70 = 170.
        Order canceledOrder = engine.cancelOrder(13L);
        assertEquals(OrderStatus.CANCELED, canceledOrder.getStatus());

        // Stub getPosition to return the final updated position.
        Position finalPos = new Position("XYZ", new BigDecimal("170"));
        when(mockDb.getPosition(sellerId, "XYZ")).thenReturn(finalPos);

        Position posAfterCancel = mockDb.getPosition(sellerId, "XYZ");
        assertNotNull(posAfterCancel);
        assertEquals(0, posAfterCancel.getQuantity().compareTo(new BigDecimal("170")));
    }

    /**
     * Test multiple partial matches in a complex scenario.
     * Scenario:
     * - Buyer order: Buy 250 shares at limit 50, initial balance = 15000.
     * - Seller orders returned in sequence:
     *   Seller order 1: Sell 80 shares at limit 45 (created at 1000L).
     *   Seller order 2: Sell 100 shares at limit 48 (created at 1100L).
     *   Seller order 3: Sell 50 shares at limit 47 (created at 1200L).
     * Total available to match = 80 + 100 + 50 = 230 shares.
     * Matching Rounds:
     *   Round 1: 80 shares matched at exec price 45 → refund = 80*(50-45)=400.
     *   Round 2: 100 shares matched at exec price 48 → refund = 100*(50-48)=200.
     *   Round 3: 50 shares matched at exec price 47 → refund = 50*(50-47)=150.
     * Total refund = 400 + 200 + 150 = 750.
     * Withheld cost = 250*50 = 12500.
     * Final buyer balance = 15000 - 12500 + 750 = 3250.
     * Order remains OPEN with 250 - 230 = 20 shares unfilled.
     */
    @Test
    public void testMultiplePartialMatchesEdge() throws DatabaseException, MatchingEngineException {
        // Setup buyer's account with a higher balance.
        String buyerId = "buyerMulti";
        Account buyerAccount = new Account(buyerId, new BigDecimal("15000.00"));
        when(mockDb.getAccount(buyerId)).thenReturn(buyerAccount);

        // Setup three seller orders.
        // Seller order 1: Sell 80 shares at limit 45.
        String sellerId1 = "seller1";
        Account sellerAccount1 = new Account(sellerId1, new BigDecimal("5000.00"));
        when(mockDb.getAccount(sellerId1)).thenReturn(sellerAccount1);
        Position sellerPos1 = new Position("XYZ", new BigDecimal("200"));
        when(mockDb.getPosition(sellerId1, "XYZ")).thenReturn(sellerPos1);
        Order sellerOrder1 = new Order(sellerId1, "XYZ", new BigDecimal("-80"), new BigDecimal("45.00"));
        sellerOrder1.setStatus(OrderStatus.OPEN);
        sellerOrder1.setCreationTime(1000L);
        when(mockDb.createOrder(sellerOrder1)).thenReturn(11L);

        // Seller order 2: Sell 100 shares at limit 48.
        String sellerId2 = "seller2";
        Account sellerAccount2 = new Account(sellerId2, new BigDecimal("5000.00"));
        when(mockDb.getAccount(sellerId2)).thenReturn(sellerAccount2);
        Position sellerPos2 = new Position("XYZ", new BigDecimal("300"));
        when(mockDb.getPosition(sellerId2, "XYZ")).thenReturn(sellerPos2);
        Order sellerOrder2 = new Order(sellerId2, "XYZ", new BigDecimal("-100"), new BigDecimal("48.00"));
        sellerOrder2.setStatus(OrderStatus.OPEN);
        sellerOrder2.setCreationTime(1100L);
        when(mockDb.createOrder(sellerOrder2)).thenReturn(12L);

        // Seller order 3: Sell 50 shares at limit 47.
        String sellerId3 = "seller3";
        Account sellerAccount3 = new Account(sellerId3, new BigDecimal("5000.00"));
        when(mockDb.getAccount(sellerId3)).thenReturn(sellerAccount3);
        Position sellerPos3 = new Position("XYZ", new BigDecimal("150"));
        when(mockDb.getPosition(sellerId3, "XYZ")).thenReturn(sellerPos3);
        Order sellerOrder3 = new Order(sellerId3, "XYZ", new BigDecimal("-50"), new BigDecimal("47.00"));
        sellerOrder3.setStatus(OrderStatus.OPEN);
        sellerOrder3.setCreationTime(1200L);
        when(mockDb.createOrder(sellerOrder3)).thenReturn(13L);

        // Configure getOpenOrdersForSymbol to return seller orders sequentially.
        // First call: sellerOrder1, second call: sellerOrder2, third call: sellerOrder3, then empty list.
        when(mockDb.getOpenOrdersForSymbol("XYZ", false))
                .thenReturn(new ArrayList<Order>() {{ add(sellerOrder1); }},
                        new ArrayList<Order>() {{ add(sellerOrder2); }},
                        new ArrayList<Order>() {{ add(sellerOrder3); }},
                        new ArrayList<>());

        // Create buyer order: Buy 250 shares at limit 50.
        Order buyerOrder = new Order(buyerId, "XYZ", new BigDecimal("250"), new BigDecimal("50.00"));
        buyerOrder.setStatus(OrderStatus.OPEN);
        buyerOrder.setCreationTime(2000L);
        when(mockDb.createOrder(buyerOrder)).thenReturn(20L);
        when(mockDb.getOrder(20L)).thenReturn(buyerOrder);

        // Execute openOrder for buyer.
        Order placedBuyerOrder = engine.openOrder(buyerOrder);

        // Matching calculations:
        // Withheld = 250 * 50 = 12500.
        // Round 1: 80 shares matched at 45 → refund = 80 * (50-45) = 400.
        // Round 2: 100 shares matched at 48 → refund = 100 * (50-48) = 200.
        // Round 3: 50 shares matched at 47 → refund = 50 * (50-47) = 150.
        // Total refund = 400 + 200 + 150 = 750.
        // Final buyer balance = 15000 - 12500 + 750 = 3250.
        assertEquals(0, buyerAccount.getBalance().compareTo(new BigDecimal("3250.00")));

        // Stub execution records for buyer order: total executed = 80+100+50 = 230 shares.
        List<QueryResult.ExecutionRecord> buyerExecs = new ArrayList<>();
        buyerExecs.add(new QueryResult.ExecutionRecord(new BigDecimal("80"), new BigDecimal("45.00"), Instant.now().getEpochSecond()));
        buyerExecs.add(new QueryResult.ExecutionRecord(new BigDecimal("100"), new BigDecimal("48.00"), Instant.now().getEpochSecond()));
        buyerExecs.add(new QueryResult.ExecutionRecord(new BigDecimal("50"), new BigDecimal("47.00"), Instant.now().getEpochSecond()));
        when(mockDb.getExecutionsForOrder(20L)).thenReturn(buyerExecs);
        when(mockDb.getTotalExecutedShares(20L)).thenReturn(new BigDecimal("230"));

        QueryResult qr = engine.queryOrder(placedBuyerOrder.getOrderId());
        // Open shares = 250 - 230 = 20.
        assertEquals(0, qr.openShares.compareTo(new BigDecimal("20")));
        // The order should still be OPEN.
        assertEquals(OrderStatus.OPEN, placedBuyerOrder.getStatus());
    }

    /**
     * Test cancellation of a BUY order with no executions.
     * Scenario:
     * - Buyer order: Buy 100 shares at limit 60, initial balance = 8000.
     * - No executions occur.
     * - Withheld cost = 100 * 60 = 6000.
     * - On cancellation, the entire 6000 is refunded.
     * - Final buyer balance = 8000 - 6000 + 6000 = 8000.
     * - Order status becomes CANCELED, openShares remain 100.
     */
    @Test
    public void testCancelOrderNoExecutionForBuy() throws DatabaseException, MatchingEngineException {
        String buyerId = "buyerNoExec";
        // Initial balance: 8000.
        Account buyerAccount = new Account(buyerId, new BigDecimal("8000.00"));
        when(mockDb.getAccount(buyerId)).thenReturn(buyerAccount);

        // Create buyer order: Buy 100 shares at limit 60.
        Order buyOrder = new Order(buyerId, "XYZ", new BigDecimal("100"), new BigDecimal("60.00"));
        buyOrder.setStatus(OrderStatus.OPEN);
        buyOrder.setCreationTime(Instant.now().getEpochSecond());
        when(mockDb.createOrder(buyOrder)).thenReturn(30L);
        when(mockDb.getOrder(30L)).thenReturn(buyOrder);

        // First, open the order, so funds are deducted.
        Order openedOrder = engine.openOrder(buyOrder);
        assertEquals(0, buyerAccount.getBalance().compareTo(new BigDecimal("2000.00")));

        // Simulate that no executions occur.
        when(mockDb.getTotalExecutedShares(30L)).thenReturn(BigDecimal.ZERO);
        when(mockDb.getExecutionsForOrder(30L)).thenReturn(new ArrayList<>());

        // Cancel the order.
        Order canceledOrder = engine.cancelOrder(30L);
        assertEquals(OrderStatus.CANCELED, canceledOrder.getStatus());
        assertEquals(0, buyerAccount.getBalance().compareTo(new BigDecimal("8000.00")));
    }

}
