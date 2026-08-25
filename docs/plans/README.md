# Plans

Plans are repo-stored design/implementation write-ups intended to be consumed by another agent (or a human) to make concrete code changes.

## Plan index

When adding or substantially changing an authoritative plan, update this index with a one-line abstract so old work remains discoverable.

| Plan | Abstract |
|---|---|
| [Bidirectional Server Stats Exchange](bidirectional-server-stats.md) | Adds UDP transaction `102` so peers exchange server stats in both directions and learn the requester's advertised Myster port safely. |
| [Domain-Specific CID Types](cid-domain-types.md) | Replaces shared raw `Cid128` API usage with non-interchangeable `MysterTypeCid` and `ServerCid` domain values. |
| [Custom Type Auto-Update](custom-type-auto-update.md) | Makes enable/disable changes for custom and default types immediately update tracker lists, search windows, and tracker UI choices. |
| [Dynamic Port Change Support](dynamic-port-change.md) | Lets the configured server port change from preferences without restart by rotating server operators and related announcements. |
| [File Metadata Cache for Expensive Media Parsing](file-metadata-cache.md) | Adds an injected file-backed metadata cache so expensive type-specific parsing, starting with MP3/Tika metadata, is reused across indexing runs. |
| [MP3 Length Column in GUI](mp3-length-column.md) | Displays MP3 duration from `/LengthSec` as a client-side sortable "Length" column without server or protocol changes. |
| [Myster 3DNS - Part 1a: Core Data Structures](myster-3dns-part-1a.md) | Builds the local 3DNS foundations: CID ordering, closest-candidate iteration, pool lookup APIs, and retained target-slot storage. |
| [Myster 3DNS - Part 1b: Tracker UI Integration](myster-3dns-part-1b.md) | Exposes the initial 3DNS retained-server list in `TrackerWindow` for developer inspection. |
| [Myster 3DNS - Part 2a: FIND_CLOSEST Protocol and Expected-Key Hook](myster-3dns-part-2a.md) | Adds the `FIND_CLOSEST` UDP transaction and expected-key transport hook needed for secure candidate probing. |
| [Myster 3DNS - Part 2b: Candidate Identity Verification](myster-3dns-part-2b.md) | Wraps `FIND_CLOSEST` candidate queries with authenticated identity proof before accepting returned peer information. |
| [Myster 3DNS - Part 3: Iterative CID Resolution](myster-3dns-part-3.md) | Implements bounded asynchronous multi-hop CID resolution using verified 3DNS peers and explicit no-route/closest results. |
| [Myster 3DNS - Part 4: Routing-Table Maintenance and Bootstrap](myster-3dns-part-4.md) | Bootstraps and periodically repairs the tracker-owned 3DNS target table using secure verified-peer onboarding. |
| [Make Myster Modules Compatible](myster-modules-compatible.md) | Plans staged Java Platform Module System compatibility, including artifact cleanup and `jpackage`/`jlink` packaging constraints. |
| [Planning agent bootstrap (meta-plan)](planning-agent-bootstrap.md) | Establishes the repo-backed planning workflow, authoritative plan location, draft convention, and documentation links. |
| [Private Types Access Lists - Part 1](private-types-access-lists.md) | Introduces signed append-only access lists as the canonical metadata, policy, key, and membership source for public and private types. |
| [Private Types Access Lists - Milestone 2: GUI & Type Lifecycle](private-types-access-lists-milestone2.md) | Makes access lists the authoritative store for custom type metadata and wires creation/editing flows into the type manager UI. |
| [Private Types - Milestone 3: Type Metadata Resolution & Import](private-types-access-lists-milestone3.md) | Fetches remote type access lists to show transient names for unknown types and support right-click permanent type import. |
| [Private Types Access Lists - Milestone 4: Member Management GUI](private-types-access-lists-milestone4.md) | Adds private-type member management UI and server picking for admins before enforcement is enabled. |
| [Private Types Access Lists - Milestone 5: Access Enforcement](private-types-access-lists-milestone5.md) | Enforces private-type membership checks across TCP file-serving paths and the UDP type lister. |
| [Private Types Access Lists - Milestone 6: Client-Only Node Join Requests](private-types-access-lists-milestone6.md) | Defers client-only private-type join requests until a broader peer-to-peer messaging design exists. |
| [Public/Private Data Path Separation](public-private-data-paths.md) | Splits user-visible content paths from private application-managed paths for keys, temp files, and internal data. |
| [Replace `mp3agic` with Apache Tika for MPG3 metadata](replace-mp3agic-with-tika.md) | Swaps MP3 metadata extraction to Apache Tika while preserving existing `MessagePak` protocol keys and removing `mp3agic`. |
| [Rolling Overall Download Rate](rolling-download-rate.md) | Replaces the download manager parent row's queue-skewed lifetime average with an approximately one-second rolling transfer rate. |
| [Search Download To Shared Folder](search-download-to-shared-folder.md) | Changes multi-selected search downloads to choose one destination folder before scheduling all selected downloads. |
| [Split Type Columns from Search-Only Columns](split-type-columns-from-search-columns.md) | Separates per-type file columns from search-only server/ping columns so client browsing can reuse type-specific metadata columns. |
| [Tabbed Search Window](tabbed-search-window.md) | Evolves search into multi-tab search windows with independent search state and multiple window support. |
| [Tracker 3DNS Target Slots UI](tracker-3dns-target-slots-ui.md) | Replaces the deduplicated 3DNS tracker list with a dedicated target-slot inspection table. |
| [Type Choice - Public/Private Grouping & File Manager Warning](type-choice-public-private-grouping.md) | Groups `TypeChoice` entries by public/private status and warns when file-manager settings affect a public type. |
| [User Configured Myster Types](user-configured-myster-types.md) | Adds a full preferences UI and persistence model for creating, editing, enabling, disabling, and deleting custom Myster types. |

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
