#!/usr/bin/env bash
# Langkah 2 skill wemade-feature-discovery: apakah fitur serupa sudah ada?
# Pemakaian: scripts/find-similar-feature.sh <kata> [kata …]   (read-only)
set -uo pipefail
cd "$(dirname "$0")/.."
[ $# -eq 0 ] && { echo "Pemakaian: $0 <kata-kunci> [kata-kunci …]"; exit 1; }
pattern=$(IFS='|'; echo "$*")
C=core/src/commonMain/kotlin/com/eventverse/app
A=app/shared/src/commonMain/kotlin/com/eventverse/app
S=server/src/main/kotlin/com/eventverse/app
section(){ printf '\n== %s ==\n' "$1"; }

section "Paket domain core (nama paket / file)"
find "$C/domain" -type f -name '*.kt' | grep -iE "$pattern" | sed "s|$C/||" | head -25
section "BusinessModule (code / displayName / description)"
grep -inE "$pattern" "$C/domain/rbac/BusinessModule.kt" | head -10
section "AppNavScreen (menu & rute layar)"
grep -inE "$pattern" "$A/presentation/navigation/AppNavScreen.kt" | head -10
section "Layar presentation"
find "$A/presentation" -type f -name '*.kt' | grep -iE "$pattern" | sed "s|$A/||" | head -20
section "Route server"
{ find "$S/routes" -type f -name '*.kt' | grep -iE "$pattern"
  grep -rliE "route\(\"[^\"]*($pattern)" "$S/routes" 2>/dev/null; } | sed "s|$S/||" | sort -u | head -12
section "Migrasi"
ls server/src/main/resources/db/migration | grep -iE "$pattern"
section "Teaching doc & TRD (judul)"
for f in docs/teaching/*.md docs/trd/*.md docs/tasks/*.md; do
  [ -f "$f" ] || continue
  head -3 "$f" | grep -qiE "$pattern" && echo "$f: $(head -1 "$f" | cut -c1-110)"
done | head -20
section "Keputusan"
echo "Sudah ada → extend · Mirip → tiru polanya · Baru → tiru pola terdekat di codebase-map.md"
