package com.myster.client.datagram;

import java.util.Vector;
import java.io.IOException;

import com.general.util.Semaphore;

import com.myster.net.StandardDatagramListener;
import com.myster.net.StandardDatagramClientImpl;
import com.myster.net.StandardDatagramEvent;
import com.myster.net.MysterAddress;
import com.myster.type.MysterType;
import com.myster.mml.RobustMML;
import com.myster.search.MysterFileStub;
import com.myster.hash.FileHash;

import com.myster.transaction.TransactionSocket;
import com.myster.transaction.TransactionListener;
import com.myster.transaction.TransactionEvent;
import com.myster.net.DataPacket;

public class StandardDatagramSuite {

	public static MysterAddress[] getTopServers(final MysterAddress ip,
				final MysterType type) throws IOException {
		
		return (MysterAddress[]) (makeBlocking(new BlockingListener(ip,
				new TopTenDatagramClient(type))));
	}
	
	public static void getTopServers(final MysterAddress ip,
				final MysterType type,
				final StandardDatagramListener listener) throws IOException {
				
		doSection(ip, new TopTenDatagramClient(type), listener);
	}
	
	
	//Vector of Strings
	public static Vector getSearch(final MysterAddress ip,
				final MysterType type, final String searchString) throws IOException {
		
		return (Vector) (makeBlocking(new BlockingListener(ip,
				new SearchDatagramClient(type, searchString))));
	}
	
	public static void getSearch(final MysterAddress ip,
				final MysterType type, final String searchString,
				final StandardDatagramListener listener) throws IOException {
				
		doSection(ip, new SearchDatagramClient(type, searchString), listener);
	}
	
	
	public static MysterType[] getTypes(final MysterAddress ip) throws IOException {
		return (MysterType[]) (makeBlocking(new BlockingListener(ip,
				new TypeDatagramClient())));
	}
	
	public static void getTypes(final MysterAddress ip,
				final StandardDatagramListener listener) throws IOException {
				
		doSection(ip, new TypeDatagramClient(), listener);
	}
	
	
	public static RobustMML getServerStats(final MysterAddress ip) throws IOException {
		return (RobustMML) (makeBlocking(new BlockingListener(ip,
				new ServerStatsDatagramClient())));
	}
	
	public static void getServerStats(final MysterAddress ip,
				final StandardDatagramListener listener) throws IOException {
				
		doSection(ip, new ServerStatsDatagramClient(), listener);
	}
	
	
	public static RobustMML getFileStats(final MysterFileStub stub) throws IOException {
		return (RobustMML) (makeBlocking(new BlockingListener(stub.getMysterAddress(),
				new FileStatsDatagramClient(stub))));
	}
	
	public static void getFileStats(final MysterFileStub stub, 
				final StandardDatagramListener listener) throws IOException {
		doSection(stub.getMysterAddress(), new FileStatsDatagramClient(stub), listener);
	}
	
	
	public static String getFileFromHash(final MysterAddress ip,
				final MysterType type, final FileHash hash) throws IOException {
		return (String) (makeBlocking(new BlockingListener(ip,
				new SearchHashDatagramClient(type, hash))));
	}
	
	public static void getFileFromHash(final MysterAddress ip,
				final MysterType type, final FileHash hash,
				final StandardDatagramListener listener) throws IOException {
		doSection(ip, new SearchHashDatagramClient(type, hash), listener);
	}
	
	
	private static void doSection(final MysterAddress address,
				final StandardDatagramClientImpl impl,
				final StandardDatagramListener listener) throws IOException {
				
		TransactionSocket tsocket=new TransactionSocket(impl.getCode());
		
		//We need to convert between a generic transaciton, listener and a Stanard Myster
		//transaction listener (to return the data pre-formated, like we want)
		//This has got to be one of longest single lines in Myster :-)
		tsocket.sendTransaction(new DataPacket() { //inline class
					public MysterAddress getAddress() { return address; }
					public byte[] getData() { return impl.getDataForOutgoingPacket(); }
					public byte[] getBytes() { return getData(); }
					public byte[] getHeader() { return new byte[]{}; }
				},
				
				new TransactionListener() { //inline class
					public void transactionReply(TransactionEvent e) {
						if (DatagramUtilities.dealWithError(e.getTransaction(), listener)) return;
					
						try {
							listener.response(new StandardDatagramEvent(e.getAddress(),
									impl.getCode(),
									impl.getObjectFromTransaction(e.getTransaction())));
						} catch (IOException ex) {
							//Packet was badly formatted so we send a null Object
							
							listener.response(new StandardDatagramEvent(e.getAddress(),
									impl.getCode(), impl.getNullObject()));
						}
					}
			
					public void transactionTimout(TransactionEvent e) {
						listener.timeout(new StandardDatagramEvent(e.getAddress(),
								impl.getCode(),
								impl.getNullObject())); // a Null object is better than a null
					}
				});
		
		// no need to close socket.. all sockets are one-shot.
	}
	
	private static Object makeBlocking(final BlockingListener passable) throws IOException {
		final Semaphore sem = new Semaphore(0);
		final BlockingResult blockingResult = new BlockingResult();
		
		
		//This stuff below might look weird but there's a danger of a datarace so I want to
		//make sure all my data uses a common monitor.
		//(Actually, I think there is still a data race)
		passable.get(new StandardDatagramListener() {
			public void response(StandardDatagramEvent event) {
				blockingResult.setData(event.getData());
				
				sem.signal();
			}
			
			public void timeout(StandardDatagramEvent event) {
				blockingResult.setError(-1);
			
				sem.signal();
			}

			public void error(StandardDatagramEvent event) {
				blockingResult.setError(2);
				
				sem.signal();
			}
		});
		
		try {
			sem.getLock();
		} catch (InterruptedException ex) {
			throw new IOException("Timeout"); 
		}
		
		if (blockingResult.getData() == null) {
			if (blockingResult.getError() < 0) throw new IOException("Timeout");
			if (blockingResult.getError() > 0) throw new com.myster.client.stream.UnknownProtocolException(blockingResult.getError(),"Protocol is not understood. (none 1 response)");
		}
		
		return blockingResult.getData();
	}
	
	private static class BlockingListener {
		final StandardDatagramClientImpl impl;
		final MysterAddress address;
	
		public BlockingListener (MysterAddress address, StandardDatagramClientImpl impl) {
			this.impl 		= impl;
			this.address	= address;
		}
	
		public void get(StandardDatagramListener listener) throws IOException {
			doSection(address, impl, listener);
		}
	}
	
	private static class BlockingResult {
		Object data;
		public int error; //at 0 by default
		
		public synchronized void setData(Object data) { this.data = data; }
		public synchronized Object getData() { return data; }
		
		public synchronized int getError() { return error;}
		public synchronized void setError(int error) { this.error = error; }
	}
}