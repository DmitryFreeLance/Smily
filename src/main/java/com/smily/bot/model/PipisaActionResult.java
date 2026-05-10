package com.smily.bot.model;

public record PipisaActionResult(
        boolean alreadyPlayedToday,
        int delta,
        int total,
        String extraText
) {
}
