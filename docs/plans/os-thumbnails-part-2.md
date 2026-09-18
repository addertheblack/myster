# OS thumbnails — Part 2: thumbnail transfer protocol

## Design Section (for the owner/reviewer)

### 1. Summary

Add one TCP operation that retrieves a thumbnail using a MessagePack request containing
the file's Myster type, filename and maximum dimension. Return a MessagePack header followed by
the image bytes outside MessagePack, supporting PNG and raw 32-bit color. This continues
[OS thumbnails](os-thumbnails.md), whose [implementation](../impl_summary/os-thumbnails.md)
already supplies local OS-generated thumbnails. This milestone makes those thumbnails
available to remote clients; displaying them in client and search windows comes later.

### 2. Non-goals

- Client-window, search-window or list-renderer changes.
- Batching, pipelining, multiplexing, prefetching or a new connection pool.
- UDP thumbnail transfer, chunked transfer, resumable downloads or download-queue integration.
- Format negotiation, capability advertisements or a general media-serving protocol.
- Additional image formats, new thumbnail providers or original-media decoding fallbacks.
- Remote-thumbnail caches, persistent image caches or cache-invalidation protocols.
- Preserving/reusing the providers' original encoded PNG bytes; track that optimization
  separately in [TODO.txt](../../TODO.txt).

### 3. Assumptions & open questions

- **Size bounds both dimensions; the image need not be square.** Send the existing
  generator's aspect-preserving thumbnail without padding, stretching, cropping or
  upscaling. Width and height must each be between 1 and the requested size, inclusive;
  neither dimension needs to reach that size. The response carries the actual dimensions.
- **Two encodings:** “PNG and 32-bit color images” means PNG files or uncompressed
  pixels. Raw pixels use the Java ARGB integer layout in network byte order, with
  straight alpha. The exact proposed layout and MIME identifier are specified below.
- Protocol request sizes are **1–256 pixels**. Both image dimensions are bounded by
  that request, giving a maximum raw body of **256 × 256 × 4 = 262,144 bytes (256 KiB)**.
  Enforce this protocol limit independently of the existing local thumbnail API, which
  supports sizes up to 1024 pixels.
- The server selects the encoding. The client sends only type, filename and size.
- Proposed TCP section **79** is unused in the current source. Recheck before implementation
  if other protocol work has landed. The number is specific to TCP sections.
- No blocking design questions remain under these assumptions.

### 4. Proposed design

Follow the established simple connection-section pattern used by file stats and
hash-to-filename lookup: the standard server dispatcher and acknowledgement, a blocking
client operation, one request/response exchange, then return control to the existing
connection loop. Reuse the existing access checks, data streams and connection ownership.
Thumbnail-specific code handles the request fields and image payload within that pattern.

A caller opens a normal Myster stream connection and asks for one thumbnail. After the
standard section acknowledgement, it sends one MessagePack request. The server checks
access to the type, resolves the filename through the shared-file index, and asks the
existing thumbnail service for the local file's thumbnail.

When an image is available, the server preserves its dimensions, chooses an encoding
and sends a small MessagePack header immediately followed by the encoded bytes.
The client reads exactly the advertised byte count and returns a decoded image. The
connection remains available for another section. When no thumbnail is available, the
server sends an empty MessagePack and no image bytes.

The client operation is blocking, like other stream operations. Future window code can
choose its own background execution and cancellation. OS extraction continues to use
the thumbnail service's existing platform workers and memory cache.

### 5. Architecture connections

The new operation follows the same route as existing file metadata requests. Application
code enters through `MysterStream`; its implementation delegates to the stream codec.
The server's section dispatcher invokes the new handler, which obtains the verified caller
identity and file manager from the existing connection context. Only a file found in the
shared index reaches the local thumbnail API. The image crosses the connection and is
decoded into a caller-owned image, ready for a later UI milestone.

| New / changed thing | Owned / created by | Called / used by | Connects to (existing) |
|---|---|---|---|
| Thumbnail retrieval API | `com.myster.net.client.MysterStream` | Future client/search consumers; protocol tests | `MysterStreamImpl`, `StandardSuiteStream`, caller-owned `MysterSocket` |
| Thumbnail TCP section | Registered by `Myster.addServerConnectionSettings` | Existing server dispatcher | `ServerStreamHandler`, `ConnectionContext`, shared-file index and access enforcement |
| Thumbnail wire codec | `com.myster.net.stream` | Client codec and server handler | `MessagePak`, Myster data streams, JDK image encoding/decoding |
| Local thumbnail source | Injected into the handler at application wiring | Thumbnail server handler | `Thumbnails.summonThumbnail`, existing cache and platform workers |

#### On-wire contract

All fixed-width integers outside MessagePack use network byte order (big-endian).
MessagePack uses the existing Myster framing: a four-byte signed integer giving the
encoded MessagePack byte count, followed by those bytes. Counts must be nonnegative
and within the limits below. A zero-byte frame is not an empty MessagePack map.

| Direction | Sequence |
|---|---|
| Client → server | Four-byte TCP section number **79**, then flush |
| Server → client | Standard one-byte acknowledgement **1**; an unsupported section uses the existing rejection behavior |
| Client → server | One length-prefixed request MessagePack, then flush |
| Server → client | One length-prefixed response MessagePack, then exactly its declared image bytes, then flush |

The client waits for the acknowledgement before sending the request. There is one
request and one response per invocation of section 79. There is no image terminator,
second image-length prefix, schema-version field or new status envelope.

The following field names use `MessagePak` path notation. They are entries in the
root map, not slash-prefixed literal wire keys.

| Request field | MessagePack value | Meaning |
|---|---|---|
| `/type` | Binary, exactly 16 bytes | `MysterType.toBytes()`, the existing type identity |
| `/filename` | Nonempty string | Exact file-reference string returned by Myster listing/search |
| `/size` | Integer, 1–256 | Maximum permitted width and maximum permitted height, in pixels |

Filename is an opaque index key. It is never interpreted as a client-supplied local
path, URL or filename to append to a shared directory. The request carries neither
separate width/height fields nor an encoding preference.

| Successful response field | MessagePack value | Meaning |
|---|---|---|
| `/mimeType` | String | One of the two encodings below |
| `/length` | Integer, 1–262,144 | Exact number of image bytes immediately following this MessagePack |
| `/width` | Integer, 1–requested size | Actual image width in pixels |
| `/height` | Integer, 1–requested size | Actual image height in pixels |

All four fields are required for either encoding. The requested size describes a
square bounding box only. The transmitted image has its actual width and height, with
no added border or padding. PNG's embedded dimensions must match the response header;
raw pixels use the header dimensions to determine the row layout.

**No thumbnail:** an empty root map, followed by **zero** image bytes. Missing/unshared
files, denied access, unsupported source media and an OS thumbnail miss use this same
response. A nonempty response lacking any required field is malformed. Extra header
fields may be ignored; an unknown MIME type is an unsupported response and raises an
I/O error. There is no fallback guess based on image contents or the source extension.

| MIME type | Body format |
|---|---|
| `image/png` | One complete PNG image. The server writes an 8-bit-per-channel RGBA image; decoded dimensions must match the header width and height, each at most the requested size. |
| `image/x-myster-argb32` | Myster-specific uncompressed pixels, with no embedded header. Exactly width × height × 4 bytes, using the response dimensions. |

The raw MIME name is a protocol-local identifier. Each pixel is the unsigned bit
pattern **0xAARRGGBB**, transmitted as **A, R, G, B** bytes. Components are 8-bit sRGB
color with straight (non-premultiplied) alpha; alpha 0 is transparent and 255 is opaque.
Pixels run left to right, rows run top to bottom, and there is no row padding. For
example, ARGB 0x80402010 is sent as bytes 0x80, 0x40, 0x20, 0x10 regardless of host
endianness. Preserve the thumbnail's existing alpha; add no pixels around the image.

For this implementation, encode PNG and use it when it is smaller than the raw pixel
body (`width × height × 4`); otherwise send raw ARGB32. This small deterministic rule
exercises both supported formats without a preference setting or negotiation. Clients
must accept either format within the limits below regardless of which would have been
smaller. A PNG larger than the raw body is sent as raw, keeping the emitted body within
the 256 KiB cap even when PNG overhead makes compression unhelpful.

Request MessagePack frames are limited to **64 KiB**; response MessagePack frames to
**4 KiB**; image bodies of either encoding to **262,144 bytes (256 KiB)**. This image-body
cap excludes the MessagePack header and framing bytes. The raw body must also match
the exact pixel-count length. Reject requests outside 1–256 and validate response width
and height against the requested size before allocation. PNG dimensions must also be
inspected and matched to the header before allocating the decoded image, so a small
compressed body cannot cause an arbitrarily large pixel allocation.

### 6. Key decisions & edge cases

- **PNG compression is available from the current thumbnail API.** The API returns a
  `BufferedImage` containing decoded pixels; it does not retain encoded PNG bytes. Linux
  decodes its freedesktop cache PNG, macOS decodes a temporary Quick Look PNG, and Windows
  supplies bitmap pixels. The shared service then fits the image to the requested bound
  and caches the pixels. Encode that final image as PNG for transmission: PNG compression
  is lossless and works regardless of which provider supplied the pixels. Preserving an
  original PNG could avoid re-encoding only when it already meets the size and wire-format
  requirements; it would require extending the provider/service result and handling cache
  metadata. Linux's smallest cache flavor is 128 pixels, so a 32-pixel request can require
  resizing and re-encoding anyway. Keep this milestone's existing encode-and-send approach;
  original-PNG reuse is deferred in [TODO.txt](../../TODO.txt).
- **Existing access policy applies.** Use `AccessEnforcementUtils.isAllowed` with
  the connection's verified caller identity before any file lookup or generation.
  Use the existing sharing state and index. Denied requests reveal no extra file details.
  The helper's existing fail-open behavior on access-list read errors is inherited;
  changing that policy is separate work.
- **Local image ownership is preserved.** The local cache returns shared read-only
  images. Any pixel-format conversion creates a separate image of the same dimensions.
  The decoded remote image belongs to the client caller; this milestone does not
  introduce a remote cache.
- **Absence is ordinary; malformed traffic is an error.** A valid empty response returns
  null, consistent with the local thumbnail facade. Malformed requests/responses, bad
  lengths, unknown MIME types, corrupt images and truncated bodies raise `IOException`.
  The server terminates malformed requests through the existing connection handling;
  client callers close/discard a connection after a transfer/framing error.
- **Old servers are distinguishable from a thumbnail miss.** Reuse the existing
  `UnknownProtocolException` for a rejected section. Do not turn it into a null image.
- **Framing does not depend on the image decoder or connection closure.** Buffer only
  the bounded body, then decode it. A PNG decoder must never read directly from the
  shared socket, where it could consume the next section's bytes.
- **Both encodings preserve the thumbnail's dimensions.** Raw and PNG responses use
  the same width, height and alpha semantics, including tiny originals and portrait
  images. Rectangles and images smaller than the requested bound are valid. No native
  pixel-memory layout is copied directly onto the network.
- **Work stays off the EDT.** The server calls the existing blocking thumbnail facade;
  that facade already routes native work to its platform-thread pool. Interrupted waits
  preserve interruption and end the section as an I/O failure.

### 7. Acceptance criteria

- [ ] The operation follows the existing simple connection-section pattern: standard
  registration/dispatch, exactly one inherited acknowledgement, blocking client I/O and
  return to the existing connection loop after the response.
- [ ] One request containing only type, filename and size retrieves a thumbnail through
  the normal Myster stream API from a server with this section registered.
- [ ] The response header identifies the encoding, exact body length and actual width/height;
  the image bytes are outside MessagePack and occupy exactly that length.
- [ ] PNG and raw ARGB32 both round-trip color, alpha and actual image dimensions, with
  width and height each no greater than the requested size.
- [ ] Requests accept sizes 1–256 and reject larger values; neither encoding sends or
  accepts an image body above 262,144 bytes. A 256 × 256 raw image fits exactly at that limit.
- [ ] Portrait, landscape and small source thumbnails retain their dimensions and proportions
  without padding, stretching, cropping, upscaling or modifying the cached local image.
- [ ] Missing, unshared, denied or unavailable thumbnails produce the same empty response
  and null client result; denied requests never invoke the thumbnail source.
- [ ] A successful image or empty response can be followed by another section on the
  same connection with no leftover or over-read bytes.
- [ ] Oversized/malformed headers, invalid sizes/dimensions, invalid pixel lengths,
  corrupt/truncated bodies and PNG/header dimension mismatches fail within the documented limits.
- [ ] An unsupported server produces the existing unsupported-protocol exception before
  the client sends the request body.
- [ ] Protocol tests run with an injected thumbnail source and do not require native
  thumbnail services, a desktop session or a public network connection.
- [ ] Client and search window behavior is unchanged in this milestone.

---
## ✦ IMPLEMENTATION DETAILS (for the implementation agent)
---

### 8. Affected files / classes

- **New:** `src/main/java/com/myster/net/stream/ThumbnailProtocolUtils.java` — shared wire constants, request/dimension validation and bounded image response encoding/decoding.
- **New:** `src/main/java/com/myster/net/stream/server/ThumbnailStreamServer.java` — section 79, access/index lookup and injected thumbnail acquisition.
- `src/main/java/com/myster/net/client/MysterStream.java` — blocking, caller-owned-socket `getThumbnail` contract.
- `src/main/java/com/myster/net/stream/client/MysterStreamImpl.java` — delegate the new operation.
- `src/main/java/com/myster/net/stream/client/StandardSuiteStream.java` — send section/request, read acknowledgement/response.
- `src/main/java/com/myster/Myster.java` — register the handler in `addServerConnectionSettings`.
- **New:** `src/test/java/com/myster/net/stream/TestThumbnailProtocolUtils.java` — encoding, dimensions and bounds tests.
- **New:** `src/test/java/com/myster/net/stream/server/TestThumbnailStreamServer.java` — request/access/index/source behavior tests.
- **New:** `src/test/java/com/myster/net/stream/client/TestThumbnailStreamProtocol.java` — client wire contract, errors and repeated-section integration tests.

No changes are needed in the thumbnail providers, local cache, generic MessagePack
implementation, socket dispatcher or Maven dependencies.

### 9. Step-by-step implementation

Use these existing implementations as the structural references:

- `src/main/java/com/myster/net/stream/server/FileStatsStreamServer.java` —
  `getSectionNumber()` and `section(ConnectionContext)`, injected access reader,
  indexed-file lookup and an empty response for denied/missing data.
- `src/main/java/com/myster/net/stream/server/FileByHash.java` — another simple
  read-request/check-access/write-response handler that returns to the dispatcher.
- `src/main/java/com/myster/net/stream/server/ServerStreamHandler.java` — inherited
  `doSection` writes and flushes the standard acknowledgement before invoking `section`.
- `src/main/java/com/myster/net/stream/client/StandardSuiteStream.java` —
  `getFileStats`, `getFileFromHash` and `checkProtocol` establish the synchronous client
  exchange; `MysterStreamImpl` exposes it through the existing facade.

Keep `ThumbnailProtocolUtils` limited to thumbnail serialization, validation and image
conversion. Section dispatch, acknowledgement, socket ownership and threading remain
with the same components that own them for the existing simple sections.

1. **Define the shared contract in `ThumbnailProtocolUtils`.** Keep the section number,
   field names, MIME identifiers and byte/size limits in named constants, including
   `MAX_SIZE = 256` and `MAX_IMAGE_BYTES = MAX_SIZE * MAX_SIZE * Integer.BYTES` (262,144).
   Apply the request size limit on both client and server before acquisition or image
   allocation. `ThumbnailStreamServer`
   may expose `NUMBER` as an alias for the shared section number, following existing handlers.
   Use `MessagePak` and the bounded `readMessagePack(int)` overload. Represent a decoded
   request with a small record containing `MysterType`, filename and size. Check required
   types/values, the 16-byte type identity and the total encoded request limit. Unknown
   extra request fields may be ignored. Do not add a generic protocol framework.

2. **Implement the image response helpers in the same class.** Given a nonnull local
   thumbnail, use its actual width and height and validate that each is between 1 and
   the requested size. Preserve these dimensions throughout encoding; never create a
   requested-size canvas or add padding. If pixel-format conversion is needed, create
   a same-width, same-height `TYPE_INT_ARGB` image and copy pixels at their original
   coordinates. `getRGB`/`setRGB` preserves the intended Java ARGB representation and
   avoids native-buffer or premultiplication assumptions. Provider images should already
   fit; treat an oversized provider result as a local contract failure. Encode the final
   image with `ImageIO.write(image, "png", output)` into a `ByteArrayOutputStream`,
   compare its length with `4L * width * height`, and select PNG or
   raw as specified. Enforce `MAX_IMAGE_BYTES` on the selected body before writing
   a success header. Include the actual width and height in the success header. If the
   PNG writer is unavailable, raw is sufficient; catch only concrete expected encoding
   failures if using raw as a fallback. Prepare the complete body before writing a
   success header, then write the MessagePack, body and flush. Never send a replacement
   empty header after starting a successful response.

3. **Implement response decoding in `ThumbnailProtocolUtils`.** Treat only an empty
   root map (`list("/").isEmpty()`) as a miss. Validate MIME, length in 1–`MAX_IMAGE_BYTES`
   and integer width/height in 1–requested size before allocating, with wide arithmetic for pixel
   counts. Read exactly the bounded body using `readFully`. For raw, enforce a body
   length of width × height × 4 bytes and decode width × height pixels into the declared
   dimensions using big-endian `readInt` or equivalent explicit shifts. For PNG, use a PNG-specific
   `ImageReader` over an in-memory image input stream, inspect width/height before
   `read(0)`, require an exact match to the response header, and reject invalid input.
   Avoid global ImageIO cache settings and dispose readers/streams locally. Return a
   caller-owned `BufferedImage` with 8-bit ARGB pixels.
   Both encodings must reject truncation and never consume subsequent socket bytes.

4. **Add `ThumbnailStreamServer extends ServerStreamHandler`.** Use constructor injection
   for `AccessListReader` and a narrow thumbnail-source functional interface with signature
   `BufferedImage load(Path path, int size) throws InterruptedException`; it can be nested
   in the handler. Reject null dependencies. Override `getSectionNumber()` to return
   `NUMBER` and implement `section(ConnectionContext)`. The inherited `doSection` writes
   and flushes the acknowledgement exactly once; the thumbnail handler must not write
   another acknowledgement. Handle one exchange and return, leaving the socket and
   connection loop to the existing dispatcher. In `section(ConnectionContext)`, decode and
   validate the complete request, call `AccessEnforcementUtils.isAllowed`, check
   `context.fileManager().isShared(type)`, then resolve
   `context.fileManager().getFileItem(type, filename)`. Pass only `FileItem.getPath()` to
   the source. Denied, unshared, missing and null-image cases write the empty response
   and flush. Catch `InterruptedException`, re-interrupt the current thread and throw
   `InterruptedIOException` with the cause. Preserve the existing facade's expected
   miss handling; do not catch all runtime failures and relabel them as misses.

5. **Expose the synchronous client operation.** Add
   `BufferedImage getThumbnail(MysterSocket socket, MysterType type, String filename,
   int size) throws IOException` to `MysterStream`. Delegate through `MysterStreamImpl`
   to `StandardSuiteStream.getThumbnail`. Validate local arguments and the serialized
   request size before writing the section number; invalid local values raise
   `IllegalArgumentException`. Write the number, flush and call
   `StandardSuiteStream.checkProtocol`. Only after acknowledgement, write and flush the
   request; decode the response through the shared helper. Return null for a valid miss.
   Leave a successful/miss socket open and do not spawn a thread or return a promise.
   Callers must sequence operations and close the socket after I/O errors. Existing
   `makeStreamConnection(ParamBuilder)` supplies address/TLS/expected-key behavior;
   no new connection configuration or convenience-overload family is needed.

6. **Wire the section into the application.** In `Myster.addServerConnectionSettings`,
   register the handler beside the other file-serving sections, supplying
   `accessListManager` and `Thumbnails::summonThumbnail`. The server connection thread
   can block while the existing facade routes acquisition to its native-compatible
   workers. Do not bypass that facade or add a second thumbnail executor.

7. **Verify the complete operation and update documentation.** Follow the focused
   verification below. Keep the prior local-generation plan and implementation history
   intact; describe this milestone in its own implementation summary after coding.

### 10. Tests to write

Use JUnit and Mockito, with in-memory Myster streams for wire fixtures. The existing
`src/test/java/com/myster/type/join/TestTypeJoinProtocol.java` demonstrates section/client
tests. For the end-to-end case, use an in-process duplex connection or loopback socket
with the real client codec and server handler, an indexed-file stub and an injected
image source. Dispatch through `ServerStreamHandler.doSection` after reading the section
number, so the test exercises the inherited acknowledgement rather than fabricating it.
Run repeated sections with a timeout so a missing flush or body-length error fails
deterministically.

- **Request and dispatch:** assert section 79, acknowledgement-before-request ordering,
  exactly one acknowledgement, only the three request fields, binary 16-byte type,
  Unicode filename round-trip and valid boundary sizes 1/256. Verify facade delegation by invoking `MysterStream` in
  client tests. Rejection must raise `UnknownProtocolException` and write no request.
- **Independent raw fixture:** use distinct alpha/red/green/blue bytes and a small
  rectangular multirow image. Assert header dimensions, width × height × 4 body length,
  exact byte order and traversal independently of the writer, including signed Java
  ints, transparency and premultiplied source conversion.
- **PNG and format selection:** a compressible image selects PNG; a tiny image selects
  raw when PNG overhead is larger. Check PNG signature and IHDR bit depth/color type
  (8-bit RGBA), and round-trip pixel values, alpha and actual dimensions through both
  formats. Verify format selection compares PNG length with the actual pixel count and
  falls back to raw when PNG overhead would exceed that length or the image-body cap.
- **Maximum image body:** accept a 256 × 256 raw fixture of exactly 262,144 bytes;
  reject a declared 262,145-byte body before reading or allocating it for either MIME
  type. Reject width or height 257 even when the compressed PNG body is small.
- **Unpadded dimensions:** landscape, portrait, square and tiny original cases verify
  that encoded/decoded dimensions equal the provider result. Include a 64 × 32 image
  requested at size 64 and a 12 × 7 image requested at size 64: both retain those exact
  dimensions in PNG and raw form. Confirm no border, resampling or source mutation.
- **Access and lookup:** public and authorized-private requests work; unauthorized and
  unidentified-private requests return exactly the empty response and never perform
  file lookup/acquisition. Unknown/unshared types, missing references and null source
  results return the same miss. A path-like reference absent from the index cannot
  cause acquisition of an arbitrary local file.
- **Request validation:** missing/wrong-type fields, invalid type length, empty filename,
  size 0/negative/257/1024, integer overflow attempts, oversized/negative/truncated MessagePack
  frames. Invalid client arguments emit no section bytes; invalid server requests never
  call the source.
- **Response validation:** nonempty incomplete headers, unknown MIME, noninteger or
  negative/zero/oversized lengths, missing/noninteger/zero/negative/over-size width or
  height, raw length mismatch, corrupt/truncated PNG, a non-PNG body labeled PNG,
  PNG/header dimension mismatches and large declared PNG dimensions. Rectangular PNGs
  matching valid header dimensions must succeed. Reject huge dimensions before raster
  allocation. Include a body whose bytes arrive in partial reads so `readFully` behavior matters.
- **Connection framing:** image → image, miss → image and image → another known section
  on one connection. Verify the next acknowledgement/sentinel remains intact after
  either decoder. These tests must exercise the real request/response framing.
- **Interruption:** a source throwing `InterruptedException` produces an I/O failure,
  restores the waiting thread's interrupt flag and emits no partial success response.
  Clear the interrupt in test cleanup.

During implementation, run focused tests first, for example:

```bash
mvn -q -Djava.awt.headless=true -Dtest=TestThumbnailProtocolUtils,TestThumbnailStreamServer,TestThumbnailStreamProtocol,TestMysterStreamImpl,TestThumbnails,TestFreedesktopThumbnailCache test
```

Then perform the normal project build and full `mvn -q test` once to check integration.
Record any environment limitations accurately. For a manual smoke test, launch a local
Myster server with an indexed JPEG/video already supported by its OS provider, fetch
16/32/64/256-pixel thumbnails through `MysterStream` from a worker, and run another section
on the same socket. Inspect decoded dimensions/alpha, confirm a rectangular thumbnail
has no added padding and test a missing filename.
Use a temporary harness or IDE expression; no new production preview UI is required.

### 11. Docs / Javadoc to update

- `MysterStream.getThumbnail`: blocking/threading contract, size range and maximum-dimension
  semantics (1–256 pixels), unpadded output, null versus unsupported-protocol/I/O errors, caller-owned
  image/socket and sequencing/error cleanup.
- `ThumbnailStreamServer` and `ThumbnailProtocolUtils`: exact section/framing contract,
  field names, actual width/height, MIME strings, raw byte/alpha order, limits, empty
  response and injected source semantics. Keep the wire specification visible in class-level Javadoc.
- `docs/design/Myster OS Thumbnail Integration Design.md`: add the remote protocol
  connection, its 256-pixel/256-KiB limits and unpadded-image semantics. Distinguish the
  network cap from the local generator's existing 1024-pixel limit.
- `docs/codebase-structure.md`: add thumbnail retrieval to the TCP operations and connect
  it to the existing thumbnail package.
- `docs/conventions/myster-important-patterns.md`: record the important thumbnail/index
  boundary findings discovered during planning; keep these consistent if implementation
  exposes an additional relevant constraint.
- **After implementation:** write `docs/impl_summary/os-thumbnails-part-2.md` with the
  implemented behavior, verification results and any deviations from this plan.
