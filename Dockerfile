# Use a lightweight JDK base image
FROM eclipse-temurin:17-jdk-jammy

# Set working directory inside the container
WORKDIR /app

# Copy the JAR from the Maven target folder
COPY target/server-0.0.1-SNAPSHOT.jar app.jar

# Expose UDP port (the tunnel port)
EXPOSE 9050/udp

# Command to run the Spring Boot UDP server
ENTRYPOINT ["java", "-jar", "app.jar"]
