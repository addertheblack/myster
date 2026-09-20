# Myster OS Thumbnail Integration

## Goal

Display file thumbnails in Myster's `MCList` using the **operating system's existing thumbnail infrastructure** rather than teaching Myster how to decode every image, video, PDF, document, etc.

The desired semantic operation is:

> Given a local file and desired thumbnail size, ask the operating system/desktop environment for the thumbnail it would normally display for that file.

Myster should not directly parse OS thumbnail-cache database formats.

Thumbnail acquisition must never block the Swing EDT. UI consumers use the asynchronous API;
background callers may use the blocking convenience method.

## Implemented API and standalone preview

The implementation lives in `com.myster.thumbnail`:

```java
// Worker threads only. A BufferedImage is also a java.awt.Image.
BufferedImage image = Thumbnails.summonThumbnail(path, 64);

// Swing callers:
Thumbnails.summonThumbnailAsync(path, 64)
        .useEdt()
        .addResultListener(image -> { /* null means no thumbnail; otherwise update the UI */ })
        .addExceptionListener(error -> { /* unexpected task failure */ });
```

Sizes are 1–1024 pixels, commonly 16, 32 or 64. The image fits inside the requested
square without cropping, padding, stretching or upscaling. Returned images are shared
and must be treated as read-only. The blocking method rejects EDT calls and declares
`InterruptedException`; ordinary missing-file, unsupported and I/O failures return null.

Four daemon platform threads perform acquisition. Platform threads keep Windows COM
initialization, extraction and cleanup on one native thread. A `BoundedExecutor` wrapping
a virtual-thread executor limits concurrency but does not guarantee that native-thread
identity: virtual threads are pinned during an individual native/FFM call and can resume
on a different carrier between calls. Preserve the platform workers unless Windows
acquisition is given its own platform-thread executor. See
[COM thread initialization](https://learn.microsoft.com/en-us/windows/win32/learnwin32/initializing-the-com-library)
and [Java virtual-thread scheduling and pinning](https://docs.oracle.com/en/java/javase/26/core/virtual-threads.html).

The memory cache retains up to 256 images / approximately 16 MiB of pixels,
with keys covering absolute normalized
path, requested size, file identity, creation/last-modified times and length. Concurrent
matching requests share extraction. Cancelling a caller makes its result moot and skips
queued work; an extraction already running can finish for other callers and the cache.
The asynchronous facade delegates to `PromiseFutures.execute(callable, executor)`,
which checks cancellation before invoking queued work and preserves interrupt status
when forwarding `InterruptedException`.

Run `com.myster.thumbnail.ThumbnailPreview.main()` in IntelliJ, or after building the
shaded jar:

```bash
java --enable-native-access=ALL-UNNAMED -cp bin/MysterBuild.jar \
  com.myster.thumbnail.ThumbnailPreview

# Optional initial folder and size:
java --enable-native-access=ALL-UNNAMED -cp bin/MysterBuild.jar \
  com.myster.thumbnail.ThumbnailPreview "/path/to/pictures" 64
```

Choose a folder, choose the size, and click **Load / reload**. The preview scans the
folder's immediate files using the platform extension whitelist, shows placeholders,
and replaces each cell as its request completes. Four requests are outstanding at
once. Per-cell timings include queueing; the footer shows completion counts and total
elapsed time. Reloads reuse the memory cache. Changing folder/reloading cancels stale
UI requests; callbacks from an older run cannot update the current grid.

`ThumbnailPlatform` holds separate immutable extension whitelists:

| Platform | Allowed extensions |
| --- | --- |
| Windows | jpg, jpeg, avi, mkv, mp4 |
| macOS | jpg, jpeg, mp4 |
| Linux | jpg, jpeg, avi, mkv, mp4 |
| Other | none |

Matching uses the final filename extension, ignoring case. `ThumbnailService` rejects
unlisted extensions before filesystem/cache checks or provider calls, including direct
API requests. `Thumbnails.isAllowed(Path)` and `allowedExtensions()` expose the same
policy without filesystem or native access; the preview uses it for filtering and
format labels. Editing a platform's set is the single point for changing its policy.
Whitelisting permits an attempt; it does not guarantee codec or thumbnail-handler support.

`java.util.logging` INFO messages identify memory reuse, shared requests, Windows Shell
cache/extraction, Quick Look output, Linux D-Bus requests and the exact freedesktop cache
PNG used. Acquisition remains OS-only: a machine without a suitable OS provider can
show **Unavailable** for any accepted file. Video thumbnails depend on installed OS
thumbnail handlers and codec support. ImageIO reads the OS-produced PNGs, with no original-file
decoding fallback and no additional Myster disk cache.

### Implementation and validation notes

- **Windows:** FFM calls `IShellItemImageFactory`, tries thumbnail-cache-only first, then
  allows Shell extraction. GDI pixels are copied into a premultiplied ARGB image, and COM,
  HBITMAP and DC resources are released. Native access is enabled in the shaded jar
  manifest and packaged launcher. For an IntelliJ/classpath launch on Windows, set
  `--enable-native-access=ALL-UNNAMED` in VM options.
- **macOS:** isolated Quick Look output directories, a 15-second process timeout,
  forced termination on timeout and temporary-file cleanup. Accepting an extension
  in the preview does not establish Quick Look support. On macOS 13.7.8 without an
  MKV-specific generator, tested MKVs left `qlmanage` running without output until
  termination, while JPEGs succeeded. The macOS whitelist excludes MKV and AVI,
  even if a suitable extension is installed. The preview omits these files and
  direct API requests return unavailable before reaching Quick Look. Linux and
  Windows retain MKV and AVI in their respective whitelists.
- **Linux:** read valid shared cache entries first, then detect a running or activatable
  `Thumbnailer1` service and request the appropriate flavor. A single interruptible
  generation permit serializes service requests; cache reads remain parallel and are
  repeated after acquiring the permit. Tumbler 4.18.1 was observed sending `Ready`
  for a different request's URI and then `Finished` without generating the requested
  file when Myster queued several requests concurrently. Wait for its `Finished`
  signal, including signals arriving before `Queue` returns, for at most 15 seconds.
  D-Bus method replies use dbus-java's 20-second default timeout. Connections close after
  each request. PNG validation checks URI, modification time and optional source size.
  Both standard whole-second times and Tumbler's fractional-second times are accepted;
  fractional timestamps are checked at their recorded precision.
  Requests and timeouts log the size/flavor. Service `Error` signals log their code and
  message; `Finished` only means processing ended and is also sent after failures.
  Completion without a usable cache entry is logged explicitly.
- **Packaging:** dbus-java 5.2.1 core and the JDK Unix-socket transport are included.
  The shade plugin merges service registrations, including the transport provider.
- **Host coverage:** Linux cache/service acquisition and the preview have been exercised
  against a real Tumbler service. macOS JPEG acquisition and preview result delivery
  succeeded in a host smoke test; MKV timeout behavior was reproduced independently
  of Java. Successful macOS video extraction with a suitable extension and Windows
  COM/bitmap behavior still need host testing.

Primary API references: [Windows GetImage](https://learn.microsoft.com/en-us/windows/win32/api/shobjidl_core/nf-shobjidl_core-ishellitemimagefactory-getimage),
[freedesktop thumbnail creation](https://specifications.freedesktop.org/thumbnail/latest/creation.html),
[cache freshness](https://specifications.freedesktop.org/thumbnail/latest/modifications.html),
[Tumbler D-Bus definition](https://github.com/xfce-mirror/tumbler/blob/master/tumblerd/tumbler-service-dbus.xml.in),
and [dbus-java](https://github.com/hypfvieh/dbus-java).

## Remote thumbnail transfer

`MysterStream.getThumbnail(socket, type, filename, size)` retrieves one thumbnail through
TCP section **79**. It is a blocking operation on a caller-owned connection; callers
choose background execution and sequence their own sections. It returns a caller-owned
`BufferedImage`, or null when the file is denied, unshared, missing or has no thumbnail.
The handler follows the existing simple connection-section pattern, including the
inherited acknowledgement and return to the connection dispatcher after each response.

Network requests accept **1–256 pixels**, independently of the local API's 1024-pixel
limit. Size bounds both dimensions. The image keeps its actual width and height without
padding, cropping, stretching or upscaling. Image bodies are limited to
**256 × 256 × 4 = 262,144 bytes (256 KiB)**; framing and headers are separate.

After the standard section acknowledgement, the client sends a length-prefixed
MessagePack with `/type` (16-byte binary type CID), `/filename` (opaque shared-index
reference) and `/size`. The server responds with a length-prefixed MessagePack containing
`/mimeType`, `/length`, `/width` and `/height`, immediately followed by exactly `/length`
image bytes outside MessagePack. An empty response map has no body. Request headers
are capped at 64 KiB, response headers at 4 KiB.

The two MIME types are `image/png` and `image/x-myster-argb32`. Raw pixels are straight-alpha
sRGB ARGB integers in big-endian order (A, R, G, B bytes), row-major from the top left,
with no row padding; their body length is exactly width × height × 4. PNG output is
8-bit RGBA and must have the header's dimensions. The server encodes the final thumbnail
as PNG and uses it when smaller than raw pixels, otherwise sends raw. The existing
providers/cache retain decoded pixels; preserving original provider PNG bytes is a
deferred optimization in [TODO.txt](../../TODO.txt).

`ThumbnailStreamServer` checks the connection's authenticated caller identity through
`AccessEnforcementUtils`, checks sharing and resolves the reference through the file
index before calling its injected `Thumbnails.summonThumbnail` source. Native acquisition
still runs on the existing thumbnail workers. The access helper's current policy,
including fail-open access-list read errors, is unchanged; the endpoint access-control
audit is tracked separately in TODO.

`ThumbnailProtocolUtils` handles bounded serialization and image conversion. Clients
validate dimensions/lengths before image allocation, check PNG chunk framing/checksums
and decode only the buffered body. Success and misses leave the connection positioned
for another section. Malformed/unsupported responses raise `IOException`; callers discard
the connection after an I/O error. Old servers rejecting section 79 produce the existing
`UnknownProtocolException`. ImageIO streams use memory caches without changing global
ImageIO settings. Part 3 adds a connection-scoped, bounded client preview cache and a
visibility-gated details-pane consumer. File-list and search-result consumers remain deferred
to Parts 4 and 5. See the [Part 2 plan](../plans/os-thumbnails-part-2.md) and
[Part 3 plan](../plans/os-thumbnails-part-3.md) for the protocol and client integration
contracts.

## Common abstraction

Hide all platform behavior behind a small platform-independent interface, conceptually:

```java
PromiseFuture<BufferedImage> Thumbnails.summonThumbnailAsync(Path file, int size);
```

This uses Myster's existing `PromiseFuture` convention. Internal platform providers
perform blocking acquisition on the facade's workers.

`ThumbnailProvider.load(Path, int)` returns `Optional<BufferedImage>`: a present
image still needs fitting, and an empty result means no thumbnail / unsupported.
It can throw `IOException` or `InterruptedException`. The service converts an empty
result or I/O failure to the public facade's nullable result; interruption propagates.

The `MCList` should know nothing about COM, Quick Look, D-Bus, freedesktop caches, etc.

Generated images can subsequently be converted to a Java2D/device-compatible image for efficient repeated rendering.

## Windows

### Preferred implementation

Use the Windows Shell thumbnail API through Java's Foreign Function & Memory API (FFM/Panama).

FFM is a final supported Java API since JDK 22, so it is already available on Myster's current Java baseline.

Do **not** introduce JNI unless FFM proves impractical.

Use:

```text
SHCreateItemFromParsingName(...)
          ↓
IShellItem / IShellItemImageFactory
          ↓
IShellItemImageFactory::GetImage(...)
          ↓
HBITMAP
          ↓
BufferedImage
```

`IShellItemImageFactory::GetImage` is specifically intended to retrieve Shell thumbnails/icons. Windows can satisfy requests from its thumbnail cache or invoke installed thumbnail handlers to generate one. Microsoft explicitly warns that extraction can be expensive and should not happen on the UI thread.

### FFM/COM implementation details

This requires a small amount of COM plumbing:

```text
CoInitializeEx
SHCreateItemFromParsingName
Query/use IShellItemImageFactory
GetImage
copy HBITMAP pixels into BufferedImage
DeleteObject(HBITMAP)
Release COM object
CoUninitialize
```

Because `IShellItemImageFactory` is a COM interface, FFM will need to call through its vtable rather than simply downcalling an exported C function.

Keep this code isolated inside something such as:

```text
WindowsThumbnailProvider
```

Do not expose COM types outside the implementation.

### Prototype risk

Before committing the Windows implementation, prototype:

1. COM apartment/thread behavior when thumbnail handlers are invoked.
2. `HBITMAP` → Java `BufferedImage` conversion.
3. cleanup/lifetime handling under FFM.
4. behavior when the Shell has no thumbnail provider.

The pixel conversion itself is not expected to be a major architectural problem; Java2D can accept the resulting image and convert it once into an efficient rendering format.

## macOS

### Preferred implementation

Use Apple's existing `/usr/bin/qlmanage` Quick Look command-line utility rather than calling Objective-C APIs through FFM.

Example:

```bash
/usr/bin/qlmanage -t -s 256 -o <temp-directory> <file>
```

`-t` requests a Quick Look thumbnail and `-s` specifies the desired size. Quick Look then uses the same OS infrastructure and installed thumbnail generators used by macOS.

Implementation:

```text
ProcessBuilder
      ↓
/usr/bin/qlmanage
      ↓
temporary thumbnail image
      ↓
ImageIO.read(...)
      ↓
BufferedImage
```

Run it off the EDT.

The process-launch overhead is acceptable because thumbnails are asynchronous and should subsequently be cached by Myster and/or Quick Look.

### Why not FFM?

Modern Quick Look APIs are Objective-C/Swift-oriented. It is technically possible to drive the Objective-C runtime using FFM and `objc_msgSend`, but that adds substantial complexity for very little benefit.

There is also an older C Quick Look API, but it is deprecated and should not be the basis of new Myster code.

Therefore:

```text
macOS → qlmanage
```

unless testing uncovers a serious problem with it.

## Linux

Linux requires capability detection rather than assuming one universal implementation.

### Standard cache

Freedesktop defines a shared thumbnail-cache standard. The cache normally lives at:

```text
$XDG_CACHE_HOME/thumbnails/
```

or:

```text
~/.cache/thumbnails/
```

with standardized size directories including:

```text
normal/
large/
x-large/
xx-large/
```

The cache exists specifically so different applications can share generated thumbnails.

Myster should avoid inventing another incompatible thumbnail-cache format unnecessarily.

### D-Bus thumbnail service

There is a D-Bus interface commonly seen as:

```text
org.freedesktop.thumbnails.Thumbnailer1
/org/freedesktop/thumbnails/Thumbnailer1
```

with operations such as `Queue(...)`.

Tumbler/XFCE implements this interface and writes results into the standard thumbnail cache.

However, do **not** assume this service exists on every Linux desktop.

Preferred strategy:

```text
Check session D-Bus
        ↓
org.freedesktop.thumbnails.Thumbnailer1 available?
        ↓ yes
request thumbnail
        ↓
read resulting standard cached thumbnail
```

If unavailable, fall back gracefully.

### Java/D-Bus

Use a pure-Java D-Bus implementation such as `dbus-java`, rather than FFM into `libdbus`.

Conceptually:

```text
Myster
  ↓
dbus-java
  ↓
D-Bus wire protocol
  ↓
desktop thumbnail service
```

This avoids native bindings entirely.

The implementation uses dbus-java 5.2.1 with its JDK Unix-domain socket transport.
Its service-loader registration is retained in the shaded jar; no JNI library is added.

### Linux fallback

If no compatible thumbnail service exists:

```text
return no thumbnail
```

Initially, do **not** add dependencies on random command-line programs such as `ffmpegthumbnailer`, ImageMagick, etc.

Those can be considered later as optional fallbacks, but the first implementation should exploit what the desktop environment already provides rather than turn Myster into its own media thumbnail stack.

## Caching

There are potentially two caches:

```text
OS/desktop thumbnail cache
          ↓
Myster thumbnail cache
          ↓
MCList rendering
```

Myster maintains a bounded in-memory cache of loaded `BufferedImage` instances so
repeated requests do not repeatedly invoke the platform provider.

Do not initially implement a second permanent disk cache unless measurements show it is useful; the operating systems already cache the expensive extraction/generation operation.

Cache keys should at minimum account for:

```text
Path/file identity
requested thumbnail size
file modification state
```

The exact strategy can be designed during implementation.

## Threading

Thumbnail acquisition must never run synchronously from Swing painting or the EDT.

Expected flow:

```text
MCList needs thumbnail
        ↓
cache lookup
        ↓ miss
schedule asynchronous request
        ↓
platform ThumbnailProvider
        ↓
image arrives
        ↓
cache it
        ↓
request repaint on EDT
```

Multiple simultaneous requests for the same file/size should ideally be coalesced.

Cancellation should follow Myster/Nuggu conventions where useful, particularly when list contents change rapidly.

## Failure behavior

Thumbnailing is cosmetic.

Failure to obtain a thumbnail must never interfere with:

- file listing;
- downloading;
- sharing;
- metadata handling;
- startup.

The UI simply displays the existing generic/file-type icon when no thumbnail is available.

Unsupported formats are normal, not exceptional application failures.

## Platform strategy summary

```text
Windows
    IShellItemImageFactory
    accessed using Java FFM
              │
              ▼
           HBITMAP
              │
              ▼
        BufferedImage


macOS
       /usr/bin/qlmanage
              │
              ▼
       generated image file
              │
              ▼
        BufferedImage


Linux
       session D-Bus
              │
              ▼
org.freedesktop.thumbnails.Thumbnailer1
        if available
              │
              ▼
freedesktop thumbnail cache
              │
              ▼
        BufferedImage

       otherwise:
        no thumbnail
```

## Guiding design principle

Myster should **ask the user's operating system to understand the file**.

If Explorer, Finder/Quick Look, or the Linux desktop already knows how to display a useful thumbnail for a file, Myster should reuse that capability.

Myster should not acquire large media/document parsing dependencies merely to draw previews in `MCList`.

The deliberately platform-specific implementations should remain small and isolated behind one platform-neutral thumbnail-provider abstraction.

### Client preview concurrency

The client details pane uses a `RemoteThumbnailCache` with one current promise and an EDT-owned
LRU cache (128 combined image/miss/error entries, at most 8 MiB of decoded pixels). Misses suppress
requests for 30 seconds and transport failures for 5 seconds; retries happen on later demand
changes, with no automatic retry loop. Unsupported endpoints are remembered until reset, bounded
to 128 entries. Completed exact or larger requests can satisfy smaller previews.

`RemoteThumbnailTask` extends `AbstractCancellableCallable` and holds a cache-owned monitor through
connection, read and try-with-resources cleanup. Cancelling a promise makes its result moot;
queued tasks check cancellation under the monitor and skip I/O. Running reads may finish. The
monitor keeps transfers from one cache serial and survives connection resets. Each client window
has its own cache and monitor, so a slow server does not block previews in other windows. This is
a per-window limit; separate windows connected to the same server can each have a transfer.
No custom executor, queue or completion counter is needed. Ordinary promise listeners use the EDT; resizing uses a cancellable `PromiseFutures.delay`.

The controller owns the preview cache. Hiding cancels current demand; closing also cancels the
delay and detaches the pane callback. Connection resets discard cached outcomes. Requests use the
already resolved address from the type-list connection. Part 4 must design shared list demand
when implemented; Part 3 does not expose unused list priorities or multi-consumer handles.
