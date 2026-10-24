---
name: myster-design
description: Create or iteratively refine an Epic Technical Design Document and decision log from a MoSCoW specification, with a formal design convergence phase before project planning. Use when invoked as $myster-design or asked to design a Myster epic spanning several plan/implementation pairs. Use myster-plan for one project's implementation plan.
---

# Myster Design

Turn a MoSCoW specification into a coherent technical design for an epic delivered through several projects.
Here, a **project** means one implementation plan and its matching implementation summary.

The workflow is:

`MoSCoW specification -> iterative epic design -> convergence -> (project plan -> implementation + summary) -> repeat`

The specification defines the required behavior and constraints. The epic design explains how
the whole feature will work. Each project plan explains how to implement one bounded slice.

## Relationship to requirements and brainstorming

- `moscow-requirements` owns requirements discovery, the baseline, stable requirement IDs,
  priorities, and observable acceptance outcomes. Consume the document's self-contained design
  handoff; do not repeat discovery or maintain a competing requirements register. If design
  exposes missing product intent, link the existing issue or record a new consequential gap
  for requirements revision. Propose changes without silently rewriting the baseline.
- `brainstorming` supplies useful discovery, alternatives, playback, and fresh-reader review
  techniques. Reuse existing answers and artifacts. For this user-specified Myster workflow,
  apply those techniques within the current stage rather than restarting its full conversation
  or generating a second design. This skill owns epic artifacts, convergence, and the handoff
  to `myster-plan`; it does not use `superpowers:writing-plans` or automatically commit documents.
- Run one combined builder/convergence review, not a separate review loop for each skill.
  The user's explicit convergence rule governs when questions stop: do not keep ending messages
  with exploratory questions once architectural sufficiency is established. Reuse settled
  product decisions; revisit them only for a concrete conflict or changed requirement.
- Requirements readiness, architectural convergence, user approval, and implementation progress
  are separate facts. Preserve their actual states and existing user authorization. None is
  implied merely by another stage finishing, and this skill does not automatically advance stages.

## Invocation and inputs

- Start: `$myster-design <specification document or path>`.
- Continue: `$myster-design docs/design/<epic-slug>.md <feedback or answers>`.
- Read the supplied specification and any existing design before making design decisions.
  When continuing, follow the design's source links. Infer the source only when unambiguous;
  if it is missing or inaccessible, ask for it and keep any useful work explicitly provisional.
- Identify the requirements baseline/revision, readiness, source confirmations, acceptance
  outcomes, dependencies, and outstanding issues. Carry non-blocking assumptions into design;
  a `Draft` baseline can support provisional exploration but its unresolved architecture-changing
  product questions remain blockers. `Ready for design` does not establish design convergence.
- The specification uses MoSCoW: Must Have, Should Have, Could Have, and Won't Have for this
  epic. Preserve its requirement identifiers, lineage, priority assignments, and location.
  Accept a supplied specification without requiring the requirements skill to be installed.
  If IDs or priorities are missing or contradictory, flag that baseline gap rather than silently
  inventing or changing them. Do not renumber or reuse retired IDs.
- Apply this skill to the current design task. Do not implement application code, create
  implementation summaries, or generate project plan files unless the user requests that work.

## Authoritative artifacts

Maintain two linked artifacts, preserving existing paths when continuing:

- Epic Technical Design Document: `docs/design/<epic-slug>.md`.
- Companion decision log: `docs/design/<epic-slug>-decisions.md`.

Keep the design current rather than appending successive drafts or a conversation transcript.
Link the specification and decision log. Clearly distinguish existing behavior, proposed behavior,
and verified implementation results. Track architectural maturity, readiness to plan each project,
and delivery progress separately: a converged architecture can still be entirely unimplemented.

The final convergence report belongs in the design document, not a third required artifact.

## Iterative workflow

1. **Ground the design.** Extract goals, non-goals, requirements, constraints, and epic acceptance
   criteria from the specification. Inspect relevant code, Javadoc, existing designs, plans,
   implementation summaries, and `docs/codebase-structure.md` / `docs/conventions/` as needed.
   Distinguish repository evidence from assumptions. Do not silently alter the specification to
   accommodate a convenient design; record conflicts and propose a resolution.
2. **Write an initial design.** Start with an overall approach and tentative delivery sequence.
   Save a useful draft even when significant questions remain. Describe the complete user and
   system flow before detailing individual components or prematurely fixing project boundaries.
3. **Resolve material questions.** Use code, documentation, and established mechanisms to resolve
   technical unknowns before asking the user. For a consequential choice, compare credible
   approaches and recommend one with rationale. Ask focused questions only where the answer
   changes a named requirement, architectural decision, or concrete blocker. Work through a
   flow, contract, or small diagram when that makes an element's feasibility clearer. Continue
   independent work while answers are pending. Never treat silence as agreement. Record routine
   choices as the author's decisions, not as user-approved choices.
4. **Revise coherently.** Incorporate answers into all affected flows, contracts, decisions,
   acceptance criteria, and candidate projects. Remove superseded contradictions, retaining
   useful decision rationale. Label assumptions and deferred questions explicitly, including
   their impact and when they must be settled. Resume from the existing document on later turns.
5. **Run formal convergence.** Apply the convergence phase below as soon as the important
   architectural elements have credible implementation paths. Stop exploratory questioning
   when its exit criteria are met. Report convergence and project readiness separately;
   convergence does not itself request planning or implementation.

## Design document content

Use these sections as a starting structure, combining or omitting inapplicable details. Write
for a human reviewing the whole epic. Use precise contracts and diagrams where they clarify
the design; leave method bodies and file-by-file implementation instructions to project plans.

### 1. Summary and source

Title the document `Epic Technical Design Document: <epic name>`. Link the specification and
identify its baseline/revision and requirements readiness; link the decision log and relevant
existing designs. State its purpose, approach, architectural maturity
(`Exploring`, `Convergence review`, `Converged`, or `Blocked`), and delivery state. Do not claim
approval that has not been given.

### 2. Scope and requirements

Summarize goals, non-goals, constraints, and success criteria without duplicating the full spec.
Use the requirements handoff's traceability matrix with these exact column labels:

| Requirement | Priority | Design component | Status |
|---|---|---|---|

Include every baselined requirement exactly once as a primary row, including deferred and
excluded requirements. Preserve IDs and priorities. Link all contributing components/design
sections when coverage spans them; naming a component alone does not establish coverage.
Put detailed explanations, project links, and open issues in linked notes rather than a second
competing coverage matrix. Use these shared statuses:

- `Covered`: the design explains how the acceptance outcomes will be met; implementation and
  testing are separate.
- `Needs decision`: an unanswered question or architectural choice prevents coverage.
- `Gap`: absent or incomplete coverage; describe the missing part, including partial coverage.
- `Deferred`: deliberately postponed, with rationale, impact, and any required scope decision.
- `Out of scope`: excluded from this release, normally Won't this time, with a stated boundary.

Replace preliminary `Not designed` statuses during actual design review. Every Must Have needs
a credible path and must be `Covered` before convergence. Never silently downgrade, defer, or
waive one. Confirmed scope changes require a revised requirements baseline and matrix. Account
for every Should/Could explicitly; preserve priority when deferring work. A lower-priority gap
is not automatically a blocker: identify whether it affects an included requirement or shared
contract and record its disposition. Won't-this-time items remain outside this epic.

### 3. Current system and proposed user experience

Explain relevant current behavior and the intended end-to-end user flows. Include meaningful
empty, loading, error, cancellation, recovery, and restart behavior where applicable. Make
changes to existing workflows and platform-specific behavior explicit.

### 4. Architecture and connections

Explain component responsibilities, ownership, boundaries, and data/control flow through the
existing application. Name relevant modules and long-lived APIs without listing every class.
Include a connections table when useful:

| Component / responsibility | Owned by | Used by | Existing connections |
|---|---|---|---|

### 5. Shared contracts and desktop concerns

Define contracts that must stay consistent across projects: domain state and invariants,
persistence and migrations, protocols and formats, compatibility, failures, and recovery.
Include security/privacy boundaries when relevant to the feature.

For desktop work, address applicable UI/background thread ownership, responsiveness,
cancellation and stale results, window/task/resource lifecycles, startup/shutdown, settings,
offline behavior, supported platforms, accessibility, and resource/performance expectations.
Do not add boilerplate for concerns that do not affect this epic.

### 6. Decisions and alternatives

Explain the chosen architecture and link significant choices to their stable decision-log IDs.
Keep alternatives, rationale, and consequences in the log rather than duplicating full records.
Distinguish settled decisions from proposals needing input.

### 7. Risks, assumptions, and open questions

For each material item, capture its impact, proposed resolution or recommendation, and whether
it blocks decomposition or belongs to a particular project. An assumption is not a resolved
requirement. Evidence-dependent risks may require an explicitly bounded feasibility project.

### 8. Validation and epic acceptance

Describe observable epic-level acceptance criteria and how integration across projects will
be checked. Trace requirements to the relevant design sections and proposed projects, using a
compact table when helpful. Include manual desktop verification where automation is inadequate.
Do not invent performance targets or other requirements absent from the specification; propose
them explicitly if needed.

### 9. Proposed project breakdown

For each candidate project, record a stable identifier/slug, intended outcome and boundary,
dependencies, acceptance outcome, and significant deferred decisions. Prefer cohesive slices
that can be verified independently; explain necessary foundations or feasibility work.

Identify the corresponding `docs/plans/<project-slug>.md` and
`docs/impl_summary/<project-slug>.md` pair. Until files exist, label these as proposed paths
rather than presenting them as existing artifacts. Link real artifacts as delivery proceeds.
Keep later projects at outcome/dependency level; do not write all detailed plans up front.

### 10. Design convergence report and next step

Include the five findings below even if a category is `None identified`:

- **Resolved architectural decisions:** decision IDs and the selected approaches.
- **Unresolved decisions that block implementation:** the exact question or obstacle, affected
  requirement/project, and what is needed to resolve it. Distinguish architecture blockers
  from local blockers that leave the architecture stable.
- **Assumptions that need validation:** basis, validation method, owning project, when validation
  is required, and the consequence if false. Plausibility is not validation.
- **Must Have requirements not fully satisfied:** each design gap or partial coverage, its
  delivery impact, and proposed resolution. Distinguish design gaps from work awaiting implementation.
- **Deferred work and its future architectural implications:** scope and priority, reason for
  deferral, future owner/project, and concrete effects on boundaries, formats, compatibility,
  migrations, or sequencing. Do not invent speculative future requirements to fill this category.

State the convergence outcome and whether the first project can be planned. Name its blockers
or next step. A final report may honestly conclude `Blocked`; it must not conceal an unresolved
Must Have or imply that a converged design has already been built.

## Companion decision log

Use stable IDs such as `D-001` for consequential architectural decisions. Each entry records:

- The question/context and the requirement IDs it supports or affects. Cite IDs per decision,
  not just once in an introductory matrix. For engineering constraints without a direct product
  requirement, cite their project source or rationale; do not invent requirement IDs to justify
  a preferred design.
- Status: `Proposed`, `Resolved`, or `Superseded`.
- The selected approach, material alternatives, and rationale.
- Consequences for shared contracts, later projects, and related assumptions or validation.
- The basis for resolution: source requirement, user answer, repository evidence, or an explicit
  author decision within the task's scope. Do not invent user approval.

Update entries as questions resolve. Keep superseded rationale with a link to the replacement
decision; the design document describes the current target architecture. Avoid logging every routine
implementation choice or preserving a chat transcript. Keep both artifacts consistent.

## Formal design convergence phase

### Entry and sufficiency test

Enter convergence when an initial end-to-end design exists and each important architectural
element is described well enough to envisage implementation. For an element, this means:

- Its responsibility, owner, lifecycle, and important interfaces/data flows are explained.
- A plausible mechanism exists, grounded in current code, an established approach, or a
  sufficiently described new component; naming a component alone is not a mechanism.
- Its relevant Must Haves, shared contracts, important failure behavior, and compatibility
  obligations have a credible path.
- Remaining local choices can be settled in a project plan without changing that architecture.

This is conceptual feasibility, not proof from implemented code. Do not require prototypes,
passing tests, exact classes/methods, exhaustive edge cases, or certainty that all risks are
absent. If a specific uncertainty defeats a credible path, record it as a blocker with a
bounded investigation or feasibility project; do not pretend it is resolved.

### Bounded review and classification

Perform one consolidated review as a future planner using the specification, design, and log.
Within this same pass, fulfill the requirements handoff's scope review:

- Check disproportionate complexity against the simplest viable approach; justify components
  or dependencies serving only deferred requirements, or propose deferring them.
- Trace cross-priority dependencies, especially Must/Should paths relying on Could/Won't work.
  Identify the minimum enabling capability or explicit scope decision; do not silently pull
  deferred features into the release or change their priorities.
- Examine concrete expensive deferrals: cite requirement IDs, likely rework/migration costs,
  and any small justified boundary worth preserving. Avoid speculative extension frameworks.

Record findings and their disposition in one scope/dependency review within the design,
including a short reason when no issue is found. Link existing requirement issues and decision
records instead of duplicating their analysis. Update the matrix and decisions after resolutions.

When useful and available, delegate this fresh-reader check to an independent agent: ask only
what missing answers prevent a credible implementation plan, and why a wrong choice matters.
Classify findings before asking the user:

- Already answered by the specification, discussion, or code: incorporate the answer.
- Routine/local choice: decide with rationale or explicitly assign it to its project plan.
- Consequential gap: identify the affected decision/requirement and resolve it through a
  focused comparison, evidence, or a targeted user question.

Consolidate consequential questions rather than feeding an endless sequence of new hypotheticals.
After changes, verify affected decisions, requirement coverage, and document consistency.
Do not restart broad exploration or dispatch repeated open-ended reviews to find more issues.
If a blocker cannot be resolved with available information, report it and the needed next action.

### Exit and stopping rule

Mark the architecture `Converged` when every Must is `Covered` with a credible design path, consequential
architectural decisions are resolved, shared contracts and project boundaries are coherent,
and remaining uncertainties are bounded validation assumptions or local/deferred decisions
with an owner and consequence. Account for all Should/Could rows and the scope-review findings.
Mark `Blocked` if a material design gap prevents this. A deferred/partly covered Must or an
unresolved architecture-changing assumption cannot be hidden by a finalized/converged label.

Then stop exploratory questions and produce the convergence report. Convergence and readiness
are separate: a particular project can still have a concrete implementation blocker. Identify
it without reopening settled architecture. The next project is ready for planning when its
outcome, dependencies, and inherited decisions allow a bounded plan against the current code.

Reopen a resolved decision only for a changed requirement or concrete new evidence that
invalidates it. Name the trigger and affected decision IDs. A newly imagined hypothetical,
optional enhancement, or undecided local detail is not a reason to restart exploration.

## Handoff and feedback during delivery

- When requested, use [myster-plan](../myster-plan/SKILL.md) for one selected project, carrying
  the source specification, parent design, and project identifier into that plan.
- After [myster-impl](../myster-impl/SKILL.md), read the actual summary, validation results,
  deviations, and relevant code before updating project status or planning the next slice.
  A summary file's existence alone does not establish completion.
- Reconcile implementation findings with the target design and later project boundaries.
  Update the decision log when findings change architectural decisions, preserving unimplemented
  epic scope. Apply the convergence stopping rule when revisiting the design.
- Do not declare the epic complete until its acceptance criteria are verified and remaining
  required work is accounted for. Track incomplete or deferred scope explicitly.

Finish each turn by linking both artifacts and stating maturity, material blockers, and next step.
On convergence or a blocked handoff, include a concise version of the five report findings.
Do not paste the full document into chat or append an exploratory question merely to keep talking.
