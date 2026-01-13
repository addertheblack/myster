# Plans

Plans are repo-stored design/implementation write-ups intended to be consumed by another agent (or a human) to make concrete code changes.

## Where plans live

- Authoritative plan (always): `docs/plans/<feature-slug>.md`
- Optional draft checkpoints: `docs/plans/_drafts/<feature-slug>-YYYYMMDD-HHMMSS.md`

## Naming

Use a kebab-case feature slug.

Examples:
- `add-mdns-discovery-ui`
- `client-window-remember-type`

If tickets are adopted later, prefixing is allowed but optional:
- `MYS-123-add-mdns-discovery-ui`

## Update policy

- The authoritative plan file should be **complete and current**.
- Prefer overwriting/updating it rather than appending history.
- Drafts may be created frequently for safety, but implementers should ignore them.

## Required content

Plans should be tailored to the change (no rigid template), but they must include at least:

- Problem statement / goal
- Non-goals
- Assumptions & open questions
- Proposed design
- Affected modules/packages
- Change list (implementation steps)
- Tests & verification
- Docs/comments to update
- Acceptance criteria

## Related

- Planning agent spec: `docs/agents/planning-agent.md`

