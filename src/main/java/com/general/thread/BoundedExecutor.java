package com.general.thread;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Implements a blocking function BoundedExecutor that limits concurrency. Can wrap
 * any Executor including other BoundedExecutors.
 */
public class BoundedExecutor implements Executor {
	private final ConcurrentLinkedQueue<Runnable> queue = new ConcurrentLinkedQueue<>();
	private final Executor executor;
	private final int concurrency;
	
	private final AtomicInteger counter = new AtomicInteger();
	
	public BoundedExecutor(int concurrency, Executor executor) {
		this.concurrency = concurrency;
		this.executor = executor;
	}
	
	@Override
	public void execute(Runnable command) {
		queue.add(command);
		
		checkRunning();
	}

	private void checkRunning() {
		for (;;) {
			int value = counter.intValue();
			if (value >= concurrency) {
				return;
			}
			
			if (queue.isEmpty()) {
				return;
			}
		
			if(!counter.compareAndSet(value, value+1)) {
				continue;
			}
			
			Runnable poll = queue.poll();
			
			// if it's null then we didn't run anything so decrement and loop
			if (poll == null) {
				counter.decrementAndGet();
				continue;
			}
			
			executor.execute(() -> {
				try { 
					poll.run();
				} finally {
					counter.decrementAndGet();
					checkRunning();
				}
			});
			
			return;
		}
	}
}
