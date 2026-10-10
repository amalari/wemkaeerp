#!/usr/bin/env bash
# Usage: bash validate-compile.sh <file> [--print-task]
set -euo pipefail
exec bash "$(dirname "$0")/validation-task.sh" compile "$@"
