# Myster Important Patterns

This document describes key architectural and design patterns used throughout the Myster codebase. Understanding these patterns is essential for maintaining consistency when adding new features or modifying existing code.

**Quick index** — what lives here:

- **Event System** — `NewGenericDispatcher`, how to fire and subscribe to events
- **Promise/Future** — `PromiseFuture<T>`, `addResultListener`, `addCallListener`/`CallAdapter`, async I/O
- **Listener Pattern** — use private inner classes, not `implements SomeListener`
- **Dependency Injection** — `MysterFrameContext`, constructor injection, no static singletons
- **`*Utils` Classes** — extract static helpers from large classes into a companion `FooUtils`; `*Util`/`*Utilities` are legacy names being renamed as touched
- **Threading & Concurrency** — EDT rules, virtual threads, `synchronized`, `Invoker`, `Util.invokeLater`, don't double-dispatch
- **FlatLaf Theming** — `UIManager.getColor("Actions.Red")` and friends; never hardcode colours

## Table of Contents

- [Event System](#event-system)
- [Promise/Future Pattern](#promisefuture-pattern)
- [Listener Pattern](#listener-pattern)
- [Dependency Injection](#dependency-injection)
- [`*Utils` Classes](#utils-classes)
- [Threading & Concurrency](#threading--concurrency)
  - [Util.invokeLater vs SwingUtilities.invokeLater](#utilinvokelater-vs-swingutilitiesinvokelater)
  - [PromiseFuture Call Listeners](#promisefuture-call-listeners)
- [FlatLaf Theming](#flatlaf-theming)

---

## Event System

Myster uses a consistent event dispatcher pattern throughout the codebase.

### NewGenericDispatcher

**`NewGenericDispatcher<L>`** - Generic typed event dispatcher

- `addListener(L)` / `removeListener(L)` - Manage listener subscriptions
- `fire()` - Returns a proxy that forwards calls to all listeners
- Thread-safe with `CopyOnWriteArrayList`

### Usage Pattern

```java
private final NewGenericDispatcher<MyListener> dispatcher = new NewGenericDispatcher<>();

// Fire events to all registered listeners
dispatcher.fire().eventMethod(arg);

// Register listeners
dispatcher.addListener(listener);
```

### Common Dispatchers in Codebase

- `ServerEventDispatcher` - Server-side events
- Type-specific dispatchers in `TypeDescriptionList`

### Where to Find It

See `com.general.events.NewGenericDispatcher` for the implementation.

---

## Promise/Future Pattern

Myster uses `PromiseFuture<T>` for asynchronous results and composable workflows.
**`PromiseFutures.execute(Callable)` and `PromiseFutures.execute(Callable, Executor)`
are the most commonly used entry points in this library and are Myster's replacement
for SwingWorker.** Use them for finite background tasks and their result/error delivery.
The first overload uses virtual threads; the second uses the supplied executor, for
example a `BoundedExecutor` or a platform executor required by a native API.

### Basic Usage

```java
PromiseFuture<Result> future = PromiseFutures.execute(() -> longOperation()).useEdt();
future.addResultListener(result -> handleResult(result));
future.addExceptionListener(ex -> handleError(ex));
```

Prefer chaining when possible:

```java
PromiseFutures.execute(() -> longOperation())
    .useEdt()
    .addResultListener(result -> handleResult(result))
    .addExceptionListener(ex -> handleError(ex));
```

You need to add an exception handler and invoker if one has not already been added. There's a method for adding ex.printStackTrace() handling.

Use `PromiseFutures.execute(callable, executor)` to run a blocking operation on a chosen
executor and deliver its result through a promise. The helper skips the callable when
cancellation is observed before invocation, forwards cancellation to `Cancellable`
callables, and preserves interrupt status when forwarding `InterruptedException`.
Cancelling a plain callable's promise does not interrupt work already running.

Reserve `PromiseFuture.newPromiseFuture(context -> ...)` for adapting callback-driven
APIs or coordinating asynchronous child operations. A manual constructor that only
schedules a callable and forwards its result/exception should use `execute` instead.
Keep protocol I/O synchronous when its caller already runs through `execute`, so one
operation does not acquire an unnecessary second worker and promise. Long-lived services
and transfer workers retain their own lifecycles where a single completion promise does
not represent the operation. Finite UI tasks can still publish progress while running
through `execute`; preserve their resource cancellation and EDT dispatch when migrating.

### Key Features

- **Non-blocking**: Returns immediately with a promise of future completion
- **Callback-based**: Use listeners to handle results and errors
- **Composable**: Chain operations together
- **Thread-aware**: Result listeners can specify which thread to run on (EDT by default for UI updates)

### Choosing the asynchronous mapper thread

Use `mapAsyncInline(mapper)` when the mapper should run inline on whichever thread completes the
source promise. Use `mapAsync(mapper, invoker)` when the function that creates the next
`PromiseFuture` must instead run on a particular subsystem invoker. The supplied invoker controls
only the mapper invocation; it does not become the listener invoker of the source, mapped, or
returned future.

```java
source.mapAsync(value -> PromiseFuture.newPromiseFuture(updateActorState(value)), actorInvoker);
```

Do not wrap the mapper body in `PromiseFutures.execute(..., invoker::invoke)` merely to choose its
thread. That represents an `Invoker` as an `Executor` and obscures which stage is being scheduled.

### Common Use Cases

- Network I/O operations
- File operations (reading, writing, hashing)
- Long computations that shouldn't block the UI
- Background indexing

### Where to Find It

See `com.general.thread.PromiseFuture` and `com.general.thread.PromiseFutures` for the implementation.

---

## Listener Pattern

### Convention: Use Private Inner Classes

**Pattern**: Use private inner classes for listener implementations instead of having the main class implement listener interfaces.

**Rationale**: Keeps listener interfaces separate from the class's primary public interface. Makes the class's purpose clearer and avoids polluting the public API with listener methods.

### Bad Example ❌

```java
public class MyComponent extends JPanel implements SomeListener {
    public MyComponent() {
        someObject.addListener(this);  // 'this' implements SomeListener
    }
    
    @Override
    public void eventOccurred(Event e) {
        // This is now part of MyComponent's public interface
        // Anyone can call this method
    }
}
```

### Good Example ✅

```java
public class MyComponent extends JPanel {
    public MyComponent() {
        someObject.addListener(new SomeListenerImpl());
    }
    
    private void eventOccurred(Event e) {
        // Private method - not part of public interface
        // Implementation can be large without extra indentation
    }
    
    // Inner classes belong at the end of the file
    private class SomeListenerImpl implements SomeListener {
        @Override
        public void eventOccurred(Event e) {
            MyComponent.this.eventOccurred(e);
        }
    }
}
```

Even better would be to use annonymous inner classes or lambda expressions for simple one-liner callbacks, but for more complex implementations, the private method + inner class approach is preferred.

### Guidelines

- **Inner class placement**: Private inner classes should be placed at the end of the file
- **Optional delegation**: You don't need to have the inner class call private methods in the parent - you can implement directly in the inner class
- **Large implementations**: The private method approach shown above is preferred when the implementation is large (avoids extra indentation)
- **Simple callbacks**: For simple one-liners, lambda expressions are fine: `someObject.addListener(e -> doSomething())`

### Examples in Codebase

- `Tracker.TypeListenerImpl`
- `TypeChoice.TypeListenerImpl`
- `FileTypeListManager.TypeListenerImpl`

---

## Dependency Injection

### MysterFrameContext Pattern

Windows receive dependencies through the `MysterFrameContext` record rather than using static singletons or service locators.

### Pattern

```java
public class MyWindow extends MysterFrame {
    private final FileTypeListManager fileManager;
    
    public MyWindow(MysterFrameContext context) {
        super(context);
        this.fileManager = context.fileManager();
    }
}
```

### MysterFrameContext Definition

```java
public record MysterFrameContext(
    WindowManager windowManager,
    MysterMenuBarFactory menuBarFactory,
    FileTypeListManager fileManager,
    ClientWindowProvider clientWindowProvider,
    Preferences preferences
) {}
```

### Benefits

- **Testability**: Easy to inject mock dependencies for testing
- **Clarity**: Dependencies are explicit in constructor
- **Flexibility**: Can easily add new dependencies without changing all window classes
- **Avoids globals**: No need for static singletons

### Guidelines

- **Use constructor injection**: Pass dependencies through constructors
- **Required means required**: Pass services directly and reject null at construction when their
  absence would be an initialization bug. Use `Optional` only when the component intentionally
  supports operating without that capability; do not keep unused overloads that manufacture empty
  dependencies for convenience.
- **Avoid static singletons**: Use dependency injection instead where possible
- **Service locator pattern**: Avoid - prefer explicit dependency injection

---

## `*Utils` Classes

### Pattern: Companion utility class for a large class

When a class `Foo` grows large (approaching ~1 000 lines) and some of its methods:

- do **not** access any private members of `Foo`, **and**
- could logically be `static`,

extract them into a companion class named **`FooUtils`**.

**Recognition rule**: you know you have the right pattern when every method in `FooUtils`
takes a `Foo` (or the relevant domain type) as its first argument and operates purely on
the public API of that type.

### Conventions

- `FooUtils` (or `FooUtil`) contains **only `static` methods** — no instance state, no
  constructor (or a private no-arg constructor to suppress instantiation).
- All methods are `public static` unless they are private helpers used only within the
  `FooUtils` class itself.
- The companion class lives in the **same package** as `Foo`.
- The name suffix is always **`Utils`** (plural). `*Util` and `*Utilities` are legacy names;
  rename them to `*Utils` whenever you touch the file (don't do a mass rename — keeps diffs
  clean). See `TODO.txt` for the full list of classes still to be renamed.

### Example

```java
// Foo.java — core class, kept focused
public class Foo {
    private final int value;
    // ... ~900 lines of core logic ...
}

// FooUtils.java — extracted static helpers; Foo is always the first argument
public final class FooUtils {
    private FooUtils() {}   // no instances

    public static boolean isValid(Foo foo) { ... }
    public static String describe(Foo foo) { ... }
}
```

### When NOT to use this pattern

- If the helper method needs access to private state → keep it inside `Foo` as a
  `private` method.
- If the method is not logically tied to `Foo` at all → put it in a more general
  `Util` class or its own class.
- If `Foo` is still small → don't pre-emptively split; wait until the class actually
  becomes hard to navigate.

### Examples in the codebase

- `com.myster.access.AccessEnforcementUtils` — enforcement helpers that operate on
  `AccessListManager` / `AccessListState`; extracted because `AccessListManager` itself
  does not need to know about TCP/UDP enforcement policy.

---

## Threading & Concurrency

### Thread Categories

Myster uses different threading strategies depending on the type of work:

#### 1. EDT (Event Dispatch Thread)

**Rule**: All Swing UI operations must run on the EDT.

**Tools**:

- `SwingUtilities.invokeLater()` - Standard Swing approach
- `Invoker.EDT_NOW_OR_LATER` - Myster utility (runs immediately if already on EDT)

**What runs on EDT**:

- All UI updates (setText, repaint, etc.)
- UI event handlers
- Result listeners (by default in PromiseFuture)

**Critical**: Never block the EDT with long-running operations!

#### 2. Virtual Threads

**Use for**: I/O and long-running operations

**What runs on virtual threads**:

- Network I/O (socket operations, HTTP requests)
- File operations (reading, writing, hashing)
- Long computations
- Background indexing

**How to use**:

```java
PromiseFutures.execute(() -> {
    return performLongComputation();
}).useEdt().addResultListener(result -> {
    // Explicitly dispatched on the EDT
    displayResult(result);
}).addStandardExceptionHandler();
```

**Exception: native thread affinity** — some native APIs require a sequence of calls to
stay on the same OS thread. The Windows thumbnail provider uses COM this way: initialization,
extraction and cleanup must run on one platform thread. Pinning during individual native/FFM
calls does not guarantee the same carrier between calls. `BoundedExecutor` limits concurrency
but does not supply that guarantee. Preserve the platform executor in `Thumbnails` unless
Windows acquisition is moved to its own platform executor; the Linux/macOS providers do not
have this COM requirement.

**Linux thumbnail generation** — keep OS cache reads parallel, but serialize D-Bus
generation requests in `LinuxThumbnailProvider`. Tumbler 4.18.1 can mix completion
signals between concurrent queues, leaving files without thumbnails. The interruptible
generation permit covers the service request through completion/cache lookup and is
released in `finally`; recheck the shared cache after acquiring it.

#### 3. Synchronization

**Rule**: Use `synchronized` on methods/blocks for shared state

**Guidelines**:

- Example: `DefaultTypeDescriptionList` synchronizes most methods
- Prefer explicit synchronization over implicit patterns when state is shared
- Use `synchronized` keyword for method-level or block-level locking
- Use `ReentrantReadWriteLock` only when concurrent readers on the same state are expected to be
  common enough that `synchronized` would create avoidable contention. Keep lock ownership local
  to the protected state, and do not hold the lock while doing slow parsing or network work.

### Invoker Utility

The `Invoker` class provides thread scheduling utilities:

- **`Invoker.EDT_NOW_OR_LATER`** - Run on EDT (immediately if already on EDT, otherwise enqueue)
- **Custom invokers** - For background work on specific threads

### `Util.invokeLater` vs `SwingUtilities.invokeLater`

**`Util.invokeLater(Runnable)`** (`com.general.util.Util`) is Myster's preferred way to
dispatch to the EDT from a background thread. It is equivalent to
`SwingUtilities.invokeLater` but is the idiom used throughout the older parts of the
codebase (e.g. `TypeListerThread`'s internal listener wrapper).

**Rule**: When adding new background→EDT dispatch in code that already uses `Util.invokeLater`,
stay consistent and use `Util.invokeLater`. In newer code, `SwingUtilities.invokeLater` or
`PromiseFutures` result listeners are equally acceptable.

**Critical — don't double-dispatch**: If a callback is already going to be dispatched to the
EDT by a wrapper (e.g. `TypeListerThread`'s constructor wraps all `TypeListener` calls with
`Util.invokeLater`), do **not** add another `invokeLater` inside the callback body. The
wrapper is the single dispatch point.

### Stream protocol facade and blocking calls

**Thumbnail/file lookup boundaries:** Myster's network filenames are opaque file-reference
keys. Resolve `(MysterType, filename)` through `FileTypeListManager.getFileItem`, then use
`FileItem.getPath()`; do not construct a filesystem path from a peer's filename. Apply
`AccessEnforcementUtils.isAllowed` before lookup or thumbnail acquisition, as other
file-serving sections do. The helper currently fails open on an access-list read error;
new handlers inherit that policy rather than assuming lookup itself checks membership.

`Thumbnails.summonThumbnail(path, size)` returns a shared, read-only image with width and
height each at most `size`; it may be rectangular, and neither dimension has to reach
the bound. Thumbnail transfer preserves those dimensions without padding. The response
must communicate the actual dimensions for raw-pixel decoding. The blocking facade already
routes native acquisition to the thumbnail platform workers, so server code should call
the facade rather than a provider directly.

Thumbnail results and the shared memory cache hold decoded `BufferedImage` pixels, not
the source PNG bytes. Linux and macOS decode their PNGs before returning from the provider;
Windows returns bitmap pixels. The service may resize any provider's result. Encode the
final image when PNG bytes are needed; retaining an original PNG would require a different
result/cache representation and is only useful when resizing is unnecessary.

The network thumbnail section caps requests at 256 pixels and image bodies at 256 KiB;
the local thumbnail facade still permits 1024 pixels. Image codecs use explicit
`MemoryCacheImageInputStream` / `MemoryCacheImageOutputStream` instances to avoid temporary
ImageIO disk caches and global cache-setting changes. Decode only the framed image body,
never directly from a reusable Myster socket.

`MysterProtocol` is the immutable aggregate of client protocol capabilities. Higher-level network
concepts belong there behind narrow interfaces in `com.myster.net.client`; for example,
`DnsLookupProtocol` is exposed by the aggregate and implemented by `ThreeDnsLookup`. Consumers
should accept the narrowest capability they use. In particular, infrastructure needed to construct
a higher-level capability must receive `MysterStream`/`MysterDatagram` directly rather than the
whole aggregate, avoiding initialization cycles and deferred setters.

Application and feature code invokes reusable TCP connection sections through `MysterStream`, not
section codec classes or `MysterSocketFactory` directly. `ParamBuilder` contains common connection
setup such as the address and independently expected server public key. Section-specific arguments
remain on the individual method. Sections that support connection sharing accept a caller-owned
`MysterSocket`; the caller sequences sections and closes the socket. Address convenience methods may
perform open/call/close for a single section.

Methods on `MysterStream` must be plain blocking calls that throw `IOException`. They must **never**
return `PromiseFuture` or start their own thread. Callers choose their own threading model:

```java
// In a background thread / TypeMetadataCache:
PromiseFutures.execute(() -> stream.getAccessList(addr, type))
              .addResultListener(result -> { ... });

// Several connection sections on one expected-key TLS socket:
ParamBuilder params = new ParamBuilder(addr).withExpectedServerPublicKey(expectedKey);
try (MysterSocket socket = stream.makeStreamConnection(params)) {
    TypeJoinStatus status = stream.redeemTypeInvitation(socket, type, invitationId, code);
    Optional<AccessList> al = stream.getAccessList(socket, type);
}
```

Rationale: returning a `PromiseFuture` from a stream method creates an abstraction inversion —
the method takes over threading decisions that belong to the caller. Any caller that needs async
behaviour can trivially wrap with `PromiseFutures.execute`.

### PromiseFuture Call Listeners

In addition to `addResultListener` / `addExceptionListener`, `PromiseFuture` supports
`addCallListener(CallAdapter<T>)` for handling both result and error in one object. This is
the preferred pattern in UI code where you need to react to both outcomes:

```java
someFuture.addCallListener(new CallAdapter<MyResult>() {
    @Override
    public void handleResult(MyResult result) {
        // called on EDT — update UI here
    }

    @Override
    public void handleException(Throwable e) {
        // called on EDT — show error here
        msg.sayError("Failed: " + e.getMessage());
    }
});
```

`CallAdapter` provides no-op default implementations for both methods, so you only override
what you need. Used extensively in `ClientWindow` for datagram and stream callbacks.

**Threading**: Ordinary promise listeners do not have an implicit dispatcher. Assign one before
registering UI callbacks—normally with `useEdt()`—and do not add another `invokeLater` inside them.

### Examples

#### Good: Run UI Update on EDT

```java
Invoker.invokeOnEDT(() -> {
    label.setText("Updated");
});
```

#### Good: Run Long Operation on Virtual Thread

```java
PromiseFutures.execute(() -> {
    return performLongComputation();
}).useEdt().addResultListener(result -> {
    // Explicitly dispatched on the EDT
    displayResult(result);
}).addStandardExceptionHandler();
```

#### Bad: Blocking the EDT

```java
// DON'T DO THIS - blocks UI thread!
public void actionPerformed(ActionEvent e) {
    String result = performLongComputation(); // Freezes UI!
    label.setText(result);
}
```

### Where to Find It

- `com.general.thread.Invoker` - Thread scheduling utilities
- `com.general.thread.PromiseFuture` - Async operations
- `com.general.thread.PromiseFutures` - Factory for creating promises

---

## Packaged Custom URI Handlers

**Pattern**: The operating-system package owns URI-scheme registration; Java owns delivery and
dispatch after launch.

- macOS declares the scheme in the app bundle's `Info.plist` and receives open-URI events through
  `Desktop.setOpenURIHandler(...)` when `Desktop.Action.APP_OPEN_URI` is supported.
- Windows declares registry values in an installer-owned WiX component. Use the jpackage
  install-scope abstraction, keep the component GUID stable, quote both the installed launcher and
  `"%1"`, and let uninstall remove the component.
- Linux installs a jpackage desktop entry with `%u` and an
  `x-scheme-handler/<scheme>` MIME declaration. Enable `linuxShortcut` so the package also provides
  ordinary launcher integration.

Keep each override in an OS-specific `--resource-dir`, copied from the exact JDK version used to
package the application. Preserve all generated substitutions and test the committed declarations.
Never edit registry keys, invoke `xdg-mime`, or write desktop files during a raw Java/test/server
startup. Command-line/paste handling remains a fallback when desktop registration is unavailable.

---

## FlatLaf Theming

### Rule: Never Hardcode Visual Feedback Colors

All semantic UI colours (errors, warnings, success) must be sourced from the active Look &
Feel rather than hardcoded. Myster uses FlatLaf as its primary L&F, which provides a rich set
of semantic UIManager keys that adapt automatically to light, dark, and custom themes.

**Pattern for Java code**:

```java
// Resolve from the active theme; fall back only for non-FlatLaf L&Fs
Color errorColor = Optional.ofNullable(UIManager.getColor("Actions.Red"))
                           .orElse(new Color(0xDB, 0x58, 0x60));

// Always reset using the theme key, not a hardcoded colour
setForeground(UIManager.getColor("Label.foreground"));
```

**Pattern for SVG icons**: Use the corresponding FlatLaf magic hex in the SVG source.
FlatLaf substitutes the magic hex with the same theme-appropriate colour it would return
for the UIManager key, so icon and text always match.

| Semantic state | UIManager key | SVG magic hex |
| --- | --- | --- |
| Error / destructive | `"Actions.Red"` | `#DB5860` |
| Warning | `"Actions.Yellow"` | `#EDA200` |
| Success | `"Actions.Green"` | `#59A869` |
| Normal foreground | `"Label.foreground"` | `#6E6E6E` |

Full key list: [`FlatIconColors.java`](https://github.com/JFormDesigner/FlatLaf/blob/main/flatlaf-core/src/main/java/com/formdev/flatlaf/FlatIconColors.java)

### Why This Matters

Hardcoded colours break on dark themes (dark-red text is invisible on a dark background)
and on high-contrast themes. The UIManager key approach costs nothing and is correct by
default.

### Where to Find It

- `com.myster.util.ThemeUtil` — theme setup and switching
- `com.general.util.MessageField` — example of `sayError` using `"Actions.Red"`
- See also the SVG icon convention in `myster-coding-conventions.md`

---

*Last updated: March 2026 — added `*Utils`/`*Util` class pattern*


## Partial-download source persistence

`MSPartialFile` stores a UTF header followed by a bitmap extending to EOF. Mutable source metadata
must not grow that header or add a trailer. `DownloadSourceStore` owns a separate append-only `.s`
server-list file and serializes its disk operations off the EDT/download monitor. Close preserves
it; deletion is ordered after queued writes and permanently rejects subsequent source notifications.

Keep stream and DNS construction acyclic: `MysterStreamImpl` exists before the resolver, so download
callers supply `DnsLookupProtocol` in `MSDownloadParams` at invocation. Do not introduce a resolver
setter or global lookup into the stream implementation. Transport peer-key observation uses
`MysterSocket.getAuthenticatedPeerKey()`; its key proves possession on that connection, while
association with an expected CID comes from the resolver/expected-key connection contract.
