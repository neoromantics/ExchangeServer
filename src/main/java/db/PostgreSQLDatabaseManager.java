package db;

import engine.QueryResult;
import model.Account;
import model.Order;

import java.math.BigDecimal;
import java.sql.*;
import java.util.List;

public class PostgreSQLDatabaseManager implements DatabaseManager {

    private Connection connection;

    @Override
    public void connect() throws DatabaseException {
        try {
            String url = System.getenv("DB_URL");
            String user = System.getenv("DB_USER");
            String pass = System.getenv("DB_PASS");

            connection = DriverManager.getConnection(url, user, pass);
            connection.setAutoCommit(false);
        } catch (SQLException e) {
            throw new DatabaseException("Failed to connect: " + e.getMessage());
        }
    }

    @Override
    public void disconnect() throws DatabaseException {

    }

    @Override
    public void createAccount(String accountId, BigDecimal initialBalance) throws DatabaseException {

    }

    @Override
    public Account getAccount(String accountId) throws DatabaseException {
        return null;
    }

    @Override
    public void updateAccount(Account account) throws DatabaseException {

    }

    @Override
    public long createOrder(Order order) throws DatabaseException {
        return 0;
    }

    @Override
    public Order getOrder(long orderId) throws DatabaseException {
        return null;
    }

    @Override
    public void updateOrder(Order order) throws DatabaseException {

    }

    @Override
    public List<Order> getOpenOrdersForSymbol(String symbol, boolean isBuySide) throws DatabaseException {
      return null;
    }

    @Override
    public void addSymbolShares(String symbol, String accountId, BigDecimal shares) throws DatabaseException {

    }

    @Override
    public void recordExecution(long orderId, long shares, BigDecimal price, long timestamp) throws DatabaseException {

    }

    @Override
    public List<QueryResult.ExecutionRecord> getExecutionsForOrder(long orderId) throws DatabaseException {
        return List.of();
    }
}
