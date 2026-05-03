package com.smily.bot.model;

public record AdminUserEntry(
        long telegramId,
        String firstName,
        String username
) {
}
