package com.example.model;

import jakarta.persistence.*;
import lombok.*;
import java.util.HashMap;
import java.util.Map;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Account {
    @Id
    private Long id;

    private double balance;

    // 把 Map<String, Double> 类型的字段 positions 映射成一个独立的数据库表 account_positions
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "account_positions", joinColumns = @JoinColumn(name = "account_id"))
    @MapKeyColumn(name = "symbol") // “键”存在symbol字段（列）里
    @Column(name = "shares") // 值”要存在shares字段（列）里
    private Map<String, Double> positions = new HashMap<>();
}