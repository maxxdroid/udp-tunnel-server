package com.udp.server.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class UdpServerService {

    private static final int PORT = 9050;

    @PostConstruct
    public void start() {
        new Thread(() -> {
            log.info("🟢 UDP Server starting on port {}...", PORT);
            try (DatagramSocket socket = new DatagramSocket(PORT)) {
                byte[] buffer = new byte[8192];

                while (true) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);

                    InetAddress clientAddress = packet.getAddress();
                    int clientPort = packet.getPort();

                    String received = new String(packet.getData(), 0, packet.getLength());
                    log.info("📥 Received packet from {}:{}\n---\n{}\n---", clientAddress, clientPort, received);

                    String response = handleRequest(received);

                    // Chunking logic
                    byte[] data = response.getBytes();
                    int chunkSize = 1200;
                    int totalChunks = (int) Math.ceil((double) data.length / chunkSize);

                    for (int i = 0; i < totalChunks; i++) {
                        int start = i * chunkSize;
                        int end = Math.min(data.length, start + chunkSize);
                        byte[] chunk = Arrays.copyOfRange(data, start, end);

                        String header = String.format("[CHUNK %d/%d]\n", i + 1, totalChunks);
                        byte[] headerBytes = header.getBytes();
                        byte[] packetData = new byte[headerBytes.length + chunk.length];

                        System.arraycopy(headerBytes, 0, packetData, 0, headerBytes.length);
                        System.arraycopy(chunk, 0, packetData, headerBytes.length, chunk.length);

                        DatagramPacket responsePacket = new DatagramPacket(packetData, packetData.length, clientAddress, clientPort);
                        socket.send(responsePacket);

                        log.debug("🔹 Sent chunk {}/{}", i + 1, totalChunks);
                    }
                }

            } catch (Exception e) {
                log.error("❌ Server Error: {}", e.getMessage(), e);
            }
        }).start();
    }

    private String handleRequest(String raw) {
        try {
            BufferedReader reader = new BufferedReader(new StringReader(raw));

            // Parse method + URL
            String line = reader.readLine();
            if (line == null || !line.contains(" ")) {
                log.warn("⚠️ Invalid request line: '{}'", line);
                return "Invalid request line";
            }

            String[] parts = line.split(" ", 2);
            String method = parts[0].trim();
            String url = parts[1].trim();

            // Parse headers
            Map<String, String> headers = new HashMap<>();
            String headerLine;
            while ((headerLine = reader.readLine()) != null && !headerLine.trim().isEmpty()) {
                int colon = headerLine.indexOf(":");
                if (colon > 0) {
                    String name = headerLine.substring(0, colon).trim();
                    String value = headerLine.substring(colon + 1).trim();
                    headers.put(name, value);
                }
            }

            // Read body
            StringBuilder bodyBuilder = new StringBuilder();
            String bodyLine;
            while ((bodyLine = reader.readLine()) != null) {
                bodyBuilder.append(bodyLine).append("\n");
            }
            String body = bodyBuilder.toString().trim();

            log.debug("📤 Parsed request: \nMethod: {}\nURL: {}\nHeaders: {}\nBody: {}", method, url, headers, body);
            return forwardHttpRequest(method, url, headers, body);

        } catch (Exception e) {
            log.error("❌ Error while handling request: {}", e.getMessage(), e);
            return "Error parsing request: " + e.getMessage();
        }
    }

    private String forwardHttpRequest(String method, String url, Map<String, String> headers, String body) {
        try {
            log.info("🌐 Forwarding HTTP request: {} {}", method, url);
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod(method);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(10000);

            for (Map.Entry<String, String> entry : headers.entrySet()) {
                connection.setRequestProperty(entry.getKey(), entry.getValue());
            }

            if (!body.isEmpty() && (method.equalsIgnoreCase("POST") || method.equalsIgnoreCase("PUT") || method.equalsIgnoreCase("PATCH"))) {
                connection.setDoOutput(true);
                try (OutputStream os = connection.getOutputStream()) {
                    os.write(body.getBytes());
                    os.flush();
                }
                log.debug("📦 Sent body: {}", body);
            }

            int status = connection.getResponseCode();
            InputStream is = (status >= 200 && status < 400) ? connection.getInputStream() : connection.getErrorStream();
            BufferedReader in = new BufferedReader(new InputStreamReader(is));

            StringBuilder response = new StringBuilder("HTTP " + status + "\n");
            String line;
            while ((line = in.readLine()) != null) {
                response.append(line).append("\n");
            }
            in.close();

            log.info("✅ HTTP response status: {}", status);
            return response.toString().trim();

        } catch (Exception e) {
            log.error("❌ HTTP forwarding error: {}", e.getMessage(), e);
            return "HTTP request failed: " + e.getMessage();
        }
    }
}
