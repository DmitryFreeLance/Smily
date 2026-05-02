# Game Telegram Bot (Java)

Игровой Telegram-бот с режимами:
- 🍔 Еда-метр
- 📏 Писька-метр

Реализовано:
- индивидуальные счётчики пользователей
- ежедневная попытка в каждом режиме
- случайные успехи/неудачи в еда-режиме
- топ-10 рейтинги за сегодня / за месяц / за весь период + позиция пользователя
- челленджи с прогрессом и наградами (бонус кг / временный множитель)
- админ-панель на несколько админов
- управление счётчиками (установка и добавление/вычитание)
- добавление ключевых слов и ссылок
- только inline-кнопки, с навигацией `Назад` и `В меню`

## Стек
- Java 17
- Maven
- `org.telegram:telegrambots:6.9.7.1`
- SQLite

## Переменные окружения
- `BOT_TOKEN` (обязательно)
- `BOT_USERNAME` (обязательно)
- `ADMIN_IDS` (опционально, список Telegram ID через запятую)
- `DB_PATH` (опционально, по умолчанию `./bot.db`)

Пример:
```bash
export BOT_TOKEN="123456:ABC..."
export BOT_USERNAME="my_game_meter_bot"
export ADMIN_IDS="111111111,222222222"
export DB_PATH="./bot.db"
```

## Локальный запуск
```bash
mvn -DskipTests package
java -jar target/game-telegram-bot-1.0.0.jar
```

## Docker
Сборка:
```bash
docker build -t game-telegram-bot .
```

Запуск:
```bash
docker run -d \
  --name game-telegram-bot \
  -e BOT_TOKEN="123456:ABC..." \
  -e BOT_USERNAME="my_game_meter_bot" \
  -e ADMIN_IDS="111111111,222222222" \
  -e DB_PATH="/data/bot.db" \
  -v $(pwd)/data:/data \
  game-telegram-bot
```

## Команды
- `/start` — регистрация/обновление профиля и главное меню
- `/eat` или `/поесть` — попытка в еда-режиме (лимит 1 раз в день)
- `/menu` — открыть главное меню
- `/admin` — админ-панель (только для `ADMIN_IDS`)
