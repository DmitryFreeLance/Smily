package com.smily.bot;

import com.smily.bot.bot.GameTelegramBot;
import com.smily.bot.config.BotConfig;
import com.smily.bot.db.Database;
import com.smily.bot.service.GameService;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

public class BotApplication {
    public static void main(String[] args) throws Exception {
        BotConfig config = BotConfig.fromEnv();

        Database database = new Database(config.dbPath());
        database.init();

        GameService gameService = new GameService(database);
        GameTelegramBot bot = new GameTelegramBot(
                config.token(),
                config.username(),
                config.adminIds(),
                gameService
        );

        TelegramBotsApi api = new TelegramBotsApi(DefaultBotSession.class);
        api.registerBot(bot);

        System.out.println("Bot started: @" + config.username());
    }
}
