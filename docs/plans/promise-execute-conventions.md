# Standard background task submission

## 1. Goal

Document `PromiseFutures.execute(Callable)` and `execute(Callable, Executor)` as
Myster's standard SwingWorker replacement and audit duplicate task plumbing.

## 2. Scope

Use the existing helper for finite background work that reports completion/failure.
Preserve callback-based protocol adapters, actor coordination, streaming progress
and explicitly owned long-lived workers.

## 3. Conventions

Default to virtual-thread execution; supply an executor when concurrency limits or
native thread affinity require one. UI listeners explicitly select the EDT and
register an exception handler. Cancellation-sensitive operations implement
`CancellableCallable` or use existing cancellation ownership.

## 4. Architecture connections

Audit `com.general.thread`, browser UI workers, startup, stream clients, search,
tracker/3DNS and server/transfer workers. Blocking protocol operations belong below
the promise boundary; avoid nested worker/promise layers around already-running work.

## 5. Decisions

The Windows thumbnail executor remains a platform-thread pool. A constructor that
adapts asynchronous callbacks to a promise does not need an executor. Progress and
cancellation lifetimes must remain correct when simplifying workers.

## 6. Compatibility

Keep wire formats, result ordering, UI dispatch and transfer ownership unchanged.
Limit interface changes to internal callers found by the audit.

## 7. Acceptance criteria

Conventions clearly name both preferred entry points. Equivalent finite task wrappers
use the helper, and all retained manual asynchronous paths have a concrete reason.
Tests cover changed result/error/cancellation behavior and the project builds.

---

## ✦ IMPLEMENTATION DETAILS

## 8. Affected files

Threading conventions, startup task submission, stream batch metadata, associated
search caller, and any legacy browser workers included in the final audit scope.

## 9. Steps

1. Inventory promise constructors, executors, thread subclasses and UI result delivery.
2. Update conventions and replace equivalent background task wrappers.
3. Keep protocol I/O synchronous when the caller already runs through `execute`.
4. Record retained callback/service/lifecycle cases and verification results.

## 10. Verification

Run focused tests for changed paths, IntelliJ compilation and the Maven suite.
Verify progressive results and exception propagation for metadata batches.

## 11. Documentation

Update conventions, affected Javadoc and a matching implementation summary with
concrete audit findings and any legacy migration boundaries.
