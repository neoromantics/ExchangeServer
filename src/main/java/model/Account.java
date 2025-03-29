package model;

import java.math.BigDecimal;
import java.util.Map;

public class Account {
    private String accountId;
    private BigDecimal balance;
    private Map<String, Position> positions;  // Optional, if you use it

    // Constructor that initializes accountId and balance
    public Account(String accountId, BigDecimal balance) {
        this.accountId = accountId;
        // Ensure balance is not null—if null, default to BigDecimal.ZERO.
        this.balance = (balance != null) ? balance : BigDecimal.ZERO;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public Map<String, Position> getPositions() {
        return positions;
    }

    public void setPositions(Map<String, Position> positions) {
        this.positions = positions;
    }
}
