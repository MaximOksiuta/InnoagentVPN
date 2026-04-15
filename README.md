# vpn-auto Docker setup

## Запуск

1. Запустить:

```bash
./docker-up.sh
```

Скрипт автоматически создаст `.env`, если его ещё нет, и сгенерирует `JWT_SECRET` и `SUPER_KEY`, если они пустые или стоят на заглушках.

После запуска приложение будет доступно на порту `APP_PORT` из `.env` (по умолчанию `80`).

Если нужен именно прямой запуск через `docker compose up --build`, `.env` должен уже существовать заранее: `docker compose` читает его до старта сборки и не умеет сам генерировать файл в процессе.

## Что поднимается

- `frontend` — production-сборка React-приложения, раздаётся через `nginx`
- `backend` — Ktor API на Java 17
- `tg_bot` — Telegram-бот на Kotlin, использующий тот же API
- `backend_data` — именованный volume для SQLite базы

`nginx` проксирует `/api`, `/docs`, `/swagger`, `/api.json` и `/health` в backend, поэтому фронтенд и API работают с одного домена.

## Telegram Bot

Бот находится в папке [tg_bot](/Users/max/shit/vpn-auto/tg_bot:1).

Для запуска вместе с проектом:

```bash
./docker-up.sh
```

Нужно дополнительно задать `TG_BOT_TOKEN` в `.env`. Бот использует тот же `SUPER_KEY`, что и backend.
