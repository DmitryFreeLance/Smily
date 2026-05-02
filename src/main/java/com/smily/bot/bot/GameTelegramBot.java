package com.smily.bot.bot;

import com.smily.bot.model.ChallengeView;
import com.smily.bot.model.FoodActionResult;
import com.smily.bot.model.KeywordLink;
import com.smily.bot.model.LeaderboardPeriod;
import com.smily.bot.model.PipisaActionResult;
import com.smily.bot.model.UserProfile;
import com.smily.bot.service.GameService;
import com.smily.bot.util.FormatUtil;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.*;
import java.util.stream.Collectors;

public class GameTelegramBot extends TelegramLongPollingBot {
    private final String token;
    private final String username;
    private final Set<Long> adminIds;
    private final GameService gameService;
    private final Map<Long, AdminPendingAction> pendingActions = new HashMap<>();

    public GameTelegramBot(String token, String username, Set<Long> adminIds, GameService gameService) {
        this.token = token;
        this.username = username;
        this.adminIds = adminIds;
        this.gameService = gameService;
    }

    @Override
    public String getBotUsername() {
        return username;
    }

    @Override
    public String getBotToken() {
        return token;
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasCallbackQuery()) {
                handleCallback(update.getCallbackQuery());
                return;
            }
            if (update.hasMessage() && update.getMessage().hasText()) {
                handleMessage(update.getMessage());
            }
        } catch (Exception e) {
            long chatId = resolveChatId(update);
            if (chatId != 0) {
                long userId = resolveUserId(update);
                send(chatId, "⚠️ Что-то пошло не так. Попробуй ещё раз через пару секунд.", mainMenu(userId));
            }
        }
    }

    private void handleMessage(Message msg) {
        User tgUser = msg.getFrom();
        if (tgUser == null) {
            return;
        }
        long chatId = msg.getChatId();
        long telegramId = tgUser.getId();
        String text = msg.getText().trim();

        UserProfile profile = gameService.ensureUser(
                telegramId,
                chatId,
                tgUser.getUserName(),
                tgUser.getFirstName(),
                tgUser.getLastName()
        );

        if (pendingActions.containsKey(telegramId) && !text.startsWith("/")) {
            handleAdminInput(profile, text);
            return;
        }

        if ("/start".equalsIgnoreCase(text)) {
            String welcome = "Привет, " + gameService.displayName(profile.firstName(), profile.username()) + "! 👋\n" +
                    "Добро пожаловать в мини-игру. Выбирай режим и прокачивайся каждый день.";
            send(chatId, welcome, mainMenu(telegramId));
            return;
        }

        if ("/admin".equalsIgnoreCase(text) && isAdmin(telegramId)) {
            send(chatId, adminMenuText(), adminMenu());
            return;
        }

        if ("/eat".equalsIgnoreCase(text) || "/поесть".equalsIgnoreCase(text)) {
            processFoodAction(profile, chatId, null);
            return;
        }

        if ("/menu".equalsIgnoreCase(text) || "меню".equalsIgnoreCase(text)) {
            send(chatId, mainMenuText(), mainMenu(telegramId));
            return;
        }

        send(chatId,
                "Я понимаю команды `/start`, `/eat`, `/поесть`, `/menu` и кнопки меню ниже.",
                mainMenu(telegramId));
    }

    private void handleCallback(CallbackQuery callback) throws TelegramApiException {
        User from = callback.getFrom();
        if (from == null) {
            return;
        }
        long telegramId = from.getId();
        long chatId = callback.getMessage().getChatId();
        int messageId = callback.getMessage().getMessageId();

        gameService.ensureUser(telegramId, chatId, from.getUserName(), from.getFirstName(), from.getLastName());

        String data = callback.getData();
        answer(callback.getId(), "");

        if (CallbackData.MENU_MAIN.equals(data)) {
            edit(chatId, messageId, mainMenuText(), mainMenu(telegramId));
            return;
        }
        if (CallbackData.MENU_FOOD.equals(data)) {
            UserProfile user = gameService.findUser(telegramId).orElseThrow();
            edit(chatId, messageId, gameService.buildFoodScreen(user), foodMenu());
            return;
        }
        if (CallbackData.MENU_PIPISA.equals(data)) {
            UserProfile user = gameService.findUser(telegramId).orElseThrow();
            edit(chatId, messageId, gameService.buildPipisaScreen(user), pipisaMenu());
            return;
        }
        if (CallbackData.FOOD_EAT.equals(data)) {
            UserProfile user = gameService.findUser(telegramId).orElseThrow();
            processFoodAction(user, chatId, messageId);
            return;
        }
        if (CallbackData.FOOD_RATING.equals(data)) {
            edit(chatId, messageId, gameService.buildFoodLeaderboard(telegramId, LeaderboardPeriod.DAY), foodRatingMenu());
            return;
        }
        if (CallbackData.FOOD_RATING_DAY.equals(data)) {
            edit(chatId, messageId, gameService.buildFoodLeaderboard(telegramId, LeaderboardPeriod.DAY), foodRatingMenu());
            return;
        }
        if (CallbackData.FOOD_RATING_MONTH.equals(data)) {
            edit(chatId, messageId, gameService.buildFoodLeaderboard(telegramId, LeaderboardPeriod.MONTH), foodRatingMenu());
            return;
        }
        if (CallbackData.FOOD_RATING_ALL.equals(data)) {
            edit(chatId, messageId, gameService.buildFoodLeaderboard(telegramId, LeaderboardPeriod.ALL), foodRatingMenu());
            return;
        }
        if (CallbackData.PIPISA_RATING.equals(data)) {
            edit(chatId, messageId, gameService.buildPipisaLeaderboard(telegramId, LeaderboardPeriod.DAY), pipisaRatingMenu());
            return;
        }
        if (CallbackData.PIPISA_RATING_DAY.equals(data)) {
            edit(chatId, messageId, gameService.buildPipisaLeaderboard(telegramId, LeaderboardPeriod.DAY), pipisaRatingMenu());
            return;
        }
        if (CallbackData.PIPISA_RATING_MONTH.equals(data)) {
            edit(chatId, messageId, gameService.buildPipisaLeaderboard(telegramId, LeaderboardPeriod.MONTH), pipisaRatingMenu());
            return;
        }
        if (CallbackData.PIPISA_RATING_ALL.equals(data)) {
            edit(chatId, messageId, gameService.buildPipisaLeaderboard(telegramId, LeaderboardPeriod.ALL), pipisaRatingMenu());
            return;
        }
        if (CallbackData.PIPISA_MEASURE.equals(data)) {
            processPipisaAction(telegramId, chatId, messageId);
            return;
        }
        if (CallbackData.FOOD_CHALLENGES.equals(data)) {
            edit(chatId, messageId, gameService.renderChallengeList(telegramId), challengesMenu(telegramId));
            return;
        }
        if (data.startsWith("challenge:accept:")) {
            long challengeId = Long.parseLong(data.substring("challenge:accept:".length()));
            boolean accepted = gameService.acceptChallenge(telegramId, challengeId);
            String text = accepted ? "✅ Челлендж принят. Время пошло!" : "ℹ️ Челлендж уже принят или недоступен.";
            edit(chatId, messageId, text + "\n\n" + gameService.renderChallengeList(telegramId), challengesMenu(telegramId));
            return;
        }
        if (CallbackData.ADMIN_MENU.equals(data) && isAdmin(telegramId)) {
            edit(chatId, messageId, adminMenuText(), adminMenu());
            return;
        }
        if (CallbackData.ADMIN_STATS.equals(data) && isAdmin(telegramId)) {
            edit(chatId, messageId,
                    "📊 Статистика\n\n" +
                            "Игроков в базе: " + gameService.usersCount() + "\n" +
                            "Ключевых слов: " + gameService.listKeywords().size(),
                    adminBack());
            return;
        }
        if (CallbackData.ADMIN_KEYWORDS.equals(data) && isAdmin(telegramId)) {
            edit(chatId, messageId, adminKeywordsText(), adminKeywordsMenu());
            return;
        }
        if (CallbackData.ADMIN_KEYWORD_ADD_PROMPT.equals(data) && isAdmin(telegramId)) {
            pendingActions.put(telegramId, AdminPendingAction.ADD_KEYWORD);
            edit(chatId, messageId,
                    "✍️ Пришли данные в формате:\n`ключевое_слово | https://ссылка`\n\nПример: `youtube | https://youtube.com`",
                    adminKeywordsMenu());
            return;
        }
        if (data.startsWith("admin:keyword:remove:") && isAdmin(telegramId)) {
            long keywordId = Long.parseLong(data.substring("admin:keyword:remove:".length()));
            boolean removed = gameService.removeKeyword(keywordId);
            String text = removed ? "🗑 Ссылка удалена." : "Не удалось удалить ссылку.";
            edit(chatId, messageId, text + "\n\n" + adminKeywordsText(), adminKeywordsMenu());
            return;
        }
        if (CallbackData.ADMIN_COUNTERS.equals(data) && isAdmin(telegramId)) {
            edit(chatId, messageId,
                    "🎛 Управление счётчиками\n\n" +
                            "• Изменить абсолютное значение\n" +
                            "• Добавить/убавить значение",
                    adminCountersMenu());
            return;
        }
        if (CallbackData.ADMIN_LIMITS.equals(data) && isAdmin(telegramId)) {
            edit(chatId, messageId,
                    "🗓 Дневные лимиты\n\n" +
                            "Можно обнулить дневные ограничения, чтобы игрок(и) могли сыграть ещё раз сегодня.",
                    adminLimitsMenu());
            return;
        }
        if (CallbackData.ADMIN_LIMITS_RESET_ALL.equals(data) && isAdmin(telegramId)) {
            int affected = gameService.resetDailyLimitsForAllUsers();
            edit(chatId, messageId, "✅ Лимиты сброшены для " + affected + " игрок(ов).", adminLimitsMenu());
            return;
        }
        if (CallbackData.ADMIN_LIMITS_RESET_USER_PROMPT.equals(data) && isAdmin(telegramId)) {
            pendingActions.put(telegramId, AdminPendingAction.RESET_LIMITS_USER);
            edit(chatId, messageId,
                    "✍️ Пришли `telegram_id` игрока, для которого нужно обнулить дневные лимиты.",
                    adminLimitsMenu());
            return;
        }
        if (CallbackData.ADMIN_COUNTER_SET_PROMPT.equals(data) && isAdmin(telegramId)) {
            pendingActions.put(telegramId, AdminPendingAction.SET_COUNTERS);
            edit(chatId, messageId,
                    "✍️ Формат:\n`telegram_id | food_kg | pipisa_cm`\nМожно оставить поле пустым: `12345 | 100.0 |`",
                    adminCountersMenu());
            return;
        }
        if (CallbackData.ADMIN_COUNTER_ADD_PROMPT.equals(data) && isAdmin(telegramId)) {
            pendingActions.put(telegramId, AdminPendingAction.ADD_COUNTERS);
            edit(chatId, messageId,
                    "✍️ Формат:\n`telegram_id | +food_kg | +pipisa_cm`\nПример: `12345 | -2.5 | 3`",
                    adminCountersMenu());
            return;
        }
    }

    private void handleAdminInput(UserProfile admin, String text) {
        AdminPendingAction action = pendingActions.get(admin.telegramId());
        if (action == null) {
            return;
        }

        try {
            if (action == AdminPendingAction.ADD_KEYWORD) {
                String[] parts = text.split("\\|", 2);
                String keyword = parts[0].trim().toLowerCase();
                String url = parts.length > 1 ? parts[1].trim() : "правило";
                if (keyword.isBlank()) {
                    send(admin.chatId(), "Проверь данные: паттерн не должен быть пустым.", adminKeywordsMenu());
                    return;
                }
                gameService.addKeyword(keyword, url, admin.telegramId());
                pendingActions.remove(admin.telegramId());
                send(admin.chatId(), "✅ Правило фильтра добавлено/обновлено.\n\n" + adminKeywordsText(), adminKeywordsMenu());
                return;
            }

            if (action == AdminPendingAction.SET_COUNTERS || action == AdminPendingAction.ADD_COUNTERS) {
                String[] parts = Arrays.stream(text.split("\\|", -1)).map(String::trim).toArray(String[]::new);
                if (parts.length < 3) {
                    send(admin.chatId(), "Неверный формат. Используй подсказку из меню админа.", adminCountersMenu());
                    return;
                }
                long targetTelegramId = Long.parseLong(parts[0]);
                Double food = parts[1].isBlank() ? null : Double.parseDouble(parts[1].replace(',', '.'));
                Integer pipisa = parts[2].isBlank() ? null : Integer.parseInt(parts[2]);

                if (action == AdminPendingAction.SET_COUNTERS) {
                    boolean ok = gameService.setCounters(targetTelegramId, food, pipisa);
                    send(admin.chatId(), ok ? "✅ Значения обновлены." : "Пользователь не найден.", adminCountersMenu());
                } else {
                    UserProfile updated = gameService.adjustCounters(targetTelegramId, food, pipisa);
                    send(admin.chatId(),
                            "✅ Изменения применены.\n" +
                                    "Еда: " + FormatUtil.kg(updated.foodTotal()) + " кг\n" +
                                    "Писька-метр: " + updated.pipisaTotal() + " см",
                            adminCountersMenu());
                }
                pendingActions.remove(admin.telegramId());
            }
            if (action == AdminPendingAction.RESET_LIMITS_USER) {
                long targetTelegramId = Long.parseLong(text.trim());
                boolean ok = gameService.resetDailyLimitsForUser(targetTelegramId);
                pendingActions.remove(admin.telegramId());
                send(admin.chatId(), ok ? "✅ Лимиты игрока сброшены." : "Пользователь не найден.", adminLimitsMenu());
                return;
            }
        } catch (Exception e) {
            send(admin.chatId(), "Ошибка формата. Проверь сообщение и попробуй снова.", adminMenu());
        }
    }

    private void processFoodAction(UserProfile user, long chatId, Integer messageId) {
        FoodActionResult result = gameService.playFood(user.telegramId());
        String text;
        if (result.alreadyPlayedToday()) {
            text = "⏳ Сегодня лимит уже использован. Новая попытка будет завтра.\n" +
                    "Последний результат: " + FormatUtil.kg(result.delta()) + " кг.\n" +
                    "Всего: " + FormatUtil.kg(result.total()) + " кг.";
        } else if (result.success()) {
            text = "🍔 Ты съел(а) " + FormatUtil.kg(result.delta()) + " кг.\n" +
                    "Всего теперь: " + FormatUtil.kg(result.total()) + " кг." + result.extraText();
        } else {
            text = "🥗 Сегодня не зашло: ты похудел(а) на " + FormatUtil.kg(Math.abs(result.delta())) + " кг.\n" +
                    "Итого: " + FormatUtil.kg(result.total()) + " кг." + result.extraText();
        }

        if (messageId == null) {
            send(chatId, text, foodMenu());
        } else {
            edit(chatId, messageId, text, foodMenu());
        }
    }

    private void processPipisaAction(long telegramId, long chatId, int messageId) {
        PipisaActionResult result = gameService.playPipisa(telegramId);
        String text;
        if (result.alreadyPlayedToday()) {
            text = "⏳ Ты уже играл(а) сегодня.\nТекущий размер: " + result.total() + " см.\nСледующая попытка завтра.";
        } else {
            String action = result.delta() > 0
                    ? "вырос на " + result.delta() + " см"
                    : (result.delta() < 0 ? "уменьшился на " + Math.abs(result.delta()) + " см" : "не изменился");
            text = "📏 Результат дня: " + action + ".\n" +
                    "Теперь показатель: " + result.total() + " см.\n" +
                    "Рейтинг обновлён, проверь место в таблице.";
        }
        edit(chatId, messageId, text, pipisaMenu());
    }

    private String mainMenuText() {
        return "🎮 Главное меню\n\nВыбери режим игры:";
    }

    private String adminMenuText() {
        return "🛠 Админ-панель\n\nЗдесь можно управлять счётчиками, ключевыми ссылками и смотреть статистику.";
    }

    private String adminKeywordsText() {
        List<KeywordLink> links = gameService.listKeywords();
        if (links.isEmpty()) {
            return "🔑 Фильтр ников\n\nСписок пуст. Добавь первый паттерн для маскировки ников.";
        }
        String rendered = links.stream()
                .map(l -> "• " + l.id() + ": " + l.keyword() + " (" + l.url() + ")")
                .collect(Collectors.joining("\n"));
        return "🔑 Фильтр ников\n\nЕсли ник содержит паттерн или ссылку, вместо имени будет `НикСкрыт`.\n\n" + rendered;
    }

    private InlineKeyboardMarkup mainMenu(long telegramId) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(btn("🍔 Еда-метр", CallbackData.MENU_FOOD), btn("📏 Писька-метр", CallbackData.MENU_PIPISA)));
        if (isAdmin(telegramId)) {
            rows.add(List.of(btn("🛠 Админ-панель", CallbackData.ADMIN_MENU)));
        }
        return kb(rows);
    }

    private InlineKeyboardMarkup foodMenu() {
        return kb(List.of(
                List.of(btn("🍽 Съесть ещё!", CallbackData.FOOD_EAT)),
                List.of(btn("🏆 Рейтинг", CallbackData.FOOD_RATING), btn("🎯 Челленджи", CallbackData.FOOD_CHALLENGES)),
                List.of(btn("⬅️ Назад", CallbackData.MENU_MAIN), btn("🏠 В меню", CallbackData.MENU_MAIN))
        ));
    }

    private InlineKeyboardMarkup pipisaMenu() {
        return kb(List.of(
                List.of(btn("📏 Измерить сегодня", CallbackData.PIPISA_MEASURE)),
                List.of(btn("🥇 Рейтинг", CallbackData.PIPISA_RATING)),
                List.of(btn("⬅️ Назад", CallbackData.MENU_MAIN), btn("🏠 В меню", CallbackData.MENU_MAIN))
        ));
    }

    private InlineKeyboardMarkup foodRatingMenu() {
        return kb(List.of(
                List.of(btn("🥇 За сегодня", CallbackData.FOOD_RATING_DAY), btn("🌞 За месяц", CallbackData.FOOD_RATING_MONTH)),
                List.of(btn("🧔 За весь период", CallbackData.FOOD_RATING_ALL)),
                List.of(btn("⬅️ Назад", CallbackData.MENU_FOOD), btn("🏠 В меню", CallbackData.MENU_MAIN))
        ));
    }

    private InlineKeyboardMarkup pipisaRatingMenu() {
        return kb(List.of(
                List.of(btn("🥇 За сегодня", CallbackData.PIPISA_RATING_DAY), btn("🌞 За месяц", CallbackData.PIPISA_RATING_MONTH)),
                List.of(btn("🧔 За весь период", CallbackData.PIPISA_RATING_ALL)),
                List.of(btn("⬅️ Назад", CallbackData.MENU_PIPISA), btn("🏠 В меню", CallbackData.MENU_MAIN))
        ));
    }

    private InlineKeyboardMarkup challengesMenu(long telegramId) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (ChallengeView challenge : gameService.getChallenges(telegramId)) {
            if (!challenge.accepted()) {
                rows.add(List.of(btn("✅ Принять: " + challenge.title(), "challenge:accept:" + challenge.challengeId())));
            }
        }
        rows.add(List.of(btn("⬅️ Назад", CallbackData.MENU_FOOD), btn("🏠 В меню", CallbackData.MENU_MAIN)));
        return kb(rows);
    }

    private InlineKeyboardMarkup adminMenu() {
        return kb(List.of(
                List.of(btn("📊 Статистика", CallbackData.ADMIN_STATS), btn("🎛 Счётчики", CallbackData.ADMIN_COUNTERS)),
                List.of(btn("🔑 Фильтр ников", CallbackData.ADMIN_KEYWORDS), btn("🗓 Лимиты", CallbackData.ADMIN_LIMITS)),
                List.of(btn("⬅️ Назад", CallbackData.MENU_MAIN), btn("🏠 В меню", CallbackData.MENU_MAIN))
        ));
    }

    private InlineKeyboardMarkup adminBack() {
        return kb(List.of(
                List.of(btn("⬅️ Назад", CallbackData.ADMIN_MENU), btn("🏠 В меню", CallbackData.MENU_MAIN))
        ));
    }

    private InlineKeyboardMarkup adminKeywordsMenu() {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(btn("➕ Добавить/обновить", CallbackData.ADMIN_KEYWORD_ADD_PROMPT)));
        for (KeywordLink link : gameService.listKeywords()) {
            rows.add(List.of(btn("🗑 Удалить: " + link.keyword(), "admin:keyword:remove:" + link.id())));
        }
        rows.add(List.of(btn("⬅️ Назад", CallbackData.ADMIN_MENU), btn("🏠 В меню", CallbackData.MENU_MAIN)));
        return kb(rows);
    }

    private InlineKeyboardMarkup adminCountersMenu() {
        return kb(List.of(
                List.of(btn("🧮 Установить значение", CallbackData.ADMIN_COUNTER_SET_PROMPT)),
                List.of(btn("➕ Добавить/убавить", CallbackData.ADMIN_COUNTER_ADD_PROMPT)),
                List.of(btn("⬅️ Назад", CallbackData.ADMIN_MENU), btn("🏠 В меню", CallbackData.MENU_MAIN))
        ));
    }

    private InlineKeyboardMarkup adminLimitsMenu() {
        return kb(List.of(
                List.of(btn("♻️ Сбросить всем", CallbackData.ADMIN_LIMITS_RESET_ALL)),
                List.of(btn("👤 Сбросить игроку", CallbackData.ADMIN_LIMITS_RESET_USER_PROMPT)),
                List.of(btn("⬅️ Назад", CallbackData.ADMIN_MENU), btn("🏠 В меню", CallbackData.MENU_MAIN))
        ));
    }

    private InlineKeyboardButton btn(String text, String callback) {
        InlineKeyboardButton b = new InlineKeyboardButton();
        b.setText(text);
        b.setCallbackData(callback);
        return b;
    }

    private InlineKeyboardMarkup kb(List<List<InlineKeyboardButton>> rows) {
        InlineKeyboardMarkup m = new InlineKeyboardMarkup();
        m.setKeyboard(rows);
        return m;
    }

    private void send(long chatId, String text, InlineKeyboardMarkup markup) {
        SendMessage msg = new SendMessage();
        msg.setChatId(String.valueOf(chatId));
        msg.setText(text);
        msg.setReplyMarkup(markup);
        try {
            execute(msg);
        } catch (TelegramApiException ignored) {
        }
    }

    private void edit(long chatId, int messageId, String text, InlineKeyboardMarkup markup) {
        EditMessageText msg = new EditMessageText();
        msg.setChatId(String.valueOf(chatId));
        msg.setMessageId(messageId);
        msg.setText(text);
        msg.setReplyMarkup(markup);
        try {
            execute(msg);
        } catch (TelegramApiException e) {
            send(chatId, text, markup);
        }
    }

    private void answer(String callbackId, String text) throws TelegramApiException {
        AnswerCallbackQuery ans = new AnswerCallbackQuery();
        ans.setCallbackQueryId(callbackId);
        ans.setText(text);
        execute(ans);
    }

    private boolean isAdmin(long telegramId) {
        return adminIds.contains(telegramId);
    }

    private long resolveChatId(Update update) {
        if (update.hasMessage()) {
            return update.getMessage().getChatId();
        }
        if (update.hasCallbackQuery() && update.getCallbackQuery().getMessage() != null) {
            return update.getCallbackQuery().getMessage().getChatId();
        }
        return 0;
    }

    private long resolveUserId(Update update) {
        if (update.hasMessage() && update.getMessage().getFrom() != null) {
            return update.getMessage().getFrom().getId();
        }
        if (update.hasCallbackQuery() && update.getCallbackQuery().getFrom() != null) {
            return update.getCallbackQuery().getFrom().getId();
        }
        return 0;
    }

    private enum AdminPendingAction {
        ADD_KEYWORD,
        SET_COUNTERS,
        ADD_COUNTERS,
        RESET_LIMITS_USER
    }
}
