package model;

import java.math.BigDecimal;
import java.util.Map;

public class Account {
    private String accountId;
    private BigDecimal balance;
    private Map<String, Position> positions;

    // Constructor that initializes accountId and balance.
    // If balance is null, it defaults to BigDecimal.ZERO.
    public Account(String accountId, BigDecimal balance) {
        this.accountId = accountId;
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
