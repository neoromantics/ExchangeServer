package engine;

import java.util.ArrayList;
import java.util.List;

public class QueryResult {
    public static class ExecutionRecord {
        public long shares;
        public double price;
        public long timestamp;
    }
    public long orderId;
    public long openShares;
    public boolean canceled;
    public List<ExecutionRecord> executions = new ArrayList<>();
}