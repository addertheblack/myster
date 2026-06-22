---
name: myster-plan
description: Myster planning workflow. Use when invoked as $myster-plan, when the user asks to plan/design Myster work before implementation, or when creating/updating docs/plans/<feature-slug>.md without code changes.
---

# Myster Planning/Design

Use this skill as the task-scoped Planning/Design workflow for the Myster project.

## Invocation

- Explicit use: `$myster-plan <feature request or plan path>`
- This is not a sticky mode. Apply it to the current task, then stop after the plan is written
  and verified against the request.
- If the user asks to continue into implementation, require a separate `$myster-impl` turn or
  an explicit instruction to leave planning mode.

## Goal

Produce a single, up-to-date implementation plan that a separate implementation agent can
execute with minimal back-and-forth, while remaining reviewable by a human who cannot read
every file changed.

## Primary output

Write exactly one authoritative plan file to:

- `docs/plans/<feature-slug>.md`

The plan file must be complete and current. If it already exists, overwrite/update it rather
than appending history. Use a short kebab-case slug derived from the feature name.

## Optional draft checkpoints

Only when needed, write partial drafts to:

- `docs/plans/_drafts/<feature-slug>-YYYYMMDD-HHMMSS.md`

Drafts are optional, partial, and must never be treated as the source of truth.

## Behavior rules

- Ask clarifying questions when requirements are ambiguous or critical details are missing.
  Write a draft first so progress is not lost, then ask, then update the authoritative plan.
- Use the repository's existing Java package structure, Maven modules, Javadoc, and code to
  ground decisions.
- Do not implement code changes. Only design and plan.
- Do not run broad test suites unless needed to understand the existing behavior; planning may
  inspect tests, compile commands, or errors as context, but should not make code edits.
- Note any codebase-specific surprises in `docs/conventions/*`.
- Final response should name the authoritative plan file and summarize unresolved questions or
  important risks. Do not include a long copy of the plan in chat.

## Required plan structure

Every plan file serves two audiences:

- The owner/reviewer, who needs architecture, connections, decisions, and edge cases at a
  high level.
- The implementation agent, who needs exact file paths, symbols, ordered steps, and tests.

Use one file with two clearly labelled halves. The implementation half must begin with:

```md
---
## ✦ IMPLEMENTATION DETAILS (for the implementation agent)
---
```

The owner reads the Design Section and Acceptance Criteria only. The implementation agent
reads the entire file.

## Design Section

The design section must be human-reviewable. Do not include code snippets, method bodies, or
file-level checklists. Use prose and bullet points.

### 1. Summary

What is being changed and why. One paragraph max.

### 2. Non-goals

Explicitly list what will not be done in this milestone.

### 3. Assumptions & open questions

Ambiguities that affect design choices. If blocked, stop and ask before finalising.

### 4. Proposed design

- High-level approach.
- Data flow or UI flow if applicable.
- Key decisions made and why.

### 5. Architecture connections

This is the most important section for human review. Explain how the new code plugs into the
existing codebase using narrative plus a connections table. Focus on relationships that matter
for understanding the design: new protocols, file formats, package-level structure, and
long-lived APIs. Do not list every class touched.

Table format:

| New / changed thing | Owned / created by | Called / used by | Connects to (existing) |
|---|---|---|---|

Prose must describe the data flow in plain English.

Include any new or changed protocols or file formats here with enough detail that a future
reader can understand the on-wire or on-disk contract.

### 6. Key decisions & edge cases

Only decisions that affect architecture or long-lived API. Include edge cases that affect the
design, not implementation detail.

### 7. Acceptance criteria

Behavioural, user-visible, or system-level outcomes. Write as checkboxes. Focus on what done
looks like, not on class or method details.

## ✦ IMPLEMENTATION DETAILS (for the implementation agent)

This section is not normally reviewed. Do not bury architecture-level concerns here.

### 8. Affected files / classes

Flat list: package, class, what changes in one line each. Mark new files clearly.

### 9. Step-by-step implementation

Ordered, concrete steps. Include exact symbols and file paths. Include code sketches where
useful and implementation-level edge cases.

### 10. Tests to write

Unit and integration tests with what each verifies. Include manual smoke steps if needed.

### 11. Docs / Javadoc to update

Specific classes or methods needing Javadoc or comment updates.
