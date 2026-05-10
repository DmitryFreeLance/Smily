package com.smily.bot.db;

import com.smily.bot.model.ChallengeView;
import com.smily.bot.model.AdminUserEntry;
import com.smily.bot.model.KeywordLink;
import com.smily.bot.model.LeaderboardEntry;
import com.smily.bot.model.LeaderboardPeriod;
import com.smily.bot.model.UserProfile;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Database {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private final String jdbcUrl;

    public Database(String dbPath) {
        this.jdbcUrl = "jdbc:sqlite:" + dbPath;
    }

    public void init() {
        try (Connection conn = getConnection(); Statement st = conn.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS users (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        telegram_id INTEGER NOT NULL UNIQUE,
                        chat_id INTEGER NOT NULL,
                        username TEXT,
                        first_name TEXT,
                        last_name TEXT,
                        food_total REAL NOT NULL DEFAULT 0,
                        pipisa_total INTEGER NOT NULL DEFAULT 0,
                        last_food_play_date TEXT,
                        last_pipisa_play_date TEXT,
                        food_last_delta REAL NOT NULL DEFAULT 0,
                        pipisa_last_delta INTEGER NOT NULL DEFAULT 0,
                        created_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL
                    )
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS challenges (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        code TEXT NOT NULL UNIQUE,
                        title TEXT NOT NULL,
                        description TEXT NOT NULL,
                        goal_kg REAL NOT NULL,
                        duration_days INTEGER NOT NULL,
                        reward_type TEXT NOT NULL,
                        reward_value REAL NOT NULL,
                        reward_days INTEGER NOT NULL DEFAULT 0,
                        active INTEGER NOT NULL DEFAULT 1
                    )
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS user_challenges (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        user_id INTEGER NOT NULL,
                        challenge_id INTEGER NOT NULL,
                        progress REAL NOT NULL DEFAULT 0,
                        status TEXT NOT NULL,
                        accepted_at TEXT NOT NULL,
                        expires_at TEXT NOT NULL,
                        completed_at TEXT,
                        reward_expires_at TEXT,
                        UNIQUE(user_id, challenge_id),
                        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                        FOREIGN KEY (challenge_id) REFERENCES challenges(id) ON DELETE CASCADE
                    )
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS admin_keywords (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        keyword TEXT NOT NULL UNIQUE,
                        url TEXT NOT NULL,
                        created_by INTEGER NOT NULL,
                        created_at TEXT NOT NULL
                    )
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS score_events (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        user_id INTEGER NOT NULL,
                        mode TEXT NOT NULL,
                        delta REAL NOT NULL,
                        event_date TEXT NOT NULL,
                        event_month TEXT NOT NULL,
                        created_at TEXT NOT NULL,
                        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
                    )
                    """);
            st.execute("CREATE INDEX IF NOT EXISTS idx_score_events_mode_date ON score_events(mode, event_date)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_score_events_mode_month ON score_events(mode, event_month)");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to init DB", e);
        }
        seedChallenges();
    }

    private void seedChallenges() {
        upsertChallenge("WEEK_BULK", "🔥 Набор недели", "Набери 20,0 кг за 7 дней.", 20.0, 7,
                "BONUS_KG", 5.0, 0, true);
        upsertChallenge("IRON_STOMACH", "⚙️ Железный желудок", "Набери 40,0 кг за 10 дней.", 40.0, 10,
                "MULTIPLIER", 1.5, 3, true);
        upsertChallenge("SPEED_BITE", "⚡ Быстрый перекус", "Набери 12,0 кг за 3 дня.", 12.0, 3,
                "BONUS_KG", 3.0, 0, true);
    }

    private void upsertChallenge(String code, String title, String description, double goal, int days,
                                 String rewardType, double rewardValue, int rewardDays, boolean active) {
        String sql = """
                INSERT INTO challenges (code, title, description, goal_kg, duration_days, reward_type, reward_value, reward_days, active)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(code) DO UPDATE SET
                    title=excluded.title,
                    description=excluded.description,
                    goal_kg=excluded.goal_kg,
                    duration_days=excluded.duration_days,
                    reward_type=excluded.reward_type,
                    reward_value=excluded.reward_value,
                    reward_days=excluded.reward_days,
                    active=excluded.active
                """;
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, code);
            ps.setString(2, title);
            ps.setString(3, description);
            ps.setDouble(4, goal);
            ps.setInt(5, days);
            ps.setString(6, rewardType);
            ps.setDouble(7, rewardValue);
            ps.setInt(8, rewardDays);
            ps.setInt(9, active ? 1 : 0);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public UserProfile upsertUser(long telegramId, long chatId, String username, String firstName, String lastName) {
        String now = now();
        String sql = """
                INSERT INTO users (telegram_id, chat_id, username, first_name, last_name, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(telegram_id) DO UPDATE SET
                    chat_id=excluded.chat_id,
                    username=excluded.username,
                    first_name=excluded.first_name,
                    last_name=excluded.last_name,
                    updated_at=excluded.updated_at
                """;
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, telegramId);
            ps.setLong(2, chatId);
            ps.setString(3, username);
            ps.setString(4, firstName);
            ps.setString(5, lastName);
            ps.setString(6, now);
            ps.setString(7, now);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return findUserByTelegramId(telegramId).orElseThrow();
    }

    public Optional<UserProfile> findUserByTelegramId(long telegramId) {
        String sql = "SELECT * FROM users WHERE telegram_id=?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, telegramId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapUser(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return Optional.empty();
    }

    public UserProfile adjustCounters(long telegramId, Double foodDelta, Integer pipisaDelta) {
        String sql = "UPDATE users SET food_total = MAX(food_total + ?, 0), pipisa_total = MAX(pipisa_total + ?, 0), updated_at=? WHERE telegram_id=?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, foodDelta == null ? 0.0 : foodDelta);
            ps.setInt(2, pipisaDelta == null ? 0 : pipisaDelta);
            ps.setString(3, now());
            ps.setLong(4, telegramId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return findUserByTelegramId(telegramId).orElseThrow();
    }

    public boolean setCounters(long telegramId, Double foodValue, Integer pipisaValue) {
        String sql = "UPDATE users SET food_total = COALESCE(?, food_total), pipisa_total = COALESCE(?, pipisa_total), updated_at=? WHERE telegram_id=?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            if (foodValue == null) {
                ps.setNull(1, Types.DOUBLE);
            } else {
                ps.setDouble(1, Math.max(foodValue, 0.0));
            }
            if (pipisaValue == null) {
                ps.setNull(2, Types.INTEGER);
            } else {
                ps.setInt(2, Math.max(pipisaValue, 0));
            }
            ps.setString(3, now());
            ps.setLong(4, telegramId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public int usersCount() {
        try (Connection conn = getConnection(); Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM users")) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<AdminUserEntry> listUsersPage(int limit, int offset) {
        List<AdminUserEntry> users = new ArrayList<>();
        String sql = "SELECT telegram_id, first_name, username FROM users ORDER BY id ASC LIMIT ? OFFSET ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            ps.setInt(2, Math.max(offset, 0));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    users.add(new AdminUserEntry(
                            rs.getLong("telegram_id"),
                            rs.getString("first_name"),
                            rs.getString("username")
                    ));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return users;
    }

    public double getActiveMultiplier(long userId) {
        String sql = """
                SELECT MAX(c.reward_value)
                FROM user_challenges uc
                JOIN challenges c ON c.id = uc.challenge_id
                WHERE uc.user_id = ?
                  AND uc.status = 'COMPLETED'
                  AND c.reward_type = 'MULTIPLIER'
                  AND uc.reward_expires_at IS NOT NULL
                  AND uc.reward_expires_at >= ?
                """;
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setString(2, now());
            try (ResultSet rs = ps.executeQuery()) {
                double mult = rs.next() ? rs.getDouble(1) : 0.0;
                return mult > 0 ? mult : 1.0;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public UserProfile applyFoodAction(long telegramId, double delta, LocalDate today) {
        String now = now();
        String sql = """
                UPDATE users
                SET food_total = MAX(food_total + ?, 0),
                    food_last_delta = ?,
                    last_food_play_date = ?,
                    updated_at = ?
                WHERE telegram_id = ?
                """;
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, delta);
            ps.setDouble(2, delta);
            ps.setString(3, today.toString());
            ps.setString(4, now);
            ps.setLong(5, telegramId);
            ps.executeUpdate();
            insertScoreEvent(conn, telegramId, "FOOD", delta, today, now);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return findUserByTelegramId(telegramId).orElseThrow();
    }

    public UserProfile applyPipisaAction(long telegramId, int delta, LocalDate today) {
        String now = now();
        String sql = """
                UPDATE users
                SET pipisa_total = MAX(pipisa_total + ?, 0),
                    pipisa_last_delta = ?,
                    last_pipisa_play_date = ?,
                    updated_at = ?
                WHERE telegram_id = ?
                """;
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, delta);
            ps.setInt(2, delta);
            ps.setString(3, today.toString());
            ps.setString(4, now);
            ps.setLong(5, telegramId);
            ps.executeUpdate();
            insertScoreEvent(conn, telegramId, "PIPISA", delta, today, now);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return findUserByTelegramId(telegramId).orElseThrow();
    }

    public List<LeaderboardEntry> getTopFood(LeaderboardPeriod period, int limit) {
        return getTopByPeriod("FOOD", period, limit, "food_total");
    }

    public List<LeaderboardEntry> getTopPipisa(LeaderboardPeriod period, int limit) {
        return getTopByPeriod("PIPISA", period, limit, "pipisa_total");
    }

    public List<LeaderboardEntry> getTopPipisaDayPlayedOnly(int limit) {
        String today = LocalDate.now().toString();
        String sql = """
                SELECT u.telegram_id, u.first_name, u.username, s.value
                FROM (
                    SELECT se.user_id, SUM(se.delta) AS value
                    FROM score_events se
                    WHERE se.mode = 'PIPISA' AND se.event_date = ?
                    GROUP BY se.user_id
                ) s
                JOIN users u ON u.id = s.user_id
                ORDER BY s.value DESC, u.id ASC
                LIMIT ?
                """;
        List<LeaderboardEntry> list = new ArrayList<>();
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, today);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                int rank = 1;
                while (rs.next()) {
                    list.add(new LeaderboardEntry(
                            rank++,
                            rs.getLong("telegram_id"),
                            rs.getString("first_name"),
                            rs.getString("username"),
                            rs.getDouble("value")
                    ));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return list;
    }

    public int getFoodRank(long telegramId, LeaderboardPeriod period) {
        return getRankByPeriod(telegramId, "FOOD", period, "food_total");
    }

    public int getPipisaRank(long telegramId, LeaderboardPeriod period) {
        return getRankByPeriod(telegramId, "PIPISA", period, "pipisa_total");
    }

    public int getPipisaDayPlayedRank(long telegramId) {
        String today = LocalDate.now().toString();
        String sql = """
                WITH scores AS (
                    SELECT se.user_id, SUM(se.delta) AS value
                    FROM score_events se
                    WHERE se.mode = 'PIPISA' AND se.event_date = ?
                    GROUP BY se.user_id
                )
                SELECT 1 + (
                    SELECT COUNT(*)
                    FROM scores s2
                    WHERE s2.value > s1.value
                ) AS rank
                FROM scores s1
                JOIN users me ON me.id = s1.user_id
                WHERE me.telegram_id = ?
                """;
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, today);
            ps.setLong(2, telegramId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("rank") : 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public double getFoodScore(long telegramId, LeaderboardPeriod period) {
        return getScoreByPeriod(telegramId, "FOOD", period, "food_total");
    }

    public double getPipisaScore(long telegramId, LeaderboardPeriod period) {
        return getScoreByPeriod(telegramId, "PIPISA", period, "pipisa_total");
    }

    public boolean hasPlayedToday(long telegramId, String mode) {
        String today = LocalDate.now().toString();
        String sql = """
                SELECT 1
                FROM score_events se
                JOIN users u ON u.id = se.user_id
                WHERE u.telegram_id = ? AND se.mode = ? AND se.event_date = ?
                LIMIT 1
                """;
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, telegramId);
            ps.setString(2, mode);
            ps.setString(3, today);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private List<LeaderboardEntry> getTopByPeriod(String mode, LeaderboardPeriod period, int limit, String allTimeField) {
        String sql;
        if (period == LeaderboardPeriod.ALL) {
            String safeField = safeScoreField(allTimeField);
            sql = "SELECT telegram_id, first_name, username, " + safeField + " AS value FROM users ORDER BY " + safeField + " DESC, id ASC LIMIT ?";
        } else {
            String periodColumn = period == LeaderboardPeriod.DAY ? "event_date" : "event_month";
            sql = "SELECT u.telegram_id, u.first_name, u.username, COALESCE(s.value, 0) AS value " +
                    "FROM users u " +
                    "LEFT JOIN (" +
                    "  SELECT se.user_id, SUM(se.delta) AS value " +
                    "  FROM score_events se " +
                    "  WHERE se.mode = ? AND se." + periodColumn + " = ? " +
                    "  GROUP BY se.user_id" +
                    ") s ON s.user_id = u.id " +
                    "ORDER BY value DESC, u.id ASC " +
                    "LIMIT ?";
        }
        List<LeaderboardEntry> list = new ArrayList<>();
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            if (period == LeaderboardPeriod.ALL) {
                ps.setInt(1, limit);
            } else {
                ps.setString(1, mode);
                ps.setString(2, periodKey(period));
                ps.setInt(3, limit);
            }
            try (ResultSet rs = ps.executeQuery()) {
                int rank = 1;
                while (rs.next()) {
                    list.add(new LeaderboardEntry(
                            rank++,
                            rs.getLong("telegram_id"),
                            rs.getString("first_name"),
                            rs.getString("username"),
                            rs.getDouble("value")
                    ));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return list;
    }

    private int getRankByPeriod(long telegramId, String mode, LeaderboardPeriod period, String allTimeField) {
        String sql;
        if (period == LeaderboardPeriod.ALL) {
            String safeField = safeScoreField(allTimeField);
            sql = "SELECT 1 + (SELECT COUNT(*) FROM users u2 WHERE u2." + safeField + " > u1." + safeField + ") AS rank " +
                    "FROM users u1 WHERE u1.telegram_id = ?";
        } else {
            String periodColumn = period == LeaderboardPeriod.DAY ? "event_date" : "event_month";
            sql = "WITH scores AS (" +
                    "  SELECT u.id AS user_id, COALESCE(SUM(se.delta), 0) AS value " +
                    "  FROM users u " +
                    "  LEFT JOIN score_events se " +
                    "    ON se.user_id = u.id " +
                    "   AND se.mode = ? " +
                    "   AND se." + periodColumn + " = ? " +
                    "  GROUP BY u.id" +
                    ") " +
                    "SELECT 1 + (SELECT COUNT(*) FROM scores s2 WHERE s2.value > s1.value) AS rank " +
                    "FROM scores s1 " +
                    "JOIN users me ON me.id = s1.user_id " +
                    "WHERE me.telegram_id = ?";
        }
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            if (period == LeaderboardPeriod.ALL) {
                ps.setLong(1, telegramId);
            } else {
                ps.setString(1, mode);
                ps.setString(2, periodKey(period));
                ps.setLong(3, telegramId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("rank") : 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private double getScoreByPeriod(long telegramId, String mode, LeaderboardPeriod period, String allTimeField) {
        String sql;
        if (period == LeaderboardPeriod.ALL) {
            String safeField = safeScoreField(allTimeField);
            sql = "SELECT " + safeField + " AS value FROM users WHERE telegram_id = ?";
        } else {
            String periodColumn = period == LeaderboardPeriod.DAY ? "event_date" : "event_month";
            sql = "SELECT COALESCE(SUM(se.delta), 0) AS value " +
                    "FROM users u " +
                    "LEFT JOIN score_events se ON se.user_id = u.id AND se.mode = ? AND se." + periodColumn + " = ? " +
                    "WHERE u.telegram_id = ?";
        }
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            if (period == LeaderboardPeriod.ALL) {
                ps.setLong(1, telegramId);
            } else {
                ps.setString(1, mode);
                ps.setString(2, periodKey(period));
                ps.setLong(3, telegramId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble("value") : 0.0;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private String safeScoreField(String field) {
        if ("food_total".equals(field) || "pipisa_total".equals(field)) {
            return field;
        }
        throw new IllegalArgumentException("Unsupported score field: " + field);
    }

    private String periodKey(LeaderboardPeriod period) {
        if (period == LeaderboardPeriod.DAY) {
            return LocalDate.now().toString();
        }
        if (period == LeaderboardPeriod.MONTH) {
            return monthKey(LocalDate.now());
        }
        throw new IllegalArgumentException("Period key is not supported for: " + period);
    }

    public List<ChallengeView> getChallengesForUser(long telegramId) {
        String sql = """
                SELECT c.id,
                       c.code,
                       c.title,
                       c.description,
                       c.goal_kg,
                       c.duration_days,
                       c.reward_type,
                       c.reward_value,
                       c.reward_days,
                       uc.id IS NOT NULL AS accepted,
                       COALESCE(uc.status, 'NONE') AS status,
                       COALESCE(uc.progress, 0) AS progress,
                       uc.expires_at,
                       CASE WHEN uc.status='COMPLETED' THEN 1 ELSE 0 END AS claimable
                FROM challenges c
                LEFT JOIN users u ON u.telegram_id = ?
                LEFT JOIN user_challenges uc ON uc.challenge_id = c.id AND uc.user_id = u.id
                WHERE c.active = 1
                ORDER BY c.id
                """;
        List<ChallengeView> list = new ArrayList<>();
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, telegramId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new ChallengeView(
                            rs.getLong("id"),
                            rs.getString("code"),
                            rs.getString("title"),
                            rs.getString("description"),
                            rs.getDouble("goal_kg"),
                            rs.getInt("duration_days"),
                            rs.getString("reward_type"),
                            rs.getDouble("reward_value"),
                            rs.getInt("reward_days"),
                            rs.getBoolean("accepted"),
                            rs.getString("status"),
                            rs.getDouble("progress"),
                            rs.getString("expires_at"),
                            rs.getBoolean("claimable")
                    ));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return list;
    }

    public boolean acceptChallenge(long telegramId, long challengeId) {
        Optional<UserProfile> userOpt = findUserByTelegramId(telegramId);
        if (userOpt.isEmpty()) {
            return false;
        }
        long userId = userOpt.get().id();

        String challengeSql = "SELECT duration_days FROM challenges WHERE id = ? AND active = 1";
        Integer duration = null;
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(challengeSql)) {
            ps.setLong(1, challengeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    duration = rs.getInt("duration_days");
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        if (duration == null) {
            return false;
        }

        String sql = """
                INSERT INTO user_challenges (user_id, challenge_id, progress, status, accepted_at, expires_at)
                VALUES (?, ?, 0, 'ACTIVE', ?, ?)
                ON CONFLICT(user_id, challenge_id) DO NOTHING
                """;

        LocalDateTime accepted = LocalDateTime.now();
        LocalDateTime expires = accepted.plusDays(duration);

        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setLong(2, challengeId);
            ps.setString(3, DATE_TIME.format(accepted));
            ps.setString(4, DATE_TIME.format(expires));
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<String> updateChallengesOnFoodGain(long telegramId, double gain) {
        if (gain <= 0) {
            return List.of();
        }
        Optional<UserProfile> userOpt = findUserByTelegramId(telegramId);
        if (userOpt.isEmpty()) {
            return List.of();
        }
        long userId = userOpt.get().id();

        List<String> messages = new ArrayList<>();
        String now = now();
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                String failExpired = "UPDATE user_challenges SET status='FAILED' WHERE user_id=? AND status='ACTIVE' AND expires_at < ?";
                try (PreparedStatement ps = conn.prepareStatement(failExpired)) {
                    ps.setLong(1, userId);
                    ps.setString(2, now);
                    ps.executeUpdate();
                }

                String select = """
                        SELECT uc.id, uc.progress, c.goal_kg, c.title, c.reward_type, c.reward_value, c.reward_days
                        FROM user_challenges uc
                        JOIN challenges c ON c.id = uc.challenge_id
                        WHERE uc.user_id = ? AND uc.status = 'ACTIVE' AND uc.expires_at >= ?
                        """;
                try (PreparedStatement ps = conn.prepareStatement(select)) {
                    ps.setLong(1, userId);
                    ps.setString(2, now);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            long ucId = rs.getLong("id");
                            double newProgress = rs.getDouble("progress") + gain;
                            double goal = rs.getDouble("goal_kg");
                            String title = rs.getString("title");
                            String rewardType = rs.getString("reward_type");
                            double rewardValue = rs.getDouble("reward_value");
                            int rewardDays = rs.getInt("reward_days");

                            if (newProgress >= goal) {
                                String rewardExpires = null;
                                if ("MULTIPLIER".equals(rewardType)) {
                                    rewardExpires = DATE_TIME.format(LocalDateTime.now().plusDays(rewardDays));
                                }
                                String complete = "UPDATE user_challenges SET progress=?, status='COMPLETED', completed_at=?, reward_expires_at=? WHERE id=?";
                                try (PreparedStatement cps = conn.prepareStatement(complete)) {
                                    cps.setDouble(1, newProgress);
                                    cps.setString(2, now);
                                    cps.setString(3, rewardExpires);
                                    cps.setLong(4, ucId);
                                    cps.executeUpdate();
                                }

                                if ("BONUS_KG".equals(rewardType)) {
                                    String addBonus = "UPDATE users SET food_total = food_total + ?, updated_at=? WHERE id=?";
                                    try (PreparedStatement bps = conn.prepareStatement(addBonus)) {
                                        bps.setDouble(1, rewardValue);
                                        bps.setString(2, now);
                                        bps.setLong(3, userId);
                                        bps.executeUpdate();
                                    }
                                    insertScoreEventByUserId(conn, userId, "FOOD", rewardValue, LocalDate.now(), now);
                                    messages.add("✅ Челлендж «" + title + "» выполнен! Бонус: +" + rewardValue + " кг.");
                                } else if ("MULTIPLIER".equals(rewardType)) {
                                    messages.add("✅ Челлендж «" + title + "» выполнен! Множитель x" + rewardValue + " на " + rewardDays + " дн.");
                                }
                            } else {
                                String update = "UPDATE user_challenges SET progress=? WHERE id=?";
                                try (PreparedStatement ups = conn.prepareStatement(update)) {
                                    ups.setDouble(1, newProgress);
                                    ups.setLong(2, ucId);
                                    ups.executeUpdate();
                                }
                            }
                        }
                    }
                }

                conn.commit();
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return messages;
    }

    public List<KeywordLink> listKeywords() {
        List<KeywordLink> links = new ArrayList<>();
        String sql = "SELECT id, keyword, url FROM admin_keywords ORDER BY keyword ASC";
        try (Connection conn = getConnection(); Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                links.add(new KeywordLink(rs.getLong("id"), rs.getString("keyword"), rs.getString("url")));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return links;
    }

    public boolean addKeyword(String keyword, String url, long adminId) {
        String sql = "INSERT INTO admin_keywords (keyword, url, created_by, created_at) VALUES (?, ?, ?, ?) ON CONFLICT(keyword) DO UPDATE SET url=excluded.url, created_by=excluded.created_by, created_at=excluded.created_at";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, keyword.toLowerCase());
            ps.setString(2, url);
            ps.setLong(3, adminId);
            ps.setString(4, now());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean removeKeyword(long id) {
        String sql = "DELETE FROM admin_keywords WHERE id=?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public int resetDailyLimitsForAllUsers() {
        String sql = "UPDATE users SET last_food_play_date = NULL, last_pipisa_play_date = NULL, updated_at = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, now());
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean resetDailyLimitsForUser(long telegramId) {
        String sql = "UPDATE users SET last_food_play_date = NULL, last_pipisa_play_date = NULL, updated_at = ? WHERE telegram_id = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, now());
            ps.setLong(2, telegramId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private UserProfile mapUser(ResultSet rs) throws SQLException {
        return new UserProfile(
                rs.getLong("id"),
                rs.getLong("telegram_id"),
                rs.getLong("chat_id"),
                rs.getString("username"),
                rs.getString("first_name"),
                rs.getString("last_name"),
                rs.getDouble("food_total"),
                rs.getInt("pipisa_total"),
                rs.getString("last_food_play_date"),
                rs.getString("last_pipisa_play_date"),
                rs.getDouble("food_last_delta"),
                rs.getInt("pipisa_last_delta")
        );
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }

    private void insertScoreEvent(Connection conn, long telegramId, String mode, double delta, LocalDate eventDate, String createdAt) throws SQLException {
        String sql = """
                INSERT INTO score_events (user_id, mode, delta, event_date, event_month, created_at)
                SELECT id, ?, ?, ?, ?, ?
                FROM users
                WHERE telegram_id = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, mode);
            ps.setDouble(2, delta);
            ps.setString(3, eventDate.toString());
            ps.setString(4, monthKey(eventDate));
            ps.setString(5, createdAt);
            ps.setLong(6, telegramId);
            ps.executeUpdate();
        }
    }

    private void insertScoreEventByUserId(Connection conn, long userId, String mode, double delta, LocalDate eventDate, String createdAt) throws SQLException {
        String sql = """
                INSERT INTO score_events (user_id, mode, delta, event_date, event_month, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setString(2, mode);
            ps.setDouble(3, delta);
            ps.setString(4, eventDate.toString());
            ps.setString(5, monthKey(eventDate));
            ps.setString(6, createdAt);
            ps.executeUpdate();
        }
    }

    private String monthKey(LocalDate date) {
        return String.format("%04d-%02d", date.getYear(), date.getMonthValue());
    }

    private String now() {
        return DATE_TIME.format(LocalDateTime.now());
    }
}
