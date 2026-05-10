package com.smily.bot.service;

import com.smily.bot.db.Database;
import com.smily.bot.model.*;
import com.smily.bot.util.FormatUtil;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.regex.Pattern;

public class GameService {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final Pattern URL_LIKE = Pattern.compile("(https?://|www\\.|t\\.me/|telegram\\.me|\\.[a-z]{2,})", Pattern.CASE_INSENSITIVE);
    private final Database db;
    private final Random random = new Random();

    public GameService(Database db) {
        this.db = db;
    }

    public UserProfile ensureUser(long telegramId, long chatId, String username, String firstName, String lastName) {
        return db.upsertUser(telegramId, chatId, username, firstName, lastName);
    }

    public Optional<UserProfile> findUser(long telegramId) {
        return db.findUserByTelegramId(telegramId);
    }

    public FoodActionResult playFood(long telegramId) {
        UserProfile user = db.findUserByTelegramId(telegramId).orElseThrow();
        LocalDate today = LocalDate.now();

        if (today.toString().equals(user.lastFoodPlayDate())) {
            return new FoodActionResult(false, true, user.foodLastDelta(), user.foodTotal(), "");
        }

        double multiplier = db.getActiveMultiplier(user.id());
        double roll = random.nextDouble();
        double delta;

        if (roll < 0.70) {
            double base = randomStep(0.5, 5.0, 0.1);
            delta = roundOne(base * multiplier);
        } else if (roll < 0.75) {
            delta = 0.0;
        } else {
            double loss = randomStep(0.5, 3.0, 0.1);
            delta = -Math.min(loss, user.foodTotal());
        }

        UserProfile updated = db.applyFoodAction(telegramId, delta, today);
        List<String> challengeUpdates = db.updateChallengesOnFoodGain(telegramId, Math.max(delta, 0));
        updated = db.findUserByTelegramId(telegramId).orElseThrow();

        StringBuilder extra = new StringBuilder();
        if (multiplier > 1.0 && delta > 0) {
            extra.append("\n⚡ Активен множитель x").append(multiplier).append(".");
        }
        if (!challengeUpdates.isEmpty()) {
            extra.append("\n\n").append(String.join("\n", challengeUpdates));
        }
        return new FoodActionResult(delta > 0, false, delta, updated.foodTotal(), extra.toString());
    }

    public PipisaActionResult playPipisa(long telegramId) {
        UserProfile user = db.findUserByTelegramId(telegramId).orElseThrow();
        LocalDate today = LocalDate.now();

        if (today.toString().equals(user.lastPipisaPlayDate())) {
            return new PipisaActionResult(true, user.pipisaLastDelta(), user.pipisaTotal(), "");
        }

        int delta = generatePipisaDelta();
        db.applyPipisaAction(telegramId, delta, today);
        List<String> challengeUpdates = db.updateChallengesOnPipisaGain(telegramId, Math.max(delta, 0));
        UserProfile updated = db.findUserByTelegramId(telegramId).orElseThrow();
        String extra = challengeUpdates.isEmpty() ? "" : "\n\n" + String.join("\n", challengeUpdates);
        return new PipisaActionResult(false, delta, updated.pipisaTotal(), extra);
    }

    public String buildFoodScreen(UserProfile user) {
        return "🍔 Еда-метр\n\n" +
                displayName(user.firstName(), user.username()) +
                ", за прошлый заход: " + FormatUtil.kg(user.foodLastDelta()) + " кг.\n" +
                "Всего накоплено: " + FormatUtil.kg(user.foodTotal()) + " кг еды.";
    }

    public String buildPipisaScreen(UserProfile user) {
        return "📏 Писька-метр\n\n" +
                displayName(user.firstName(), user.username()) +
                ", текущий показатель: " + user.pipisaTotal() + " см.\n" +
                "Последний прирост: " + signed(user.pipisaLastDelta()) + " см.";
    }

    public String buildFoodLeaderboard(long telegramId, LeaderboardPeriod period) {
        List<LeaderboardEntry> top = (period == LeaderboardPeriod.DAY)
                ? db.getTopFoodDayPlayedOnly(10)
                : db.getTopFood(period, 10);
        Optional<UserProfile> me = db.findUserByTelegramId(telegramId);
        int myRank = (period == LeaderboardPeriod.DAY)
                ? db.getFoodDayPlayedRank(telegramId)
                : db.getFoodRank(telegramId, period);

        StringBuilder sb = new StringBuilder("🏆 ТОП-10 Еда-метр (" + periodLabel(period) + ")\n");
        if (top.isEmpty()) {
            sb.append("\nПока пусто. Будь первым, кто откроет сезон еды!\n");
        } else {
            sb.append("\n");
            for (LeaderboardEntry e : top) {
                sb.append(e.rank()).append(". ")
                        .append(displayName(e.firstName(), e.username()))
                        .append(" — ").append(FormatUtil.kg(e.value())).append(" кг\n");
            }
        }

        if (me.isPresent()) {
            if (period == LeaderboardPeriod.DAY && myRank == 0) {
                sb.append("\nСегодня ты ещё не ел.");
                return sb.toString();
            }
            double myValue = db.getFoodScore(telegramId, period);
            sb.append("\n").append(myRank).append(". Ты — ").append(FormatUtil.kg(myValue)).append(" кг");
        }
        return sb.toString();
    }

    public String buildPipisaLeaderboard(long telegramId, LeaderboardPeriod period) {
        List<LeaderboardEntry> top = (period == LeaderboardPeriod.DAY)
                ? db.getTopPipisaDayPlayedOnly(10)
                : db.getTopPipisa(period, 10);
        Optional<UserProfile> me = db.findUserByTelegramId(telegramId);
        int myRank = (period == LeaderboardPeriod.DAY)
                ? db.getPipisaDayPlayedRank(telegramId)
                : db.getPipisaRank(telegramId, period);

        StringBuilder sb = new StringBuilder("🥇 ТОП-10 Писька-метр (" + periodLabel(period) + ")\n");
        if (top.isEmpty()) {
            sb.append("\nПока в таблице никого нет.\n");
        } else {
            sb.append("\n");
            for (LeaderboardEntry e : top) {
                sb.append(e.rank()).append(". ")
                        .append(displayName(e.firstName(), e.username()))
                        .append(" — ").append((int) e.value()).append(" см\n");
            }
        }

        if (me.isPresent()) {
            if (period == LeaderboardPeriod.DAY && myRank == 0) {
                sb.append("\nСегодня ты ещё не играл(а).");
                return sb.toString();
            }
            int myValue = (int) Math.round(db.getPipisaScore(telegramId, period));
            sb.append("\n").append(myRank).append(". Ты — ").append(myValue).append(" см");
        }
        return sb.toString();
    }

    public List<ChallengeView> getChallenges(long telegramId, String mode) {
        return db.getChallengesForUser(telegramId, mode);
    }

    public boolean acceptChallenge(long telegramId, long challengeId) {
        return db.acceptChallenge(telegramId, challengeId);
    }

    public int usersCount() {
        return db.usersCount();
    }

    public AdminUsersPage renderAdminUsersPage(int requestedPage, int pageSize) {
        int totalUsers = db.usersCount();
        int totalPages = Math.max(1, (int) Math.ceil(totalUsers / (double) pageSize));
        int page = Math.min(Math.max(requestedPage, 0), totalPages - 1);
        int offset = page * pageSize;

        List<AdminUserEntry> users = db.listUsersPage(pageSize, offset);
        StringBuilder sb = new StringBuilder();
        sb.append("👥 Пользователи\n\n")
                .append("Страница ").append(page + 1).append("/").append(totalPages)
                .append(" • Всего: ").append(totalUsers).append("\n\n");

        if (users.isEmpty()) {
            sb.append("Список пока пуст.");
        } else {
            int idx = offset + 1;
            for (AdminUserEntry u : users) {
                String name = (u.firstName() == null || u.firstName().isBlank()) ? "Без имени" : u.firstName();
                String tag = (u.username() == null || u.username().isBlank()) ? "-" : "@" + u.username();
                sb.append(idx++)
                        .append(". ")
                        .append(name)
                        .append(" | ")
                        .append(tag)
                        .append(" | id: ")
                        .append(u.telegramId())
                        .append("\n");
            }
        }

        return new AdminUsersPage(page, totalPages, totalUsers, sb.toString());
    }

    public UserProfile adjustCounters(long telegramId, Double foodDelta, Integer pipisaDelta) {
        return db.adjustCounters(telegramId, foodDelta, pipisaDelta);
    }

    public boolean setCounters(long telegramId, Double foodValue, Integer pipisaValue) {
        return db.setCounters(telegramId, foodValue, pipisaValue);
    }

    public List<KeywordLink> listKeywords() {
        return db.listKeywords();
    }

    public boolean addKeyword(String keyword, String url, long adminId) {
        return db.addKeyword(keyword, url, adminId);
    }

    public boolean removeKeyword(long id) {
        return db.removeKeyword(id);
    }

    public int resetDailyLimitsForAllUsers() {
        return db.resetDailyLimitsForAllUsers();
    }

    public boolean resetDailyLimitsForUser(long telegramId) {
        return db.resetDailyLimitsForUser(telegramId);
    }

    public int getFoodAllTimeRank(long telegramId) {
        return db.getFoodRank(telegramId, LeaderboardPeriod.ALL);
    }

    public int getPipisaAllTimeRank(long telegramId) {
        return db.getPipisaRank(telegramId, LeaderboardPeriod.ALL);
    }

    public String renderChallengeList(long telegramId, String mode) {
        List<ChallengeView> challenges = getChallenges(telegramId, mode);
        if (challenges.isEmpty()) {
            return "🎯 Сейчас нет активных челленджей. Загляни позже.";
        }
        boolean pipisaMode = "PIPISA".equals(mode);
        List<String> blocks = new ArrayList<>();
        for (ChallengeView c : challenges) {
            String reward;
            if ("BONUS_KG".equals(c.rewardType())) {
                reward = "+" + FormatUtil.kg(c.rewardValue()) + " кг";
            } else if ("BONUS_CM".equals(c.rewardType())) {
                reward = "+" + (int) Math.round(c.rewardValue()) + " см";
            } else {
                reward = "x" + c.rewardValue() + " на " + c.rewardDays() + " дн.";
            }
            String status;
            if (!c.accepted()) {
                status = "Статус: не принят";
            } else if ("ACTIVE".equals(c.status())) {
                double percent = Math.min((c.progress() / c.goalKg()) * 100.0, 100.0);
                String until = c.expiresAt() == null ? "-" : shortDate(c.expiresAt());
                if (pipisaMode) {
                    status = "Прогресс: " + (int) Math.round(c.progress()) + "/" + (int) Math.round(c.goalKg()) +
                            " см (" + (int) percent + "%) до " + until;
                } else {
                    status = "Прогресс: " + FormatUtil.kg(c.progress()) + "/" + FormatUtil.kg(c.goalKg()) +
                            " кг (" + (int) percent + "%) до " + until;
                }
            } else if ("COMPLETED".equals(c.status())) {
                status = "Статус: выполнен ✅";
            } else {
                status = "Статус: провален ❌";
            }

            String text;
            if (pipisaMode) {
                text = c.title() + "\n" +
                        c.description() + "\n" +
                        "Награда: " + reward + "\n" +
                        status;
            } else {
                text = c.title() + "\n" +
                        c.description() + "\n" +
                        "Цель: " + FormatUtil.kg(c.goalKg()) + " кг за " + c.durationDays() + " дн.\n" +
                        "Награда: " + reward + "\n" +
                        status;
            }
            blocks.add(text);
        }
        String modeTitle = "🎯 Активные челленджи";
        return modeTitle + "\n\n" + String.join("\n\n", blocks);
    }

    public String renderKeywordList() {
        List<KeywordLink> links = listKeywords();
        if (links.isEmpty()) {
            return "🔗 Пока ссылок нет.";
        }
        StringBuilder sb = new StringBuilder("🔗 Полезные ссылки\n\n");
        for (KeywordLink link : links) {
            sb.append("• ").append(link.keyword()).append(" — ").append(link.url()).append("\n");
        }
        return sb.toString();
    }

    private int generatePipisaDelta() {
        double roll = random.nextDouble();
        if (roll < 0.70) {
            return random.nextInt(15) + 1;
        }
        if (roll < 0.75) {
            return 0;
        }
        return -(random.nextInt(10) + 1);
    }

    private double randomStep(double min, double max, double step) {
        int steps = (int) Math.round((max - min) / step);
        int n = random.nextInt(steps + 1);
        return roundOne(min + n * step);
    }

    private double roundOne(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private String signed(int value) {
        return value > 0 ? "+" + value : String.valueOf(value);
    }

    private String shortDate(String value) {
        try {
            return LocalDateTime.parse(value, DATE_TIME).toLocalDate().toString();
        } catch (Exception ignored) {
            return value;
        }
    }

    private String periodLabel(LeaderboardPeriod period) {
        return switch (period) {
            case DAY -> "за сегодня";
            case MONTH -> "за месяц";
            case ALL -> "за всё время";
        };
    }

    public String displayName(String firstName, String username) {
        if (shouldMaskName(firstName, username)) {
            return "НикСкрыт";
        }
        return FormatUtil.safeName(firstName, username);
    }

    private boolean shouldMaskName(String firstName, String username) {
        String name = (firstName == null ? "" : firstName) + " " + (username == null ? "" : username);
        String low = name.toLowerCase();
        if (URL_LIKE.matcher(low).find()) {
            return true;
        }
        for (KeywordLink link : db.listKeywords()) {
            String key = link.keyword() == null ? "" : link.keyword().trim().toLowerCase();
            if (!key.isBlank() && low.contains(key)) {
                return true;
            }
        }
        return false;
    }
}
