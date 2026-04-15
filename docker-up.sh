#!/bin/sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
ENV_FILE="$ROOT_DIR/.env"
ENV_EXAMPLE="$ROOT_DIR/.env.example"

generate_secret() {
    if command -v openssl >/dev/null 2>&1; then
        openssl rand -hex 32
        return
    fi

    tr -dc 'A-Za-z0-9' </dev/urandom | head -c 64
}

ensure_file() {
    if [ ! -f "$ENV_FILE" ]; then
        if [ -f "$ENV_EXAMPLE" ]; then
            cp "$ENV_EXAMPLE" "$ENV_FILE"
        else
            : >"$ENV_FILE"
        fi
    fi
}

set_env_value() {
    key=$1
    value=$2
    tmp_file=$(mktemp)

    if [ -f "$ENV_FILE" ] && grep -q "^${key}=" "$ENV_FILE"; then
        sed "s|^${key}=.*$|${key}=${value}|" "$ENV_FILE" >"$tmp_file"
    else
        cat "$ENV_FILE" >"$tmp_file"
        printf '%s=%s\n' "$key" "$value" >>"$tmp_file"
    fi

    mv "$tmp_file" "$ENV_FILE"
}

read_env_value() {
    key=$1
    if [ ! -f "$ENV_FILE" ]; then
        return 0
    fi

    grep "^${key}=" "$ENV_FILE" | tail -n 1 | cut -d '=' -f 2-
}

ensure_secret() {
    key=$1
    current_value=$(read_env_value "$key")

    if [ -z "$current_value" ] || [ "$current_value" = "change-me-to-a-long-random-secret" ]; then
        set_env_value "$key" "$(generate_secret)"
    fi
}

ensure_file
ensure_secret "JWT_SECRET"
ensure_secret "SUPER_KEY"

cd "$ROOT_DIR"
exec docker compose up --build "$@"
