package com.weather.app;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class FailureHandling {

    private static final String SERVER_URL = "http://localhost:4567/weather.json";
    private static final String FILE_PATH = "data.txt"; // Ensure this file exists with valid data
    private static final int NUM_CLIENTS = 5; // Number of ContentServer instances to simulate
    private static final int RETRY_LIMIT = 3;

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

        List<Long> disconnectionTimes = new ArrayList<>();
        List<Long> reconnectionTimes = new ArrayList<>();
        List<Boolean> reconnectionSuccess = new ArrayList<>();

        // Test disconnection and reconnection handling for each ContentServer instance
        for (int i = 0; i < NUM_CLIENTS; i++) {
            int clientId = i;
            new Thread(() -> {
                int retries = RETRY_LIMIT;
                boolean connected = false;

                while (retries > 0 && !connected) {
                    long startTime = System.currentTimeMillis();
                    try {
                        System.out.println("ContentServer " + clientId + " attempting to connect...");
                        ContentServer.sendPutRequest(SERVER_URL, FILE_PATH); // Attempt to connect and send data
                        connected = true;

                        // Simulate disconnection by throwing an exception
                        System.out.println("Simulating disconnection for ContentServer " + clientId);
                        disconnectionTimes.add(System.currentTimeMillis() - startTime);
                        throw new IOException("Simulated disconnection");

                    } catch (IOException e) {
                        retries--;
                        connected = false;
                        System.err.println("ContentServer " + clientId + " disconnected: " + e.getMessage());

                        if (retries > 0) {
                            System.out.println("ContentServer " + clientId + " attempting to reconnect in 3 seconds...");
                            try {
                                Thread.sleep(3000); // Wait before retrying
                                long reconnectStartTime = System.currentTimeMillis();
                                ContentServer.sendPutRequest(SERVER_URL, FILE_PATH); // Re-attempt to send data
                                reconnectionTimes.add(System.currentTimeMillis() - reconnectStartTime);
                                reconnectionSuccess.add(true);
                                System.out.println("ContentServer " + clientId + " reconnected successfully.");
                                connected = true; // Reconnection was successful
                            } catch (InterruptedException ie) {
                                System.err.println("Reconnection delay interrupted for ContentServer " + clientId);
                                reconnectionSuccess.add(false);
                            }
                        } else {
                            System.out.println("ContentServer " + clientId + " exceeded maximum retries and will not reconnect.");
                            reconnectionSuccess.add(false);
                        }
                    }
                }
            }).start();
        }

        // Allow time for all clients to attempt connections
        Thread.sleep(20000);

        // Evaluate metrics after all attempts
        evaluateMetrics(disconnectionTimes, reconnectionTimes, reconnectionSuccess);
    }

    private static void evaluateMetrics(List<Long> disconnectionTimes, List<Long> reconnectionTimes, List<Boolean> reconnectionSuccess) {
        System.out.println("\n--- Failure Handling Test Results ---");

        // Disconnection Detection Time
        long avgDisconnectionTime = disconnectionTimes.stream().mapToLong(Long::longValue).sum() / disconnectionTimes.size();
        System.out.println("Average Disconnection Detection Time: " + avgDisconnectionTime + " ms");

        // Reconnection Success Rate
        long successfulReconnections = reconnectionSuccess.stream().filter(s -> s).count();
        double reconnectionSuccessRate = (double) successfulReconnections / reconnectionSuccess.size() * 100;
        System.out.println("Reconnection Success Rate: " + reconnectionSuccessRate + "%");

        // Time to Reconnect
        long avgReconnectionTime = reconnectionTimes.stream().mapToLong(Long::longValue).sum() / reconnectionTimes.size();
        System.out.println("Average Time to Reconnect: " + avgReconnectionTime + " ms");

        System.out.println("\n--- End of Test Results ---");
    }
}

