FROM eclipse-temurin:17-jdk

WORKDIR /app

COPY . .

RUN javac ParkingServer.java

EXPOSE 8080

CMD ["java", "ParkingServer"]
