# Myster

Myster is a fully distributed, old-school P2P application (originally started around 1999).
It scales via **virtual overlay networks** that segment the broader network by interest, so searches stay within relevant sub-networks instead of flooding everyone.

## Prerequisites

- Java (use the version specified in `pom.xml`)
- Maven

## Build & test

```bash
mvn clean test
```

To build artifacts:

```bash
mvn package
```

The authoritative build configuration and targets are in `pom.xml`.

## Run

Typical ways to run Myster:

- **From an IDE**: import the project as Maven and run the appropriate main class/run configuration.
- **From the command line**: after `mvn package`, run the produced jar from the relevant `target/` directory (if an executable jar is produced).

## Documentation

- Design docs (living docs describing the current implementation): `docs/design/`
- Coding conventions (including Javadoc guidance): `docs/conventions/`

## Agent-driven development workflow

Myster uses a two-agent workflow for planning and implementing features:

1. **Planning Agent** (`.github/agents/myster-plan-agent.md`)
   - Produces implementation plans in `docs/plans/<feature-slug>.md`
   - Spec: `docs/agents/planning-agent.md`

2. **Implementation Agent** (`.github/agents/myster-impl-agent.md`)
   - Executes plans, updates code/Javadoc/design docs
   - Writes summaries to `docs/impl_summary/<feature-slug>.md`
   - Spec: `docs/agents/implementation-agent.md`

Related:

- Plans: `docs/plans/README.md`
- Implementation summaries: `docs/impl_summary/README.md`

## Background

This repository is here to keep the project accessible for anyone curious or interested in working on it.
In theory, Myster can also be used as a foundation for building your own P2P network by defining interest-based overlay networks and layering access control on top.
