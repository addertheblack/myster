package com.general.util;


/**
*	A DoubleBlockingQueuing is a queue that is made to support multiple
*	threads adding data to the queue and multiple threads getting data
*	from the queue. If there are no elements to get, threads are blocked
*	until data is available. If the queue is at its maximum length,
*	the adding threads will block until some space is available in the queue.
*	
*	A DoubleBlockingQueue differs from a BlockingQueue because it
*	blocks adding threads if the queue becomes to large as well
*	as blocking getter threads if the queue has no items. A
*	DoubleBlockingQueue of length 0 is sometimes known as a
*	rendezvous point/variable or as a simple channel.
*
*/
public class DoubleBlockingQueue extends BlockingQueue {
	Semaphore addSem;
	
	public DoubleBlockingQueue(int length) { //if length=0, then it becomes a channel.
		super();
		if (length<0) throw new RuntimeException("Length cannot be less than 0");
		
		addSem=new Semaphore(length+1);
	}
	
	/**
	*	Adds an object to the queue and returns when
	*	the data has been accepted by the queue.
	*/
	public void add(Object o) {
		addSem.waitx();
		super.add(o);
	}
	
	/**
	*	Gets the next element from the queue. Blocks until
	*	Data is available.
	*/
	public Object get() throws InterruptedException {
		Object t_o=super.get();
		addSem.signalx();
		return t_o;
	}

}