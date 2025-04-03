package db;

import model.Account;
import model.Order;
import model.Position;
import engine.QueryResult.ExecutionRecord;

import java.math.BigDecimal;
import java.util.List;

public interface DatabaseManager {

    void connect() throws DatabaseException;
    void disconnect() throws DatabaseException;

    void beginTransaction() throws DatabaseException;
    void commitTransaction() throws DatabaseException;
    void rollbackTransaction() throws DatabaseException;

    Account getAccountForUpdate(String accountId) throws DatabaseException;
    Position getPositionForUpdate(String accountId, String symbol) throws DatabaseException;
    Order getOrderForUpdate(long orderId) throws DatabaseException;

    void createAccount(String accountId, BigDecimal initialBalance) throws DatabaseException;
    Account getAccount(String accountId) throws DatabaseException;
    void updateAccount(Account account) throws DatabaseException;

    void createOrAddSymbol(String symbol, String accountId, BigDecimal shares) throws DatabaseException;

    long createOrder(Order order) throws DatabaseException;
    Order getOrder(long orderId) throws DatabaseException;
    void updateOrder(Order order) throws DatabaseException;

    List<Order> getOpenOrdersForSymbol(String symbol, boolean isBuySide) throws DatabaseException;

    void insertExecution(long orderId, BigDecimal shares, BigDecimal price, long timestamp) throws DatabaseException;
    BigDecimal getTotalExecutedShares(long orderId) throws DatabaseException;
    List<ExecutionRecord> getExecutionsForOrder(long orderId) throws DatabaseException;

    Position getPosition(String accountId, String symbol) throws DatabaseException;
    void updatePosition(String accountId, String symbol, BigDecimal newQuantity) throws DatabaseException;
}
