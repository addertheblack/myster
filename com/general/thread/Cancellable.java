package com.general.thread;

/**
 * Interface objects should implement if they represent a task that can be
 * cancelled.
 * <p>
 * Implementors should allow cancel to be called multiple times with no ill
 * effects.
 * <p>
 * Implementors should mark the thread safety of the implementation of this
 * call.
 * <p>
 * NOTE: flagToEnd() and cancel() are not the same thing. flagToEnd() is a
 * request to stop a thread as soon as possible. cancel() is a request to cancel
 * the task being executed. flagToEnd() can be viewed as a request to cancel the
 * execution of a thread. HOWEVER, since SafeThread objects have a tendency of
 * not only representing the thread but also representing the task the thread is
 * executing, giving SafeThread a cancel() method would be overload the meaning
 * of cancel(). An example of where this could be bad. If a safe thread is being
 * used for a download task, and the user cancels the download, then the
 * download is stopped and the partial file is deleted. However, if the user
 * wishes to pause the download, the thread might be stopped with a flagToEnd()
 * and restarted again later. flagToEnd() wouldn't delete the partial file.(in
 * this case, though, it would be polite to provide a more tailored interface to
 * the download and not rely on the semantics of SafeThread.)
 */
public interface Cancellable {
    public void cancel();
}