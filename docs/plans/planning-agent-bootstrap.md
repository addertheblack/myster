# Planning agent bootstrap (meta-plan)

## Problem statement / goal

Add a repo-backed planning agent workflow so that feature work in Myster can be planned once and then implemented deterministically by another agent.

## Non-goals

- Implementing any product feature.
- Setting up an actual cloud runner (GitHub Actions / remote agent infrastructure).

## Assumptions & open questions

- Assumption: You’re using an IDE agent (IntelliJ plugin and/or Eclipse plugin) that can be prompted with an agent spec.
- Open question: Which agent framework you’ll standardize on (JetBrains AI, Copilot Chat, etc.). This plan stays repo-only.

## Proposed design

- Store a single authoritative plan file per feature under `docs/plans/`.
- Store optional draft checkpoints under `docs/plans/_drafts/`.
- Keep the plan structure flexible (no template), but enforce a few required sections to keep plans implementable.

## Affected modules/packages

- Docs only:
  - `docs/agents/*`
  - `docs/plans/*`

## Change list (implementation steps)

1. Create `docs/agents/planning-agent.md` defining:
   - goal, output contract, overwrite semantics
   - “draft checkpoint” conventions
   - specific required plan sections
   - clarifying questions rule

2. Create `docs/plans/README.md` defining:
   - naming conventions
   - overwrite vs drafts
   - required plan sections

3. Ensure `docs/plans/_drafts/` exists and is kept in git (via `.gitkeep`).

4. Add quick pointers in `README.md` so the workflow is discoverable.

## Tests & verification

- N/A (docs-only).
- Smoke check: confirm files render and paths exist.

## Docs/comments to update

- `README.md` (add links to agent spec and plans folder).

## Acceptance criteria

- `docs/agents/planning-agent.md` exists and defines an output contract.
- `docs/plans/README.md` exists and describes plan conventions without a rigid template.
- `docs/plans/_drafts/.gitkeep` exists.
- `README.md` links to the new docs.

