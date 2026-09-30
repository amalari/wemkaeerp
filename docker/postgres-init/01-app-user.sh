#!/bin/bash
# Init PostgreSQL (docker-entrypoint-initdb.d, hanya volume BARU / first initdb).
# Plan A0 (PLAN-discovery-blueprint-prototype-studio §2): membuat LOGIN role aplikasi tenant-scoped
# agar server bisa membuka pool kedua sebagai non-superuser — syarat Row-Level Security benar-benar
# menegakkan isolasi tenant. Migrasi Flyway (V16 dst.) yang memberikan GRANT tabel public dan
# REVOKE schema ops pada role ini.
#
# Env diambil dari docker-compose.yml (APP_USER / APP_USER_PASSWORD, dari DB_APP_USER/DB_APP_PASSWORD).
set -euo pipefail

APP_USER="${APP_USER:-wemade_app}"
APP_USER_PASSWORD="${APP_USER_PASSWORD:-wemade-app-dev}"

if psql -v ON_ERROR_STOP=1 -tAc "SELECT 1 FROM pg_roles WHERE rolname = '$APP_USER'" | grep -q 1; then
  echo "[init] Role '$APP_USER' sudah ada; mengaktifkan login."
  psql -v ON_ERROR_STOP=1 -c "ALTER ROLE $APP_USER LOGIN PASSWORD '$APP_USER_PASSWORD';"
else
  echo "[init] Membuat role aplikasi tenant-scoped '$APP_USER'."
  psql -v ON_ERROR_STOP=1 -c "CREATE ROLE $APP_USER LOGIN PASSWORD '$APP_USER_PASSWORD';"
fi
