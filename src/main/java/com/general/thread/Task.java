package com.general.thread;

/**
 * Represents an asynchronously executing task (conceptually a thread, although
 * a thread need not be how it's implemented.)
 * <p>
 * Implementors of this class MUST be thread safe since this class is to be used
 * by different threads to talk to one another.
 */
public interface Task {
    /**
     * Starts this asynchronous task.
     */
    public void start();

    /**
     * Tells this task to stop as soon as possible and blocks until the task has stopped
     * completely.
     */
    public void end();

    /**
     * Tells this task to stop as soon as possible but does not block.
     */
    public void flagToEnd();
}