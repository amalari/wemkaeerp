#!/usr/bin/env bash
# Verifikasi iPaymu bridge (TRD-PAY-001 §5.2) — AC-PAY-2/3/4/7.
#
# Pemakaian (dengan SSH forward & cloudflared sudah berjalan):
#   EXPECTED_STATIC_IP="$(pulumi -C infra/ipaymu-bridge stack output egressIp)" \
#   IPAYMU_NOTIFY_URL="$(pulumi -C infra/ipaymu-bridge stack output --json webhookUrls | jq -r '.achmad')" \
#   ./scripts/test-ipaymu-bridge.sh
set -euo pipefail

: "${EXPECTED_STATIC_IP:?'set EXPECTED_STATIC_IP = pulumi stack output egressIp'}"
: "${IPAYMU_NOTIFY_URL:?'set IPAYMU_NOTIFY_URL = webhook URL developer kamu'}"

PROXY=http://127.0.0.1:8888
HOOK_HOST=$(echo "$IPAYMU_NOTIFY_URL" | awk -F/ '{print $3}')

echo "==> AC-PAY-2: port 8888 tidak terbuka ke publik"
if nc -z -w 5 "$EXPECTED_STATIC_IP" 8888 2>/dev/null; then
  echo "GAGAL: 8888 terbuka dari internet"; exit 1
fi
echo "OK"

echo "==> AC-PAY-3: egress IP = Reserved IP (butuh ssh -N -L 8888:127.0.0.1:8888 aktif)"
OUT=$(curl -fsS -x "$PROXY" https://api.ipify.org)
if [ "$OUT" != "$EXPECTED_STATIC_IP" ]; then
  echo "GAGAL: egress $OUT ≠ $EXPECTED_STATIC_IP"; exit 1
fi
echo "OK"

echo "==> AC-PAY-4: domain di luar allowlist ditolak oleh filter tinyproxy"
if curl -fsS -o /dev/null -x "$PROXY" https://example.com 2>/dev/null; then
  echo "GAGAL: filter bocor — example.com lolos"; exit 1
fi
echo "OK"

echo "==> AC-PAY-7: ingress tunnel hanya meloloskan path webhook (butuh cloudflared aktif)"
CODE=$(curl -s -o /dev/null -w "%{http_code}" "https://$HOOK_HOST/health")
if [ "$CODE" != "404" ]; then
  echo "GAGAL: /health lewat tunnel = $CODE (harus 404 dari edge)"; exit 1
fi
echo "OK"

echo "Semua pemeriksaan LOLOS (AC-PAY-2/3/4/7)."
