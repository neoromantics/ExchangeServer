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
            String dbHost = System.getenv("DB_HOST");      // "db"
            String dbPort = System.getenv("DB_PORT");      // "5432"
            String dbName = System.getenv("DB_NAME");      // "exchange_test"
            String dbUser = System.getenv("DB_USER");      // "myuser"
            String dbPass = System.getenv("DB_PASSWORD");  // "mypassword"

            if (dbHost == null) dbHost = "localhost";  // fallback if not in Docker
            if (dbPort == null) dbPort = "5432";
            if (dbName == null) dbName = "exchange_test";
            if (dbUser == null) dbUser = "myuser";
            if (dbPass == null) dbPass = "mypassword";

            String jdbcUrl = "jdbc:postgresql://" + dbHost + ":" + dbPort + "/" + dbName;
            connection = DriverManager.getConnection(jdbcUrl, dbUser, dbPass);

            // Optionally setAutoCommit(false) if manual transaction control
            connection.setAutoCommit(true);

        } catch (SQLException e) {
            throw new DatabaseException("Connect failed: " + e.getMessage());
        }
    }


    @Override
    public void disconnect() throws DatabaseException {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                throw new DatabaseException("Disconnect failed: " + e.getMessage());
            }
        }    }

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
