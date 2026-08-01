"""
EventVerse Feature Brainstorm Agent

An interactive terminal agent that helps brainstorm features for the EventVerse
Kotlin Multiplatform project and publishes them as structured GitHub Issues.

Usage:
    cd .agents/agents/brainstorm
    cp .env.example .env          # fill in your tokens
    python agent.py

Requirements:
    pip install google-antigravity python-dotenv
"""

import asyncio
import os
import sys

# Load .env from same directory
from pathlib import Path

# ---------------------------------------------------------------------------
# Load .env
# ---------------------------------------------------------------------------
env_file = Path(__file__).parent / ".env"
if env_file.exists():
    with open(env_file) as f:
        for line in f:
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                key, _, val = line.partition("=")
                os.environ.setdefault(key.strip(), val.strip())

# ---------------------------------------------------------------------------
# Validate required env vars
# ---------------------------------------------------------------------------
missing = [k for k in ["GEMINI_API_KEY", "GITHUB_TOKEN"] if not os.environ.get(k)]
if missing:
    print(f"❌ Missing environment variables: {', '.join(missing)}")
    print(f"   Please fill in .agents/agents/brainstorm/.env")
    sys.exit(1)

GITHUB_REPO = os.environ.get("GITHUB_REPO", "amalari/event-verse")
GITHUB_PROJECT_NUMBER = int(os.environ.get("GITHUB_PROJECT_NUMBER", "0") or "0")
GITHUB_PROJECT_OWNER = os.environ.get("GITHUB_PROJECT_OWNER", "amalari")

# ---------------------------------------------------------------------------
# Antigravity imports
# ---------------------------------------------------------------------------
from google.antigravity import Agent, LocalAgentConfig

# ---------------------------------------------------------------------------
# Import our tools
# ---------------------------------------------------------------------------
sys.path.insert(0, str(Path(__file__).parent))
from tools.github_tools import (
    list_github_labels,
    list_github_milestones,
    create_github_issue,
    add_to_github_project,
    get_issue_node_id,
)
from tools.brainstorm_tools import structure_feature_as_issue

# ---------------------------------------------------------------------------
# System prompt
# ---------------------------------------------------------------------------

SYSTEM_PROMPT = f"""\
You are the **EventVerse Feature Brainstorm Agent** — a product + engineering \
assistant specialized in the EventVerse Kotlin Multiplatform (KMP) project.

## Your Mission
Help the user brainstorm a feature idea through a structured but conversational \
Q&A, then create a complete, well-structured GitHub Issue in the repository \
`{GITHUB_REPO}`.

## EventVerse Project Context
- Stack: Kotlin Multiplatform (KMP) — Android, iOS, Desktop, Web (WasmJS), Server (Ktor)
- Architecture: Domain-Driven Design (DDD) with MVI presentation pattern
- Base package: `com.eventverse.app`
- Layers: Domain → Application → Presentation, Infrastructure
- Use cases named: `[Verb][Noun]UseCase` (e.g. `PublishEventUseCase`)
- Value objects: `@JvmInline value class EventTitle(val value: String)`

## Brainstorm Workflow

### Phase 1 — Discovery (ask these if not already clear)
1. What is the feature? (brief description)
2. Which users does it serve? (organizer, attendee, admin?)
3. What problem does it solve?
4. What platform(s)? (Android / iOS / Desktop / Web / Server)
5. How does it fit the DDD architecture? Which domain? Which layer?
6. What are 3–5 acceptance criteria for "done"?
7. Priority? (High / Medium / Low)
8. Any technical considerations?
9. Estimation? (optional)

### Phase 2 — Structuring
- Call `structure_feature_as_issue(...)` to generate the issue body.
- Call `list_github_labels("{GITHUB_REPO}")` to see available labels.
- Call `list_github_milestones("{GITHUB_REPO}")` to see available milestones.
- Show the user the draft and ask: "Shall I create this issue? (yes/no/edit)"

### Phase 3 — Publishing
- If confirmed: Call `create_github_issue(...)`.
- After issue is created, if GITHUB_PROJECT_NUMBER is set (>0): 
  call `get_issue_node_id(...)` then `add_to_github_project(...)`.
- Print the final issue URL.

## Rules
- Never create the issue without user confirmation.
- Always show the formatted draft before publishing.
- Suggest relevant labels based on the DDD domain and layer.
- Use concise, friendly language. Ask one cluster of questions at a time.
- The repo is `{GITHUB_REPO}`. GitHub Project owner is `{GITHUB_PROJECT_OWNER}`, \
project number is `{GITHUB_PROJECT_NUMBER}`.
"""

# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

async def main():
    print("=" * 60)
    print("  🚀 EventVerse Feature Brainstorm Agent")
    print(f"  📦 Repo: {GITHUB_REPO}")
    if GITHUB_PROJECT_NUMBER:
        print(f"  📋 GitHub Project: #{GITHUB_PROJECT_NUMBER}")
    print("=" * 60)
    print("  Describe a feature you want to add to EventVerse.")
    print("  Type 'quit' or 'exit' to stop.\n")

    config = LocalAgentConfig(
        system_instructions=SYSTEM_PROMPT,
        tools=[
            list_github_labels,
            list_github_milestones,
            create_github_issue,
            add_to_github_project,
            get_issue_node_id,
            structure_feature_as_issue,
        ],
    )

    async with Agent(config) as agent:
        while True:
            try:
                user_input = input("You: ").strip()
            except (EOFError, KeyboardInterrupt):
                print("\n👋 Goodbye!")
                break

            if not user_input:
                continue
            if user_input.lower() in ("quit", "exit", "q"):
                print("👋 Goodbye!")
                break

            print("\nAgent: ", end="", flush=True)
            response = await agent.chat(user_input)
            async for chunk in response:
                print(chunk, end="", flush=True)
            print("\n")


if __name__ == "__main__":
    asyncio.run(main())
