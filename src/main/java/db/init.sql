CREATE TABLE accounts (
                          account_id VARCHAR(50) PRIMARY KEY,
                          balance NUMERIC(18,2) NOT NULL DEFAULT 0
);

CREATE TABLE positions (
                           account_id VARCHAR(50) NOT NULL,
                           symbol VARCHAR(50) NOT NULL,
                           quantity NUMERIC(18,8) NOT NULL DEFAULT 0,
                           PRIMARY KEY (account_id, symbol),
                           FOREIGN KEY (account_id) REFERENCES accounts(account_id)
);

CREATE TABLE orders (
                        order_id BIGSERIAL PRIMARY KEY,
                        account_id VARCHAR(50) NOT NULL,
                        symbol VARCHAR(50) NOT NULL,
                        amount NUMERIC(18,8) NOT NULL,
                        limit_price NUMERIC(18,8) NOT NULL,
                        status VARCHAR(10) NOT NULL,
                        creation_time BIGINT NOT NULL,
                        FOREIGN KEY (account_id) REFERENCES accounts(account_id)
);

CREATE TABLE executions (
                            exec_id BIGSERIAL PRIMARY KEY,
                            order_id BIGINT NOT NULL,
                            shares NUMERIC(18,8) NOT NULL,
                            price NUMERIC(18,8) NOT NULL,
                            exec_time BIGINT NOT NULL,
                            FOREIGN KEY (order_id) REFERENCES orders(order_id)
);
