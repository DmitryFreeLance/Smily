FROM maven:3.9.8-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
ENV BOT_TOKEN="" \
    BOT_USERNAME="" \
    ADMIN_IDS="" \
    DB_PATH="/data/bot.db" \
    TZ="Europe/Moscow"
RUN mkdir -p /data
COPY --from=builder /app/target/game-telegram-bot-1.0.0.jar /app/bot.jar
CMD ["java", "-jar", "/app/bot.jar"]
