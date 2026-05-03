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
- список всех пользователей в админке (имя, тег, id) с пагинацией по 10
- управление счётчиками (установка и добавление/вычитание)
- фильтр ников по паттернам (если найдено совпадение/ссылка -> `НикСкрыт`)
- удаление сообщений со ссылками/доменами в группах, где бот является админом
- админ-сброс дневных лимитов (всем или конкретному игроку)
- только inline-кнопки, с навигацией `Назад` и `В меню`

## Стек
- Java 17
- Maven
- `org.telegram:telegrambots:6.9.7.1`
- SQLite

## Переменные окружения
- `BOT_TOKEN` (обязательно)
- `BOT_USERNAME` (обязательно)
- `BOT_OWNER_ID` (опционально, один Telegram ID админа)
- `ADMIN_IDS` (опционально, список Telegram ID через запятую)
- `BOT_DB_PATH` (опционально, путь к БД; приоритетнее `DB_PATH`)
- `DB_PATH` (опционально, по умолчанию `./bot.db`)

Пример:
```bash
export BOT_TOKEN="123456:ABC..."
export BOT_USERNAME="my_game_meter_bot"
export BOT_OWNER_ID="111111111"
export ADMIN_IDS="111111111,222222222"
export BOT_DB_PATH="./bot.db"
```

## Локальный запуск
```bash
mvn -DskipTests package
java -jar target/game-telegram-bot-1.0.0.jar
```

## Docker
Сборка:
```bash
docker build -t game-meter-bot .
```

Запуск:
```bash
sudo mkdir -p "$(pwd)/data"

docker rm -f game-meter-bot 2>/dev/null || true
docker run -d \
  --name game-meter-bot \
  --restart unless-stopped \
  --network host \
  --add-host api.telegram.org:149.154.167.220 \
  -e BOT_TOKEN="123456:ABC..." \
  -e BOT_USERNAME="my_game_meter_bot" \
  -e BOT_OWNER_ID="111111111" \
  -e ADMIN_IDS="111111111,222222222" \
  -e BOT_DB_PATH="/data/bot.db" \
  -e JAVA_TOOL_OPTIONS="-Djava.net.preferIPv4Stack=true -Djava.net.preferIPv6Addresses=false" \
  -v $(pwd)/data:/data \
  game-meter-bot
```

## Команды
- `/start` — регистрация/обновление профиля и главное меню
- `/eat` или `/поесть` — попытка в еда-режиме (лимит 1 раз в день)
- `/menu` — открыть главное меню
- `/admin` — админ-панель (только для `ADMIN_IDS`)
