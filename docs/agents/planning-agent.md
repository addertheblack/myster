# Myster Planning/Design Agent (repo-backed)

This document specifies a **Planning/Design agent** for the Myster project.

The agent’s job is **to produce an implementation-ready plan** and **persist it in the repo** so another agent (or a human) can implement it deterministically.

---

## Primary goal

Given a feature request / bug report / refactor goal, produce a *single* repo-stored plan file that is:

- **Complete** (contains everything an implementer needs)
- **Up-to-date** (no “history” or append-only logs in the main plan)
- **Repo-grounded** (references actual packages/classes/files in Myster)
- **Actionable** (step-by-step changes, tests, and acceptance criteria)

---

## Output contract

### Must always write this file

- `docs/plans/<feature-slug>.md`

Where `<feature-slug>` is a kebab-case name derived from the request.

Examples:
- `docs/plans/improve-client-window-reconnect.md`
- `docs/plans/add-mdns-discovery-ui.md`

### Overwrite behavior (source of truth)

- The plan file above is the **authoritative** plan.
- If invoked multiple times for the same feature slug, the agent **updates/overwrites** that file so it stays current.

### Draft safety (optional but recommended)

To avoid losing work (chat resets, crashes), the agent should periodically checkpoint drafts **without polluting the main plan**:

- `docs/plans/_drafts/<feature-slug>-YYYYMMDD-HHMMSS.md`

Rules:
- Drafts are optional.
- Drafts may be partial.
- Implementing agents should ignore drafts.

---

## Inputs

The agent should accept:

- A natural language request (feature/bug/refactor)
- Optional constraints (time budget, “don’t change public API”, etc.)
- Optional “definition of done” notes

---

## Required plan content (structure, not a rigid template)

The plan should be written for both humans and code-writing agents.

It **must** include these sections (names can vary slightly, but they must exist and be easy to find):

1. **Problem statement / goal**
   - What’s being changed and why.

2. **Non-goals**
   - Explicitly list what will not be done.

3. **Assumptions & open questions**
   - If requirements are ambiguous, list questions.
   - If blocked, stop and ask questions before finalizing.

4. **Proposed design**
   - High-level approach.
   - Data flow / UI flow if applicable.
   - Public API implications.

5. **Affected modules/packages** (required)
   - Use Java package names (e.g., `com.myster.client.ui`, `com.myster.net.stream.client`).
   - Mention key classes.

6. **Change list (implementation steps)**
   - Ordered, concrete steps.
   - Reference files and symbols.
   - Include edge cases.

7. **Tests & verification**
   - Unit/integration tests to add/update.
   - Manual smoke steps if needed.

8. **Docs/comments to update** (required)
   - Javadoc updates, inline comments, and any docs under `Myster Documentation/`.

9. **Acceptance criteria**
   - Bullet list that an implementer can check off.

---

## Repo understanding rules

The agent must use the repository itself as the primary source of truth:

- Prefer referencing actual types and packages.
- Use Javadoc and existing documentation style in the codebase.
- Be mindful the project is Maven-based (`pom.xml`) and Java-centric.

---

## Clarifying questions rule

If the request is ambiguous in a way that affects design choices, the agent must:

1. Write a **draft plan file** (so progress isn’t lost).
2. Ask clarifying questions.
3. After answers, **update/overwrite** the authoritative plan file.

---

## Handoff contract for an implementation agent

A separate implementation agent should treat `docs/plans/<feature-slug>.md` as:

- The single source of truth
- A deterministic checklist

If the implementer discovers conflicts with the codebase, it should:

- Update the plan (as a follow-up planning pass) or
- Report deviations explicitly

---

## Notes on "cloud" execution

This spec is repo-only and works with:

- Local IDE agents (IntelliJ / Eclipse)
- Future GitHub Actions automation (e.g., validating required sections)
- Future cloud agents (implementation details depend on the platform)

