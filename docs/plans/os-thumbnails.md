# OS thumbnails and standalone preview

## 1. Goal

Implement the supplied OS thumbnail integration design as a reusable package with
`Thumbnails.summonThumbnail(Path, int)` and a standalone folder/size preview grid.

## 2. Scope

Windows Shell through FFM, macOS Quick Look, Linux freedesktop PNG cache and optional
D-Bus thumbnail service. No MCList integration or permanent Myster thumbnail cache.

## 3. API

The blocking method returns a `BufferedImage` fitting the requested square without
cropping, stretching, or upscaling; null means unavailable. Reject use on the EDT.
Provide a cancellable `PromiseFuture<BufferedImage>` companion for UI callers.
Internal `ThumbnailProvider.load` returns `Optional<BufferedImage>`; an empty result
means the platform cannot supply a thumbnail. The service adapts this to the public
facade's nullable result.

## 4. Architecture connections

New `com.myster.thumbnail` package. Platform providers perform blocking acquisition;
the facade handles scaling, file-state-aware bounded memory caching and concurrent
request coalescing. Async calls use a small daemon platform-thread pool because COM
apartments belong to native threads. A standalone Swing grid uses promise listeners
on the EDT and limits outstanding requests.

Use `PromiseFutures.execute(callable, executor)` for scheduling and result delivery.
The shared helper must skip a callable cancelled before it starts and preserve the
executing thread's interrupt status when the callable throws `InterruptedException`.

## 5. Decisions

Log the source and elapsed time of each result, including memory/cache hits and
unavailable thumbnails. Linux diagnostics include the requested size/flavor, service
error signals and completion without a usable cache entry. Use dbus-java with the
JDK Unix socket transport; preserve its service registration in the shaded jar.
Bound external process/service waits.
Serialize Linux service generation requests while retaining parallel cache reads:
the installed Tumbler service misroutes completion notifications for concurrent queues.
Acquisition remains OS-only, matching the supplied design. ImageIO reads OS-produced
PNG files; it does not decode the original media as a fallback.

## 6. Failure behavior

Unsupported/unreadable files return null and log the reason. Native resources,
processes, temporary files, and D-Bus connections must be released. A changed folder
or size cancels stale UI requests and prevents stale callbacks changing the grid.

## 7. Acceptance criteria

16/32/64 pixel requests preserve aspect ratio. Repeat requests reuse memory;
changed files invalidate entries. All JPG/JPEG, AVI, MKV and MP4 files directly in a chosen folder
appear in a scrollable grid whose cells update in completion order. Logs identify
the actual acquisition path. UI remains interactive while loading.

---

## ✦ IMPLEMENTATION DETAILS

## 8. Files

Add facade/service, provider interface, platform providers, image helper, preview
main and focused tests. Update pom.xml for D-Bus/FFM packaging and design/summary
documentation.

## 9. Steps

1. Implement sizing, caching, coalescing and async entry point.
2. Implement OS providers and resource cleanup. Serialize Linux cache-miss generation,
   recheck the cache after waiting and release the generation permit on all exits.
3. Add folder/size controls and progressive grid accepting JPG/JPEG, AVI, MKV and MP4
   extensions case-insensitively, with matching prompts and empty-folder text.
4. Verify behavior, document usage and platform verification limits.

## 10. Verification

Test dimensions, invalid arguments/EDT use, missing/unsupported files, freshness,
coalescing and cancellation isolation. Validate freedesktop PNG metadata and stale
cache rejection. Build in IntelliJ and run Maven tests. Exercise the Linux provider
and preview locally where the desktop is available; Windows/macOS require host
smoke tests.
Verify concurrent callers against an empty Linux cache at 512 and 1024 pixels; all
files supported by the desktop service must succeed on the first pass.

## 11. Documentation

Document public method threading, null/cancellation, image ownership, limits and
cache contracts. Update the design with the implemented API and test-main usage.
Write `docs/impl_summary/os-thumbnails.md` with results and remaining host checks.
