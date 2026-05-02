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
        boolean fail = random.nextDouble() < 0.18;
        double delta;

        if (fail) {
            double loss = randomStep(0.2, 1.5, 0.1);
            delta = -Math.min(loss, user.foodTotal());
        } else {
            double base = randomStep(0.5, 5.0, 0.1);
            delta = roundOne(base * multiplier);
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
        return new FoodActionResult(!fail, false, delta, updated.foodTotal(), extra.toString());
    }

    public PipisaActionResult playPipisa(long telegramId) {
        UserProfile user = db.findUserByTelegramId(telegramId).orElseThrow();
        LocalDate today = LocalDate.now();

        if (today.toString().equals(user.lastPipisaPlayDate())) {
            return new PipisaActionResult(true, user.pipisaLastDelta(), user.pipisaTotal());
        }

        int delta = generatePipisaDelta();
        UserProfile updated = db.applyPipisaAction(telegramId, delta, today);
        return new PipisaActionResult(false, delta, updated.pipisaTotal());
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
        List<LeaderboardEntry> top = db.getTopFood(period, 10);
        Optional<UserProfile> me = db.findUserByTelegramId(telegramId);
        int myRank = db.getFoodRank(telegramId, period);

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
            double myValue = db.getFoodScore(telegramId, period);
            sb.append("\n").append(myRank).append(". Ты — ").append(FormatUtil.kg(myValue)).append(" кг");
        }
        return sb.toString();
    }

    public String buildPipisaLeaderboard(long telegramId, LeaderboardPeriod period) {
        List<LeaderboardEntry> top = db.getTopPipisa(period, 10);
        Optional<UserProfile> me = db.findUserByTelegramId(telegramId);
        int myRank = db.getPipisaRank(telegramId, period);

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
            int myValue = (int) Math.round(db.getPipisaScore(telegramId, period));
            sb.append("\n").append(myRank).append(". Ты — ").append(myValue).append(" см");
        }
        return sb.toString();
    }

    public List<ChallengeView> getChallenges(long telegramId) {
        return db.getChallengesForUser(telegramId);
    }

    public boolean acceptChallenge(long telegramId, long challengeId) {
        return db.acceptChallenge(telegramId, challengeId);
    }

    public int usersCount() {
        return db.usersCount();
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

    public String renderChallengeList(long telegramId) {
        List<ChallengeView> challenges = getChallenges(telegramId);
        if (challenges.isEmpty()) {
            return "🎯 Сейчас нет активных челленджей. Загляни позже.";
        }
        List<String> blocks = new ArrayList<>();
        for (ChallengeView c : challenges) {
            String reward = "BONUS_KG".equals(c.rewardType())
                    ? "+" + FormatUtil.kg(c.rewardValue()) + " кг"
                    : "x" + c.rewardValue() + " на " + c.rewardDays() + " дн.";
            String status;
            if (!c.accepted()) {
                status = "Статус: не принят";
            } else if ("ACTIVE".equals(c.status())) {
                double percent = Math.min((c.progress() / c.goalKg()) * 100.0, 100.0);
                String until = c.expiresAt() == null ? "-" : shortDate(c.expiresAt());
                status = "Прогресс: " + FormatUtil.kg(c.progress()) + "/" + FormatUtil.kg(c.goalKg()) +
                        " кг (" + (int) percent + "%) до " + until;
            } else if ("COMPLETED".equals(c.status())) {
                status = "Статус: выполнен ✅";
            } else {
                status = "Статус: провален ❌";
            }

            String text = c.title() + "\n" +
                    c.description() + "\n" +
                    "Цель: " + FormatUtil.kg(c.goalKg()) + " кг за " + c.durationDays() + " дн.\n" +
                    "Награда: " + reward + "\n" +
                    status;
            blocks.add(text);
        }
        return "🎯 Активные челленджи\n\n" + String.join("\n\n", blocks);
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
        if (roll < 0.7) {
            return random.nextInt(12) + 1;
        }
        if (roll < 0.9) {
            return 0;
        }
        return -(random.nextInt(4) + 1);
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
