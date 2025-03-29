package engine;

import db.DatabaseManager;
import model.Order;

public class MatchingEngine {

    private final DatabaseManager dbManager;
    public MatchingEngine(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public Order openOrder(Order order) throws MatchingEngineException {
        // 1) Validate account, check funds/positions
        // 2) Lock funds/shares by updating the account and DB
        // 3) Insert the order in DB (createOrder)
        // 4) Attempt matching logic (look at opposing open orders, fill if price constraints allow)
        // 5) Return the final state of the newly-created order (with assigned ID)
        return null;
    }

    public Order cancelOrder(long orderId) throws MatchingEngineException {
        // 1) Retrieve the order
        // 2) If it's still partially or fully open, mark canceled in DB
        // 3) Refund locked funds or shares
        // 4) Return final updated order
        return null;
    }

    public QueryResult getOrderStatus(long orderId) throws MatchingEngineException {
        // 1) Retrieve the order from DB
        // 2) Retrieve all partial execution records
        // 3) Return a QueryResult that has the open/canceled/executed data
        return null;
    }


    protected void match(Order newOrder) throws MatchingEngineException {
        // 1) While there is a compatible opposing order
        // 2) Determine execution price (whichever order was on the book first)
        // 3) Execute partial or full fill
        // 4) Update DB for both orders, record partial fills, etc.
    }

}
