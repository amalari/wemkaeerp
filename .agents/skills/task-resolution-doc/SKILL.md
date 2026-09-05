---
name: task-resolution-doc
description: >-
  Generates a structured, educational task resolution document explaining changes made, architectural decisions, code walkthrough, and verification results linked to a specific GitHub Issue, saved as Markdown in the docs/tasks/ directory.
---

# Task Resolution & Learning Summary Generator

You are a **Senior Technical Mentor and Software Engineer Agent**.
Your objective is to document the completion of a development task in a clear, educational, and structured Markdown document linked directly to its relevant GitHub Issue, saved inside `docs/tasks/`.

---

## When to Activate This Skill

Trigger this skill when:
- A task or GitHub Issue implementation is completed.
- The user asks: *"buat ringkasan penyelesaian task ini"*, *"dokumentasikan task issue ini ke docs"*, *"buat summary perubahan untuk issue #X"*, *"jelaskan cara penyelesaian task ini untuk saya pelajari"*.
- After resolving an issue or merging a PR to create an educational audit trail.

---

## Task Resolution Document Blueprint

Every generated resolution summary **MUST follow this structured format**:

```markdown
# Resolution Summary: Issue #[ID] — [Task Title]

- **GitHub Issue**: [#ID](https://github.com/owner/repo/issues/ID)
- **Status**: ✅ Completed
- **Date**: [YYYY-MM-DD]
- **Category/Layer**: [e.g. Core / 3D World / UI / Gameplay / Architecture]

---

## 🎯 1. Problem Statement & Objective
- **What was required**: Summary of the original requirement.
- **Why it matters**: The architectural and user experience value.
- **Acceptance Criteria Checklist**:
  - [x] Criterion 1 (Status & implementation notes)
  - [x] Criterion 2 (Status & implementation notes)

---

## 🧠 2. Architectural & Design Decisions
- **Approach Chosen**: Explanation of the design pattern or technical strategy used.
- **Trade-off Analysis**: Why this solution was chosen over alternatives (e.g. procedural 3D vs heavy GLTF models, Web Audio API vs audio files).
- **Key Concepts / Architecture Flow**: (Include Mermaid diagram if helpful).

---

## 🛠 3. Code Walkthrough & File Changes

Summary of modified and newly created files with direct clickable links:

### New Files
- [`path/to/FileA.ts`](file:///absolute/path/to/FileA.ts): Description of purpose.
- [`path/to/FileB.ts`](file:///absolute/path/to/FileB.ts): Description of purpose.

### Key Code Snippets & Explanations
```typescript
// Highlight of the most critical logic with detailed explanation of how it works
```

---

## 🧪 4. Verification & Testing
- **Test Procedures**: How the changes were tested and validated (unit tests, browser checks, mobile viewport).
- **Results**: Confirmation that no regressions occurred and frame rates/rendering pass criteria.

---

## 🎓 5. Key Learnings & Educational Takeaways
- **Concept 1**: Step-by-step breakdown of how the technique works so the developer can learn and apply it.
- **Concept 2**: Common pitfalls avoided during the implementation.

---

## ⏭ 6. Next Related Tasks
- Next sequential tasks or dependent issues to proceed with.
```

---

## File Storage & Naming Convention
All task resolution documents **MUST** be saved in `docs/tasks/` with the following naming format:
```text
docs/
└── tasks/
    └── issue-[ID]-[task-slug].md
```
Example: `docs/tasks/issue-01-core-threejs-setup.md`

---

## Execution Protocol

1. **Inspect Changes**:
   - Inspect the git diff, modified files, and code changes made for the specific issue.
2. **Draft the Resolution Doc**:
   - Write the detailed resolution summary following the blueprint above. Make sure explanations are intuitive and easy to learn from.
3. **Save to Docs Folder**:
   - Create `docs/tasks/` if not present, and save the file as `docs/tasks/issue-[ID]-[task-slug].md`.
4. **Link & Report**:
   - Present a concise summary in the chat with a direct clickable link to the generated document.
