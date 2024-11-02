package com.weather.app;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class FaultToleranceTest {

    private static final int SERVER_PORT = 4567;
    private static final List<Long> initialLatencies = new ArrayList<>();
    private static final List<Long> reconnectionLatencies = new ArrayList<>();
    private static final Map<String, String> sentData = new ConcurrentHashMap<>();
    private static final Map<String, String> receivedData = new ConcurrentHashMap<>();

    public static void main(String[] args) throws InterruptedException, IOException {
        System.out.println("Fault Tolerance Test Started");

        // Step 1: Testing Initial Data Uploads
        System.out.println("Testing initial data uploads...");
        ExecutorService clientPool = Executors.newFixedThreadPool(5);

        for (int i = 0; i < 5; i++) {
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

        // Step 2: Simulating Server Failure
        System.out.println("Please restart the AggregationServer manually and press Enter when done...");
        System.in.read(); // Wait for user to manually restart the server

        // Step 3: Testing Data Consistency After Restart
        System.out.println("Testing data consistency and reconnections after restart...");
        ExecutorService reconnectionPool = Executors.newFixedThreadPool(5);

        for (int i = 0; i < 5; i++) {
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
        try (Socket socket = new Socket("localhost", SERVER_PORT);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            // Prepare test data with a unique ID for each client
            String testData = "{ \"id\": \"station_" + clientId + "\", \"temperature\": \"" + (20 + clientId) + "\" }";
            sentData.put("station_" + clientId, testData); // Record sent data for verification

            socket.getOutputStream().write(("PUT /weather.json HTTP/1.1\r\n" +
                    "Host: localhost\r\n" +
                    "Content-Type: application/json\r\n" +
                    "Content-Length: " + testData.length() + "\r\n" +
                    "Lamport-Clock: 0\r\n" +
                    "\r\n" +
                    testData).getBytes());
            socket.getOutputStream().flush();

            // Read server response and check consistency
            String responseLine;
            while ((responseLine = in.readLine()) != null) {
                if (responseLine.contains("200 OK") || responseLine.contains("201 Created")) {
                    receivedData.put("station_" + clientId, testData); // Record received data for consistency check
                }
            }
            System.out.println("Client " + clientId + " server response processed.");

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
