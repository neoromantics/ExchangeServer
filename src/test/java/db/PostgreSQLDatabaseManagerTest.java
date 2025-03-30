package db;

import engine.QueryResult;
import model.Account;
import model.OrderStatus;
import model.Order;
import model.Position;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class PostgreSQLDatabaseManagerTest {

    private PostgreSQLDatabaseManager dbManager;

    @BeforeEach
    public void setUp() throws DatabaseException {
        dbManager = new PostgreSQLDatabaseManager();
        dbManager.connect();
        clearTables();
    }

    @AfterEach
    public void tearDown() throws DatabaseException {
        clearTables();
        dbManager.disconnect();
    }

    /**
     * Clears all rows from the test tables.
     * This method uses the getConnection() method from PostgreSQLDatabaseManager.
     */
    private void clearTables() throws DatabaseException {
        Connection conn = dbManager.getConnection();
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM executions");
            stmt.executeUpdate("DELETE FROM orders");
            stmt.executeUpdate("DELETE FROM positions");
            stmt.executeUpdate("DELETE FROM accounts");
        } catch (SQLException e) {
            throw new DatabaseException("Failed to clear tables: " + e.getMessage());
        }
    }

    @Test
    public void testCreateAndGetAccount() throws DatabaseException {
        String accountId = "acct1";
        BigDecimal balance = new BigDecimal("1000.00");
        dbManager.createAccount(accountId, balance);
        Account acct = dbManager.getAccount(accountId);
        assertNotNull(acct, "Account should not be null");
        assertEquals(accountId, acct.getAccountId());
        assertEquals(0, acct.getBalance().compareTo(balance));
    }

    @Test
    public void testUpdateAccount() throws DatabaseException {
        String accountId = "acct1";
        BigDecimal balance = new BigDecimal("1000.00");
        dbManager.createAccount(accountId, balance);
        Account acct = dbManager.getAccount(accountId);
        BigDecimal newBalance = new BigDecimal("1500.00");
        acct.setBalance(newBalance);
        dbManager.updateAccount(acct);
        Account updated = dbManager.getAccount(accountId);
        assertEquals(0, updated.getBalance().compareTo(newBalance));
    }

    @Test
    public void testCreateOrAddSymbol() throws DatabaseException {
        String accountId = "acct1";
        dbManager.createAccount(accountId, new BigDecimal("1000.00"));
        String symbol = "ABC";
        BigDecimal shares1 = new BigDecimal("100.00");
        dbManager.createOrAddSymbol(symbol, accountId, shares1);
        Position pos = dbManager.getPosition(accountId, symbol);
        assertNotNull(pos, "Position should exist");
        assertEquals(0, pos.getQuantity().compareTo(shares1));

        // Add additional shares
        BigDecimal shares2 = new BigDecimal("50.00");
        dbManager.createOrAddSymbol(symbol, accountId, shares2);
        pos = dbManager.getPosition(accountId, symbol);
        assertEquals(0, pos.getQuantity().compareTo(shares1.add(shares2)));
    }

    @Test
    public void testCreateAndGetOrder() throws DatabaseException {
        String accountId = "acct1";
        dbManager.createAccount(accountId, new BigDecimal("5000.00"));
        Order order = new Order(accountId, "XYZ", new BigDecimal("100"), new BigDecimal("25.00"));
        order.setStatus(OrderStatus.OPEN);
        order.setCreationTime(1234567890L);
        long orderId = dbManager.createOrder(order);
        assertTrue(orderId > 0);
        Order fetched = dbManager.getOrder(orderId);
        assertNotNull(fetched, "Fetched order should not be null");
        assertEquals(accountId, fetched.getAccountId());
        assertEquals("XYZ", fetched.getSymbol());
        assertEquals(0, order.getAmount().compareTo(fetched.getAmount()));
        assertEquals(0, order.getLimitPrice().compareTo(fetched.getLimitPrice()));
        assertEquals(OrderStatus.OPEN, fetched.getStatus());
        assertEquals(1234567890L, fetched.getCreationTime());
    }

    @Test
    public void testUpdateOrder() throws DatabaseException {
        String accountId = "acct1";
        dbManager.createAccount(accountId, new BigDecimal("5000.00"));
        Order order = new Order(accountId, "XYZ", new BigDecimal("100"), new BigDecimal("25.00"));
        order.setStatus(OrderStatus.OPEN);
        order.setCreationTime(1234567890L);
        long orderId = dbManager.createOrder(order);
        order.setOrderId(orderId);

        // Update order status to EXECUTED
        order.setStatus(OrderStatus.EXECUTED);
        dbManager.updateOrder(order);

        Order updated = dbManager.getOrder(orderId);
        assertEquals(OrderStatus.EXECUTED, updated.getStatus());
    }

    @Test
    public void testInsertExecutionAndQuery() throws DatabaseException {
        String accountId = "acct1";
        dbManager.createAccount(accountId, new BigDecimal("5000.00"));
        Order order = new Order(accountId, "XYZ", new BigDecimal("100"), new BigDecimal("25.00"));
        order.setStatus(OrderStatus.OPEN);
        order.setCreationTime(1234567890L);
        long orderId = dbManager.createOrder(order);
        order.setOrderId(orderId);

        // Insert two execution records for this order.
        BigDecimal execShares1 = new BigDecimal("40");
        BigDecimal execPrice1 = new BigDecimal("24.00");
        long execTime1 = 1234567900L;
        dbManager.insertExecution(orderId, execShares1, execPrice1, execTime1);

        BigDecimal execShares2 = new BigDecimal("30");
        BigDecimal execPrice2 = new BigDecimal("23.50");
        long execTime2 = 1234567910L;
        dbManager.insertExecution(orderId, execShares2, execPrice2, execTime2);

        BigDecimal totalExecuted = dbManager.getTotalExecutedShares(orderId);
        assertEquals(0, totalExecuted.compareTo(execShares1.add(execShares2)));

        List<QueryResult.ExecutionRecord> execRecords = dbManager.getExecutionsForOrder(orderId);
        assertEquals(2, execRecords.size());
        // Verify ordering by execution time
        assertTrue(execRecords.get(0).timestamp <= execRecords.get(1).timestamp);
    }

    @Test
    public void testGetOpenOrdersForSymbol() throws DatabaseException {
        String accountId = "acct1";
        dbManager.createAccount(accountId, new BigDecimal("10000.00"));

        // Create a BUY order
        Order buyOrder = new Order(accountId, "XYZ", new BigDecimal("100"), new BigDecimal("30.00"));
        buyOrder.setStatus(OrderStatus.OPEN);
        buyOrder.setCreationTime(1000L);
        long buyId = dbManager.createOrder(buyOrder);
        buyOrder.setOrderId(buyId);

        // Create a SELL order
        Order sellOrder = new Order(accountId, "XYZ", new BigDecimal("-50"), new BigDecimal("32.00"));
        sellOrder.setStatus(OrderStatus.OPEN);
        sellOrder.setCreationTime(1001L);
        long sellId = dbManager.createOrder(sellOrder);
        sellOrder.setOrderId(sellId);

        List<Order> openBuys = dbManager.getOpenOrdersForSymbol("XYZ", true);
        assertEquals(1, openBuys.size());
        assertEquals(buyId, openBuys.get(0).getOrderId());

        List<Order> openSells = dbManager.getOpenOrdersForSymbol("XYZ", false);
        assertEquals(1, openSells.size());
        assertEquals(sellId, openSells.get(0).getOrderId());
    }
}
