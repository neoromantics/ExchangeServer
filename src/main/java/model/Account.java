package model;

import java.math.BigDecimal;
import java.util.Map;

public class Account {
    private String accountId;
    private BigDecimal balance;

    // Positions keyed by symbol. Each Position tells how many shares of that symbol are owned.
    private Map<String, Position> positions;

    public Account(String accountId, BigDecimal balance) {
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
    }

    public Map<String, Position> getPositions() {
        return positions;
    }

    public void setPositions(Map<String, Position> positions) {
    }
}
