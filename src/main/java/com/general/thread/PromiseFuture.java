
package com.general.thread;

import java.util.Objects;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A cancellable asynchronous result with invoker-dispatched listeners.
 *
 * <p>Ordinary listeners are dispatched in registration order. Convenience
 * listener methods register independent listeners at their position in that
 * order; they do not create outcome-wide phases.
 */
public interface PromiseFuture<T> extends Cancellable, Future<T> {
    public static <R> PromiseFuture<R> newPromiseFuture(Consumer<AsyncContext<R>> context) {
        PromiseFutureImpl<R> f = new PromiseFutureImpl<>();
        
        context.accept(f.getAsyncContext());
        
        return f;
    }
    
    public static <R> PromiseFuture<R> newPromiseFuture(R r) {
        PromiseFutureImpl<R> f = new PromiseFutureImpl<>();
        
        f.getAsyncContext().setResult(r);
        
        return f;
    }
    
    public static <R> PromiseFuture<R> newPromiseFutureException(Exception e) {
        PromiseFutureImpl<R> f = new PromiseFutureImpl<>();
        
        f.getAsyncContext().setException(e);
        
        return f;
    }

    /**
     * Renders this computation moot and makes cancellation the outcome visible
     * to listeners registered afterward. Cancellation also propagates to owned
     * work when possible, but it does not promise that the underlying operation
     * has stopped.
     *
     * <p>Cancellation is intentionally not an immutable compare-and-set terminal
     * transition. A listener already dispatched may have observed a result or
     * exception before a later cancellation, while subsequent listeners observe
     * cancellation. This differs from {@link Future#cancel(boolean)}, whose
     * return value follows the {@code Future} contract.
     */
    @Override
    void cancel();
    
    /**
     * Returns a cancellation-linked view with no ordinary-listener invoker.
     * This does not remove or replace the invoker on this future.
     */
    PromiseFuture<T> clearInvoker();

    /**
     * Assigns the ordinary-listener invoker when this future does not already
     * have one.
     *
     * @throws IllegalStateException if an invoker is already assigned
     */
    PromiseFuture<T> setInvoker(Invoker invoker);

    /**
     * Returns a future whose ordinary listeners use {@code requestedInvoker}.
     *
     * <p>Unlike a conventional wither, this method does not always allocate a
     * new object. It returns this future when the requested invoker is already
     * assigned, assigns the invoker directly when none exists, and otherwise
     * returns a cancellation-linked wrapper without changing this future's
     * existing listener dispatch.
     *
     * @param requestedInvoker invoker for ordinary listeners
     * @return this future or an equivalent cancellation-linked view
     */
    default PromiseFuture<T> withInvoker(Invoker requestedInvoker) {
        Objects.requireNonNull(requestedInvoker, "requestedInvoker");
        Invoker currentInvoker = getInvoker();
        if (currentInvoker == requestedInvoker) {
            return this;
        }
        if (currentInvoker == null) {
            return setInvoker(requestedInvoker);
        }
        return clearInvoker().setInvoker(requestedInvoker);
    }

    Invoker getInvoker();
    
    /**
     * Similar to {@link #addFinallyCallResultListener(Consumer)} but completely
     * synchronous. Does not use the invoker and runs on whichever thread sets
     * the result. This exists primarily for low-level promise composition;
     * application state protected by an invoker should use ordinary listeners.
     */
    PromiseFuture<T> addSynchronousCallback(Consumer<CallResult<T>> c);

    default PromiseFuture<T> useEdt() {
        return setInvoker(Invoker.EDT);
    }
    
    /**
     * Is always called once a task finished. Similar to addFinally but with a callResult
     */
    PromiseFuture<T> addFinallyCallResultListener(Consumer<CallResult<T>> c);

    PromiseFuture<T> addCallListener(CallListener<T> callListener);

    PromiseFuture<T> addResultListener(Consumer<T> resultListener);

    PromiseFuture<T> addExceptionListener(Consumer<Throwable> exceptionListener);

    /**
     * Adds a listener that runs for every dispatched outcome.
     *
     * <p>This listener runs at its registration position. In particular, a
     * finally listener registered before a result listener runs before that
     * result listener; it is not deferred until all other listeners finish.
     *
     * @param runnable listener to run for every outcome
     * @return this future, for chaining
     */
    PromiseFuture<T> addFinallyListener(Runnable runnable);

    /**
     * Adds a listener that runs only when cancellation is dispatched.
     *
     * @param cancelListener listener to run on cancellation
     * @return this future, for chaining
     */
    PromiseFuture<T> addCancelListener(Runnable cancelListener);

    /**
     * Adds a standard exception handler that logs the exception and shows a dialog to the user.
     * All PromiseFutures must have an exception handler, so this is a convenient way to add a standard one if you don't care about the details of the exception.
     * If you don't add any exception handler the exception will be swallowed and you'll never know about it, which is bad. So please add an exception handler, either a custom one or this standard one.
     * The code might even throw an exception complaining about an unregistered exception handler if you forget to add one, but don't rely on that.
     * @return myself, for chaining
     */
    PromiseFuture<T> addStandardExceptionHandler();
    
    /**
     * Maps a successful result to a second asynchronous operation. An exception
     * or cancellation from this source future is forwarded without invoking the
     * mapper. The invoker is not mapped.
     *
     * <p>Cancelling the returned future cancels the mapped operation once it
     * exists, but does not cancel this source future.
     *
     * @param <R> mapped result type
     * @param mapper operation to start after this future succeeds
     * @return future completed from the operation returned by {@code mapper}
     */
    default <R> PromiseFuture<R> mapAsync(Function<T, PromiseFuture<R>> mapper) {
        return PromiseFuture.newPromiseFuture(context -> {
            addSynchronousCallback(c -> {
                if (c.isException()) {
                    context.setException(c.getException());
                } else if (c.isCancelled()) {
                    context.cancel();
                } else {
                    context.trackForCancellation(mapper.apply(c.getResult())
                            .addSynchronousCallback(context::setCallResult));
                }
            });
        });
    }
}
