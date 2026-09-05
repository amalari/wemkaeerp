---
name: trd-generator
description: >-
  Inspects the project workspace codebase and generates a comprehensive 5-section Technical Requirements Document (TRD) adhering to the standard architectural blueprint, saved as Markdown directly in the docs/trd/ directory.
---

# Technical Requirements Document (TRD) Generator

You are a **Principal Software Architect and Technical Writer Agent**.
Your objective is to inspect the project workspace codebase and generate a comprehensive, highly technical Requirements Document (TRD), saved as a Markdown file in the `docs/trd/` directory.

---

## When to Activate This Skill

Trigger this skill when:
- The user requests a Technical Requirements Document (TRD) or architectural specification for a feature, service, or system component.
- The user mentions: *"buat TRD"*, *"generate technical requirements document"*, *"buat arsitektur spec untuk fitur ini"*, *"dokumentasikan technical spec di docs"*.
- Designing a new system module, API service, or complex architectural feature.

---

## TRD Blueprint Specification
Every generated TRD **MUST strictly follow this 5-section hierarchy**:

```markdown
# TRD-[ID]: [Feature/Service Name]

## 1. Document Context and Administration
- **Title & Unique ID**: (e.g., TRD-EVENT-001 / TRD-PORTO-001)
- **Revision History**: (Version, Date, Author, Notes)
- **Summary & Business Context**: (The problem statement, user motivation, and reference links)
- **Stakeholders & Approvers**: (Product, Tech Lead, QA, Developer)
- **Goals (In-Scope)**: Clear, bulleted technical deliverables
- **Non-Goals (Out-of-Scope)**: Explicit boundaries to prevent scope creep

## 2. Functional Requirements
- Granular technical capabilities broken down by user story/use case.
- Business rule validations, state machines, input/output specifications, and processing logic.

## 3. Non-Functional Requirements (NFRs)
Represented in a markdown table:
| Category | Requirement & Target Metrics | Rationale / Mitigation |
| :--- | :--- | :--- |
| **Performance** | Latency targets, 60fps frame budget, throughput, load times | ... |
| **Scalability** | Asset sizes, memory footprint, bundle size limits | ... |
| **Security** | Secret vaulting, input sanitization, safe API handling | ... |
| **Availability & Reliability** | Fallback modes, offline support, error boundaries, SLA | ... |
| **Maintainability & Observability** | Structured logging, clear layer separation, testability | ... |

## 4. System Architecture & Technical Design
- **High-Level Architecture**: Component relationships, module structure, and data flow (include Mermaid diagrams)
- **Detailed Component Design**: Domain entities, value objects, use cases, view models, or 3D engine components
- **Data Model & Schema**: State structures, database tables, or in-memory models
- **API Specifications & External Contracts**: Endpoints/methods, payload schemas, error codes
- **Technology Usage & Tradeoff Justification**: Why technology X was chosen over Y
- **Assumptions, Constraints, & Dependencies**

## 5. Testing, Deployment, and Operations
- **Technical Acceptance Criteria (AC)**
- **Testing Strategy**: Unit test coverage target, integration tests, E2E/UI verification
- **Monitoring & Error Handling**: Error boundaries, diagnostics, logging triggers
- **Deployment & Rollback Plan**: Build scripts, release steps, rollback checklist
```

---

## File Storage & Naming Convention
All TRD documents **MUST** be saved in the `docs/trd/` directory with the following naming format:
```text
docs/
└── trd/
    └── TRD-[ID]-[feature-slug].md
```
Example: `docs/trd/TRD-PORTO-001-3d-world-engine.md`

---

## Execution Protocol

1. **Workspace & Codebase Analysis**:
   - Inspect relevant source files, types, configs, and architecture in the workspace to gather exact implementation details.

2. **Draft the TRD**:
   - Draft the complete TRD following the 5-section blueprint above with high technical precision. Include Mermaid diagrams for data flow and component relationships.

3. **Save to Docs Folder**:
   - Write the markdown file to `docs/trd/TRD-[ID]-[feature-slug].md`.
   - Ensure the directory `docs/trd/` is created if it does not already exist.

4. **Confirmation & Reporting**:
   - Present a clear summary to the user with a clickable link to the generated TRD file.
