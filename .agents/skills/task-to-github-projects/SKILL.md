---
name: task-to-github-projects
description: >
  Convert brainstormed features, plans, or task breakdowns into structured GitHub Issues
  and link them to GitHub Projects (v2) using GitHub MCP. Activate when the user wants to
  export/sync tasks, brainstorm results, or implementation plans to GitHub Projects or Issues.
---

# Task to GitHub Projects Skill

Convert brainstormed ideas, technical specifications, and architectural plans into structured GitHub Issues and assign them to GitHub Projects (v2) via the GitHub MCP server.

---

## When to Activate This Skill

Trigger this skill when:
- The user completes a brainstorming session or implementation plan and wants to convert tasks into GitHub Issues/Projects.
- The user mentions: *"create GitHub issues from this"*, *"sync tasks to GitHub Projects"*, *"export plan to GitHub"*, *"push tasks to GitHub"*.
- After breaking down a large epic or domain feature into actionable backlog items.

---

## Prerequisites

1. **GitHub MCP Server** configured in `~/.gemini/config/mcp_config.json`:
   ```json
   "github": {
     "command": "npx",
     "args": ["-y", "@modelcontextprotocol/server-github"],
     "env": {
       "GITHUB_PERSONAL_ACCESS_TOKEN": "<YOUR_GITHUB_PAT>"
     }
   }
   ```
2. **Token Scopes Needed**:
   - Classic PAT: `repo`, `project`, `read:org`
   - Fine-grained PAT:
     - **Repository permissions**: Issues (`Read and write`), Metadata (`Read-only`)
     - **Organization / Account permissions**: Projects (`Read and write`)

---

## 4-Step Execution Workflow

```
[ Brainstorm Output / Plan ]
            ↓
  1. Task Structuring & Decomposition (Atomic Issues)
            ↓
  2. User Review & Approval (Confirm Repos, Labels & Milestones)
            ↓
  3. GitHub MCP Execution (Create Issues & Link to Project Board)
            ↓
  4. Output Summary Table & Direct Links
```

---

### Step 1: Task Structuring & Decomposition

Break down the brainstormed content into well-scoped, atomic issues:

#### Issue Structure Template:
- **Title**: `[<Domain/Feature>] <Imperative Verb Action>` (e.g., `[Event] Implement EventRepository interface and SQLDelight driver`)
- **Labels**:
  - Type: `type:feature`, `type:bug`, `type:refactor`, `type:task`, `type:docs`
  - Domain/Area: `domain:event`, `domain:ticket`, `layer:core`, `layer:ui`, `layer:server`
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

Before executing MCP commands, summarize the planned issues to the user:
- Target Repository: `owner/repo`
- Target Project Name/Number (if applicable)
- Table of proposed issues (Title, Labels, Priority)

Ask for quick confirmation or adjustments.

---

### Step 3: GitHub MCP Execution

Use the GitHub MCP tools to create issues and link them to the project board:

1. **Create the Issues**:
   - Call MCP tool `create_issue` with `owner`, `repo`, `title`, `body`, and `labels`.
   - Record returned issue numbers and node IDs / URLs.

2. **Add to GitHub Project (Projects v2)**:
   - If adding to a project board:
     - Use `add_issue_to_project` / `update_project_item_field` to assign status (e.g., `Todo`, `Backlog`) and priority/size fields.

---

### Step 4: Summary & Verification

Present a clean markdown table showing the created artifacts:

| # | Issue Title | Labels | GitHub Link | Project Status |
|---|---|---|---|---|
| #101 | `[Event] Add Event domain model` | `domain:event`, `type:feature` | [#101](https://github.com/owner/repo/issues/101) | `Todo` |
| #102 | `[Event] Create EventListViewModel` | `layer:ui`, `type:feature` | [#102](https://github.com/owner/repo/issues/102) | `Todo` |

---

## Best Practices & Guidelines

1. **Keep tasks atomic**: Each issue should be completable in 1-3 days or represent a single cohesive pull request.
2. **Follow DDD and Project Rules**: Align labels and scope with the project's architecture (`core`, `app/shared`, `server`).
3. **Avoid duplicate issues**: If issues may already exist, check existing issues in the repo first using MCP `list_issues` or `search_issues`.
