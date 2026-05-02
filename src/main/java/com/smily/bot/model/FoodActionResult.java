package com.smily.bot.model;

public record FoodActionResult(
        boolean success,
        boolean alreadyPlayedToday,
        double delta,
        double total,
        String extraText
) {
}
