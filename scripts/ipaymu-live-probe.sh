#!/usr/bin/env bash
# Probe live sandbox iPaymu (TRD-PAY-001 §6 #5): buat transaksi Hosted Checkout, lalu cek
# status — memvalidasi signature + create + /transaction end-to-end tanpa UI.
# Hasil eksperimen 2026-10-01: /transaction menolak SessionID (400 "transaction not found") —
# hanya trx_id numerik dari callback yang valid.
# Kredensial dibaca dari .env repo, TIDAK pernah dicetak.
# Pemakaian: scripts/ipaymu-live-probe.sh
set -euo pipefail
cd /Volumes/amalari/Projects/wemkaeerp

VA=$(grep -E '^IPAYMU_VA=' .env | tail -1 | cut -d= -f2- | tr -d '"' | xargs)
KEY=$(grep -E '^IPAYMU_API_KEY=' .env | tail -1 | cut -d= -f2- | tr -d '"' | xargs)
BASE=$(grep -E '^IPAYMU_BASE_URL=' .env | tail -1 | cut -d= -f2- | tr -d '"' | xargs)
[[ -n "$VA" && -n "$KEY" && "$BASE" == https://* ]] || { echo "env tidak lengkap"; exit 1; }

sign() { # $1 = body
  python3 -c '
import hmac, hashlib, sys
body, va, key = sys.argv[1], sys.argv[2], sys.argv[3]
bh = hashlib.sha256(body.encode()).hexdigest().lower()
sts = "POST:" + va + ":" + bh + ":" + key
print(hmac.new(key.encode(), sts.encode(), hashlib.sha256).hexdigest())
' "$1" "$VA" "$KEY"
}

post_signed() { # $1 = path, $2 = body
  local sig; sig=$(sign "$2")
  curl -s -m 30 -X POST "$BASE$1" \
    -H 'Content-Type: application/json' -H 'Accept: application/json' \
    -H "va: $VA" -H "timestamp: $(date +%s)000" -H "signature: $sig" -d "$2"
}

echo "==> 1) Buat transaksi Hosted Checkout"
CREATE_BODY='{"product":["Eksperimen rekonsiliasi"],"qty":[1],"price":[10000],"description":["uji /transaction dengan SessionID"],"notifyUrl":"https://ipaymu-hook-achmad.wemakeerp.com/api/payment/ipaymu/notify","returnUrl":"http://localhost:3001/builder/billing","cancelUrl":"http://localhost:3001/builder/billing","referenceId":"recon-probe-001","buyerName":"probe","buyerEmail":"billing@wemakeerp.com"}'
CREATE_RESP=$(post_signed "/payment" "$CREATE_BODY")
echo "$CREATE_RESP"

SESSION=$(echo "$CREATE_RESP" | python3 -c '
import re, sys
m = re.search(r"(?i)\"SessionID\"\s*:\s*\"([^\"]+)\"", sys.stdin.read())
print(m.group(1) if m else "")
')
[[ -n "$SESSION" ]] || { echo "GAGAL: SessionID tidak ditemukan"; exit 1; }
echo "SessionID diperoleh (panjang ${#SESSION})"

echo ""
echo "==> 2) Cek status pakai SessionID sebagai transactionId"
post_signed "/transaction" "{\"transactionId\":\"$SESSION\"}"
echo ""
