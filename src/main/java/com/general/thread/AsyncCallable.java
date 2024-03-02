
package com.general.thread;

public interface AsyncCallable<T> {
    PromiseFuture<T> call();
}
