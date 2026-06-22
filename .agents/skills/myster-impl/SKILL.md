---
name: myster-impl
description: Myster implementation workflow. Use when invoked as $myster-impl or when the user asks to execute an existing docs/plans/<feature-slug>.md plan, change code/Javadoc/design docs, run tests, and write docs/impl_summary/<feature-slug>.md.
---

# Myster Implementation

Use this skill as the task-scoped Implementation workflow for the Myster project.

## Invocation

- Explicit use: `$myster-impl docs/plans/<feature-slug>.md`
- This is not a sticky mode. Apply it to the current implementation task.
- If the user has not named a plan file, infer one only when the request unambiguously points to
  a single `docs/plans/<feature-slug>.md`; otherwise ask for the path.
- If the user asks only for review or planning, do not use this implementation workflow.

## Primary goal

Given a plan file in `docs/plans/<feature-slug>.md`, execute it deterministically:

- Make code changes as specified in the plan.
- Update Javadoc for all modified classes and methods when the Javadoc contract changes.
- Update design docs in `docs/design/` if they become stale.
- Run tests and verify acceptance criteria.
- Write a summary to `docs/impl_summary/<feature-slug>.md`.

## Prerequisite

The plan file must exist before starting.

- Plan file location: `docs/plans/<feature-slug>.md`
- If no plan is specified or the file does not exist, stop and ask the user for the plan file
  path.
- If the plan appears stale or conflicts with the current code, stop and report the mismatch
  before making broad changes.

## Context sources

Use these sources when relevant:

1. Package/class Javadoc for module contracts and public APIs.
2. Existing code for established patterns and conventions.
3. `docs/design/*.md` for high-level system design. These are living documents and should be
   updated if changes make them stale.
4. `docs/codebase-structure.md` for codebase structure.
5. `docs/conventions/Code Comments.md` for Javadoc rules.
6. `docs/conventions/myster-coding-conventions.md` for Myster coding conventions.
7. `docs/conventions/myster-important-patterns.md` for important patterns and architectural
   styles.

## Implementation workflow

### 1. Read and validate the plan

Plans follow a two-section structure:

- Design Section, sections 1-7: architecture, connections, decisions, and acceptance criteria.
  Read this first and fully before touching any code.
- Implementation Section, sections 8-11 below the `✦ IMPLEMENTATION DETAILS` divider:
  affected files, step-by-step changes, tests, and Javadoc list.

Confirm the plan file exists and is complete. Understand affected modules from the Architecture
Connections section. Note files to change and docs to update.

### 2. Make code changes

- Follow the step-by-step implementation plan in order.
- Use existing code patterns and conventions.
- Maintain consistency with the rest of the codebase.

### 3. Update Javadoc

As code changes, update Javadoc to reflect new behaviour. Follow
`docs/conventions/Code Comments.md`:

- Document the method contract: what it does, not how.
- Document implicit types such as UTC timestamp as `long`.
- Document magic values, including nulls with special meaning and sentinel values.
- Document edge cases and error conditions.
- Use `@param`, `@return`, `@throws`, and `@see` where they add value.
- Do not add trivial Javadoc. Add real value or leave it out.
- Keep inline comments non-obvious and useful. When in doubt, leave them out.

### 4. Update design docs

- Review `docs/design/*.md` for modules being changed.
- If a design doc describes the changed subsystem and becomes outdated, update it.
- Design docs are living documents. They reflect current state, not history.

### 5. Run tests and verify

- Run existing tests and ensure they pass.
- Add new tests as specified in the plan.
- Document test failures in the summary if they cannot be fixed immediately.

### 6. Write implementation summary

Create `docs/impl_summary/<feature-slug>.md` with:

- What was implemented, as a short tight summary.
- Files changed.
- Key design decisions made during implementation.
- Deviations from the plan, with rationale.
- Javadoc or design docs updated that need a manual double-check.
- Known issues or follow-up work.
- Tests that should be added later.
- Anything the plan author or maintainer should know.

## Additional outputs

When you encounter a coding style, convention, preferred library, or other project-specific
preferred way of doing things, document it in `docs/conventions/myster-coding-conventions.md`
or `docs/conventions/myster-important-patterns.md` for future AI agents.

## Final checklist

Before writing the summary, verify:

- [ ] All code changes from the plan are implemented.
- [ ] Modified classes/methods have updated Javadoc when non-trivial contract details changed.
- [ ] Design docs were reviewed and updated if needed.
- [ ] Tests pass, or failures are documented.
- [ ] Inline comments are not obvious or trivial.
- [ ] Implementation is consistent with `docs/conventions/*`.
- [ ] Implementation summary is written to `docs/impl_summary/<feature-slug>.md`.

## Related

- Planning skill: `.agents/skills/myster-plan/SKILL.md`
- Plans: `docs/plans/README.md`
- Implementation summaries: `docs/impl_summary/README.md`
- Javadoc conventions: `docs/conventions/Code Comments.md`
- Design docs: `docs/design/`
