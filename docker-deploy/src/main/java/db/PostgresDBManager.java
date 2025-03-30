package db;

import engine.QueryResult;
import model.Account;
import model.Order;
import model.OrderStatus;
import model.Position;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class PostgresDBManager implements DatabaseManager {

    private Connection connection;
    public Connection getConnection() {
        return connection;
    }
    @Override
    public void connect() throws DatabaseException {
        try {
            // Read these from environment variables or a config file
            String dbHost = System.getenv("DB_HOST");
            String dbPort = System.getenv("DB_PORT");
            String dbName = System.getenv("DB_NAME");
            String dbUser = System.getenv("DB_USER");
            String dbPass = System.getenv("DB_PASSWORD");

            // Fallback defaults if not set
            if (dbHost == null) dbHost = "localhost";
            if (dbPort == null) dbPort = "5432";
            if (dbName == null) dbName = "exchange_test";
            if (dbUser == null) dbUser = "myuser";
            if (dbPass == null) dbPass = "mypassword";

            String jdbcUrl = "jdbc:postgresql://" + dbHost + ":" + dbPort + "/" + dbName;

            connection = DriverManager.getConnection(jdbcUrl, dbUser, dbPass);
            connection.setAutoCommit(true);

        } catch (SQLException e) {
            throw new DatabaseException("Failed to connect: " + e.getMessage());
        }
    }


    @Override
    public void disconnect() throws DatabaseException {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                throw new DatabaseException("Failed to disconnect: " + e.getMessage());
            }
        }
    }

    @Override
    public void createAccount(String accountId, BigDecimal initialBalance) throws DatabaseException {
        String sql = "INSERT INTO accounts (account_id, balance) VALUES (?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, accountId);
            stmt.setBigDecimal(2, initialBalance);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("createAccount: " + e.getMessage());
        }
    }


    @Override
    public Account getAccount(String accountId) throws DatabaseException {
        String sql = "SELECT account_id, balance FROM accounts WHERE account_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, accountId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String id = rs.getString("account_id");
                    BigDecimal bal = rs.getBigDecimal("balance");
                    Account acc = new Account(id, bal);
                    return acc;
                }
                return null;
            }
        } catch (SQLException e) {
            throw new DatabaseException("getAccount: " + e.getMessage());
        }
    }

    @Override
    public void updateAccount(Account account) throws DatabaseException {
        String sql = "UPDATE accounts SET balance = ? WHERE account_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setBigDecimal(1, account.getBalance());
            stmt.setString(2, account.getAccountId());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("updateAccount: " + e.getMessage());
        }
    }

    @Override
    public void createOrAddSymbol(String symbol, String accountId, BigDecimal shares) throws DatabaseException {
        // Check if positions row already exists
        String selectPos = "SELECT quantity FROM positions WHERE account_id=? AND symbol=?";
        try (PreparedStatement stmt = connection.prepareStatement(selectPos)) {
            stmt.setString(1, accountId);
            stmt.setString(2, symbol);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    // update existing row
                    BigDecimal currentQty = rs.getBigDecimal("quantity");
                    BigDecimal newQty = currentQty.add(shares);

                    // no negative quantity allowed as short is disallowed
                    if (newQty.compareTo(BigDecimal.ZERO) < 0) {
                        throw new DatabaseException("Cannot add negative shares that lead to short position");
                    }

                    String upd = "UPDATE positions SET quantity=? WHERE account_id=? AND symbol=?";
                    try (PreparedStatement upStmt = connection.prepareStatement(upd)) {
                        upStmt.setBigDecimal(1, newQty);
                        upStmt.setString(2, accountId);
                        upStmt.setString(3, symbol);
                        upStmt.executeUpdate();
                    }
                } else {
                    // insert a new row
                    if (shares.compareTo(BigDecimal.ZERO) < 0) {
                        throw new DatabaseException("Cannot create a negative position");
                    }
                    String ins = "INSERT INTO positions (account_id, symbol, quantity) VALUES (?, ?, ?)";
                    try (PreparedStatement inStmt = connection.prepareStatement(ins)) {
                        inStmt.setString(1, accountId);
                        inStmt.setString(2, symbol);
                        inStmt.setBigDecimal(3, shares);
                        inStmt.executeUpdate();
                    }
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("createOrAddSymbol: " + e.getMessage());
        }
    }

    /**
     * Insert a new order into the orders table. Returns the generated order ID.
     */
    @Override
    public long createOrder(Order order) throws DatabaseException {
        String sql = "INSERT INTO orders (account_id, symbol, amount, limit_price, status, creation_time) "
                + "VALUES (?, ?, ?, ?, ?, ?) RETURNING order_id";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, order.getAccountId());
            stmt.setString(2, order.getSymbol());
            stmt.setBigDecimal(3, order.getAmount());
            stmt.setBigDecimal(4, order.getLimitPrice());
            stmt.setString(5, order.getStatus().name());
            stmt.setLong(6, order.getCreationTime());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    long generatedId = rs.getLong("order_id");
                    return generatedId;
                } else {
                    throw new DatabaseException("createOrder: no ID returned");
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("createOrder: " + e.getMessage());
        }
    }

    /**
     * Retrieve an order by ID.
     */
    @Override
    public Order getOrder(long orderId) throws DatabaseException {
        String sql = "SELECT account_id, symbol, amount, limit_price, status, creation_time "
                + "FROM orders WHERE order_id=?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, orderId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Order o = new Order(
                            rs.getString("account_id"),
                            rs.getString("symbol"),
                            rs.getBigDecimal("amount"),
                            rs.getBigDecimal("limit_price")
                    );
                    o.setOrderId(orderId);
                    o.setStatus(OrderStatus.valueOf(rs.getString("status")));
                    o.setCreationTime(rs.getLong("creation_time"));
                    return o;
                }
                return null;
            }
        } catch (SQLException e) {
            throw new DatabaseException("getOrder: " + e.getMessage());
        }
    }

    /**
     * Update an existing order
     */
    @Override
    public void updateOrder(Order order) throws DatabaseException {
        String sql = "UPDATE orders "
                + "SET account_id=?, symbol=?, amount=?, limit_price=?, status=?, creation_time=? "
                + "WHERE order_id=?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, order.getAccountId());
            stmt.setString(2, order.getSymbol());
            stmt.setBigDecimal(3, order.getAmount());
            stmt.setBigDecimal(4, order.getLimitPrice());
            stmt.setString(5, order.getStatus().name());
            stmt.setLong(6, order.getCreationTime());
            stmt.setLong(7, order.getOrderId());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("updateOrder: " + e.getMessage());
        }
    }

    /**
     * Return all open orders for a symbol, sorted by best price first (descending for buys, ascending for sells),
     * then by earliest creation_time.
     */
    @Override
    public List<Order> getOpenOrdersForSymbol(String symbol, boolean isBuySide) throws DatabaseException {
        // For buy side: order by limit_price DESC, creation_time ASC
        // For sell side: order by limit_price ASC, creation_time ASC
        String orderByClause = isBuySide ? "ORDER BY limit_price DESC, creation_time ASC"
                : "ORDER BY limit_price ASC, creation_time ASC";

        String sql = "SELECT order_id, account_id, amount, limit_price, status, creation_time "
                + "FROM orders "
                + "WHERE symbol=? AND status='OPEN' "
                + "AND ((?=TRUE AND amount>0) OR (?=FALSE AND amount<0)) "
                + orderByClause;

        List<Order> list = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, symbol);
            stmt.setBoolean(2, isBuySide);
            stmt.setBoolean(3, isBuySide);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    long oid = rs.getLong("order_id");
                    String acctId = rs.getString("account_id");
                    BigDecimal amt = rs.getBigDecimal("amount");
                    BigDecimal limPrice = rs.getBigDecimal("limit_price");
                    Order o = new Order(acctId, symbol, amt, limPrice);
                    o.setOrderId(oid);
                    o.setStatus(OrderStatus.valueOf(rs.getString("status")));
                    o.setCreationTime(rs.getLong("creation_time"));
                    list.add(o);
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("getOpenOrdersForSymbol: " + e.getMessage());
        }
        return list;
    }

    /**
     * Insert an execution record in the 'executions' table.
     */
    @Override
    public void insertExecution(long orderId, BigDecimal shares, BigDecimal price, long timestamp)
            throws DatabaseException {
        String sql = "INSERT INTO executions (order_id, shares, price, exec_time) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, orderId);
            ps.setBigDecimal(2, shares);
            ps.setBigDecimal(3, price);
            ps.setLong(4, timestamp);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("insertExecution: " + e.getMessage());
        }
    }

    /**
     * Sum all shares from the executions table for the given orderId.
     */
    @Override
    public BigDecimal getTotalExecutedShares(long orderId) throws DatabaseException {
        String sql = "SELECT COALESCE(SUM(shares), 0) AS total_filled FROM executions WHERE order_id=?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBigDecimal("total_filled");
                }
                return BigDecimal.ZERO;
            }
        } catch (SQLException e) {
            throw new DatabaseException("getTotalExecutedShares: " + e.getMessage());
        }
    }

    /**
     * Return all execution records for the given order, in ascending exec_time order.
     * Then convert them to QueryResult.ExecutionRecord objects for convenience.
     */
    @Override
    public List<QueryResult.ExecutionRecord> getExecutionsForOrder(long orderId) throws DatabaseException {
        String sql = "SELECT shares, price, exec_time "
                + "FROM executions "
                + "WHERE order_id=? "
                + "ORDER BY exec_time ASC";

        List<QueryResult.ExecutionRecord> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    BigDecimal sh = rs.getBigDecimal("shares");
                    BigDecimal pr = rs.getBigDecimal("price");
                    long t = rs.getLong("exec_time");
                    result.add(new QueryResult.ExecutionRecord(sh, pr, t));
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("getExecutionsForOrder: " + e.getMessage());
        }
        return result;
    }

    // Get position:
    public Position getPosition(String accountId, String symbol) throws DatabaseException {
        String sql = "SELECT quantity FROM positions WHERE account_id=? AND symbol=?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, accountId);
            stmt.setString(2, symbol);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    BigDecimal qty = rs.getBigDecimal("quantity");
                    return new Position(symbol, qty);
                } else {
                    return null;
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("getPosition: " + e.getMessage());
        }
    }

    public void updatePosition(String accountId, String symbol, BigDecimal newQuantity) throws DatabaseException {
        String sql = "UPDATE positions SET quantity=? WHERE account_id=? AND symbol=?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setBigDecimal(1, newQuantity);
            stmt.setString(2, accountId);
            stmt.setString(3, symbol);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("updatePosition: " + e.getMessage());
        }
    }
}
