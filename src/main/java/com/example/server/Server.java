package com.example.server;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

@Component
public class Server {
    @PostConstruct
    public void startServer() {
        System.out.println("[SocketServer] Listening on port 12345...");
        // TODO: Implement custom protocol socket logic here
    }
}