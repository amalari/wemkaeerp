#!/usr/bin/env python3
"""Codex PostToolUse adapter: report paths from JSON, including multi-file patches."""
import json
import re
import sys


def changed_paths(payload):
    data = payload.get('tool_input', {})
    if not isinstance(data, dict):
        raise ValueError('tool_input must be an object')
    paths = [data['file_path']] if isinstance(data.get('file_path'), str) else []
    patch = data.get('command', '')
    if isinstance(patch, str):
        paths.extend(re.findall(r'^\*\*\* (?:Add File|Update File|Delete File|Move to): (.+)$', patch, re.M))
    return list(dict.fromkeys(paths))


if __name__ == '__main__':
    try:
        paths = changed_paths(json.load(sys.stdin))
        if paths:
            print(json.dumps({'systemMessage': 'Files modified: ' + ', '.join(paths)}))
    except (ValueError, TypeError) as error:
        print(f'Invalid hook input: {error}', file=sys.stderr)
        sys.exit(1)
