# Use a lightweight JDK base image
FROM eclipse-temurin:17-jdk-jammy

# Set working directory
WORKDIR /app

# Copy the packaged JAR into the image
COPY target/udp-tunnel-server-1.0.0.jar app.jar

# Expose UDP port
EXPOSE 9050/udp

# Run the app
ENTRYPOINT ["java", "-jar", "app.jar"]