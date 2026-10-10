#!/usr/bin/env bash
# Detekt is not installed; never report an unavailable check as PASS.
set -euo pipefail
echo 'UNAVAILABLE: Detekt is not configured. Use repository audits and review.' >&2
exit 2
