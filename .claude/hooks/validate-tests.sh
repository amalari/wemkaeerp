#!/usr/bin/env bash
# Usage: bash validate-tests.sh <:module-or-file> [--print-task]
set -euo pipefail
exec bash "$(dirname "$0")/validation-task.sh" test "$@"
