/**

	A rather primitive Channels implementation.



	Usage: 

*/



package com.general.util;



public class Channel {

	private Object data;

	private Semaphore semIn=new Semaphore(0);

	private Semaphore semOut=new Semaphore(0);

	public final In in=new In();

	public final Out out=new Out(); 



	public class In {

		Semaphore me=new Semaphore(1);

		public synchronized void put(Object o) {

			me.waitx();

			data=o;

			semOut.signalx();

			semIn.waitx();

			me.signalx();

		}

	}

	

	public class Out {

		public synchronized Object get() {

			semOut.waitx();

			Object o=data;

			semIn.signalx();

			return o;

		}

	} 



}