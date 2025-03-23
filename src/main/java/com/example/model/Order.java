package com.example.model;

import jakarta.persistence.*;
import lombok.*;
import java.util.ArrayList;
import java.util.List;

enum OrderStatus {
    OPEN,
    EXECUTED
}

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String symbol;
    private double amount;
    private double limitPrice;
    private long accountId;
    private long timestamp;

    @Enumerated(EnumType.STRING)
    private OrderStatus status = OrderStatus.OPEN;

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinColumn(name = "order_id")
    private List<Execution> executions = new ArrayList<>();
}

