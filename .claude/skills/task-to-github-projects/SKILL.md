---
name: task-to-github-projects
description: >
  Convert brainstormed features, plans, or task breakdowns into structured GitHub Issues
  and link them to GitHub Projects (v2) using the `gh` CLI. Activate when the user wants to
  export/sync tasks, brainstorm results, or implementation plans to GitHub Projects or Issues.
---

# Task to GitHub Projects Skill

Convert brainstormed ideas, technical specifications, and architectural plans into structured GitHub Issues and assign them to GitHub Projects (v2).

---

## When to Activate This Skill

Trigger this skill when:
- The user completes a brainstorming session or implementation plan and wants to convert tasks into GitHub Issues/Projects.
- The user mentions: *"create GitHub issues from this"*, *"sync tasks to GitHub Projects"*, *"export plan to GitHub"*, *"push tasks to GitHub"*.
- After breaking down a large epic or domain feature into actionable backlog items.

---

## Prerequisites

Execute via the **`gh` CLI** (already available in this environment; see the harness's own "Use the `gh` CLI for GitHub operations" guidance) rather than a GitHub MCP server:

1. Confirm authentication: `gh auth status`.
2. Confirm the target repo's remote: `gh repo view --json nameWithOwner`.
3. Projects v2 operations go through `gh project` (e.g. `gh project item-add`, `gh project field-list`) — run `gh project --help` if the target project number/owner isn't already known.

If a GitHub MCP server is connected in this session instead, prefer its tools over shelling out to `gh` for the same operations — don't do both.

---

## 4-Step Execution Workflow

```
[ Brainstorm Output / Plan ]
            ↓
  1. Task Structuring & Decomposition (Atomic Issues)
            ↓
  2. User Review & Approval (Confirm Repos, Labels & Milestones)
            ↓
  3. Execution via `gh` CLI (Create Issues & Link to Project Board)
            ↓
  4. Output Summary Table & Direct Links
```

---

### Step 1: Task Structuring & Decomposition

Break down the brainstormed content into well-scoped, atomic issues:

#### Issue Structure Template:
- **Title**: `[<Domain/Feature>] <Imperative Verb Action>` (e.g., `[Pipeline] Implement TenantEntitlementRepository and Postgres binding`)
- **Labels**:
  - Type: `type:feature`, `type:bug`, `type:refactor`, `type:task`, `type:docs`
  - Domain/Area: `domain:pipeline`, `domain:rbac`, `layer:core`, `layer:app-shared`, `layer:server`
  - Priority: `priority:p0-critical`, `priority:p1-high`, `priority:p2-medium`, `priority:p3-low`
- **Body Markdown**:
  ```markdown
  ## 🎯 Goal & Context
  Brief description of what this task accomplishes and why it is needed.

  ## 📋 Acceptance Criteria
  - [ ] Specific condition 1
  - [ ] Specific condition 2
  - [ ] Unit/Integration tests added

  ## 🛠 Technical Checklist / Implementation Details
  - [ ] File 1 changes
  - [ ] File 2 changes

  ## 🔗 References & Dependencies
  - Blocked by: #<issue_id> (or None)
  - Related to: <architecture note or design doc>
  ```

---

### Step 2: Confirmation Before Publishing

Before creating anything, summarize the planned issues to the user:
- Target Repository: `owner/repo` (from `gh repo view`)
- Target Project Name/Number (if applicable)
- Table of proposed issues (Title, Labels, Priority)

Ask for quick confirmation or adjustments — issue/project creation is outward-facing and hard to fully undo.

---

### Step 3: Execution via `gh` CLI

1. **Create the Issues**:
   ```bash
   gh issue create --title "<title>" --body "<body>" --label "type:feature,domain:pipeline"
   ```
   Record the returned issue numbers and URLs.

2. **Add to GitHub Project (Projects v2)**:
   ```bash
   gh project item-add <project-number> --owner <owner> --url <issue-url>
   ```
   Then set status/priority/size fields with `gh project item-edit` as needed.

---

### Step 4: Summary & Verification

Present a clean markdown table showing the created artifacts:

| # | Issue Title | Labels | GitHub Link | Project Status |
|---|---|---|---|---|
| #101 | `[Pipeline] Add TenantModuleEntitlement domain model` | `domain:pipeline`, `type:feature` | [#101](https://github.com/owner/repo/issues/101) | `Todo` |
| #102 | `[Admin] Create AdminRoutes entitlement endpoints` | `layer:server`, `type:feature` | [#102](https://github.com/owner/repo/issues/102) | `Todo` |

---

## Best Practices & Guidelines

1. **Keep tasks atomic**: Each issue should be completable in 1-3 days or represent a single cohesive pull request.
2. **Follow DDD and Project Rules**: Align labels and scope with the project's architecture (`core`, `app/shared`, `server`) — see `.claude/CLAUDE.md`.
3. **Avoid duplicate issues**: Check for existing issues first with `gh issue list --search "<keywords>"`.
