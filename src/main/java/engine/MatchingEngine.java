package engine;

import java.math.RoundingMode;

import db.DatabaseException;
import db.DatabaseManager;
import model.Account;
import model.Order;
import model.OrderStatus;
import model.Position;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public class MatchingEngine {

    private final DatabaseManager db;

    public MatchingEngine(DatabaseManager dbManager) {
        this.db = dbManager;
    }

    /**
     * Place a new buy/sell order:
     * 1) Validate no short-selling or insufficient funds.
     * 2) Mark the order as OPEN, store it, and withhold cost or shares.
     * 3) Immediately attempt to match with existing orders on the other side.
     */
    public Order openOrder(Order order) throws MatchingEngineException {
        try {
            // 1. Validate the account
            Account acct = db.getAccount(order.getAccountId());
            if (acct == null) {
                throw new MatchingEngineException("Account not found: " + order.getAccountId());
            }

            // Withhold the full cost or full shares
            BigDecimal sharesNeeded = order.getAmount().abs();
            if (order.getAmount().compareTo(BigDecimal.ZERO) > 0) {
                // BUY
                BigDecimal cost = order.getLimitPrice().multiply(sharesNeeded);
                if (acct.getBalance().compareTo(cost) < 0) {
                    throw new MatchingEngineException("Insufficient funds to open buy order");
                }
                // Withhold entire cost
                acct.setBalance(acct.getBalance().subtract(cost));
                db.updateAccount(acct);
            } else {
                // SELL
                // Withhold shares from positions
                Position pos = db.getPosition(acct.getAccountId(), order.getSymbol());
                if (pos == null || pos.getQuantity().compareTo(sharesNeeded) < 0) {
                    throw new MatchingEngineException("Insufficient shares - short selling disallowed");
                }
                BigDecimal newQty = pos.getQuantity().subtract(sharesNeeded);
                db.updatePosition(acct.getAccountId(), order.getSymbol(), newQty);
            }

            // 2. Insert the order as OPEN
            order.setStatus(OrderStatus.OPEN);
            order.setCreationTime(Instant.now().getEpochSecond());
            long newId = db.createOrder(order);
            order.setOrderId(newId);

            // 3. Attempt to match
            matchOrders(order);

            return order;
        } catch (DatabaseException e) {
            throw new MatchingEngineException("DB Error: " + e.getMessage());
        }
    }

    /**
     * Cancel an existing open order. Refund leftover money/shares for the unfilled portion.
     */
    public Order cancelOrder(long orderId) throws MatchingEngineException {
        try {
            Order ord = db.getOrder(orderId);
            if (ord == null) {
                throw new MatchingEngineException("Order not found: " + orderId);
            }
            if (ord.getStatus() != OrderStatus.OPEN) {
                throw new MatchingEngineException("Order not OPEN, cannot cancel");
            }

            // Mark canceled
            ord.setStatus(OrderStatus.CANCELED);
            db.updateOrder(ord);

            // Withheld full cost/shares at open, but have been adjusting incrementally after each fill
            // The unfilled portion remains withheld, so must now return it.
            BigDecimal totalExec = db.getTotalExecutedShares(orderId);
            BigDecimal original = ord.getAmount().abs();
            BigDecimal leftover = original.subtract(totalExec);

            Account acct = db.getAccount(ord.getAccountId());
            if (ord.getAmount().compareTo(BigDecimal.ZERO) > 0) {
                // BUY: leftover cost => leftover * limitPrice
                BigDecimal leftoverCost = leftover.multiply(ord.getLimitPrice());
                acct.setBalance(acct.getBalance().add(leftoverCost));
                db.updateAccount(acct);
            } else {
                // SELL: leftover shares => add them back to user
                if (leftover.compareTo(BigDecimal.ZERO) > 0) {
                    Position pos = db.getPosition(acct.getAccountId(), ord.getSymbol());
                    if (pos == null) {
                        db.createOrAddSymbol(ord.getSymbol(), acct.getAccountId(), leftover);
                    } else {
                        BigDecimal newQty = pos.getQuantity().add(leftover);
                        db.updatePosition(acct.getAccountId(), ord.getSymbol(), newQty);
                    }
                }
            }

            return ord;
        } catch (DatabaseException e) {
            throw new MatchingEngineException("DB error: " + e.getMessage());
        }
    }

    /**
     * Query an order, returning open/executed/canceled shares, partial fills, etc.
     */
    public QueryResult queryOrder(long orderId) throws MatchingEngineException {
        try {
            Order ord = db.getOrder(orderId);
            if (ord == null) {
                throw new MatchingEngineException("Order not found: " + orderId);
            }

            // Gather all execution records
            List<QueryResult.ExecutionRecord> execs = db.getExecutionsForOrder(orderId);
            BigDecimal totalExec = BigDecimal.ZERO;
            for (QueryResult.ExecutionRecord er : execs) {
                totalExec = totalExec.add(er.shares);
            }
            BigDecimal original = ord.getAmount().abs();
            BigDecimal openShares = original.subtract(totalExec);

            return new QueryResult(orderId, ord.getStatus(), openShares, execs);
        } catch (DatabaseException e) {
            throw new MatchingEngineException("DB error: " + e.getMessage());
        }
    }

    /**
     * Match the newly opened or re-evaluated order against opposite open orders.
     * For each partial fill:
     *  - If buy => immediately refund leftover difference & credit partial shares
     *  - If sell => immediately pay out proceeds & finalize that partial portion
     */
    private void matchOrders(Order incomingOrder) throws MatchingEngineException {
        try {
            boolean isBuy = incomingOrder.getAmount().compareTo(BigDecimal.ZERO) > 0;
            BigDecimal sharesRemaining = incomingOrder.getAmount().abs();
            String symbol = incomingOrder.getSymbol();

            while (sharesRemaining.compareTo(BigDecimal.ZERO) > 0
                    && incomingOrder.getStatus() == OrderStatus.OPEN) {

                // 1. get best opposite side order
                List<Order> oppList = db.getOpenOrdersForSymbol(symbol, !isBuy);
                if (oppList.isEmpty()) {
                    break;
                }
                Order candidate = oppList.get(0);

                // 2. check limit price compatibility
                BigDecimal buyLimit, sellLimit;
                Order olderOrder;
                if (isBuy) {
                    buyLimit = incomingOrder.getLimitPrice();
                    sellLimit = candidate.getLimitPrice();
                    olderOrder = (candidate.getCreationTime() <= incomingOrder.getCreationTime())
                            ? candidate : incomingOrder;
                } else {
                    buyLimit = candidate.getLimitPrice();
                    sellLimit = incomingOrder.getLimitPrice();
                    olderOrder = (candidate.getCreationTime() <= incomingOrder.getCreationTime())
                            ? candidate : incomingOrder;
                }
                if (sellLimit.compareTo(buyLimit) > 0) {
                    // not compatible
                    break;
                }
                // 3. determine execution price
                BigDecimal execPrice = olderOrder.getLimitPrice();

                // 4. how many shares can match
                BigDecimal candidateShares = candidate.getAmount().abs();
                BigDecimal matchedShares = sharesRemaining.min(candidateShares);

                // record execution for both
                long now = Instant.now().getEpochSecond();
                db.insertExecution(candidate.getOrderId(), matchedShares, execPrice, now);
                db.insertExecution(incomingOrder.getOrderId(), matchedShares, execPrice, now);

                // 5. partial fill logic for the candidate
                BigDecimal newCandidateShares = candidateShares.subtract(matchedShares);
                if (newCandidateShares.compareTo(BigDecimal.ZERO) == 0) {
                    // fully filled candidate
                    candidate.setStatus(OrderStatus.EXECUTED);
                    db.updateOrder(candidate);

                    // finalize that candidate
                    partialFillPayout(candidate, matchedShares, execPrice);
                } else {
                    // partially filled candidate => remains open
                    // reduce its "amount" in the DB or re-insert some leftover logic
                    // Typically keep the same order row. Can't just update "orders.amount" directly,
                    // or might lose the original total.
                    // But for simplicity, let's just store the original amount and rely on 'executions' to see how many are left.
                    // Will do immediate partial payout anyway:
                    partialFillPayout(candidate, matchedShares, execPrice);
                }

                // 6. partial fill logic for the incoming order
                sharesRemaining = sharesRemaining.subtract(matchedShares);
                partialFillPayout(incomingOrder, matchedShares, execPrice);

            }

            // if sharesRemaining = 0 => fully executed
            if (sharesRemaining.compareTo(BigDecimal.ZERO) == 0) {
                incomingOrder.setStatus(OrderStatus.EXECUTED);
                db.updateOrder(incomingOrder);
            }
        } catch (DatabaseException e) {
            throw new MatchingEngineException("matchOrders DB error: " + e.getMessage());
        }
    }

    /**
     * Do the partial fill logic for the order that just matched matchedShares at execPrice.
     * Will do an immediate partial cost refund (if it's a buy) or partial proceeds payout (if it's a sell).
     * Will also credit partial shares to a buyer, or confirm partial shares are removed from a seller.
     */

    private void partialFillPayout(Order order, BigDecimal matchedShares, BigDecimal execPrice)
            throws DatabaseException {
        Account acct = db.getAccount(order.getAccountId());
        if (acct == null) return; // should not happen

        // If it's a BUY order
        if (order.getAmount().compareTo(BigDecimal.ZERO) > 0) {
            // Calculate the cost at the limit price for this partial fill.
            BigDecimal limitCost = order.getLimitPrice().multiply(matchedShares);
            // Calculate the actual cost for the fill at the execution price.
            BigDecimal actualCost = execPrice.multiply(matchedShares);
            // Calculate the difference, and round to 2 decimal places.
            BigDecimal diff = limitCost.subtract(actualCost).setScale(2, RoundingMode.HALF_UP);
            if (diff.compareTo(BigDecimal.ZERO) > 0) {
                // Immediately refund the difference.
                acct.setBalance(acct.getBalance().add(diff));
                db.updateAccount(acct);
            }
            // Also credit the matched shares to the buyer's position.
            db.createOrAddSymbol(order.getSymbol(), acct.getAccountId(), matchedShares);
        } else {
            // SELL order: pay out proceeds immediately.
            BigDecimal proceeds = execPrice.multiply(matchedShares);
            acct.setBalance(acct.getBalance().add(proceeds));
            db.updateAccount(acct);
        }
    }

}
