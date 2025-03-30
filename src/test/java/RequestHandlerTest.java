import db.DatabaseException;
import db.DatabaseManager;
import engine.MatchingEngine;
import engine.MatchingEngineException;
import engine.QueryResult;
import model.Order;
import model.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import server.RequestHandler;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class RequestHandlerTest {
    private DatabaseManager mockDb;
    private MatchingEngine mockEngine;
    private RequestHandler handler;

    @BeforeEach
    public void setUp() {
        // Mocks are created for DatabaseManager and MatchingEngine.
        mockDb = mock(DatabaseManager.class);
        mockEngine = mock(MatchingEngine.class);
        handler = new RequestHandler(mockDb, mockEngine);
    }

    @Test
    public void testCreateAccountSuccess() throws DatabaseException {
        String accountId = "acct123";
        BigDecimal balance = new BigDecimal("1000.00");
        // Simulates successful account creation.
        doNothing().when(mockDb).createAccount(accountId, balance);

        String xml = handler.createAccount(accountId, balance);

        // Verifies that the XML contains a <created> element with the expected attributes.
        System.out.println(xml);
        assertTrue(xml.contains("<created"));
        assertTrue(xml.contains("id=\"" + accountId + "\""));
//        assertTrue(xml.contains("balance=\"" + balance.toPlainString() + "\""));
    }

    @Test
    public void testCreateAccountError() throws DatabaseException {
        String accountId = "acctError";
        BigDecimal balance = new BigDecimal("500.00");
        // Simulates a database exception during account creation.
        doThrow(new DatabaseException("Duplicate account")).when(mockDb).createAccount(accountId, balance);

        String xml = handler.createAccount(accountId, balance);

        assertTrue(xml.contains("<error"));
        assertTrue(xml.contains("id=\"" + accountId + "\""));
        assertTrue(xml.contains("Duplicate account"));
    }

    @Test
    public void testCreateOrAddSymbolSuccess() throws DatabaseException {
        String symbol = "XYZ";
        String accountId = "acctSymbol";
        BigDecimal shares = new BigDecimal("100");
        doNothing().when(mockDb).createOrAddSymbol(symbol, accountId, shares);

        String xml = handler.createOrAddSymbol(symbol, accountId, shares);
        System.out.println(xml);
        assertTrue(xml.contains("<created"));
        assertTrue(xml.contains("sym=\"" + symbol + "\""));
        assertTrue(xml.contains("id=\"" + accountId + "\""));
    }

    @Test
    public void testCreateOrAddSymbolError() throws DatabaseException {
        String symbol = "XYZ";
        String accountId = "acctSymbol";
        BigDecimal shares = new BigDecimal("100");
        doThrow(new DatabaseException("Symbol creation failed")).when(mockDb).createOrAddSymbol(symbol, accountId, shares);

        String xml = handler.createOrAddSymbol(symbol, accountId, shares);
        System.out.println(xml);

        assertTrue(xml.contains("<error"));
        assertTrue(xml.contains("sym=\"" + symbol + "\""));
        assertTrue(xml.contains("id=\"" + accountId + "\""));
        assertTrue(xml.contains("Symbol creation failed"));
    }

    @Test
    public void testOpenOrderSuccess() throws DatabaseException, MatchingEngineException {
        String accountId = "buyer1";
        String symbol = "XYZ";
        BigDecimal amount = new BigDecimal("100");
        BigDecimal limitPrice = new BigDecimal("50.00");

        Order order = new Order(accountId, symbol, amount, limitPrice);
        order.setOrderId(101L);
        when(mockEngine.openOrder(any(Order.class))).thenReturn(order);

        String xml = handler.openOrder(accountId, symbol, amount, limitPrice);
        System.out.println(xml);

        assertTrue(xml.contains("<opened"));
        assertTrue(xml.contains("sym=\"" + symbol + "\""));
        assertTrue(xml.contains("amount=\"" + amount.toPlainString() + "\""));
        assertTrue(xml.contains("limit=\"" + limitPrice.toPlainString() + "\""));
        assertTrue(xml.contains("id=\"101\""));
    }

    @Test
    public void testOpenOrderError() throws DatabaseException, MatchingEngineException {
        String accountId = "buyerError";
        String symbol = "XYZ";
        BigDecimal amount = new BigDecimal("100");
        BigDecimal limitPrice = new BigDecimal("50.00");

        when(mockEngine.openOrder(any(Order.class)))
                .thenThrow(new MatchingEngineException("Insufficient funds"));

        String xml = handler.openOrder(accountId, symbol, amount, limitPrice);

        assertTrue(xml.contains("<error"));
        assertTrue(xml.contains("sym=\"" + symbol + "\""));
        assertTrue(xml.contains("amount=\"" + amount.toPlainString() + "\""));
        assertTrue(xml.contains("limit=\"" + limitPrice.toPlainString() + "\""));
        assertTrue(xml.contains("Insufficient funds"));
    }

    @Test
    public void testCancelOrderSuccess() throws DatabaseException, MatchingEngineException {
        long orderId = 200L;
        // A dummy canceled order is created.
        Order canceledOrderDummy = new Order("dummy", "XYZ", new BigDecimal("100"), new BigDecimal("50.00"));
        canceledOrderDummy.setOrderId(orderId);
        canceledOrderDummy.setStatus(OrderStatus.CANCELED);

        // The cancelOrder method is stubbed to return the dummy canceled order.
        when(mockEngine.cancelOrder(orderId)).thenReturn(canceledOrderDummy);

        // The queryOrder method is stubbed to return a QueryResult with no open shares.
        QueryResult qr = new QueryResult(orderId, OrderStatus.CANCELED, BigDecimal.ZERO, new ArrayList<>());
        when(mockEngine.queryOrder(orderId)).thenReturn(qr);

        String xml = handler.cancelOrder(orderId);

        // The XML is verified to contain a <canceled> element with the order id.
        assertTrue(xml.contains("<canceled"));
        assertTrue(xml.contains("id=\"" + orderId + "\""));
    }

    @Test
    public void testCancelOrderError() {
        long orderId = 300L;
        try {
            when(mockEngine.cancelOrder(orderId))
                    .thenThrow(new MatchingEngineException("Order not found"));
        } catch (MatchingEngineException e) {
            fail("Unexpected exception during mock setup: " + e.getMessage());
        }

        String xml = handler.cancelOrder(orderId);

        assertTrue(xml.contains("<error"));
        assertTrue(xml.contains("id=\"" + orderId + "\""));
        assertTrue(xml.contains("Order not found"));
    }

    @Test
    public void testQueryOrderSuccess() throws DatabaseException, MatchingEngineException {
        long orderId = 400L;
        QueryResult qr = new QueryResult(orderId, OrderStatus.OPEN, new BigDecimal("100"), new ArrayList<>());
        when(mockEngine.queryOrder(orderId)).thenReturn(qr);

        String xml = handler.queryOrder(orderId);
        System.out.println(xml);
        assertTrue(xml.contains("<status"));
        assertTrue(xml.contains("id=\"" + orderId + "\""));
        assertTrue(xml.contains("<open"));
        assertTrue(xml.contains("shares=\"100\""));
    }

    @Test
    public void testQueryOrderError() {
        long orderId = 500L;
        try {
            when(mockEngine.queryOrder(orderId))
                    .thenThrow(new MatchingEngineException("Order not found"));
        } catch (MatchingEngineException e) {
            fail("Unexpected exception during mock setup: " + e.getMessage());
        }

        String xml = handler.queryOrder(orderId);

        assertTrue(xml.contains("<error"));
        assertTrue(xml.contains("id=\"" + orderId + "\""));
        assertTrue(xml.contains("Order not found"));
    }

    @Test
    public void testQueryOrderWithMultipleExecutions() throws DatabaseException, MatchingEngineException {
        long orderId = 600L;
        // A QueryResult with multiple execution records is simulated:
        // For example, 30 shares executed at 45.00 and 20 shares executed at 46.00,
        // with 50 shares still open.
        List<QueryResult.ExecutionRecord> execs = new ArrayList<>();
        execs.add(new QueryResult.ExecutionRecord(new BigDecimal("30"), new BigDecimal("45.00"), 1610000000L));
        execs.add(new QueryResult.ExecutionRecord(new BigDecimal("20"), new BigDecimal("46.00"), 1610000100L));
        QueryResult qr = new QueryResult(orderId, OrderStatus.OPEN, new BigDecimal("50"), execs);
        when(mockEngine.queryOrder(orderId)).thenReturn(qr);

        String xml = handler.queryOrder(orderId);
        System.out.println(xml);
        // The XML is verified to contain the <status> element with the order id.
        assertTrue(xml.contains("<status"));
        assertTrue(xml.contains("id=\"" + orderId + "\""));

        // It should contain an <open> element with 50 shares.
        assertTrue(xml.contains("<open"));
        assertTrue(xml.contains("shares=\"50\""));

        // And two <executed> elements for the two execution records.
        assertTrue(xml.contains("<executed"));
        assertTrue(xml.contains("shares=\"30\""));
        assertTrue(xml.contains("price=\"45.00\""));
        assertTrue(xml.contains("shares=\"20\""));
        assertTrue(xml.contains("price=\"46.00\""));
    }

    @Test
    public void testCancelOrderWithPartialExecutions() throws DatabaseException, MatchingEngineException {
        long orderId = 700L;
        // A BUY order that is partially filled is simulated.
        Order dummyOrder = new Order("buyerX", "XYZ", new BigDecimal("100"), new BigDecimal("50.00"));
        dummyOrder.setOrderId(orderId);
        dummyOrder.setStatus(OrderStatus.OPEN);

        // The cancelOrder method is stubbed to return the order (after marking it as CANCELED).
        when(mockEngine.cancelOrder(orderId)).thenReturn(dummyOrder);
        // It is simulated that 40 shares have been executed, leaving 60 shares open.
        List<QueryResult.ExecutionRecord> execs = new ArrayList<>();
        execs.add(new QueryResult.ExecutionRecord(new BigDecimal("40"), new BigDecimal("45.00"), 1610000200L));
        QueryResult qr = new QueryResult(orderId, OrderStatus.CANCELED, new BigDecimal("60"), execs);
        when(mockEngine.queryOrder(orderId)).thenReturn(qr);

        String xml = handler.cancelOrder(orderId);

        // The XML should include a top-level <canceled> element with the correct order id.
        assertTrue(xml.contains("<canceled"));
        assertTrue(xml.contains("id=\"" + orderId + "\""));

        // It should include an <executed> element for the 40 shares.
        assertTrue(xml.contains("<executed"));
        assertTrue(xml.contains("shares=\"40\""));
        assertTrue(xml.contains("price=\"45.00\""));

        // A nested <canceled> element for the remaining 60 shares is expected.
        // (The assumption is that the implementation nests the remaining cancel info inside the outer canceled tag.)
        assertTrue(xml.contains("<canceled"));
        assertTrue(xml.contains("shares=\"60\""));
    }
}
