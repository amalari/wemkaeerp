#!/bin/bash

# Audit script for i18n literal counting in presentation layer
# This script counts literal sentences in presentation/ that should be translatable

set -e

echo "=== i18n Literal Audit ==="
echo "Counting literal strings in presentation layer..."
echo ""

PRESENTATION_DIR="app/shared/src/commonMain/kotlin/com/eventverse/app/presentation"

# Count Text("...") literals (excluding comments and string resources)
TEXT_LITERALS=$(grep -r "Text(\"" "$PRESENTATION_DIR" --include="*.kt" 2>/dev/null | grep -v "stringResource\|//.*Text\|//.*\"" | wc -l)

# Count total files with Text literals
FILES_WITH_LITERALS=$(grep -rl "Text(\"" "$PRESENTATION_DIR" --include="*.kt" 2>/dev/null | wc -l)

# Count total .kt files in presentation
TOTAL_FILES=$(find "$PRESENTATION_DIR" -name "*.kt" 2>/dev/null | wc -l)

echo "=== Results ==="
echo "Total .kt files in presentation: $TOTAL_FILES"
echo "Files with Text literals: $FILES_WITH_LITERALS"
echo "Total Text(\"...\") literals: $TEXT_LITERALS"
echo ""

# Calculate baseline (this should be captured on first run)
BASELINE_FILE=".i18n-audit-baseline.txt"
if [ -f "$BASELINE_FILE" ]; then
    BASELINE=$(cat "$BASELINE_FILE")
    echo "Baseline: $BASELINE literals"
    echo ""

    if [ "$TEXT_LITERALS" -gt "$BASELINE" ]; then
        echo "WARNING: Literal count INCREASED from baseline!"
        echo "New literals added: $((TEXT_LITERALS - BASELINE))"
        exit 1
    else
        echo "OK: Literal count is at or below baseline"
    fi
else
    echo "No baseline found. Creating baseline with current count: $TEXT_LITERALS"
    echo "$TEXT_LITERALS" > "$BASELINE_FILE"
    echo "Baseline saved. Run again to compare."
fi

echo ""
echo "=== Top files with most literals ==="
grep -rh "Text(\"" "$PRESENTATION_DIR" --include="*.kt" 2>/dev/null | \
    sed 's/.*Text("//; s/").*//' | \
    sort | uniq -c | sort -rn | head -20

echo ""
echo "=== Done ==="
