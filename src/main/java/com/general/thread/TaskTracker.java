
package com.general.thread;

public interface TaskTracker {
    /**
     * Tracks tasks that must be cancelled when this tracker is cancelled.
     *
     * <p>If the tracker is already cancelled, the supplied tasks are cancelled
     * immediately. This method tracks cancellation only; it does not observe or
     * propagate task completion.
     *
     * @param tasks cancellation targets owned by this tracker
     */
    void trackForCancellation(Cancellable... tasks);
}
