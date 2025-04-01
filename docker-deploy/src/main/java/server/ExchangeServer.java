package server;

import db.DatabaseManager;
import db.PostgresDBManager;
import engine.MatchingEngine;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
/**
 * A simple multi-threaded TCP server that listens on port 12345 and
 * spawns a worker for each connection.
 */
public class ExchangeServer {

    private final int port;
    private final ExecutorService threadPool;
    private final RequestHandler requestHandler;

    public ExchangeServer(int port, DatabaseManager dbManager, MatchingEngine engine) {
        this.port = port;
        this.threadPool = Executors.newFixedThreadPool(10);
        this.requestHandler = new RequestHandler(dbManager, engine);
    }

    public void start() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("server.ExchangeServer listening on port " + port);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Accepted connection from " + clientSocket.getRemoteSocketAddress());
                // Submit to thread pool
                ClientHandler worker = new ClientHandler(clientSocket, requestHandler);
                threadPool.submit(worker);
            }
        }
    }

    public void stop() {
        threadPool.shutdown();
    }

    public static void main(String[] args) throws Exception {
        // 1) connect DB
        DatabaseManager db = new PostgresDBManager();
        db.connect();

        // 2) create engine
        MatchingEngine engine = new MatchingEngine(db);

        // 3) run server
        ExchangeServer server = new ExchangeServer(12345, db, engine);
        server.start(); // runs forever
    }
}
