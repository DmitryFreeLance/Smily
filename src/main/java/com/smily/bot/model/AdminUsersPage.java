package com.smily.bot.model;

public record AdminUsersPage(
        int page,
        int totalPages,
        int totalUsers,
        String text
) {
}
