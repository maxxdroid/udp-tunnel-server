package com.udp.server.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Service
public class UdpServerService {

    private static final Logger logger = LoggerFactory.getLogger(UdpServerService.class);

    private static final int PORT = 9050;
    private static final int BUFFER_SIZE = 4096;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    @PostConstruct
    public void startServer() {
        new Thread(() -> {
            try (DatagramSocket socket = new DatagramSocket(PORT)) {
                logger.info("UDP Tunnel Server started on port {}", PORT);

                while (true) {
                    byte[] buffer = new byte[BUFFER_SIZE];
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);

                    String receivedMessage = new String(packet.getData(), 0, packet.getLength()).trim();
                    InetAddress clientAddress = packet.getAddress();
                    int clientPort = packet.getPort();

                    logger.info("Received request from {}:{} → {}", clientAddress, clientPort, receivedMessage);

                    String httpResponseText;

                    try {
                        if (!receivedMessage.startsWith("GET ")) {
                            httpResponseText = "Invalid format. Expected: GET <url>";
                        } else {
                            String url = receivedMessage.substring(4).trim();

                            HttpRequest request = HttpRequest.newBuilder()
                                    .uri(URI.create(url))
                                    .GET()
                                    .build();

                            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                            httpResponseText = response.body();
                        }
                    } catch (Exception e) {
                        httpResponseText = "Error: " + e.getMessage();
                        logger.error("Error forwarding request", e);
                    }

                    // Send back response (trimmed to fit UDP limit)
                    byte[] responseBytes = httpResponseText.getBytes();
                    if (responseBytes.length > BUFFER_SIZE) {
                        logger.warn("Response too large, truncating to {} bytes", BUFFER_SIZE);
                        responseBytes = new String(responseBytes).substring(0, BUFFER_SIZE).getBytes();
                    }

                    DatagramPacket responsePacket = new DatagramPacket(
                            responseBytes, responseBytes.length, clientAddress, clientPort
                    );
                    socket.send(responsePacket);
                    logger.info("Sent HTTP response back to {}:{}", clientAddress, clientPort);
                }

            } catch (Exception e) {
                logger.error("UDP server error: ", e);
            }
        }).start();
    }
}