package server;

import db.DatabaseException;
import db.DatabaseManager;
import engine.MatchingEngine;
import engine.MatchingEngineException;
import engine.QueryResult;
import model.Order;
import model.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
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

    @Test
    public void testProcessCreateSingleAccountSuccess() throws DatabaseException {
        // XML with one <account>
        String xml = "<create>" +
                "  <account id=\"acct100\" balance=\"5000\"/>" +
                "</create>";

        // Mock DB so that createAccount doesn't throw
        doNothing().when(mockDb).createAccount("acct100", new BigDecimal("5000"));

        String result = handler.processCreate(xml);
        System.out.println(result);

        // Expect a <results> with a <created id="acct100"/>
        assertTrue(result.contains("<results>"));
        assertTrue(result.contains("<created id=\"acct100\"/>"));
        verify(mockDb).createAccount("acct100", new BigDecimal("5000"));
    }

    @Test
    public void testProcessCreateSymbolWithAccount() throws DatabaseException {
        // <symbol sym="ABC">
        //   <account id="acct111">200</account>
        // </symbol>
        String xml = "<create>" +
                "  <symbol sym=\"ABC\">" +
                "    <account id=\"acct111\">200</account>" +
                "  </symbol>" +
                "</create>";

        doNothing().when(mockDb).createOrAddSymbol("ABC", "acct111", new BigDecimal("200"));

        String result = handler.processCreate(xml);
        System.out.println(result);

        // Expect <results><created sym="ABC" id="acct111"/></results>
        assertTrue(result.contains("<results>"));
        assertTrue(result.contains("<created"));
        assertTrue(result.contains("sym=\"ABC\""));
        assertTrue(result.contains("id=\"acct111\""));        verify(mockDb).createOrAddSymbol("ABC", "acct111", new BigDecimal("200"));
    }

    @Test
    public void testProcessCreateMultipleMixed() throws DatabaseException {
        // A <create> with multiple children: an <account> and a <symbol>
        String xml = "<create>" +
                " <account id=\"acctA\" balance=\"1000\"/>" +
                " <symbol sym=\"XYZ\">" +
                "   <account id=\"acctA\">50</account>" +
                "   <account id=\"acctB\">75</account>" +
                " </symbol>" +
                "</create>";

        // For <account id="acctA" balance="1000"/>
        doNothing().when(mockDb).createAccount("acctA", new BigDecimal("1000"));
        // For <symbol sym="XYZ"><account id="acctA">50</account>
        doNothing().when(mockDb).createOrAddSymbol("XYZ", "acctA", new BigDecimal("50"));
        // For <symbol sym="XYZ"><account id="acctB">75</account>
        doNothing().when(mockDb).createOrAddSymbol("XYZ", "acctB", new BigDecimal("75"));

        String result = handler.processCreate(xml);
        System.out.println(result);

        // Should produce multiple <created> tags in <results> in the same order
        assertTrue(result.contains("<results>"));
        assertTrue(result.contains("<created"));
        assertTrue(result.contains("id=\"acctA\""), "Account creation for acctA");
        assertTrue(result.contains("sym=\"XYZ\""), "Symbol for acctA");
        assertTrue(result.contains("id=\"acctB\""), "Account creation for acctB");

    }

    @Test
    public void testProcessCreateErrorOnAccount() throws DatabaseException {
        // Suppose creation of an account fails with "Duplicate account"
        String xml = "<create>" +
                "  <account id=\"acct100\" balance=\"3000\"/>" +
                "</create>";

        doThrow(new DatabaseException("Duplicate account"))
                .when(mockDb).createAccount("acct100", new BigDecimal("3000"));

        String result = handler.processCreate(xml);
        System.out.println(result);

        // Expect <results><error id="acct100">Duplicate account</error></results>
        assertTrue(result.contains("<results>"));
        assertTrue(result.contains("<error id=\"acct100\">Duplicate account</error>"));
    }

    // ===============================
    // processTransactions tests
    // ===============================

    @Test
    public void testProcessTransactionsInvalidAccount() throws DatabaseException {
        // <transactions id="acctX"> with an <order> child
        String xml = "<transactions id=\"acctX\">" +
                "  <order sym=\"TST\" amount=\"100\" limit=\"50.00\"/>" +
                "  <cancel id=\"123\"/>" +
                "</transactions>";

        // mockDb.getAccount("acctX") returns null => invalid account
        when(mockDb.getAccount("acctX")).thenReturn(null);

        String result = handler.processTransactions(xml);
        System.out.println(result);

        // Expect <results><error sym="TST" amount="100" limit="50.00">Invalid account</error>
        //  plus <error id="123">Invalid account</error>
        assertTrue(result.contains("Invalid account"));
        assertTrue(result.contains("<error sym=\"TST\" amount=\"100\" limit=\"50.00\">Invalid account</error>"));
        assertTrue(result.contains("<error id=\"123\">Invalid account</error>"));
    }

    @Test
    public void testProcessTransactionsSingleOrder() throws DatabaseException, MatchingEngineException {
        // <transactions id="acct1"><order sym="ABC" amount="100" limit="60.00"/></transactions>
        String xml = "<transactions id=\"acct1\">" +
                "  <order sym=\"ABC\" amount=\"100\" limit=\"60.00\"/>" +
                "</transactions>";

        // The account is valid
        when(mockDb.getAccount("acct1")).thenReturn(new model.Account("acct1", BigDecimal.valueOf(9999)));
        // Suppose openOrder(...) yields an Order with ID=777
        Order fakeOrder = new Order("acct1", "ABC", BigDecimal.valueOf(100), BigDecimal.valueOf(60.00));
        fakeOrder.setOrderId(777L);
        when(mockEngine.openOrder(any(Order.class))).thenReturn(fakeOrder);

        String result = handler.processTransactions(xml);
        System.out.println(result);

        // Expect <results><opened sym="ABC" amount="100" limit="60.00" id="777"/></results>
        assertTrue(result.contains("<opened"), "Result should contain an <opened> element");
        assertTrue(result.contains("sym=\"ABC\""), "Opened element should have sym=\"ABC\"");
        assertTrue(result.contains("amount=\"100\""), "Opened element should have amount=\"100\"");
        assertTrue(result.contains("limit=\"60.00\""), "Opened element should have limit=\"60.00\"");
        assertTrue(result.contains("id=\"777\""), "Opened element should have id=\"777\"");

    }

    @Disabled
    @Test
    public void testProcessTransactionsMultipleChildren() throws DatabaseException, MatchingEngineException {
        // E.g. <transactions id="acct2">
        //   <order sym="AAA" amount="50" limit="10.00"/>
        //   <order sym="BBB" amount="-20" limit="12.00"/>
        //   <query id="777"/>
        //   <cancel id="999"/>
        // </transactions>
        String xml = "<transactions id=\"acct2\">" +
                "  <order sym=\"AAA\" amount=\"50\" limit=\"10.00\"/>" +
                "  <order sym=\"BBB\" amount=\"-20\" limit=\"12.00\"/>" +
                "  <query id=\"777\"/>" +
                "  <cancel id=\"999\"/>" +
                "</transactions>";

        // mockDb says acct2 is valid
        when(mockDb.getAccount("acct2")).thenReturn(new model.Account("acct2", BigDecimal.valueOf(8000)));

        // For the first order
        Order ordA = new Order("acct2", "AAA", BigDecimal.valueOf(50), BigDecimal.valueOf(10));
        ordA.setOrderId(1001L);
        when(mockEngine.openOrder(argThat(o -> o != null && o.getSymbol().equals("AAA")))).thenReturn(ordA);

        // For the second order
        Order ordB = new Order("acct2", "BBB", BigDecimal.valueOf(-20), BigDecimal.valueOf(12));
        ordB.setOrderId(1002L);
        when(mockEngine.openOrder(argThat(o -> o != null && o.getSymbol().equals("BBB")))).thenReturn(ordB);

        // For the query on ID=777 => let's fake a partial fill
        engine.QueryResult qres = new engine.QueryResult(777, OrderStatus.OPEN, BigDecimal.valueOf(30),
                new java.util.ArrayList<>());
        when(mockEngine.queryOrder(777L)).thenReturn(qres);

        // For the cancel on ID=999 => let's fake a canceled order
        Order dummyCanceled = new Order("acct2", "CANCELED_SYM", BigDecimal.valueOf(10), BigDecimal.valueOf(5));
        dummyCanceled.setOrderId(999L);
        dummyCanceled.setStatus(OrderStatus.CANCELED);
        when(mockEngine.cancelOrder(999L)).thenReturn(dummyCanceled);
        // The query after cancellation =>
        engine.QueryResult cancQres = new engine.QueryResult(999L, OrderStatus.CANCELED, BigDecimal.valueOf(5),
                new java.util.ArrayList<>());
        when(mockEngine.queryOrder(999L)).thenReturn(cancQres);

        String result = handler.processTransactions(xml);
        System.out.println(result);

        // Check for results regardless of order of attributes
        assertTrue(result.contains("<results>"));

        // Check the opened elements for AAA and BBB
//        assertTrue(result.contains("<opened sym=\"AAA\""));
        assertTrue(result.contains("amount=\"50\""));
        assertTrue(result.contains("limit=\"10.00\""));
        assertTrue(result.contains("id=\"1001\""));

        assertTrue(result.contains("<opened sym=\"BBB\""));
        assertTrue(result.contains("amount=\"-20\""));
        assertTrue(result.contains("limit=\"12.00\""));
        assertTrue(result.contains("id=\"1002\""));

        // Check for the status element with id 777
        assertTrue(result.contains("<status id=\"777\">"));
        assertTrue(result.contains("<open shares=\"30\""));

        // Check for the canceled element with id 999
        assertTrue(result.contains("<canceled id=\"999\">"));
        assertTrue(result.contains("<canceled shares=\"5\""));
        assertTrue(result.contains("time=\""));
    }



    @Test
    public void testProcessTransactionsOrderError() throws DatabaseException, MatchingEngineException {
        // Suppose the user tries to open an order but the engine throws "Insufficient funds"
        String xml = "<transactions id=\"acctX\">" +
                "  <order sym=\"ZZZ\" amount=\"100\" limit=\"9999.00\"/>" +
                "</transactions>";
        when(mockDb.getAccount("acctX")).thenReturn(new model.Account("acctX", BigDecimal.valueOf(50)));
        when(mockEngine.openOrder(any(Order.class))).thenThrow(new MatchingEngineException("Insufficient funds"));

        String result = handler.processTransactions(xml);
        System.out.println(result);

        // Expect <results><error sym="ZZZ" amount="100" limit="9999.00">Insufficient funds</error></results>
        assertTrue(result.contains("<error"), "Result should contain an <error> element");
        assertTrue(result.contains("sym=\"ZZZ\""), "Error element should contain sym=\"ZZZ\"");
        assertTrue(result.contains("amount=\"100\""), "Error element should contain amount=\"100\"");
        assertTrue(result.contains("limit=\"9999.00\""), "Error element should contain limit=\"9999.00\"");
        assertTrue(result.contains("Insufficient funds"), "Error element should contain the error message");

    }
}
