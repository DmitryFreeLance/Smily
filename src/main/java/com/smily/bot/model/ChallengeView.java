package com.smily.bot.model;

public record ChallengeView(
        long challengeId,
        String code,
        String title,
        String description,
        double goalKg,
        int durationDays,
        String rewardType,
        double rewardValue,
        int rewardDays,
        boolean accepted,
        String status,
        double progress,
        String expiresAt,
        boolean claimable
) {
}
