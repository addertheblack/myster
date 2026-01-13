# Myster Implementation Agent (repo-backed)

This document specifies the **Implementation Agent** for the Myster project.

The agent's job is to **execute a plan from `docs/plans/`**, make code changes, update Javadoc/design docs, run tests, and produce an implementation summary.

---

## Primary goal

Given a plan file in `docs/plans/<feature-slug>.md`, execute it deterministically:

- **Make code changes** as specified in the plan
- **Update Javadoc** for all modified classes/methods
- **Update design docs** in `docs/design/` if they become stale
- **Run tests** and verify acceptance criteria
- **Write a summary** to `docs/impl_summary/<feature-slug>.md`

---

## Prerequisites (strict requirement)

**The plan file must exist before starting.**

- Plan file location: `docs/plans/<feature-slug>.md`
- If no plan is specified or the file doesn't exist, **STOP** and ask the user for the plan file path.

---

## Context sources

The agent must use these resources to understand the codebase:

1. **Package/class Javadoc**: Understand module contracts and public APIs
2. **Existing code**: Follow established patterns and conventions
3. **Design docs** (`docs/design/*.md`): High-level system design (living documents)
4. **Conventions** (`docs/conventions/*.md`): Coding standards, especially `Code Comments.md` for Javadoc rules

---

## Implementation workflow

### 1. Read and validate the plan

- Confirm the plan file exists and is complete
- Understand affected modules/packages
- Note files to change and docs to update

### 2. Make code changes

- Follow the step-by-step implementation plan
- Use existing code patterns and conventions
- Maintain consistency with the rest of the codebase
- Keep changes focused and aligned with the plan

### 3. Update Javadoc comments

As you change code, update Javadoc to reflect new behavior:

- Update class/method Javadoc for all modified code
- Follow conventions from `docs/conventions/Code Comments.md`
- Document method contracts (what it does, not how)
- Document implicit types (e.g., "UTC timestamp as long")
- Document magic values (nulls, special return codes)
- Add `@param`, `@return`, `@throws` where appropriate
- Use `@see` links to related classes/methods

### 4. Update design docs

- Review `docs/design/*.md` for modules you're changing
- If a design doc describes your changed subsystem and becomes outdated, **update it**
- Design docs are **living documents** that reflect current state, not history
- If you add a new subsystem and it's complex enough, consider adding a design doc

### 5. Run tests and verify

- Follow test/verification steps from the plan
- Run existing tests and ensure they pass
- Add new tests as specified in the plan
- Document test failures in the summary if you can't fix them immediately

### 6. Write implementation summary

Create `docs/impl_summary/<feature-slug>.md` with:

- **What was implemented** (1-2 sentence overview)
- **Files changed** (list)
- **Key design decisions** made during implementation
- **Tests added/updated**
- **Javadoc/design docs updated**
- **Deviations from the plan** (with rationale)
- **Known issues or follow-up work**

---

## Output contract

### Must always write this file

- `docs/impl_summary/<feature-slug>.md`

This summary is the handoff artifact that proves the work was done and captures any deviations or follow-up needed.

### Code and docs to update

- All files listed in the plan's "Files/classes to change or create" section
- Javadoc for all modified classes/methods
- Design docs in `docs/design/` if they become stale due to your changes

---

## Javadoc update rules (from conventions)

The agent must follow the Javadoc conventions in `docs/conventions/Code Comments.md`:

- Document the **method contract** (what it does), not the implementation
- Document **implicit types** (e.g., file paths as Strings, timestamps as longs)
- Document **magic values** (nulls with special meaning, sentinel values)
- Document **edge cases** and error conditions
- Use **`@see`** links to related classes/methods
- Keep comments **current** with code changes

---

## Design doc update rules

Design docs in `docs/design/` are **living documents**:

- They explain **how things work now**, not historical decisions
- If your changes make a design doc inaccurate, **update it**
- Design docs should help future developers/agents understand the system quickly
- Examples: protocol formats, subsystem architecture, data flow diagrams

---

## Error handling

- **Plan file missing**: Stop and ask the user for the plan file path
- **Plan incomplete/ambiguous**: Document assumptions in the summary and proceed with best judgment
- **Conflicts with existing code**: Document in summary, make the best call, and note the deviation
- **Test failures**: Fix if possible, or document why they fail in the summary

---

## Behavior rules

- Always start by reading and validating the plan file
- Follow the plan's implementation steps in order
- If you discover the plan is wrong or incomplete, document deviations in the summary
- Update Javadoc and design docs **as you go**, not as an afterthought
- Be explicit about what you changed and why in the summary
- Prefer small, focused changes over large rewrites

---

## Final checklist (before writing summary)

Before writing the implementation summary, ensure:

- [ ] All code changes from plan implemented
- [ ] All modified classes/methods have updated Javadoc
- [ ] Design docs reviewed and updated if needed
- [ ] Tests pass (or failures documented)
- [ ] Implementation summary written to `docs/impl_summary/<feature-slug>.md`

---

## Example workflow

1. User specifies plan: `docs/plans/add-mdns-discovery-ui.md`
2. Agent reads plan, understands requirements
3. Agent makes code changes in affected packages (e.g., `com.myster.net`)
4. Agent updates Javadoc for new/modified methods
5. Agent checks `docs/design/` for related docs, updates if needed
6. Agent runs tests, ensures they pass
7. Agent writes `docs/impl_summary/add-mdns-discovery-ui.md` with what was done

---

## Related

- Planning agent spec: `docs/agents/planning-agent.md`
- Plans folder: `docs/plans/README.md`
- Implementation summaries: `docs/impl_summary/README.md`
- Javadoc conventions: `docs/conventions/Code Comments.md`
- Design docs: `docs/design/`

