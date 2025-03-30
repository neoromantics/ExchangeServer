package engine;

import model.OrderStatus;

import java.math.BigDecimal;
import java.util.List;

/**
 * Holds the result of a query for one order.
 * Store status, how many shares remain open, and a list of partial fill records.
 */
public class QueryResult {

    public final long orderId;
    public final OrderStatus status;
    public final BigDecimal openShares;  // how many remain unfilled
    public final List<ExecutionRecord> executions;

    public QueryResult(long orderId, OrderStatus status, BigDecimal openShares, List<ExecutionRecord> execs) {
        this.orderId = orderId;
        this.status = status;
        this.openShares = openShares;
        this.executions = execs;
    }

    // Nested class to describe a single partial fill
    public static class ExecutionRecord {
        public final BigDecimal shares;
        public final BigDecimal price;
        public final long timestamp;

        public ExecutionRecord(BigDecimal shares, BigDecimal price, long timestamp) {
            this.shares = shares;
            this.price = price;
            this.timestamp = timestamp;
        }
    }
}
