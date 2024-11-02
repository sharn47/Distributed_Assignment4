package com.weather.app;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class FaultToleranceTest {

    private static final int SERVER_PORT = 8080;
    private static final String SERVER_HOST = "localhost";
    private static final int NUM_CLIENTS = 5;
    private static final List<Long> initialLatencies = new ArrayList<>();
    private static final List<Long> reconnectionLatencies = new ArrayList<>();
    private static final Map<String, String> sentData = new ConcurrentHashMap<>();
    private static final Map<String, String> receivedData = new ConcurrentHashMap<>();

    public static void main(String[] args) throws InterruptedException, IOException {
        System.out.println("Fault Tolerance Test Started");

        // Step 1: Upload Initial Data
        System.out.println("Uploading initial data...");
        ExecutorService clientPool = Executors.newFixedThreadPool(NUM_CLIENTS);

        for (int i = 0; i < NUM_CLIENTS; i++) {
            int clientId = i;
            clientPool.execute(() -> {
                try {
                    long startTime = System.nanoTime();
                    sendTestData(clientId, true);
                    long latency = System.nanoTime() - startTime;
                    initialLatencies.add(latency / 1_000_000); // Convert to ms
                } catch (Exception e) {
                    System.err.println("Error in client " + clientId + ": " + e.getMessage());
                }
            });
        }
        clientPool.shutdown();
        clientPool.awaitTermination(5, TimeUnit.SECONDS);

        // Step 2: Simulate Server Failure
        System.out.println("Please stop the AggregationServer manually and press Enter to simulate failure...");
        System.in.read(); // Wait for the user to stop the server manually

        System.out.println("Server stopped. Press Enter after manually restarting the server...");
        System.in.read(); // Wait for the user to restart the server

        // Step 3: Test Data Re-upload After Server Restart
        System.out.println("Testing data re-upload after server restart...");
        ExecutorService reconnectionPool = Executors.newFixedThreadPool(NUM_CLIENTS);

        for (int i = 0; i < NUM_CLIENTS; i++) {
            int clientId = i;
            reconnectionPool.execute(() -> {
                try {
                    long startTime = System.nanoTime();
                    sendTestData(clientId, false);
                    long latency = System.nanoTime() - startTime;
                    reconnectionLatencies.add(latency / 1_000_000); // Convert to ms
                } catch (Exception e) {
                    System.err.println("Error in client " + clientId + " after restart: " + e.getMessage());
                }
            });
        }
        reconnectionPool.shutdown();
        reconnectionPool.awaitTermination(5, TimeUnit.SECONDS);

        // Step 4: Display Metrics and Results
        displayMetrics();
    }

    // Send test data to the server from clients
    private static void sendTestData(int clientId, boolean isInitial) {
        try (Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            // Prepare test data with a unique ID for each client
            String testData = "{ \"id\": \"station_" + clientId + "\", \"temperature\": \"" + (20 + clientId) + "\" }";
            sentData.put("station_" + clientId, testData); // Record sent data for verification

            out.println("PUT /weather.json HTTP/1.1");
            out.println("Host: " + SERVER_HOST);
            out.println("Content-Type: application/json");
            out.println("Content-Length: " + testData.length());
            out.println("Lamport-Clock: 0");
            out.println();
            out.print(testData);
            out.flush();

            // Process the server response
            String responseLine;
            while ((responseLine = in.readLine()) != null) {
                if (responseLine.contains("200 OK") || responseLine.contains("201 Created")) {
                    receivedData.put("station_" + clientId, testData); // Record received data for consistency check
                }
            }
            System.out.println("Client " + clientId + " processed response.");

        } catch (IOException e) {
            System.err.println("Error in client " + clientId + ": " + e.getMessage());
        }
    }

    // Display the captured metrics
    private static void displayMetrics() {
        System.out.println("\nMetrics Summary:");
        System.out.println("----------------------------");

        // Initial Upload Latency
        System.out.println("Initial Upload Latency (ms): " + initialLatencies);
        long initialAvgLatency = initialLatencies.stream().mapToLong(Long::longValue).sum() / initialLatencies.size();
        System.out.println("Average Initial Latency: " + initialAvgLatency + " ms");

        // Reconnection Latency
        System.out.println("Reconnection Latency (ms): " + reconnectionLatencies);
        long reconnectionAvgLatency = reconnectionLatencies.stream().mapToLong(Long::longValue).sum() / reconnectionLatencies.size();
        System.out.println("Average Reconnection Latency: " + reconnectionAvgLatency + " ms");

        // Data Consistency Check
        System.out.println("\nData Consistency Check:");
        boolean isConsistent = sentData.equals(receivedData);
        System.out.println("Data consistency maintained: " + (isConsistent ? "Yes" : "No"));

        if (!isConsistent) {
            System.out.println("Inconsistent data detected!");
            System.out.println("Sent Data: " + sentData);
            System.out.println("Received Data: " + receivedData);
        }
    }
}

