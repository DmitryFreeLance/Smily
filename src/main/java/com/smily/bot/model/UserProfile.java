package com.smily.bot.model;

public record UserProfile(
        long id,
        long telegramId,
        long chatId,
        String username,
        String firstName,
        String lastName,
        double foodTotal,
        int pipisaTotal,
        String lastFoodPlayDate,
        String lastPipisaPlayDate,
        double foodLastDelta,
        int pipisaLastDelta
) {
}
