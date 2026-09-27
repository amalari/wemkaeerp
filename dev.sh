#!/usr/bin/env bash

# ==============================================================================
# WeMade ERP — Fullstack Development Runner
# Menjalankan Ktor Backend Server & Wasm Compose Web App dengan Auto-Watch/Reload
#
# Juga menyediakan runner test:
#   ./dev.sh test                → seluruh suite test (core + shared + server)
#   ./dev.sh test-changed [ref]  → hanya test yang berhubungan dengan file yang berubah
# ==============================================================================

# Warna ANSI untuk terminal
BOLD="\033[1m"
GREEN="\033[0;32m"
CYAN="\033[0;36m"
YELLOW="\033[1;33m"
RED="\033[0;31m"
PURPLE="\033[0;35m"
RESET="\033[0m"

# Direktori proyek root
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_DIR"

# Muat .env ke environment proses.
#
# docker-compose membaca .env sendiri untuk substitusi ${VAR}, tapi JVM tidak — System.getenv()
# hanya melihat environment proses. Tanpa baris ini, DB_APP_USER/DB_APP_PASSWORD yang sudah diisi
# di .env tidak pernah sampai ke server, dan koneksi diam-diam kembali memakai role pemilik
# (yang melewati seluruh Row-Level Security). Gagalnya senyap, jadi pemuatannya dibuat eksplisit.
if [ -f "$PROJECT_DIR/.env" ]; then
    set -a
    # shellcheck disable=SC1091
    . "$PROJECT_DIR/.env"
    set +a
fi

print_banner() {
    echo -e "${CYAN}${BOLD}"
    echo "========================================================================"
    echo "            🚀 WeMade ERP — Development Environment                     "
    echo "========================================================================"
    echo -e "${RESET}"
    echo -e "  🌐 ${BOLD}Frontend (Wasm Compose)${RESET} : ${GREEN}http://localhost:3000${RESET}"
    echo -e "  🔌 ${BOLD}Backend API (Ktor)${RESET}     : ${GREEN}http://localhost:8080${RESET}"
    echo -e "  🔄 ${BOLD}Webpack Proxy API${RESET}      : ${PURPLE}/api -> http://localhost:8080${RESET}"
    echo -e "  🐘 ${BOLD}Database (PostgreSQL)${RESET}  : ${YELLOW}localhost:${DB_PORT:-5432} (${DB_NAME:-wemade_erp})${RESET}"
    echo -e "${CYAN}------------------------------------------------------------------------${RESET}"
}

# Fungsi cek port aktif
check_port() {
    local port=$1
    local name=$2
    if lsof -Pi :"$port" -sTCP:LISTEN -t >/dev/null 2>&1 ; then
        echo -e "${YELLOW}⚠️  Port $port ($name) sedang digunakan oleh proses lain.${RESET}"
        read -p "   Apakah ingin menghentikan proses di port $port otomatis? (y/N): " -n 1 -r
        echo
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            lsof -ti :"$port" | xargs kill -9 2>/dev/null || true
            echo -e "${GREEN}   Proses di port $port berhasil dihentikan.${RESET}"
        fi
    fi
}

# Cek apakah docker postgres perlu dijalankan
check_postgres() {
    local pg_port="${DB_PORT:-5432}"
    if ! nc -z localhost "$pg_port" >/dev/null 2>&1; then
        echo -e "${YELLOW}ℹ️  PostgreSQL di port $pg_port belum aktif.${RESET}"
        if command -v docker &> /dev/null && docker compose ps >/dev/null 2>&1; then
            read -p "   Nyalakan container database via docker compose? (y/N): " -n 1 -r
            echo
            if [[ $REPLY =~ ^[Yy]$ ]]; then
                echo -e "${GREEN}🐘 Menjalankan PostgreSQL via docker compose...${RESET}"
                docker compose up -d postgres
                sleep 2
            fi
        else
            echo -e "${YELLOW}   Pastikan service PostgreSQL lokal aktif untuk fitur database.${RESET}"
        fi
    else
        echo -e "${GREEN}✅ PostgreSQL aktif di port $pg_port.${RESET}"
    fi
}

# Handler cleanup saat Ctrl+C ditekan
SERVER_PID=""
WASM_PID=""
WATCH_PID=""

# Auto-reload Ktor: `:server:run` sudah berjalan dalam mode development (lihat server/build.gradle.kts)
# dan memantau build/classes. Proses ini yang mengisi ulang folder itu setiap file server berubah.
# Ditunda sampai server selesai binding supaya dua build Gradle tidak berebut kompilasi awal.
# Perubahan di core/ tetap butuh restart manual: core masuk sebagai jar, bukan folder class.
start_server_watcher() {
    (
        until lsof -Pi :8080 -sTCP:LISTEN -t >/dev/null 2>&1; do sleep 2; done
        echo -e "${GREEN}${BOLD}[RELOAD]${RESET} Memantau perubahan server/ — class dikompilasi ulang otomatis."
        ./gradlew -t :server:classes -q 2>&1 | sed -e "s/^/[RELOAD] /"
    ) &
    WATCH_PID=$!
}

cleanup() {
    echo -e "\n${YELLOW}🛑 Menghentikan seluruh proses development...${RESET}"
    if [ -n "$SERVER_PID" ]; then
        kill "$SERVER_PID" 2>/dev/null || true
    fi
    if [ -n "$WASM_PID" ]; then
        kill "$WASM_PID" 2>/dev/null || true
    fi
    if [ -n "$WATCH_PID" ]; then
        kill "$WATCH_PID" 2>/dev/null || true
    fi
    # Hentikan background jobs terkait gradle jika ada
    kill $(jobs -p) 2>/dev/null || true
    echo -e "${GREEN}✅ Seluruh proses dev server berhasil dihentikan secara bersih.${RESET}"
    exit 0
}

trap cleanup SIGINT SIGTERM EXIT

# Parsing parameter perintah
MODE="${1:-all}"

case "$MODE" in
    wasm)
        print_banner
        check_port 3000 "Wasm Webpack Dev Server"
        echo -e "${CYAN}${BOLD}[WASM]${RESET} Memulai Wasm Development Server (Auto-Watching & Hot Reload)..."
        ./gradlew :app:webApp:wasmJsBrowserDevelopmentRun --continuous
        ;;

    server)
        print_banner
        check_postgres
        check_port 8080 "Ktor Backend Server"
        echo -e "${GREEN}${BOLD}[SERVER]${RESET} Memulai Ktor Backend Server pada port 8080 (auto-reload aktif)..."
        start_server_watcher
        ./gradlew :server:run
        ;;

    docker)
        echo -e "${GREEN}🐘 Menjalankan PostgreSQL container...${RESET}"
        docker compose up -d postgres
        echo -e "${GREEN}✅ Database siap di port ${DB_PORT:-5432}.${RESET}"
        trap - SIGINT SIGTERM EXIT
        exit 0
        ;;

    # Seluruh suite (core + shared + server).
    #
    # Dijalankan lewat dev.sh, bukan langsung `./gradlew`, karena script ini sudah
    # memuat .env di atas: Gradle sendiri tidak membaca .env, dan tanpa DB_PORT=5435
    # test server akan menembak PostgreSQL di 5432 (project lain) lalu gagal dengan
    # HikariPool$PoolInitializationException yang tampak seperti bug kode.
    test)
        trap - SIGINT SIGTERM EXIT
        exec ./tools/test-changed.sh --full
        ;;

    # Hanya test yang berhubungan dengan file yang berubah.
    # Contoh: ./dev.sh test-changed origin/main
    test-changed)
        shift
        trap - SIGINT SIGTERM EXIT
        exec ./tools/test-changed.sh "$@"
        ;;

    help|--help|-h)
        echo -e "${BOLD}Panduan Penggunaan dev.sh:${RESET}"
        echo "  ./dev.sh         : Menjalankan Server Backend (8080) dan Wasm Watcher (3000) sekaligus"
        echo "  ./dev.sh wasm    : Hanya menjalankan Wasm Dev Server dengan auto-watching/hot-reload"
        echo "  ./dev.sh server  : Hanya menjalankan Ktor Backend API Server (auto-reload server/)"
        echo "  ./dev.sh docker  : Menyalakan container database PostgreSQL"
        echo "  ./dev.sh test    : Menjalankan seluruh suite test (core + shared + server)"
        echo "  ./dev.sh test-changed [ref] : HANYA test yang berhubungan dengan file yang berubah"
        echo "  ./dev.sh help    : Menampilkan bantuan ini"
        trap - SIGINT SIGTERM EXIT
        exit 0
        ;;

    all|*)
        print_banner
        check_postgres
        check_port 8080 "Ktor Backend Server"
        check_port 3000 "Wasm Webpack Dev Server"

        echo -e "\n${GREEN}${BOLD}▶ [1/2] Menjalankan Ktor Backend Server (Port 8080)...${RESET}"
        ./gradlew :server:run 2>&1 | sed -e "s/^/[SERVER] /" &
        SERVER_PID=$!
        start_server_watcher

        # Beri jeda singkat agar Ktor sempat binding port sebelum webpack proxy aktif
        sleep 2

        echo -e "\n${CYAN}${BOLD}▶ [2/2] Menjalankan Wasm Compose Dev Server dengan Continuous Watcher (Port 3000)...${RESET}"
        ./gradlew :app:webApp:wasmJsBrowserDevelopmentRun --continuous 2>&1 | sed -e "s/^/[WASM] /" &
        WASM_PID=$!

        echo -e "\n${BOLD}${GREEN}✨ Keduanya sedang berjalan! Tekan Ctrl+C untuk menghentikan.${RESET}\n"

        # Tunggu hingga salah satu proses berhenti
        wait
        ;;
esac
