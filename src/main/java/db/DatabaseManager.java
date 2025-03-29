package db;

import java.math.BigDecimal;
import java.util.List;

import engine.QueryResult;
import model.Account;
import model.Order;

public interface DatabaseManager {

    void connect() throws DatabaseException;

    void disconnect() throws DatabaseException;

    void createAccount(String accountId, BigDecimal initialBalance) throws DatabaseException;

    Account getAccount(String accountId) throws DatabaseException;

    void updateAccount(Account account) throws DatabaseException;

    long createOrder(Order order) throws DatabaseException;

    Order getOrder(long orderId) throws DatabaseException;

    void updateOrder(Order order) throws DatabaseException;

    List<Order> getOpenOrdersForSymbol(String symbol, boolean isBuySide) throws DatabaseException;

    void addSymbolShares(String symbol, String accountId, BigDecimal shares) throws DatabaseException;

    void recordExecution(long orderId, long shares, BigDecimal price, long timestamp) throws DatabaseException;

    List<QueryResult.ExecutionRecord> getExecutionsForOrder(long orderId) throws DatabaseException;
}
