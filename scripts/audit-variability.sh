#!/usr/bin/env bash
# Gerbang 7 skill wemade-feature-workflow — melapor, tidak memblokir. Read-only.
# Pemakaian: scripts/audit-variability.sh [base-ref=main]   → hanya baris yang DITAMBAH terhadap base.
#            scripts/audit-variability.sh --all             → pindai seluruh repo.
set -uo pipefail
cd "$(dirname "$0")/.."
count=0
report(){ printf '  %s\n' "$1"; count=$((count+1)); }

if [ "${1:-}" = "--all" ]; then
  lines=$(grep -rnE '.' --include='*.kt' core/src/commonMain app/shared/src/commonMain server/src/main 2>/dev/null)
else
  base=${1:-main}
  lines=$( { git diff -U0 "$base" -- '*.kt'; git ls-files --others --exclude-standard -- '*.kt' | xargs -I{} awk '{print "+++ b/{}"; print "+"$0}' {} 2>/dev/null; } | awk '
    /^\+\+\+ b\//{f=substr($0,7); next}
    /^@@/{split($3,a,","); n=substr(a[1],2)+0; next}
    /^\+/{print f":"n":"substr($0,2); n++}')
fi

check(){ # $1 judul, $2 regex, $3 filter path (regex), $4 kecualikan (regex)
  local hits; hits=$(printf '%s\n' "$lines" | grep -E "^[^:]*($3)[^:]*:[0-9]+:" | grep -E "$2" | grep -vE "${4:-^$}" | head -15)
  [ -z "$hits" ] && return
  echo "▸ $1"; while IFS= read -r h; do report "$(echo "$h" | cut -c1-170)"; done <<< "$hits"
}

check "enum class baru di domain — lolos Uji Variabilitas? (tenant-variability Kontrak 1)" \
  'enum class ' 'core/src/commonMain/.*/domain/'
check "tabel when per nilai enum tahap/status — seharusnya data/peran (Kontrak 3)" \
  ':[0-9]+: *[A-Z][A-Za-z]+(Stage|Step|Phase)\.[A-Z_]+ *(,|->)' '/(domain|presentation)/' 'Test\.kt|FILE-SIZE-EXEMPT'
check ".entries dipakai sebagai urutan/kerangka (Kontrak 1)" \
  '[A-Z][A-Za-z]+(Stage|Step|Phase)\.entries' '/(domain|presentation|routes)/' 'Test\.kt'
check "literal warna di luar theme (design-system Kontrak 1)" \
  'Color\(0xFF' 'presentation/' 'WeMadeTheme\.kt|FILE-SIZE-EXEMPT|:[0-9]+: *(\*|//)'
check "route mutasi — pastikan ada guard modul / fail-closed (Kontrak 7)" \
  ':[0-9]+: *(post|put|patch|delete)\(' 'server/src/main/.*/routes/'

echo; echo "Total temuan: $count (melapor, tidak memblokir — jelaskan setiap temuan di PR)"
