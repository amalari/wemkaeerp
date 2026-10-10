"""Offline regression checks for generated config and hook behavior."""
import importlib.util
import json
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

from sync_codex_config import ROOT, sync


class SyncTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name).resolve()
        for folder in ('skills', 'commands', 'agents', 'hooks', 'codex'):
            shutil.copytree(ROOT / '.claude' / folder, self.root / '.claude' / folder)
        for name in ('.claude/settings.json', '.mcp.json', 'AGENTS.md'):
            shutil.copy2(ROOT / name, self.root / name)

    def test_idempotence_drift_and_unmanaged_preservation(self):
        self.assertEqual(sync(self.root), 0)
        self.assertEqual(sync(self.root, True), 0)
        extra = self.root / '.agents/skills/extra/SKILL.md'
        extra.parent.mkdir()
        extra.write_text('user owned')
        target = self.root / '.codex/hooks.json'
        target.write_text('drift')
        self.assertEqual(sync(self.root, True), 1)
        self.assertEqual(target.read_text(), 'drift')
        self.assertEqual(sync(self.root), 0)
        self.assertEqual(extra.read_text(), 'user owned')
        self.assertEqual(sync(self.root, True), 0)

    def test_removed_source_is_reported_and_preserved(self):
        sync(self.root)
        (self.root / '.claude/agents/fixer.md').unlink()
        with self.assertRaisesRegex(ValueError, 'Stale generated'):
            sync(self.root)
        self.assertTrue((self.root / '.codex/agents/fixer.toml').exists())

    def test_symlink_output_is_rejected_before_writes(self):
        victim = self.root / 'victim'
        victim.write_text('keep')
        target = self.root / '.codex/config.toml'
        target.parent.mkdir()
        target.symlink_to(victim)
        with self.assertRaisesRegex(ValueError, 'symlink'):
            sync(self.root)
        self.assertEqual(victim.read_text(), 'keep')
        self.assertFalse((self.root / '.codex/hooks.json').exists())

    def test_unknown_hook_requires_adapter(self):
        path = self.root / '.claude/settings.json'
        settings = json.loads(path.read_text())
        settings['hooks']['PostToolUse'][0]['hooks'][0]['command'] = 'echo $CLAUDE_NEW_FIELD'
        path.write_text(json.dumps(settings))
        with self.assertRaisesRegex(ValueError, 'explicit Codex adapter'):
            sync(self.root)

    def test_generated_toml_and_skill_resources(self):
        try:
            import tomllib
        except ImportError:
            self.skipTest('TOML parse check requires Python 3.11+; generator supports 3.9')
        sync(self.root)
        config = tomllib.loads((self.root / '.codex/config.toml').read_text())
        self.assertGreater(config['project_doc_max_bytes'], (self.root / 'AGENTS.md').stat().st_size)
        self.assertEqual(set(config['mcp_servers']), {'graphify', 'playwright'})
        for path in (self.root / '.codex/agents').glob('*.toml'):
            agent = tomllib.loads(path.read_text())
            self.assertEqual(agent['name'], path.stem)
            self.assertTrue(agent['developer_instructions'])
            self.assertNotIn('model', agent)
        for source in (self.root / '.claude/skills').rglob('*'):
            if source.is_file():
                target = self.root / '.agents/skills' / source.relative_to(self.root / '.claude/skills')
                self.assertEqual(source.read_bytes(), target.read_bytes())


class HookTests(unittest.TestCase):
    def test_multiple_patch_paths_including_move(self):
        spec = importlib.util.spec_from_file_location('hook', ROOT / '.claude/codex/file-modified.py')
        hook = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(hook)
        patch = '*** Update File: a.kt\n*** Move to: b.kt\n*** Delete File: c.kt\n*** Add File: d.kt\n'
        self.assertEqual(hook.changed_paths({'tool_input': {'command': patch}}), ['a.kt', 'b.kt', 'c.kt', 'd.kt'])
        self.assertEqual(hook.changed_paths({'tool_input': {'file_path': 'a.kt'}}), ['a.kt'])

    def test_task_mapping_and_unavailable_checks(self):
        for kind, argument, expected in (
            ('compile', 'core/src/commonMain/A.kt', ':core:compileKotlinJvm'),
            ('compile', 'app/shared/src/commonMain/A.kt', ':app:shared:compileKotlinJvm'),
            ('compile', 'server/src/main/A.kt', ':server:compileKotlin'),
            ('test', ':app:shared', ':app:shared:jvmTest'),
            ('test', ':server', ':server:test'),
        ):
            result = subprocess.run(['bash', '.claude/hooks/validation-task.sh', kind, argument, '--print-task'], cwd=ROOT, capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual(result.stdout.strip(), expected)
        result = subprocess.run(['bash', '.claude/hooks/validate-detekt.sh', 'core/src/A.kt'], cwd=ROOT, capture_output=True)
        self.assertEqual(result.returncode, 2)


if __name__ == '__main__':
    unittest.main()
