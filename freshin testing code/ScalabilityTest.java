package com.weather.app;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ScalabilityTest {

    private static final String SERVER_INFO = "localhost:4567";
    private static final String FILE_PATH = "txt.txt"; // Ensure this file exists with valid data
    private static final int[] CLIENT_COUNTS = {5, 10, 20, 50}; // Number of clients for each test stage

    public static void main(String[] args) throws InterruptedException {
        // Start the AggregationServer in a separate thread
        Thread serverThread = new Thread(() -> {
            try {
                AggregationServer.main(new String[]{"4567"});  // Assuming AggregationServer takes a port argument
            } catch (Exception e) {
                System.err.println("Server failed to start: " + e.getMessage());
            }
        });
        serverThread.start();

        // Allow the server some time to initialize
        Thread.sleep(3000);

        for (int clientCount : CLIENT_COUNTS) {
            System.out.println("\nTesting with " + clientCount + " concurrent clients...");

            ExecutorService clientPool = Executors.newFixedThreadPool(clientCount);

            long startTime = System.currentTimeMillis();

            for (int i = 0; i < clientCount; i++) {
                int clientId = i;
                clientPool.submit(() -> {
                    try {
                        System.out.println("Client " + clientId + " is attempting to connect and send data.");
                        ContentServer.main(new String[]{SERVER_INFO, FILE_PATH}); // Each ContentServer instance sends data
                        System.out.println("Client " + clientId + " sent data successfully.");
                    } catch (Exception e) {
                        System.err.println("Client " + clientId + " encountered an error: " + e.getMessage());
                    }
                });
            }

            // Shut down the client pool and wait for all tasks to complete
            clientPool.shutdown();
            clientPool.awaitTermination(30, TimeUnit.SECONDS); // Adjust if higher client counts need more time

            long endTime = System.currentTimeMillis();
            System.out.println("Completed test with " + clientCount + " clients in " + (endTime - startTime) + " ms.");

            // Introduce a delay before scaling up to the next stage
            Thread.sleep(5000);
        }

        System.out.println("\nScalability test completed. Check server logs for performance under load.");
    }
}

