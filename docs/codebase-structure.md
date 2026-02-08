# Myster Codebase Structure

This document provides an overview of the Myster codebase organization to help developers and AI agents navigate the project efficiently.

## Table of Contents

- [Project Overview](#project-overview)
- [Package Organization](#package-organization)
- [Key Subsystems](#key-subsystems)

---

## Project Overview

**Language**: Java (modern Java with virtual threads, records, pattern matching)  
**Build System**: Maven (`pom.xml`)  
**Main Entry Point**: `com.myster.Myster.main()`

Myster is a peer-to-peer file-sharing application built on a distributed architecture with virtual overlay networks. The codebase is organized into clearly separated subsystems for networking, file management, identity, UI, and more.

---

## Package Organization

The source code is located in `src/main/java/` with two top-level packages:

### `com.general.*` - Generic Utilities & Frameworks

Low-level utilities and infrastructure components that could theoretically be used by any Java application:

- **`com.general.application`** - Application singleton management, single-instance enforcement
- **`com.general.events`** - Generic event dispatcher framework (`NewGenericDispatcher`)
- **`com.general.mclist`** - Custom list/tree UI components (`MCList`, `TreeMCList`)
- **`com.general.net`** - Low-level network utilities, async datagram sockets
- **`com.general.tab`** - Tab management utilities
- **`com.general.thread`** - Thread utilities including `PromiseFuture`, `Invoker`, async patterns
- **`com.general.util`** - General utilities (dialogs, timers, UI helpers)

### `com.myster.*` - Myster-Specific Code

Application-specific logic for the Myster P2P network:

#### Core Application
- **`com.myster.Myster`** - Main entry point, application bootstrap
- **`com.myster.application`** - Application-level globals (`MysterGlobals`), lifecycle management

#### Networking Layer
- **`com.myster.net`** - Network abstractions and core classes
  - **`net.client`** - Client-side protocol interfaces (`MysterProtocol`, `MysterStream`, `MysterDatagram`)
  - **`net.server`** - Server infrastructure (`ServerFacade`, `Operator`, `ConnectionSection`)
  - **`net.stream`** - TCP stream-based protocols
    - **`stream.client`** - Client stream implementations (`StandardSuiteStream`, `MysterSocket`)
    - **`stream.client.msdownload`** - Multi-source download engine
    - **`stream.server`** - Server stream handlers, transfer queue
  - **`net.datagram`** - UDP datagram protocols, ping/pong, encryption
    - **`datagram.client`** - Client datagram implementations
  - **`net.mdns`** - mDNS service discovery and announcement

#### File Management
- **`com.myster.filemanager`** - File sharing and indexing
  - `FileTypeListManager` - Central manager for all file type lists
  - `FileTypeList` - Per-type file indexing and search
  - `FileItem` - Represents a shared file with metadata
  - **`filemanager.ui`** - File manager UI components (`FmiChooser`)

#### Identity & Security
- **`com.myster.identity`** - Cryptographic identity system
  - `Identity` - Server identity with public/private key pairs
  - `Cid128` - 128-bit compact identity digest (hash of public key)
  - Public key-based server identification

#### Type System
- **`com.myster.type`** - Myster file type system. Uses types based on a public key for extensibility and uniqueness.
  - `MysterType` - Immutable type identifier - based on a public key
  - `TypeDescription` - Type metadata (name, extensions, description)
  - `TypeDescriptionList` - Type registry
  - `DefaultTypeDescriptionList` - Main implementation with persistence
  - `CustomTypeManager` - User-defined custom types
  - `StandardTypes` - Built-in types (Music, Applications, etc.)
  - **`type.ui`** - Type management UI

#### Tracker & Server Discovery
- **`com.myster.tracker`** - Server pool and discovery
  - `Tracker` - High-level server tracking API
  - `MysterServerPool` / `MysterServerPoolImpl` - Server instance cache
  - `MysterServer` - Represents a remote server with stats
  - `IdentityTracker` - Maps addresses to identities
  - `MysterTypeServerList` - Per-type server list with ranking
  - **`tracker.ui`** - Tracker window UI

#### Search
- **`com.myster.search`** - Search functionality
  - `SearchEngine` - Manages search across multiple servers
  - `MysterSearch` - Individual search implementation
  - `AsyncNetworkCrawler` - Asynchronous network crawling
  - `HashCrawlerManager` - Hash-based file verification
  - **`search.ui`** - Search window and tab UI

#### Client UI
- **`com.myster.client.ui`** - Client browsing windows
  - `ClientWindow` - Browse a specific server's files
  - `ClientWindowProvider` - Factory/cache for client windows

#### Server Operations
- **`com.myster.server`** - Server-side operations
  - **`server.event`** - Server event dispatching
  - **`server.datagram`** - Server-side datagram handlers
  - **`server.stream`** - Server-side stream handlers
  - **`server.ui`** - Server statistics and download info UI

#### Transactions
- **`com.myster.transaction`** - Transaction-based UDP protocol
  - `TransactionManager` - Routes transaction packets
  - `TransactionProtocol` - Interface for transaction handlers

#### Hash Management
- **`com.myster.hash`** - File hashing for integrity
  - `HashManager` - Background hash computation
  - `FileHash` - Hash value abstraction
  - `HashCache` - Persistent hash storage

#### Messaging
- **`com.myster.message`** - Peer-to-peer messaging (legacy feature)
  - **`message.ui`** - Message window and preferences

#### MML (Myster Markup Language)
- **`com.myster.mml`** - Legacy serialization format
  - `MML` - Tree-based data structure
  - `MessagePak` - Modern MessagePack-based replacement

#### User Interface
- **`com.myster.ui`** - Core UI framework
  - `MysterFrame` - Base class for Myster windows
  - `MysterFrameContext` - Dependency injection for windows
  - `WindowManager` - Tracks open windows
  - **`ui.menubar`** - Menu bar factory and actions
  - **`ui.tray`** - System tray integration

#### Bandwidth & Progress
- **`com.myster.bandwidth`** - Bandwidth throttling
- **`com.myster.progress`** - Download progress tracking
  - **`progress.ui`** - Download manager window

#### Preferences
- **`com.myster.pref`** - Preferences management (`MysterPreferences`)

#### Plugins
- **`com.myster.plugin`** - Plugin system (currently minimal)

#### Utilities
- **`com.myster.util`** - Myster-specific utilities
  - `I18n` - Internationalization
  - `TypeChoice` - Type selector combo box
  - `ContextMenu` - Context menu helpers
  - `MysterThread` - Thread utilities

---

## Key Subsystems

### 1. **Network Protocol Stack**

Myster uses both **TCP (stream)** and **UDP (datagram)** protocols:

#### Stream-based (TCP)
- **Purpose**: File downloads, detailed queries, batch operations
- **Client API**: `MysterProtocol.getStream()` → `MysterStream`
- **Implementation**: `StandardSuiteStream` (static methods for protocol operations)
- **Operations**:
  - `getSearch()` - Search a server
  - `getServerStats()` - Get server metadata
  - `getFileList()` - List files of a type
  - `getFileStats()` - Get file metadata
  - `downloadFile()` - Download via multi-source engine

#### Datagram-based (UDP)
- **Purpose**: Fast ping/pong, server stats, low-latency queries
- **Client API**: `MysterProtocol.getDatagram()` → `MysterDatagram`
- **Transaction System**: `TransactionManager` routes packets to `TransactionProtocol` handlers
- **Operations**:
  - Ping/pong with encryption
  - Server stats exchange
  - Search (newer UDP-based search)

#### Server Side
- **`ServerFacade`** - Main server controller
  - Manages `Operator` threads (listen on ports)
  - Routes stream connections to `ConnectionSection` handlers
  - Manages datagram transaction protocols
  - Handles dynamic port changes
  - Coordinates mDNS announcements

### 2. **File Management System**

- **`FileTypeListManager`** - Central coordinator
  - One `FileTypeList` per enabled `MysterType`
  - Responds to type enable/disable events from `TypeDescriptionList`
  
- **`FileTypeList`** - Per-type file index
  - Monitors a directory for files matching type extensions
  - Background indexing with `PromiseFuture`
  - Computes hashes via `HashProvider`
  - Handles file searches
  
- **Threading**: Indexing is asynchronous to avoid blocking UI

### 3. **Type System**

- **`MysterType`** - 4-byte immutable type identifier
- **`TypeDescription`** - Name, extensions, description, source
- **`TypeDescriptionList`** - Registry with enable/disable state
  - Fires events (`TypeListener`) when types are added/removed/enabled/disabled
  - Persistent storage in Java `Preferences`
  
- **Built-in Types**: `StandardTypes` (Music, Applications, Images, etc.)
- **Custom Types**: `CustomTypeManager` + `TypeManagerPreferences` UI

### 4. **Tracker & Server Pool**

- **`Tracker`** - High-level API for finding servers
  - `getAll(MysterType)` - Get servers for a type, ranked
  - `getQuickServerStats()` - Fast server lookup
  - Uses `MysterTypeServerList` for per-type rankings

- **`MysterServerPool`** - Cache of known servers
  - **Implementation**: `MysterServerPoolImpl`
  - Keyed by `MysterIdentity` (public key or address)
  - Weak references allow GC when servers are idle
  - Background refresh pings
  - `IdentityTracker` resolves addresses → identities

- **`MysterServer`** - Immutable server statistics snapshot
  - File counts per type
  - Ping time, uptime, bandwidth
  - Rank for search ordering

### 5. **Identity System**

- **`Identity`** - Local server identity with RSA keypair
- **`MysterIdentity`** - Interface for remote server identities
  - `PublicKeyIdentity` - Identity based on public key (preferred)
  - `MysterAddressIdentity` - Fallback for servers without crypto
  
- **`Cid128`** - Compact 128-bit identity hash (first 128 bits of SHA-256(public key))

### 6. **Search Engine**

- **`SearchEngine`** - Coordinates search across servers
  - Uses `Tracker` to find servers with files of the given type
  - Parallelizes searches using virtual threads
  - Returns results via `SearchResultListener` callbacks
  
- **`MysterSearchResult`** - Search result with download capability
- **UI**: `SearchWindow` with multiple `SearchTab` instances

### 7. **Multi-Source Download**

- **`MultiSourceDownload`** - Coordinates download from multiple servers
  - Splits file into segments
  - Downloads segments in parallel
  - Handles retries and source failures
  
- **`SegmentDownloader`** / `InternalSegmentDownloader` - Downloads one segment
- **`MSDownloadParams`** - Download request parameters

---

## Testing

- **Location**: `src/test/java/`
- **Framework**: JUnit 5
- **Coverage**: Unit tests for core subsystems (tracker, net, hash, type, etc.)

---

## Build Artifacts

- **JAR**: `target/Myster-fn.jar` (executable)
- **Native Packages**: `target/jpackage/` (`.deb`, etc.)
- **Build**: `mvn package`

---

## Related Documentation

- **Design Docs**: `docs/design/` - Implementation details for specific features
- **Myster Project Specific Conventions**: `docs/conventions/` - Myster-specific coding conventions and architectural patterns
  - **Coding Conventions**: `docs/conventions/myster-coding-conventions.md` - Code style and Javadoc guidelines
  - **Important Patterns**: `docs/conventions/myster-important-patterns.md` - Key architectural patterns (Event System, Promise/Future, Listener Pattern, Dependency Injection, Threading)
- **Plans**: `docs/plans/` - Feature implementation plans
- **Implementation Summaries**: `docs/impl_summary/` - Post-implementation write-ups

---

## Quick Reference: Finding Code

| **I want to...** | **Look in...** |
|------------------|----------------|
| Understand app startup | `com.myster.Myster.main()` |
| Add a new file type | `com.myster.type.CustomTypeManager`, UI in `type.ui.TypeManagerPreferences` |
| Implement a new protocol | Server: `net.server.datagram` or `net.server.stream`; Client: `net.client` |
| Change server behavior | `com.myster.net.server.ServerFacade` |
| Modify search UI | `com.myster.search.ui.SearchWindow`, `SearchTab` |
| Adjust file indexing | `com.myster.filemanager.FileTypeList` |
| Track down network issue | Client: `net.stream.client.StandardSuiteStream`; Server: `net.server.Operator`, `ConnectionSection` |
| Debug server discovery | `com.myster.tracker.Tracker`, `MysterServerPoolImpl`, `IdentityTracker` |
| Add a window | Extend `com.myster.ui.MysterFrame`, register with `WindowManager` |
| Understand type events | `com.myster.type.TypeDescriptionList`, `TypeListener` |

---

**Last Updated**: 2026-02-07  
**Maintainer**: This document should be updated when major architectural changes occur.

