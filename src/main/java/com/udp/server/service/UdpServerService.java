package com.udp.server.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class UdpServerService {

    private static final int PORT = 9050;

    @PostConstruct
    public void start() {
        new Thread(() -> {
            try (DatagramSocket socket = new DatagramSocket(PORT)) {
                byte[] buffer = new byte[8192];

                while (true) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);

                    String received = new String(packet.getData(), 0, packet.getLength());
                    String response = handleRequest(received);

                    byte[] responseBytes = response.getBytes();
                    DatagramPacket responsePacket = new DatagramPacket(
                            responseBytes, responseBytes.length,
                            packet.getAddress(), packet.getPort()
                    );
                    socket.send(responsePacket);
                }
            } catch (Exception e) {
                log.error("Error : {} \n Cause {}", e.getMessage(), e.getCause().getMessage());
            }
        }).start();
    }

    private String handleRequest(String raw) {
        try {
            BufferedReader reader = new BufferedReader(new StringReader(raw));

            // Parse method + URL
            String line = reader.readLine();
            if (line == null || !line.contains(" ")) return "Invalid request line";

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

            return forwardHttpRequest(method, url, headers, body);

        } catch (Exception e) {
            log.error("Handling Request Error: {}", e.getMessage());
            return "❌ Error parsing request: " + e.getMessage();
        }
    }

    private String forwardHttpRequest(String method, String url, Map<String, String> headers, String body) {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod(method);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(10000);

            for (Map.Entry<String, String> entry : headers.entrySet()) {
                connection.setRequestProperty(entry.getKey(), entry.getValue());
            }

            if (!body.isEmpty() && (method.equals("POST") || method.equals("PUT") || method.equals("PATCH"))) {
                connection.setDoOutput(true);
                try (OutputStream os = connection.getOutputStream()) {
                    os.write(body.getBytes());
                    os.flush();
                }
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
            return response.toString().trim();

        } catch (Exception e) {
            log.error("Fording Request Error : {}", e.getMessage());
            return "❌ HTTP request failed: " + e.getMessage();
        }
    }
}
