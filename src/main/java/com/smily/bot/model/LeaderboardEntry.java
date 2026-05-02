package com.smily.bot.model;

public record LeaderboardEntry(
        int rank,
        long telegramId,
        String firstName,
        String username,
        double value
) {
}
