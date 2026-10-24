---
name: myster-plan
description: Write or refine one bounded Myster project's implementation plan without code changes. Use when invoked as $myster-plan or when creating/updating a plan under docs/plans/. Use myster-design for specification-driven epic architecture spanning several projects.
---

# Myster Project Planning

Use this skill to plan one implementation project, either standalone or part of an epic.

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

The single authoritative plan requirement also permits updating related existing plans with
the reciprocal cross-links required below.

## Optional draft checkpoints

Only when needed, write partial drafts to:

- `docs/plans/_drafts/<feature-slug>-YYYYMMDD-HHMMSS.md`

Drafts are optional, partial, and must never be treated as the source of truth.

## Epic context (when applicable)

- For a project from an epic, read its MoSCoW specification, parent design and companion decision
  log in `docs/design/`, convergence report, and relevant earlier implementation summaries. Use
  [myster-design](../myster-design/SKILL.md) when the task is to resolve the whole epic's design.
- Link the specification, parent design, and selected project identifier near the top of the
  plan. Preserve MoSCoW priorities, shared contracts, resolved decisions, and project dependencies.
- Plan the selected slice against current code and actual earlier results. Refresh preliminary
  plans before execution; do not assume later slices can use an outdated implementation plan.
- Resolve or surface inherited questions that block this project. If findings invalidate an
  epic-level decision, record the conflict for design revision rather than silently overriding
  it in implementation details.
- Use the same slug for this plan and `docs/impl_summary/<feature-slug>.md`. Keep the parent
  design's project entry linked to the plan when creating or updating it.

## Cross-linking (REQUIRED)

Plan files are the primary navigation aid for AI agents chunking through the codebase.
Cross-links let an agent find related context without re-searching from scratch.

**Always do this before finalising any new or updated plan:**

1. Scan `docs/plans/`, including subdirectories, for related plans (same ticket prefix, same
   subsystem, predecessor/successor work, shared files or services).
2. For each related plan found, add a block near the top of the current plan, below the title
   and before section 1, using this format:

   > **Related plan:** [Title](./slug.md) — relationship label: one-line description of the relationship.

   Add or update a reciprocal link in the existing plan using the same format and placement,
   pointing back to the current plan. Use paths relative to each file; `./slug.md` assumes
   both plans are in the same directory.
3. Use precise relationship labels: `predecessor`, `follow-up`, `parallel work`, `depends on`,
   `supersedes`, etc. Label the reciprocal link from that plan's perspective (for example,
   `depends on` becomes `required by`). A vague "related" link is less useful than no link.
4. Also link to relevant context documents in `docs/plans/` (for example,
   `async-task-tracker-context.md`) if they exist and bear on the design.

## Behavior rules

- Before drafting or revising a plan, search the entire `docs/` hierarchy recursively for
  documents relevant to the request and affected areas. Read the relevant documents and use
  their requirements, conventions, contracts, and prior decisions to guide the plan. This
  applies to standalone and epic projects alike, including relevant documents outside
  `docs/conventions/` and `docs/design/`.
- Ask clarifying questions when requirements are ambiguous or critical details are missing.
  Write a draft first so progress is not lost, then ask, then update the authoritative plan.
- Use the repository's existing Java package structure, Maven modules, Javadoc, code, and
 `docs/conventions/*` to ground decisions.
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
long-lived APIs. Do not list every class touched. Prose must describe the data flow in plain English.

Table format:

| New / changed thing | Owned / created by | Called / used by | Connects to (existing) |
|---|---|---|---|

Prose must describe the data flow in plain English.

Include any new or changed protocols or file formats here with enough detail that a future
reader can understand the on-wire or on-disk contract.

Column semantics:

"Owned / created by" — which code module, class, or system owns or creates the thing at runtime. NOT the PR or branch. Think: what manages this thing's lifecycle? Examples: "created & closed by FooFactory.init()", "Flink K8s operator via deployment template", "Helm release lifecycle", "standalone (stateless)" for static utilities. For service-level elements, describe how data flows through the service instead.
"Called / used by" — what code or system consumes or invokes this thing.
"Connects to (existing)" — existing codebase elements this thing depends on or interacts with.
You are not restricted to this table. Surface any views a design reviewer needs: service topology, newly introduced couplings, file formats, protocols, etc.

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
