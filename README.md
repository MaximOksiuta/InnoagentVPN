# vpn-auto Docker setup

## Запуск

1. Запустить:

```bash
./docker-up.sh
```

Скрипт автоматически создаст `.env`, если его ещё нет, и сгенерирует `JWT_SECRET` и `SUPER_KEY`, если они пустые или стоят на заглушках.

После запуска приложение будет доступно на порту `APP_PORT` из `.env` на `127.0.0.1` хоста (по умолчанию `8080`).
Для доступа из интернета рекомендуется ставить перед ним системный `nginx` и завершать TLS там.

Если нужен именно прямой запуск через `docker compose up --build`, `.env` должен уже существовать заранее: `docker compose` читает его до старта сборки и не умеет сам генерировать файл в процессе.

## Что поднимается

- `frontend` — production-сборка React-приложения, раздаётся через `nginx`
- `backend` — Ktor API на Java 17
- `tg_bot` — Telegram-бот на Kotlin, использующий тот же API
- `backend_data` — именованный volume для SQLite базы

`nginx` проксирует `/api`, `/docs`, `/swagger`, `/api.json` и `/health` в backend, поэтому фронтенд и API работают с одного домена.

## HTTPS через Nginx + Certbot на Ubuntu

Рекомендуемая схема:

- Docker-приложение слушает только `127.0.0.1:${APP_PORT}`
- системный `nginx` на Ubuntu принимает `80/443`
- `certbot` получает и обновляет Let's Encrypt сертификат

### 1. DNS и firewall

- создайте `A`-запись домена на IP сервера
- откройте порты `80/tcp` и `443/tcp`

Если используется `ufw`:

```bash
sudo ufw allow OpenSSH
sudo ufw allow 'Nginx Full'
sudo ufw enable
```

### 2. Запустить приложение

Проверьте `.env`:

```bash
APP_PORT=8080
```

Запустите приложение:

```bash
./docker-up.sh -d
```

Проверка:

```bash
curl http://127.0.0.1:8080/health
```

### 3. Установить nginx и certbot

```bash
sudo apt update
sudo apt install -y nginx snapd
sudo snap install core
sudo snap refresh core
sudo snap install --classic certbot
sudo ln -sf /snap/bin/certbot /usr/local/bin/certbot
```

### 4. Настроить nginx reverse proxy

Создайте файл `/etc/nginx/sites-available/vpn-auto`:

```nginx
server {
    listen 80;
    listen [::]:80;
    server_name example.com www.example.com;

    client_max_body_size 20m;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

Включите сайт:

```bash
sudo ln -s /etc/nginx/sites-available/vpn-auto /etc/nginx/sites-enabled/vpn-auto
sudo nginx -t
sudo systemctl reload nginx
```

Проверка:

```bash
curl -I http://example.com/health
```

### 5. Выпустить сертификат

```bash
sudo certbot --nginx -d example.com -d www.example.com
```

Certbot сам:

- выпустит сертификат
- добавит SSL-конфиг
- предложит включить redirect с HTTP на HTTPS

### 6. Проверить автообновление

```bash
sudo certbot renew --dry-run
```

### 7. Что менять в проекте при HTTPS

Дополнительные правки в Docker-конфигах не нужны. Внутренний `nginx` контейнера остаётся HTTP-only, а внешний системный `nginx` передаёт в приложение корректный `X-Forwarded-Proto`.

## Telegram Bot

Бот находится в папке [tg_bot](/Users/max/shit/vpn-auto/tg_bot:1).

Для запуска вместе с проектом:

```bash
./docker-up.sh
```

Нужно дополнительно задать `TG_BOT_TOKEN` в `.env`. Бот использует тот же `SUPER_KEY`, что и backend.
