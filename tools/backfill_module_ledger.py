#!/usr/bin/env python3
"""
Backfill the module development ledger from work already in this repository.

WHAT THIS DOES, AND DELIBERATELY DOES NOT DO
--------------------------------------------
It fills the **feature side** of past builds — the requirement prose, the countable shape of the
work, the archetype, the files touched — and leaves `actualHours` empty, marking every row
`effort_source = "IMPORTED"`.

It does not reconstruct hours, because they are not recoverable. Git records when work *landed*,
not how long it took, and in this repository more than thirty commits land across seven working
days: a dozen tasks share a single date. There is nothing to divide.

Two temptations are worth naming, because both produce data that looks fine and teaches nothing:

  1. **Guessing hours from how big the change looks.** `size_points` is also derived from how big
     the work looks, so hours guessed that way make `hours / size_points` nearly constant *by
     construction*. A model trained on it learns "effort is proportional to size" — which is the
     hypothesis being tested, handed back as if it were evidence.

  2. **Using diff size as a stand-in for hours.** The model would learn to predict lines of code,
     and a customer's request does not arrive with a diff attached.

Rows written here therefore cannot lend a productivity ratio, and the estimator skips them. They
are still worth importing: they give retrieval real examples of what work in this codebase looks
like, so "have we built anything like this before?" is answerable from day one, and they let the
v1 sizing weights be sanity-checked against work that actually happened.

THE ONE EXCEPTION WORTH ENTERING BY HAND
----------------------------------------
Tasks remembered because they *diverged* from expectation — "looked trivial, took twice as long
because of Skiko WASM font loading". Those escape the circularity above, because the memory is of
a surprise rather than of a size. Add them manually afterwards with
`effort_source = "RECONSTRUCTED"`; three to five such rows are worth more than thirty bland ones.

USAGE
-----
    python3 tools/backfill_module_ledger.py                 # write drafts to stdout
    python3 tools/backfill_module_ledger.py -o drafts.json  # write to a file

Output is a JSON array of request bodies for `POST /api/admin/module-dev/builds`.
**Review it before posting.** The counts below are mechanical approximations, and the archetype
guess is a keyword match; both are meant to be corrected by a human who remembers the work.
"""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from dataclasses import dataclass, field
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
TEACHING_DIR = REPO_ROOT / "docs" / "teaching"

# Keyword -> archetype. Matched against the document title and topic line.
# Order matters: the first hit wins, so the more specific terms are listed first.
ARCHETYPE_KEYWORDS: list[tuple[str, tuple[str, ...]]] = [
    ("quality_control", ("qc", "defect", "cacat", "inspeksi", "quality")),
    ("costing_hpp", ("hpp", "costing", "biaya", "bom", "tech pack", "harga")),
    ("cutting", ("cutting", "potong", "spk", "mrp", "jadwal mesin")),
    ("sewing", ("sewing", "jahit", "operator", "kanban")),
    ("finishing", ("finishing", "setrika", "washing", "trimming")),
    ("fulfillment", ("packing", "surat jalan", "fulfillment", "shipment", "ekspedisi")),
    ("raw_material", ("inventory", "bahan baku", "kain", "gudang", "stok")),
    ("order_ingestion", ("order", "sales", "crm", "sampling", "prospek")),
]

DEFAULT_ARCHETYPE = "custom_extension"


@dataclass
class Draft:
    slug: str
    title: str
    requirement_text: str
    archetype: str
    commit: str | None
    files_changed: list[str] = field(default_factory=list)
    features: dict = field(default_factory=dict)


def git(*args: str) -> str:
    """Runs a git command in the repository, returning stdout (empty on failure)."""
    try:
        return subprocess.run(
            ["git", *args],
            cwd=REPO_ROOT,
            capture_output=True,
            text=True,
            check=True,
        ).stdout.strip()
    except subprocess.CalledProcessError:
        return ""


def read_teaching_doc(path: Path) -> tuple[str, str]:
    """Returns (title, requirement_text) for one teaching document.

    The requirement text is the title plus the topic line plus the first substantial paragraph.
    That combination is what a future request will be matched against, so it should read like a
    description of the problem rather than like a table of contents.
    """
    lines = path.read_text(encoding="utf-8").splitlines()

    title = ""
    topic = ""
    body: list[str] = []

    for line in lines:
        stripped = line.strip()
        if not title and stripped.startswith("# "):
            title = re.sub(r"^#\s*", "", stripped)
            title = re.sub(r"[\U0001F300-\U0001FAFF☀-➿]", "", title).strip()
            continue
        if stripped.startswith(">") and "Topik" in stripped:
            topic = re.sub(r"^>\s*\*\*Topik Utama\*\*\s*:\s*", "", stripped).strip()
            continue
        # First real prose paragraph, skipping headings, quotes and list markers.
        if title and stripped and not stripped.startswith(("#", ">", "-", "*", "|", "```")):
            body.append(stripped)
            if len(" ".join(body)) > 400:
                break

    requirement = " ".join(part for part in [title, topic, " ".join(body)] if part)
    return title, requirement.strip()


def guess_archetype(text: str) -> str:
    lowered = text.lower()
    for archetype, keywords in ARCHETYPE_KEYWORDS:
        if any(keyword in lowered for keyword in keywords):
            return archetype
    return DEFAULT_ARCHETYPE


def find_commit_for(slug: str) -> str | None:
    """Finds the commit that introduced this teaching document."""
    sha = git("log", "--diff-filter=A", "--format=%H", "-1", "--", f"docs/teaching/{slug}.md")
    return sha or None


def files_in_commit(sha: str) -> list[str]:
    output = git("show", "--name-only", "--format=", sha)
    return [line for line in output.splitlines() if line.strip()]


def derive_features(files: list[str]) -> dict:
    """Approximates the countable shape of the work from the files it touched.

    Every number here is a heuristic. They exist to give a reviewer a starting point, not to be
    trusted: a single file can hold three entities, and a screen can be rewritten without being
    added. The `_note` field in the output says so, so nobody later mistakes these for measurements.
    """
    def count(predicate) -> int:
        return sum(1 for f in files if predicate(f))

    kotlin = [f for f in files if f.endswith(".kt")]

    entity_count = count(lambda f: "/domain/" in f and f.endswith(".kt") and "UseCase" not in f)
    use_case_count = count(lambda f: "UseCase" in f)
    screen_count = count(lambda f: f.endswith("Screen.kt") or f.endswith("View.kt"))
    endpoint_count = count(lambda f: "/routes/" in f and "Routes.kt" in f) * 2
    db_table_count = count(lambda f: "db/migration" in f)
    design_component = any("designsystem/" in f for f in files)

    # Distinct feature packages under presentation/ or domain/, minus the one being built.
    packages = {
        re.sub(r".*/(domain|presentation)/([^/]+)/.*", r"\2", f)
        for f in kotlin
        if re.search(r"/(domain|presentation)/[^/]+/", f)
    }
    affected = max(0, len(packages) - 1)

    # How many KMP targets the change plausibly reaches.
    platforms = 1
    if any("commonMain" in f for f in files):
        platforms = 3
    if any(p in f for f in files for p in ("androidApp", "iosApp", "desktopApp", "webApp")):
        platforms = 5

    return {
        "entityCount": entity_count,
        "useCaseCount": use_case_count,
        "screenCount": screen_count,
        "apiEndpointCount": endpoint_count,
        "dbTableCount": db_table_count,
        "reportCount": 0,
        "integrationCount": 0,
        "targetPlatformCount": platforms,
        "affectedExistingModuleCount": affected,
        "requiresNewDesignComponent": design_component,
    }


def build_drafts() -> list[Draft]:
    if not TEACHING_DIR.is_dir():
        print(f"No teaching directory at {TEACHING_DIR}", file=sys.stderr)
        return []

    drafts: list[Draft] = []
    for path in sorted(TEACHING_DIR.glob("teaching-*.md")):
        slug = path.stem
        title, requirement = read_teaching_doc(path)
        if not requirement:
            print(f"skipped (no readable prose): {slug}", file=sys.stderr)
            continue

        sha = find_commit_for(slug)
        files = files_in_commit(sha) if sha else []

        drafts.append(
            Draft(
                slug=slug,
                title=title or slug,
                requirement_text=requirement,
                archetype=guess_archetype(requirement),
                commit=sha,
                files_changed=files,
                features=derive_features(files),
            )
        )
    return drafts


def to_request_body(draft: Draft, index: int) -> dict:
    return {
        "buildId": f"bf-{index:03d}-{draft.slug[:40]}",
        "catalogEntryId": "REVIEW_ME: map this task to a module_catalog_entries id",
        "buildType": "ENHANCEMENT",
        "requirementText": draft.requirement_text,
        "clarityScore": None,
        "features": draft.features,
        "_effortSource": "IMPORTED",
        "_note": (
            "Counts are heuristics derived from the files the commit touched, not measurements. "
            "Correct them before posting. Hours are absent on purpose and must stay absent: see "
            "the module docstring for why reconstructing them would poison the training data."
        ),
        "_source": {
            "teachingDoc": f"docs/teaching/{draft.slug}.md",
            "commit": draft.commit,
            "filesChanged": len(draft.files_changed),
            "guessedArchetype": draft.archetype,
        },
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("-o", "--output", help="write JSON here instead of stdout")
    args = parser.parse_args()

    drafts = build_drafts()
    payload = [to_request_body(d, i + 1) for i, d in enumerate(drafts)]
    rendered = json.dumps(payload, indent=2, ensure_ascii=False)

    if args.output:
        Path(args.output).write_text(rendered, encoding="utf-8")
        print(f"{len(payload)} drafts written to {args.output}", file=sys.stderr)
    else:
        print(rendered)

    print(
        f"\n{len(payload)} drafts prepared, all with effort_source=IMPORTED and no hours.\n"
        "Next: review each entry, set a real catalogEntryId, then POST to "
        "/api/admin/module-dev/builds.\n"
        "Then add 3-5 tasks you remember as having DIVERGED from expectation, by hand, with "
        "effort_source=RECONSTRUCTED. Those are the ones worth the typing.",
        file=sys.stderr,
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
