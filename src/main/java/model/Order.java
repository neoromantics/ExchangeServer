package model;

import java.math.BigDecimal;

public class Order {
    private long orderId;
    private String accountId;
    private String symbol;
    private BigDecimal amount;
    private BigDecimal limitPrice;
    private OrderStatus status;
    private long creationTime;

    public Order(String accountId, String symbol, BigDecimal amount, BigDecimal limitPrice) {
        this.accountId = accountId;
        this.symbol = symbol;
        this.amount = amount;
        this.limitPrice = limitPrice;
        this.status = OrderStatus.OPEN;
        this.creationTime = 0L;
    }

    public long getOrderId() {
        return orderId;
    }

    public void setOrderId(long orderId) {
        this.orderId = orderId;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getLimitPrice() {
        return limitPrice;
    }

    public void setLimitPrice(BigDecimal limitPrice) {
        this.limitPrice = limitPrice;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public long getCreationTime() {
        return creationTime;
    }

    public void setCreationTime(long creationTime) {
        this.creationTime = creationTime;
    }

    public boolean isBuy() {
        return amount.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean isSell() {
        return amount.compareTo(BigDecimal.ZERO) < 0;
    }
}
