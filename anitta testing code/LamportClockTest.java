package com.weather.app;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class LamportClockTest {

    public static void main(String[] args) throws InterruptedException {
        // Start the AggregationServer in a separate thread
        Thread serverThread = new Thread(() -> {
            try {
                AggregationServer.main(new String[]{"4567"});  // Start the server on port 4567
            } catch (Exception e) {
                System.err.println("Server failed to start: " + e.getMessage());
            }
        });
        serverThread.start();

        // Allow the server some time to initialize
        Thread.sleep(3000);

        // Define the server URL and file path for each ContentServer instance
        String serverUrl = "http://localhost:4567/weather.json";
        String filePath = "data.txt";  // Replace with the correct file path

        // Define the number of concurrent clients for Lamport clock testing
        int numClients = 10;

        System.out.println("Testing Lamport clock with " + numClients + " concurrent clients.");
        ExecutorService clientPool = Executors.newFixedThreadPool(numClients);

        for (int i = 0; i < numClients; i++) {
            int clientId = i;
            clientPool.execute(() -> {
                try {
                    // Each ContentServer instance sends a PUT request to simulate client behavior
                    ContentServer.main(new String[]{serverUrl, filePath});
                    System.out.println("Client " + clientId + " sent update to server.");
                } catch (Exception e) {
                    System.err.println("ContentServer " + clientId + " encountered an error: " + e.getMessage());
                }
            });
        }

        // Shutdown pool and wait for tasks to complete
        clientPool.shutdown();
        clientPool.awaitTermination(10, TimeUnit.SECONDS);

        System.out.println("Lamport clock test completed. Check server logs for Lamport clock ordering consistency.");
    }
}

