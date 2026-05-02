package com.smily.bot.config;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class BotConfig {
    private final String token;
    private final String username;
    private final String dbPath;
    private final Set<Long> adminIds;

    public BotConfig(String token, String username, String dbPath, Set<Long> adminIds) {
        this.token = token;
        this.username = username;
        this.dbPath = dbPath;
        this.adminIds = adminIds;
    }

    public static BotConfig fromEnv() {
        String token = readRequired("BOT_TOKEN");
        String username = readRequired("BOT_USERNAME");
        String dbPath = readOptional("DB_PATH", "./bot.db");
        String adminRaw = readOptional("ADMIN_IDS", "");
        Set<Long> adminIds = Arrays.stream(adminRaw.split(","))
                .map(String::trim)
                .filter(v -> !v.isEmpty())
                .map(Long::parseLong)
                .collect(Collectors.toCollection(HashSet::new));
        return new BotConfig(token, username, dbPath, adminIds);
    }

    private static String readRequired(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Environment variable is required: " + key);
        }
        return value;
    }

    private static String readOptional(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    public String token() {
        return token;
    }

    public String username() {
        return username;
    }

    public String dbPath() {
        return dbPath;
    }

    public Set<Long> adminIds() {
        return adminIds;
    }
}
