# AsyncTaskTracker completion context

## Design Section

### 1. Summary

Make `AsyncTaskTracker<R>` implement `AsyncContext<R>` and wrap an enclosing promise context. Publishing an explicit result or exception completes that parent first, then cancels all owned work. Preserve the existing promise semantics: cancelling a completed child changes what later listeners observe, without replaying delivered listeners.

### 2. Non-goals

- Changing `PromiseFutureImpl` cancellation or listener semantics.
- Replacing the promise framework or changing 3DNS routing algorithms.
- Redesigning the download-source persistence format.

### 3. Assumptions & open questions

The discussion authorizes this refactor and caller migration. `AsyncContext` is the existing interface meant by the proposed task context. Completion and cancellation must be callable from any thread; task launch and natural-exhaustion callbacks remain invoker-confined.

### 4. Proposed design

`create` requires `AsyncContext<R>` and registers the new tracker with that parent for cancellation. `setCallResult` forwards one terminal outcome and then stops child work. `cancel` completes a still-pending parent as cancelled and stops child work; cleanup after explicit success must not overwrite the published parent answer. Tracked work added after terminal completion is immediately cancelled.

`doAsync` counts work toward natural exhaustion. `trackForCancellation` owns cleanup-only resources without counting them. Exhaustion invokes its listener once and does not invent a successful answer. That listener must explicitly choose a result, exception or cancellation. A tracker with no launched work requires an explicit empty-case result.

Timers that compete to produce an explicit outcome can be counted children. Deadlines that must not prevent natural exhaustion can be cleanup-only resources. Either kind is automatically cancelled after tracker completion.

### 5. Architecture connections

| Changed thing | Owned / created by | Called / used by | Connects to (existing) |
|---|---|---|---|
| Generic completion-owning tracker | Parent `AsyncContext<R>` | Child callbacks and exhaustion listener | Existing PromiseFuture cancellation semantics |
| Crawl-cycle promises | MultiSourceHashSearch | Restart/stop lifecycle | AsyncNetworkCrawler and repeated timers |
| Resolver completion | ThreeDnsLookup | Exact result, bounded result, exhaustion | Tracked cleanup-only deadline |
| Download recovery jobs | DownloadSourceRecovery | DNS/setup result or deadline | Explicit-result cleanup |

Audit: ThreeDnsLookup and DownloadSourceRecovery already have enclosing contexts. MultiSourceHashSearch has two production `SimpleTaskTracker` parents; convert the outer scheduled crawl to a real promise and connect the nested crawl directly to its actual result context. AsyncNetworkCrawler only borrows a tracker and does not own its result type, so it uses `AsyncTaskTracker<?>`.

### 6. Key decisions & edge cases

- Publish the parent result before cancelling children; internal cleanup is not parent cancellation.
- Use terminal guards before invoking callbacks to make reentrant cancellation safe.
- Preserve delivered child outcomes while accepting that late child listeners see cancellation.
- Cancellation/explicit completion do not fire exhaustion callbacks; natural exhaustion itself does not cancel children until its listener decides an outcome.
- A synchronous failure to launch a child must not strand the task count.
- Empty crawls must complete instead of waiting forever for a task that was never launched.
- Completed download preparation hands the connection to its download owner. Cleanup of the preparation promise must not stop an established transfer.

### 7. Acceptance criteria

- [ ] Tracker can be passed as `AsyncContext<R>` and publishes parent results/exceptions.
- [ ] Explicit completion cancels counted timers and cleanup-only resources automatically.
- [ ] Parent answer survives cleanup; completed children retain intended late-cancellation behavior.
- [ ] Cancellation, exhaustion, empty work, late registration and reentrant callbacks are covered.
- [ ] Every production caller has a real parent context and typed tracker usage.
- [ ] Existing crawler, resolver and download tests pass.

---
## ✦ IMPLEMENTATION DETAILS (for the implementation agent)
---

### 8. Affected files / classes

- `com/general/thread/AsyncTaskTracker.java`: generic parent context and completion/cleanup lifecycle.
- `com/myster/search/AsyncNetworkCrawler.java`: borrowed wildcard tracker; stop scheduling after termination.
- `com/myster/search/MultiSourceHashSearch.java`: real promises for crawl lifecycle and nested crawl, empty-network handling.
- `com/myster/threedns/ThreeDnsLookup.java`: typed context tracker and automatically owned deadline cleanup.
- `com/myster/net/stream/client/msdownload/DownloadSourceRecovery.java`: typed trackers and completion-owned timer lifecycle.
- Tests in `com/general/thread`, `com/myster/search`, `com/myster/threedns` and download tests as necessary.

### 9. Step-by-step implementation

1. Add the generic AsyncContext implementation, terminal/exhaustion guards and cleanup-only registration.
2. Test explicit success/exception, counted deadline cleanup, parent cancellation, late child listeners, reentrancy, dynamic work and empty/exhausted fallback.
3. Migrate all callers, with special attention to the two contextless hash-crawl sites and their restart/cancel ownership.
4. Use tracker completion for resolver outcomes and register its deadline for cleanup without counting it toward exhaustion.
5. Simplify recovery timers through an explicitly completed per-job tracker; preserve ownership transfer when download setup completes.
6. Run focused and full tests, preserving all unrelated working changes.

### 10. Tests to write

Extend tracker tests with deterministic invoker barriers and controlled promises. Extend crawler tests to cover stop while crawling, empty-network restart and new crawl generation isolation. Retain resolver bounds/deadline tests and source-recovery cancellation tests; verify completed preparation cleanup does not end a running segment.

### 11. Docs / Javadoc to update

Update tracker/context Javadoc, concurrency conventions, relevant design statements about manual timer cleanup, and `docs/impl_summary/async-task-tracker-context.md` with audit findings and verification.
