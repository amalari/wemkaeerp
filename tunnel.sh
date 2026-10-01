#!/usr/bin/env bash

# ==============================================================================
# WeMade ERP — iPaymu Webhook Tunnel Runner (TRD-PAY-001)
# Menjalankan `cloudflared tunnel run` untuk menerima callback iPaymu sandbox
# dari https://ipaymu-hook-<dev>.wemakeerp.com ke localhost:8081.
#
#   ./tunnel.sh              → jalankan tunnel foreground (auto ambil token)
#   ./tunnel.sh refresh      → ambil ulang token dari `pulumi stack output`
#   ./tunnel.sh status       → cek proses & cache token
#   ./tunnel.sh help         → bantuan
#
# Prasyarat sekali: `pulumi login --local` + stack di infra/ipaymu-bridge/
# sudah `pulumi up`. Passphrase state dibaca dari PULUMI_CONFIG_PASSPHRASE
# (env atau .env) — lihat docs/teaching/teaching-pay-001-ipaymu-testing-bridge.md
# ==============================================================================

BOLD="\033[1m"
GREEN="\033[0;32m"
CYAN="\033[0;36m"
YELLOW="\033[1;33m"
RED="\033[0;31m"
RESET="\033[0m"

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
STACK_DIR="$PROJECT_DIR/infra/ipaymu-bridge"
DEVELOPER="${TUNNEL_DEVELOPER:-achmad}"
TOKEN_CACHE="$STACK_DIR/.tunnel-token-$DEVELOPER"

# Passphrase state Pulumi (backend local) boleh datang dari .env root.
if [ -f "$PROJECT_DIR/.env" ]; then
    set -a
    # shellcheck disable=SC1091
    . "$PROJECT_DIR/.env"
    set +a
fi

die() { echo -e "${RED}✗ $1${RESET}" >&2; exit 1; }

fetch_token_from_pulumi() {
    [ -d "$STACK_DIR" ] || die "Folder $STACK_DIR tidak ditemukan."
    command -v pulumi >/dev/null 2>&1 || die "pulumi CLI tidak ada di PATH."
    [ -n "${PULUMI_CONFIG_PASSPHRASE:-}" ] || \
        die "PULUMI_CONFIG_PASSPHRASE belum di-set (export dulu, atau taruh di .env root)."
    (cd "$STACK_DIR" && pulumi stack output tunnelTokens --show-secrets --stack dev 2>/dev/null) \
        | python3 -c "import sys,json;print(json.load(sys.stdin)['$DEVELOPER'])" 2>/dev/null \
        || die "Gagal membaca token developer '$DEVELOPER' dari Pulumi. Pastikan stack 'dev' sudah 'pulumi up' dan PULUMI_CONFIG_PASSPHRASE benar."
}

ensure_token() {
    if [ -s "$TOKEN_CACHE" ]; then
        echo -e "${GREEN}✓ Token tunnel '$DEVELOPER' dari cache${RESET}"
        return
    fi
    echo -e "${YELLOW}ℹ️  Cache token kosong — membaca dari Pulumi stack 'dev'...${RESET}"
    local token
    token=$(fetch_token_from_pulumi)
    umask 077
    printf '%s' "$token" > "$TOKEN_CACHE"
    echo -e "${GREEN}✓ Token tersimpan di $TOKEN_CACHE (0600, jangan di-commit)${RESET}"
}

case "${1:-start}" in
    refresh)
        token=$(fetch_token_from_pulumi)
        umask 077
        printf '%s' "$token" > "$TOKEN_CACHE"
        echo -e "${GREEN}✓ Token tunnel '$DEVELOPER' diperbarui. Jalankan './tunnel.sh' untuk start.${RESET}"
        exit 0
        ;;

    status)
        if pgrep -f "cloudflared tunnel run" >/dev/null 2>&1; then
            echo -e "${GREEN}✓ cloudflared sedang berjalan.${RESET}"
        else
            echo -e "${YELLOW}○ cloudflared tidak berjalan.${RESET}"
        fi
        if [ -s "$TOKEN_CACHE" ]; then
            echo -e "${GREEN}✓ Cache token ada: $TOKEN_CACHE${RESET}"
        else
            echo -e "${YELLOW}○ Cache token kosong — jalankan './tunnel.sh' atau './tunnel.sh refresh'.${RESET}"
        fi
        if lsof -Pi :8081 -sTCP:LISTEN -t >/dev/null 2>&1; then
            echo -e "${GREEN}✓ Server dev (8081) aktif — webhook akan sampai.${RESET}"
        else
            echo -e "${YELLOW}○ Port 8081 kosong — jalankan './dev.sh server' agar callback tertangani.${RESET}"
        fi
        exit 0
        ;;

    help|--help|-h)
        echo -e "${BOLD}Panduan Penggunaan tunnel.sh:${RESET}"
        echo "  ./tunnel.sh          : Jalankan cloudflared tunnel (auto ambil token dari cache/Pulumi)"
        echo "  ./tunnel.sh refresh  : Ambil ulang token dari 'pulumi stack output' (setelah pulumi up)"
        echo "  ./tunnel.sh status   : Cek proses tunnel, cache token, dan port 8081"
        echo "  ./tunnel.sh help     : Bantuan ini"
        echo ""
        echo -e "  Developer: ${CYAN}$DEVELOPER${RESET} (ubah via ${BOLD}TUNNEL_DEVELOPER=<nama>${RESET} atau .env)"
        echo -e "  Webhook  : ${CYAN}https://ipaymu-hook-$DEVELOPER.wemakeerp.com/api/payment/ipaymu/notify${RESET}"
        exit 0
        ;;

    start|*)
        command -v cloudflared >/dev/null 2>&1 || die "cloudflared belum terpasang (brew install cloudflared)."
        ensure_token
        echo -e "${CYAN}${BOLD}"
        echo "========================================================================"
        echo "            🌉 iPaymu Webhook Tunnel — $DEVELOPER"
        echo "========================================================================"
        echo -e "${RESET}"
        echo -e "  🔔 ${BOLD}Webhook URL${RESET} : https://ipaymu-hook-$DEVELOPER.wemakeerp.com/api/payment/ipaymu/notify"
        echo -e "  🎯 ${BOLD}Target lokal${RESET}: http://localhost:8081 (path webhook saja; lainnya 404 di edge)"
        echo -e "  ⌨️  ${BOLD}Ctrl+C${RESET} untuk menghentikan.\n"
        exec cloudflared tunnel run --token "$(cat "$TOKEN_CACHE")"
        ;;
esac
