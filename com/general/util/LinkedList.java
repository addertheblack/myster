package com.general.util;


/**
* Is a generic Linked List implementation. Suitable for O(1) queues.
*/
//Fast queues should use addToTail and removeFromHead
public class LinkedList {
	final Element head;
	Element tail;
	int numOfItems=0;
	
	public LinkedList() {
		tail=head=new Element(null);
	}
	
	
	//fast
	public synchronized void addToHead(Object o) {
		Element e=new Element(o);
		e.next=head.next;
		head.next=e;
		numOfItems++;
		
		assertTail(); //assertTail needs to be case in case head = tail
	}
	
	/** gets an element at the index starting from the head. */
	public synchronized Object getElementAt(int index) {
		if (index<0||index>=numOfItems) return null;
		
		Element e=head;
		for (int i=0; (e.next!=null&&i<index); e=e.next, i++) 
				;
		
		return e.next.value;
	}
	
	//fast
	public synchronized void addToTail(Object o) {
		Element e=new Element(o);
		tail.next=e;
		tail=e;
		numOfItems++;
	}
	
	//fast
	public synchronized Object getTail() {
		return tail.value;
	}
	
	//slow
	public synchronized Object removeFromTail() {
		Object o=tail.value;
		tail=head;
		while (tail.next!=null) {
			if (tail.next.next==null) {
				tail.next=null;
				break;
			}
			tail=tail.next;
		}
		numOfItems--;
		return o;
	}
	
	//fast
	public synchronized Object getHead() {
		if (head.next==null) return null;
		return head.next.value;
	}
	
	//fast
	public synchronized Object removeFromHead() {
		if (head.next==null) return null;
		Object o=head.next.value;
		head.next=head.next.next;
		assertTail(); //in case item removed was the tail.
		numOfItems--;
		return o;
	}
	
	//fast
	public int getSize() {
		return numOfItems;
	}
	
	//slow
	//deprecated use getPositionOf
	public boolean contains(Object object) {
		return (getPositionOf(object) != -1);
	}
	
	/**
	*	returns the index of the Object starting from the head.
	*/
	public synchronized int getPositionOf(Object o) {
		Element temp=head;
		int counter = 0;
		
		while (temp.next!=null) {
			if (temp.next.value.equals(o)) return counter;
			temp=temp.next;
			counter++;
		}
		return -1;
	}
	
	//slow
	public synchronized boolean remove(Object o) {
		Element temp=head;		
		while (temp.next!=null) {
			if (temp.next.value.equals(o)) {
				temp.next=temp.next.next;
				if (temp.next==null) tail=temp;
				numOfItems--;
				assertTail();
				return true;
			}
			temp=temp.next;
		}
		return false;
	}
	
	//slow
	private synchronized void findTheEnd() {
		tail=head;
		while (tail.next!=null) {
			tail=tail.next;
		}
	}
	
	//fast
	private synchronized void assertTail() {
		if (numOfItems<2) findTheEnd();	//if (head==tail) won't work!
	}

	private static class Element {
		public Object value;
		public Element next;
		
		public Element(Object value) { this.value=value; }
	}
}