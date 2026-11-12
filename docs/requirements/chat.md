# Myster chat — requirements specification

**Revision:** 0.4 — 11 November 2026  
**Readiness:** Ready with assumptions for epic design; not an approved or scheduled release baseline.  
**Scope:** Persistent hosted chat rooms with text and live voice/video, plus prioritized extensions.

## 1. Intent and sources

Make a Myster server a place for ongoing text conversations and live voice/video.
The server's existing identity identifies the community. Participants use their
Myster node identities. A room is a conversation location supporting text and one
live audio/video conversation; it is not a separate text product with unrelated
voice rooms. Rooms can be organized into nested chat groups of their own, entirely
separate from shared-file folders.

Text is asynchronous and persistent. People can return, find where they left off,
and continue. Live participants are visible. The core experience uses shared rooms,
which are public unless an applicable permission rule restricts them. Private
one-to-one and ad hoc group conversations are separate Should-haves.

This specification defines product outcomes, not architecture or implementation
steps. Jabber and Slack comparisons describe behavior; they do not mandate XMPP,
interoperability with those products, or their entire feature sets.

### Sources and authority

- **U1:** Maintainer's initial dictated specification, 10 November 2026.
- **U2:** Maintainer's Q-001 clarification, 11 November 2026: inherited whitelists,
  individual grants, people-groups, roles, and Myster-wide access harmonization.
- **U3:** Maintainer's answers to Q-002–013, 11 November 2026: chat moderation versus
  server administration; unified rooms; private conversations as Shoulds;
  notifications, deletion, content, search, read state, scheduling, and media controls.
- **U4:** Follow-up clarification, 11 November 2026: replies have **one level only**.
  Opening a thread as a separate conversation does not create another reply level.
- **U5:** REQ-021 confirmation, 11 November 2026: Unicode support with UTF-8 as the
  standard encoding is a Must.
- **H1:** [Voice-channel design exploration](../design/Myster_WebRTC_Voice_Design_Exploration.docx),
  4 October 2026. Historical voice, transport, WebRTC, and Swing discussion.
- **H2:** [WebRTC calling phase index](../plans/webrtc-video-call.md) and
  [basic calling plan](../plans/webrtc-phase-4-basic-calling.md). A separate
  one-to-one calling effort, not delivery of hosted community rooms.
- **E1:** [Resource-link design](../design/Myster%20Resource%20Links.md) and
  [implementation summary](../impl_summary/myster-resource-links.md). Server-only,
  type, and file destinations already exist; chat destinations are additional work.
- **E2:** [Codebase structure](../codebase-structure.md), including existing identities,
  discovery, types, and file access control.
- **E3:** [Deferred private-type join requests](../plans/private-types-access-lists-milestone6.md).
  Related messaging context, not an added approval-inbox requirement.
- **E4:** Existing [access-list roles](../../src/main/java/com/myster/access/Role.java)
  and [type-access policy](../../src/main/java/com/myster/access/Policy.java).
  MEMBER/ADMIN and public/member file access are foundations to investigate;
  they do not establish a general people-group/privilege-bundle system across Myster.

Later user clarifications supersede earlier wording. Historical design and library
choices are inputs for epic design, not fresh feasibility claims. In particular,
H1's voice-only boundary and H2's exclusion of community rooms do not override the
current requested voice/video scope. Existing Myster identity and federation
capabilities remain existing context; the optional new outcome is cross-host chat.

## 2. Vocabulary, success, and priorities

| Term | Meaning |
|---|---|
| Host / community server | The Myster server hosting these conversations. Its identity is the community identity. |
| Participant / member | A person represented initially by one existing server identity, called SID in U3. No separate chat account system is requested. |
| Chat group | An organizational container for rooms or further chat groups. “Folder” in earlier drafts was an analogy, not a shared filesystem folder. Final UI naming is undecided. |
| Chat room / area | A persistent conversation location with text and an attached live voice/video conversation. |
| Private conversation | Optional persistent one-to-one or ad hoc group messaging within a host; not a global inbox. |
| Reply thread | A sub-conversation attached to a top-level message. Messages can be added within it, but cannot spawn replies-to-replies. |
| Owner | The host operator. Server administration is distinct from chat moderation. |
| Moderator / chat admin | Someone with admin permission in the relevant chat scope. This does not confer server administration or authority to delegate server powers. |
| Permission rule | A whitelist at a chat group/room scope, listing identities and their read, write/post, and optional chat-admin grants. |
| Permission group | A collection of identities that can receive grants together; distinct from a chat group and from a role. |
| Role | A reusable combination of privileges, not a collection of people. |

Success for the core room experience means authorized members can exchange and
retrieve text, resume with host-maintained unread state, search a conversation,
and use the room's live voice/video with a visible participant list. The owner can
control access. Private conversations, richer media conveniences, and cross-host
chat do not have to precede these outcomes.

**MoSCoW priorities express relative importance, not delivery phases (U3).** Design
may include an easy Should alongside a related Must, or split large work into
separate projects. A Must that cannot be delivered requires an explicit scope or
priority renegotiation. No priority is silently changed to fit implementation cost.
Could means optional value, not a prohibition; Won't this time is an explicit
exclusion. No fixed phase-one/phase-two/phase-three mapping is implied.

Voice/video remains a Must as requested. U3 warns that video may be large enough
for its own feature/project; any reduced release scope must be agreed during design.
No room capacity, latency, media quality, attachment size, or storage-capacity
numbers have been supplied. Zero-to-n live participants describes the room model,
not unlimited supported capacity.

**Explicit** means the user assigned the priority. **Proposed** means the outcome
was requested but its priority remains an author proposal. Rows default to U1;
U2/U3/U4/U5 markers identify revisions or additions. Stable requirement IDs are never
renumbered or reused. Dependencies identify product prerequisites, not work order.
Acceptance evidence below describes observable behavior, not completed testing.

## 3. Requirements

### 3.1 Identity, access, and administration

Initial rules name individual identities. Read, write/post, and chat-admin grants
are distinguishable. People-groups and role bundles are Shoulds, not prerequisites
for those individual grants. Moderation applies to chat; broader server authority
is a separate distant Could.

| ID | Priority / source state | Outcome and acceptance evidence | Dependencies | Issues |
|---|---|---|---|---|
| REQ-001 | Must — Proposed | **Server is community identity.** Opening a community uses the host's existing identity; no separate community identity creation is required. | None | A-001 |
| REQ-002 | Must — Proposed | **Existing participant identities.** Grants and message authorship identify members by their Myster node identities, not separate chat accounts. | REQ-001 | A-001 |
| REQ-003 | Could — Explicit | **Multiple community identities.** A host may later offer more than its single community identity. Demonstrate creating/selecting distinct identities if included. | REQ-001 | Q-011 |
| REQ-004 | Must — Proposed | **Separate read/post and file access.** A member can read without posting; unauthorized reads/posts fail. A file grant alone supplies no chat grant. | REQ-002, REQ-011 | Q-001 |
| REQ-005 | Must — Explicit; U2/U3 | **Public default and inherited whitelists.** Use the closest rule at or above the target chat group/room. It replaces the parent's complete whitelist/grants. With no applicable rule, access is public. Demonstrate the inheritance examples below. | REQ-004, REQ-011 | Q-001, Q-013 |
| REQ-006 | Must — Explicit; U2 | **Manage individuals and access.** The owner can add known identities and grant/revoke scoped chat access, including a root-level grant. Removing an identity from the effective whitelist prevents subsequent access under that rule. | REQ-002, REQ-005, REQ-008 | Q-001 |
| REQ-007 | Must — Proposed priority; U2 | **Individual grants.** A rule can give two identities different read, write/post, and optional chat-admin privileges. No configurable people-group or role system is needed initially. | REQ-004 | Q-002 |
| REQ-008 | Must — Explicit; U3 clarification | **Owner chat authority.** The owner can control chat access and act as the room moderator. Ordinary participants cannot perform actions requiring chat-admin permission. This does not require additional server administrators. | REQ-001, REQ-002 | Q-002 |
| REQ-009 | Should — Explicit; U3 scope correction | **Scoped chat moderators.** Granting chat-admin permission lets a person moderate that chat scope. It confers no server administration, room removal, or automatic right to appoint further administrators. The owner can moderate if this feature is deferred. | REQ-005, REQ-007, REQ-008 | Q-002 |
| REQ-044 | Should — Explicit; U2/U3 | **People-groups.** A named collection of identities can receive a grant together. Demonstrate a “vintage machines” group receiving different grants in different areas, with members receiving the area-specific grant. Membership does not define a fixed privilege bundle. | REQ-002, REQ-005, REQ-007 | None |
| REQ-045 | Should — Explicit; U2/U3 | **Reusable roles.** A named combination of privileges can be reused for multiple individuals without redefining the bundle. A role is not a collection of people. | REQ-007 | None |
| REQ-046 | Should — Explicit; U2 | **Myster-wide access harmonization.** People-groups, roles, and grant concepts should apply across access-controlled resources, including chat and files. Demonstrate a shared people-group with independently chosen chat and file grants in a common privilege model. | REQ-004, REQ-007, REQ-044, REQ-045 | E4; reuse/migration is design work. |
| REQ-047 | Should — Explicit; U3 | **Moderator message removal.** A chat moderator can soft-delete another person's message. Ordinary readers cannot see its removed content; the retained content is not hard-deleted by this action. Moderator access to the retained copy is still undecided. | REQ-008, REQ-010 | Q-006, Q-016 |
| REQ-048 | Should — Proposed priority; U3 | **Chat kicks and bans.** A chat moderator can remove a participant from the relevant chat session and ban further participation in that chat scope. These actions confer no server-wide authority. | REQ-008, REQ-010 | Q-017 |
| REQ-074 | Could — Explicit, distant; U3 | **Additional server administrators.** Server administration by someone other than the owner may be explored later. Acceptance would demonstrate specifically agreed server powers; they are deliberately unspecified now. Chat-admin permission alone never satisfies this requirement. | REQ-001 | Q-018 |

#### Confirmed inheritance behavior

A local rule replaces the whole inherited rule. A group/room without a local rule
inherits its nearest ancestor's rule; it does not become public under a restricted
parent. Root-level rules follow the same model. Initial rows identify individuals
and their particular grants, rather than granting all listed users the same rights.

| Configuration | Effective outcome |
|---|---|
| No local rule and no ancestor rule. | Public access. |
| `Vintage` grants Alice read/write and Bob read only; descendants have no local rules. | The whitelist and distinct grants apply throughout the subtree; unlisted identities have no access there. |
| `Vintage/Repairs` has a rule granting only Carol read/write. | Carol receives those grants; Alice and Bob get no inherited access to this subtree. |
| `Vintage/Repairs/Parts` has no local rule. | It inherits Carol's rule from `Repairs`. |
| A sibling of `Repairs` has no local rule. | It still inherits Alice and Bob's grants from `Vintage`. |

Removing a local rule restores the nearest remaining ancestor rule, or public
access if none remains. Revoking a grant is distinct from imposing a ban. Navigation
to a permitted child under a restricted parent must preserve the child grant
without exposing the parent's restricted content; presentation belongs to design.

Rationale: simple individual grants and owner authority support core rooms.
Additional chat moderators and removal tools improve administration; server-wide
administration is a different, distant capability. Neither groups, roles, nor a
Myster-wide access rewrite is a dependency of a chat Must.

### 3.2 Rooms, persistence, private conversations, and threads

| ID | Priority / source state | Outcome and acceptance evidence | Dependencies | Issues |
|---|---|---|---|---|
| REQ-010 | Must — Proposed | **Hosted room conversations.** Authorized members exchange text in a chosen host's room; other authorized members can read it. | REQ-004, REQ-011 | None |
| REQ-011 | Must — Explicit; U3 | **Independent chat hierarchy.** Nested chat groups contain groups and rooms. Demonstrate navigation through several levels, with no requirement to create or reuse shared-file folders. | REQ-001 | Q-013 |
| REQ-012 | Must — Explicit; U3 | **Persistent history, no automatic purge initially.** Text survives later visits and a normal host restart. History and stored images do not disappear due to age. Explicit deletion follows REQ-018, REQ-047, and REQ-066. | REQ-010 | Q-006 |
| REQ-013 | Should — Explicit | **Configurable retention.** A configured age can expire/delete history if this feature is included. Without it, history simply remains stored. It is not a prerequisite for persistence. | REQ-012 | Q-019 |
| REQ-014 | Should — Explicit; U3 revises proposed Must | **Persistent one-to-one private conversations.** Two members can exchange private text and reopen that conversation on the same host. Unrelated ordinary members cannot read it. No global or cross-host inbox is required. | REQ-002, REQ-004, REQ-012 | Q-004 |
| REQ-015 | Should — Revised with REQ-014; U3 | **Returning private-message recipients.** If private conversations are included, new messages remain indicated when the recipient next opens that host. Core room unread behavior is independently required below. | REQ-014, REQ-053, REQ-054 | Q-004, Q-005 |
| REQ-016 | Must — Explicit; U4 confirms | **One reply level.** A top-level message can start an attached conversation. Participants can open it as a separate conversation and add messages within it. They cannot start nested replies to those replies. The parent conversation need not remain visible while viewing the thread. | REQ-010, REQ-012 | Q-007, Q-014 |
| REQ-017 | Could — Explicit; U4 retains original scope | **Deeper replies.** Replies-to-replies may be supported later. If included, demonstrate an additional reply level and its relationship. This is not required for opening a first-level thread. | REQ-016 | Q-014 |
| REQ-018 | Must — Explicit; U3 | **Hard-delete own messages.** An author can delete their message; its actual content is hard-deleted and cannot be recovered through moderator views. Image cleanup follows REQ-066; a structural deleted-message indicator may remain under REQ-070. Editing is separately preserved in REQ-071. | REQ-002, REQ-010, REQ-012 | Q-006 |
| REQ-049 | Should — Explicit; U3 | **Ad hoc private group conversations.** Members can bring several people into a persistent private conversation on the same host. This is distinct from one-to-one messaging and from a public/restricted shared room. Unrelated ordinary members cannot read it. | REQ-002, REQ-004, REQ-012 | Q-004 |
| REQ-067 | Must — Explicit; U3 | **Discover reply conversations.** Members can tell which messages have reply threads and get an idea of the threads within a chat, at least through visible reply indicators while browsing messages. Each thread can be opened as a conversation; a separate thread index or exact aggregate counter is not prescribed. | REQ-016 | Q-007 |
| REQ-068 | Should — Proposed priority; U3 | **Mark important conversations.** A member can flag a conversation, including a reply conversation, as important and recognize that flag later. No particular UI or shared-versus-personal flag policy is prescribed yet. | REQ-010, REQ-016 | Q-020 |
| REQ-070 | Must — Explicit; U3/U4 | **Deleted parents preserve replies.** Deleting a thread's parent leaves a deleted-message indicator and all its replies accessible. Both author hard deletion and moderator soft deletion have this visible structure; the difference is retention of the underlying parent content. | REQ-016, REQ-018 | Q-006, Q-007 |
| REQ-071 | Must — Proposed, carried forward from REQ-018 | **Edit own messages.** An author can edit their own text message and later readers see the updated content. No authority to edit someone else's message is conferred. | REQ-002, REQ-010, REQ-012 | None |

Persistence, one-level threads, self-deletion, and navigable reply conversations
support the core room experience. Private one-to-one and private group messaging
are independently prioritized Shoulds. Their initiation/owner-access policy can be
settled for those extensions without blocking shared rooms.

For moderator removal, soft-deleted content is hidden from ordinary users and
retained, possibly accessible to moderators later. For author deletion, the content
is hard-deleted, including its stored images. A deleted-message indicator preserves
thread structure without preserving that author's deleted message body. Moderator
soft-deletion behavior is conditional on the Should removal feature being included;
it does not promote that feature to Must.

### 3.3 Content, formatting, and search

| ID | Priority / source state | Outcome and acceptance evidence | Dependencies | Issues |
|---|---|---|---|---|
| REQ-019 | Must — Explicit; U3 | **Paste URLs.** Members can paste a URL into a message and recipients can open its destination. A graphical preview is a separate Should. | REQ-010 | Q-008 |
| REQ-020 | Must — Explicit; U3 | **Paste images.** Copying an image and pasting it into the chat attaches it to the message on the host; authorized readers can view it now and in stored history. A file-picker upload control is not required for this path. | REQ-004, REQ-010, REQ-012 | Q-008 |
| REQ-021 | Must — Explicit; U5 | **Unicode and emoji, UTF-8 standard encoding.** Chat text supports Unicode, including emoji, and uses UTF-8 as its standard encoding for transmission and persistent storage. Representative non-ASCII text and emoji survive sending, storage, and reopening without corruption, subject to available client rendering. | REQ-010, REQ-012 | None |
| REQ-022 | Must — Explicit; U3 confirms | **Bold, italic, underline.** Members can compose and read all three formats. Underline is confirmed, not a transcription guess. | REQ-010 | Q-009 |
| REQ-023 | Should — Explicit; U3 confirms | **Bullet lists.** Members can compose/read bullets. Plain text is the workaround if omitted. | REQ-010 | Q-009 |
| REQ-024 | Could — Explicit; U3 confirms | **Tables.** Members can compose/read tables if included; tables are optional, not an inferred Must. | REQ-010 | Q-009 |
| REQ-025 | Should — Explicit | **Emoji picker.** A picker inserts Unicode emoji; pasting or OS input remains sufficient without it. | REQ-021 | None |
| REQ-026 | Should — Explicit | **Mentions.** Mentioning a member gives that member a distinct indication in the relevant conversation. Complex mention rules are not required. | REQ-002, REQ-010 | Q-005 |
| REQ-027 | Must — Explicit; U3 confirms | **Search one conversation.** Search a selected room or reply conversation's readable text history and locate a matching message. No unreadable conversation content is exposed. | REQ-004, REQ-012, REQ-016 | Q-010 |
| REQ-028 | Should — Explicit; U3 | **Search across selected histories.** Choose a scope covering multiple rooms and/or reply conversations on the current host and find readable matching messages across that scope. Searching each conversation separately is the workaround. | REQ-027 | Q-010 |
| REQ-063 | Should — Explicit; U3 | **Rich link previews.** A URL such as a YouTube link can show a small representative preview/thumbnail. A player or embedded playback is not required. This is separate from the Must URL/title outcome. | REQ-019, REQ-072 | Q-008 |
| REQ-064 | Should — Explicit; U3 | **File-picker uploads and arbitrary attachments.** An upload control can attach images, text files, or other arbitrary file data to a message, subject to a file-size limit. Authorized recipients can retrieve an accepted attachment; oversized attachments are refused. No size value has been chosen. | REQ-004, REQ-010, REQ-012 | Q-008, Q-021 |
| REQ-065 | Must — Explicit; U3 | **Rich-text composition.** Formatting uses a rich-text editor, not a user-entered markup authoring mode. Demonstrate composing formatted text through editor controls without formatting syntax; storage representation and implementation toolkit remain design choices. | REQ-022 | Q-009 |
| REQ-066 | Must — Explicit; U3 | **Stored-image lifecycle.** Stored images persist without automatic purge. Author hard deletion deletes the message's stored images; moderator soft deletion, if included, retains them while hiding them with the removed message. | REQ-012, REQ-018, REQ-020 | Q-006 |
| REQ-072 | Must — Explicit; U3 | **URL titles.** A pasted URL has an associated title visible somewhere in its message presentation. Demonstrate a titled link without requiring the graphical preview feature. Handling an unavailable external title is a design detail to specify. | REQ-019 | Q-008 |

Plain pasted links with titles and pasted images are core capabilities. Preview
artwork, arbitrary uploads, lists, and the emoji picker can be added opportunistically.
Search is host-local; a thread is itself a searchable conversation. Searching a
parent plus other conversations/threads is cross-conversation search, a Should.
Private-message search adds no initial requirement because private conversations
are not part of the core room scope.

### 3.4 Voice/video and presence

Each persistent room has one attached live conversation, with zero-to-n participants.
There cannot be several simultaneous audio conversations within that same room.
Text remains available to people who have not joined its live portion. A participant
may be in only one live audio/video room at a time across open hosts.

| ID | Priority / source state | Outcome and acceptance evidence | Dependencies | Issues |
|---|---|---|---|---|
| REQ-029 | Must — Explicit; U3 | **Voice/video belongs to the room.** Authorized participants in a room can converse live by voice, with or without video, using that room's attached live conversation. Demonstrate a multi-participant room, not just a direct server call. | REQ-002, REQ-004, REQ-010, REQ-011 | Q-003 |
| REQ-030 | Must — Explicit; U3 | **Live presence.** Show who is currently in the room's live conversation and update the roster as participants join/leave. An idle room can have no live participants. | REQ-029 | Q-003 |
| REQ-031 | Must — Explicit | **Asynchronous text independent of live presence.** A member can read/post text without joining the live portion. A live text-reader roster is not required. | REQ-010, REQ-012 | None |
| REQ-032 | Should — Explicit; U3 narrows former audio-processing umbrella | **Echo cancellation.** Enable available echo cancellation and demonstrate reduced speaker-to-microphone echo in the selected implementation. No custom echo-cancellation engine is prescribed. | REQ-029 | Q-012 |
| REQ-033 | Should — Explicit; U3 | **Background blur.** Participants can blur the background of their outgoing video. Unblurred video is sufficient if omitted. | REQ-029 | Q-012 |
| REQ-050 | Should — Explicit; U3 | **Ad hoc direct live conversations.** People can create a temporary live room for a direct person-to-person conversation outside the persistent room structure. This is distinct from private text messaging and is not required for persistent hosted rooms. | REQ-029, REQ-052 | Q-004 |
| REQ-051 | Could — Explicit; U3 | **Live and persistent transcription.** Live speech can appear as text in the room while participants contribute typed messages. A person unable to listen/use a microphone can follow along by reading and typing. The transcript remains available to people who missed the live conversation. This does not require text-to-speech. | REQ-010, REQ-012, REQ-029 | Q-022 |
| REQ-052 | Must — Explicit; U3 | **One live room per participant.** A participant cannot simultaneously take part in two live audio/video rooms. Demonstrate the restriction across different rooms, including rooms on different open hosts. Switching UX is a design choice. | REQ-029 | A-005 |
| REQ-056 | Should — Explicit; U3 | **Noise suppression.** The selected audio path offers noise suppression; demonstrate its effect with background noise. | REQ-029 | Q-012 |
| REQ-057 | Must — Explicit; U3 | **Gain control.** Audio gain is controlled to provide usable levels. Automatic gain adjustment alone is acceptable initially; manual gain controls are not required. | REQ-029 | Q-012 |
| REQ-058 | Must — Explicit; U3 | **Audio-device selection.** Select the microphone/input and speaker/headphone output used by live chat and demonstrate the selected devices in use. | REQ-029 | Q-012 |
| REQ-059 | Must — Explicit; U3 | **Microphone mute.** A participant can mute/unmute their outgoing microphone. Muted input is not transmitted. | REQ-029 | Q-012 |
| REQ-060 | Must — Explicit, conditional on video; U3 | **Camera toggle.** When video is delivered, participants can turn outgoing video off/on while retaining audio participation. No camera frames are sent while video is off. | REQ-029 | Q-012 |
| REQ-062 | Should — Explicit; U3 | **Speaking indicators.** Show which live participants are currently speaking. The participant roster works without this enhancement. | REQ-030 | Q-012 |

Rationale: room-associated voice/video, gain control, device choice, mute, and camera
control are necessary for the requested usable live experience. Echo cancellation,
noise suppression, background blur, and speaking indicators are important optional
improvements. Transcription is an optional accessibility and catch-up capability;
it is not a dependency of text or audio/video. Other H1 conveniences do not become
requirements merely by appearing in the old design.

### 3.5 Read state, history, and notifications

Read/unread state is authoritative on the host, initially per connecting identity.
It is not merely remembered by the local client. Real-time desktop notifications
are limited to hosts whose client/chat window is currently open; no background
notification infrastructure for closed hosts is required.

| ID | Priority / source state | Outcome and acceptance evidence | Dependencies | Issues |
|---|---|---|---|---|
| REQ-053 | Must — Explicit; U3 | **Host-maintained read state.** The host tracks what each connecting SID has seen/read. Reconnecting as that identity preserves its read position without depending only on local client state. | REQ-002, REQ-012 | Q-010 |
| REQ-054 | Must — Proposed priority; U3 | **Unread and read-position indicators.** A person can identify rooms/conversations with unread messages and where they previously left off. Reading further updates the host's tracked state. | REQ-053 | Q-005 |
| REQ-055 | Must — Proposed priority; U3 | **Resume history with context.** On reconnect, show unread conversation history with some preceding context. On the first visit, with no prior read state, open at the end of the conversation instead of starting at the oldest message. This does not silently mean all older messages have been read. No context-message count is prescribed. | REQ-012, REQ-053 | Q-010, A-006 |
| REQ-069 | Should — Proposed priority, future; U3 | **Conceptual-person SID grouping.** Several identities can represent the same conceptual person, with shared read/unread state on a host. Demonstrate reading under one grouped SID and observing that position under another. Initial per-SID tracking works without grouping. | REQ-002, REQ-053 | Q-023 |
| REQ-073 | Should — Proposed priority; U3 | **Mention desktop notifications and opt-out.** A direct mention can produce an OS toast while that host's chat/client window is open and notifications are enabled. A preference disables these alerts. No real-time toast is generated for a closed host. Mention support remains a Should; this adds no always-on push service. | REQ-026 | Q-005, A-007 |

Mentions/desktop alerts can be deferred while read state and unread indicators
still provide the core return-to-room experience. Private-message notification
REQ-015 is an extension using these foundations, not a Must dependency on private chat.

### 3.6 Discovery and links

| ID | Priority / source state | Outcome and acceptance evidence | Dependencies | Issues |
|---|---|---|---|---|
| REQ-034 | Must — Proposed | **Existing discovery/bookmarks.** Find hosts through the existing tracker/type mechanism and bookmark them as today, without a separate community directory. | REQ-001 | A-003 |
| REQ-035 | Must — Proposed | **Server URL.** A shareable Myster URL opens/connects to the chosen server without creating a new front page. Reuse E1's existing server destination where sufficient. | REQ-001 | None |
| REQ-036 | Must — Proposed | **Room URL.** A URL identifies a room on a host and takes an authorized recipient there. The URL does not grant access. | REQ-004, REQ-011, REQ-035 | Q-001, Q-013 |
| REQ-037 | Could — Explicit; U3 | **Cross-host chat.** A person in one hosting context can communicate with a person in another. If included, demonstrate delivery across hosts. It is optional, expected to be later work, and not required for hosted rooms. Existing Myster identity/federation facilities are not removed or declared absent. | REQ-001, REQ-002 | Q-011 |

Tracker/bookmarks are sufficient discovery. Cross-host chat stays a Could, not a
Won't, and must not become a prerequisite for core room messaging.

### 3.7 Exclusions

Acceptance evidence for exclusions is a scope review: no required product flow or
implementation dependency introduces the excluded behavior. These have no enabling
dependencies. Explicit exceptions are listed so unrelated exclusions do not defeat
newly confirmed requirements.

| ID | Priority / source | Excluded outcome and rationale |
|---|---|---|
| REQ-038 | Won't this time — Explicit; U1 | Subscriptions and monetization; not part of the requested chat service. |
| REQ-039 | Won't this time — Explicit; U1 | Bots, automation, and webhooks; no general integrations platform. |
| REQ-040 | Won't this time — Explicit; U1, qualified by U3 | General AI features and embedded games. The specific transcription Could REQ-051 is an exception; it does not authorize other AI features. Its implementation technology is not prescribed. |
| REQ-041 | Won't this time — Explicit; U1 | Enterprise administration/analytics. The server-admin Could REQ-074 is not an enterprise administration suite. |
| REQ-042 | Won't this time — Explicit; U1/U2 | Fine-grained permission systems beyond the agreed coarse grants. This excludes neither inherited whitelists nor the group/role/harmonization Shoulds. |
| REQ-043 | Won't this time — Explicit; U1 | Community recommendations, rankings, or new discovery infrastructure; existing discovery is sufficient. |
| REQ-061 | Won't this time — Explicit; U3 | Deafen controls. Microphone mute remains a separate Must. |

Room removal is not a chat-moderator power (REQ-009). Configuring/owning the server
is distinct from giving someone chat-admin permission. No server-power delegation
is inferred from the moderation features.

## 4. Questions, decisions, and assumptions

The original question IDs are retained with their answers; they are not repeatedly
reopened. Newly deferred optional-feature decisions are separate from resolved
core scope. None currently blocks meaningful design of the core Must outcomes.

| ID | Requirements | Answer / current position | Readiness effect |
|---|---|---|---|
| Q-001 | REQ-004–007, REQ-011, REQ-036 | **Resolved U2.** Public with no applicable rule; nearest full whitelist overrides its parent. Examples in §3.1. Hierarchy identity was separated into Q-013. | Resolved. |
| Q-002 | REQ-006–009, REQ-047–048, REQ-074 | **Resolved scope U3.** Chat admin means moderator, not server admin. Message removal, kicks/bans are allowed; room removal and server-power delegation are not. Message removal is explicitly Should. Individual read/post/admin grants remain distinct; broader server administration is a distant Could. | No core blocker. Ban details are Q-017. |
| Q-003 | REQ-011, REQ-029–030, REQ-050–052 | **Resolved U3.** One room has text and one attached audio/video conversation, with zero-to-n live participants. Ad hoc direct live rooms are a Should. One live room at a time per participant. | Resolved. |
| Q-004 | REQ-014–015, REQ-049–050 | **Scope resolved; policy deferred U3.** One-to-one private text and ad hoc private groups are separate Shoulds. Initiation rules and host-owner access will be decided for those features. No encryption or owner-reading policy is assumed. | Does not block core rooms; resolve before finalizing those optional designs. |
| Q-005 | REQ-015, REQ-026, REQ-053–055, REQ-073 | **Resolved core intent U3.** Host-maintained unread state and visible read position; mention OS notifications only for currently open hosts, with an opt-out. “Open” needs an operational UI definition, not a new background-push requirement. Desktop-alert priority is proposed; see A-007. | No core blocker. |
| Q-006 | REQ-012, REQ-018, REQ-047, REQ-066, REQ-070 | **Resolved U3.** Authors hard-delete message content/images. Moderator removal is a Should and soft-deletes content while retaining images, hidden from ordinary readers. A structural deleted-parent indicator may remain. | Resolved. Optional moderator review is Q-016; retention settings are Q-019. |
| Q-007 | REQ-016–018, REQ-067, REQ-070 | **Resolved U3/U4.** Deleted parents leave an indicator and retain replies. Threads are navigable conversations with reply indicators; the parent need not remain visible. One reply level only. | Resolved; clarification recorded in Q-014. |
| Q-008 | REQ-019–020, REQ-063–064, REQ-072 | **Resolved scope U3.** Paste images to host: Must. Arbitrary file upload control with size limit: Should. Paste URLs with titles: Must. Graphical rich previews: Should, after the user's explicit priority correction. Embedded playback is not required. | No core blocker. Upload limit remains Q-021. |
| Q-009 | REQ-022–024, REQ-065 | **Resolved U3.** Bold/italic/underline and rich-text composition are Musts; bullets Should; tables Could. Users do not need to enter formatting syntax. Internal representation is design work. | Resolved. |
| Q-010 | REQ-027–028, REQ-053–055, REQ-069 | **Resolved U3.** Search/read history is current-host and readable-content only, including threads. Each thread is a conversation; selected cross-conversation search is Should. The host tracks read state per SID initially and provides unread history/context; first visits open at the end. | Resolved; grouping is Q-023. |
| Q-011 | REQ-003, REQ-017, REQ-037 and all priorities | **Resolved U3.** Priorities express importance, not phases. Design schedules opportunistically; inability to deliver a Must requires renegotiation. Cross-host chat is optional, not prohibited. Existing Myster federation/identity is acknowledged. No new blanket compatibility obligation is intended. | Resolved. |
| Q-012 | REQ-029, REQ-032–033, REQ-056–062 | **Resolved U3.** Gain control, devices, mute, and camera toggle are Musts; echo cancellation, suppression, blur, and speaking indication Shoulds; deafen Won't. Automatic gain is acceptable initially. Video may warrant a separate project, with scope renegotiation if needed. | Resolved; no unbounded “modern features” requirement. |
| Q-013 | REQ-005, REQ-011, REQ-036 | **Resolved U3.** Chat groups/rooms have their own hierarchy. They are not shared-file folders. Harmonizing access concepts does not merge these content structures. | Resolved. |
| Q-014 | REQ-016–017, REQ-067 | **Resolved U4.** U3's navigation/nesting wording prompted clarification. The user confirmed one level of replies: top-level messages can open reply conversations; messages inside them cannot spawn deeper reply conversations. Deeper nesting remains Could. | Resolved; no promotion of REQ-017. |
| Q-016 | REQ-047 | **Deferred optional decision.** May chat moderators inspect retained soft-deleted content, and through what authorization/view? U3 says “possibly admins.” | Resolve for a review UI; ordinary-user hiding and retention are already settled. |
| Q-017 | REQ-048 | **Deferred detail.** Define kick/rejoin behavior, ban persistence/duration, and whether a scoped ban prevents text, live access, or both. U3 allows chat kicks/bans but does not specify these details or their priority. | Should-feature refinement; no core blocker. |
| Q-018 | REQ-074 | **Deferred distant scope.** Additional server-admin powers and their assignment model are unspecified. Do not infer them from chat moderation. | Could-feature definition only. |
| Q-019 | REQ-013 | **Deferred optional decision.** Expiry configuration, applicable histories/media, and interaction with explicit deletion need definition if retention settings are delivered. | Should-feature design; no automatic purge initially. |
| Q-020 | REQ-068 | **Deferred optional decision.** Are important-conversation flags personal or shared? Confirm proposed Should priority. | Does not block core thread discovery. |
| Q-021 | REQ-064 | **Unspecified limit.** Arbitrary uploads require a file-size limit; value/configuration has not been chosen. | Set before upload acceptance validation; no invented number. |
| Q-022 | REQ-051 | **Deferred Could details.** Transcript generation, speaker attribution, correction, visibility/retention, and failure behavior need definition if included. No provider, service, or model is chosen. | No dependency from core chat to transcription. |
| Q-023 | REQ-069 | **Deferred future decision.** Define authorization for grouping identities into one conceptual person and confirm the proposed priority. Existing identity-equivalence facilities should be investigated rather than replaced by assumption. | Per-SID state remains sufficient initially. |

| ID | Author assumption / interpretation | Status |
|---|---|---|
| A-001 | Normalize dictated “Meister/Maister” to repository spelling **Myster**. SID means the existing connecting server identity, not a newly prescribed wire-format identifier. | Editorial; exact identity types belong to design. |
| A-002 | “Never deleted” means no automatic expiry, not a prohibition on explicit deletion. | Confirmed by U3; exact author/moderator distinction is now in requirements. |
| A-003 | Preserve existing tracker/type discovery and bookmarks. | Interpretation of U1; no new discovery service. |
| A-004 | Search covers readable current-host content, including reply conversations; no global cross-host search. | Confirmed by U3. |
| A-005 | “Only one live room at a time” applies across the participant's currently open hosts. Initial enforcement concerns the active participant/client session; coordination between separate devices or grouped SIDs is not an added guarantee. | Proposed implementation boundary; preserve the user-visible single-live-room rule. |
| A-006 | First-visit scrolling to the end does not itself mark unseen older history read. When displayed content becomes “seen/read” and the exact context amount are design details. | Proposed interpretation of U3's first-visit behavior. |
| A-007 | Desktop toasts inherit a proposed Should priority from the explicit Should mention feature. Their opt-out is part of the same feature. No notifications for ordinary messages or closed hosts are inferred. | Confirm priority during scope review; unread indicators are independent. |

Proposed priorities remain proposals, not user approvals. Hard deletion requires
removing the underlying message content and stored images, not merely hiding them;
no additional secure-media-erasure or backup-management feature is inferred.

## 5. Bounded scope and dependency review

| Finding | Requirements | Resolution and owning stage |
|---|---|---|
| Moderation had been conflated with server administration. | REQ-008–009, REQ-047–048, REQ-074 | Corrected in place: chat-admin permission is scoped moderation, not delegated server authority. Message removal Should; kicks/bans priority proposed. Define distant server powers separately. |
| Private messages were initially proposed as Must. | REQ-014–015, REQ-049 | U3 explicitly makes one-to-one/private groups Should. Core unread/history requirements now stand independently in REQ-053–055. Initiation/owner privacy decisions no longer block core rooms. |
| Persistence and explicit deletion are different lifecycle operations. | REQ-012–013, REQ-018, REQ-047, REQ-066, REQ-070 | No age-based purge initially. Author deletion hard-deletes content/images; moderator deletion retains them with restricted visibility. Keep reply structure. Conditional soft-delete behavior does not make moderator removal a Must dependency. |
| Opening a thread could be mistaken for recursive reply nesting. | REQ-016–017, REQ-067, REQ-070 | U4 settles one reply level. Independent conversation navigation, reply indicators, and parent tombstones do not require recursive replies or a prescribed left-hand tree UI. |
| Content features now have distinct priorities. | REQ-019–024, REQ-063–066, REQ-072 | Paste images and titled URLs Must; graphical previews/arbitrary uploads Should; tables Could. Keep rich editor behavior without choosing toolkit/markup storage. Attachment limit and unavailable-title behavior need design detail. |
| Read state is hosted, not only client-local. | REQ-053–055, REQ-069, REQ-073 | Host state initially keyed to SID; grouping later. Limit live notification scope to open hosts. Design considers reconnect/restart persistence and history delivery without adding always-on push infrastructure. |
| Chat hierarchy and file hierarchy are independent. | REQ-005, REQ-011, REQ-044–046 | Common grant concepts are a Should, not a merged content hierarchy or a mandatory rewrite of existing file access. Investigate E4 and existing identity-equivalence support before choosing reuse/migration. |
| Transcription qualifies an older AI exclusion. | REQ-040, REQ-051 | Retain the general AI exclusion with this explicit Could exception. Accessibility/catch-up intent does not require selecting AI technology or enabling unrelated AI tools. |
| Voice/video can be substantial. | REQ-029–033, REQ-050–062 | Preserve Must priorities. Epic design evaluates native/shared-transport findings and hosted-room feasibility, may propose separate projects, and must renegotiate any omitted Must rather than silently defer it. H1/H2 do not prove room delivery. |
| Priorities must not be converted into fixed phases. | All | Design groups work by dependencies/cost and can include convenient Shoulds. Could cross-host chat is not a ban; no core Must requires it. Existing identity/federation is not being redesigned by this spec. |
| Older join-request work anticipated messaging. | REQ-006, REQ-039 | E3 remains related context; no approval inbox, bots, or webhooks are added implicitly. |

No core Must depends on a Should, Could, or excluded capability. Conditional
requirements describe required behavior when the optional feature is present;
they do not silently promote it. The optional harmonization Should can depend on
other Shoulds; that does not make all access-control work a prerequisite for chat.

## 6. Readiness and design handoff

**Ready with assumptions:** Q-001–014's core product intent is resolved, and no known
unanswered Must-level product question currently prevents meaningful epic design.
A-005–007 and proposed priorities remain visible for review. Deferred questions
concern optional features. This status is not user approval, design convergence,
a schedule, implementation, or test completion.

The next requested stage can be epic design through
`$myster-design docs/requirements/chat.md` and
[the repository's design skill](../../.agents/skills/myster-design/SKILL.md).
Proposed outputs are `docs/design/chat.md` and `docs/design/chat-decisions.md`;
this requirements revision does not create them or start design automatically.
Preserve/reference H1/H2 rather than silently rewriting their separate scopes.

The design consumer must:

- Preserve stable IDs, priorities, source states, acceptance evidence, dependencies,
  decisions, assumptions, and open optional-feature questions as the single baseline.
- Cite requirements for significant architectural decisions and record the companion
  decision log. Reconcile current room/video scope with historical transport/native
  constraints and existing identity/access facilities.
- Include every requirement exactly once in a
  `Requirement | Priority | Design component | Status` matrix, with linked sections
  and explicit reasoning. `Covered` means a credible design path, not implemented
  or tested. Otherwise use `Needs decision`, `Gap` (including partial), `Deferred`,
  or `Out of scope` with reasons. Deferral does not change priority.
- Review unnecessary complexity, dependencies on optional/excluded work, and concrete
  expensive-deferral risks. Link this issue register rather than duplicating it.
  Any newly consequential product ambiguity needs a linked issue and, where needed,
  explicit baseline revision. Do not reopen settled answers without new evidence.
- Keep importance separate from delivery sequencing. Obtain renegotiation for any
  omitted Must and record the rationale for proposed project boundaries, especially
  voice/video. Every Must needs credible coverage before formal convergence.

The design skill owns architectural alternatives, its decision log, and convergence.
Bounded project plans and implementation follow only when requested.

## 7. Revision history and lineage

| Revision | Source | Substantive changes |
|---|---|---|
| 0.1 — 10 November 2026 | U1 | Initial REQ-001–043 and Q-001–012. |
| 0.2 — 11 November 2026 | U2 | Resolved Q-001 inheritance; clarified REQ-005–007. Added Shoulds REQ-044–046. Split unanswered hierarchy identity into Q-013. |
| 0.3 — 11 November 2026 | U3/U4 | Resolved moderation/server distinction, unified rooms, separate hierarchy, notifications/read state, deletion/media lifecycle, content/editor/search, scheduling, and media priorities. REQ-009 now covers chat moderators only; new REQ-074 isolates distant server administration. REQ-014 changed proposed Must to explicit Should; REQ-015 follows that optional scope, with core return/unread behavior captured separately in REQ-053–055. REQ-018 now covers explicit Must author hard deletion; its original edit outcome is preserved as proposed Must REQ-071. REQ-032 is narrowed to echo cancellation; distinct audio features are REQ-056–062. Added REQ-047–074, retaining prior IDs. U4 confirms REQ-016's single level and REQ-017's Could priority. REQ-040 gains the explicit transcription exception. No requirement retired or renumbered. |
| 0.4 — 11 November 2026 | U5 | REQ-021 confirmed as an explicit Must; Unicode/emoji support uses UTF-8 as the standard text encoding for transmission and storage. Existing ID and dependencies retained. |
