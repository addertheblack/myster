package com.myster.search;

import java.net.*;
import java.io.*;
import com.general.mclist.*;
import java.util.Vector;
import com.myster.mml.RobustMML;
import com.myster.util.MysterThread;
import com.myster.tracker.*;
import com.myster.net.MysterSocket;
import com.myster.net.MysterAddress;
import com.myster.type.MysterType;


public class FileInfoGetter extends MysterThread {
	MysterSocket socket;
	Vector vector;
	SearchResultListener addable;
	MysterType type;
	
	boolean endFlag=false;

	
	
	public FileInfoGetter(MysterSocket s, SearchResultListener a, MysterAddress address, MysterType type,Vector searchResults) {
		super("File Info Getter : "+address+" ");
		
		socket=s;
		addable=a;
		vector=new Vector(searchResults.size(), 10);
		this.type=type;
		
		for (int i=0; i<searchResults.size(); i++ ){
			vector.addElement(new MysterSearchResult(new MysterFileStub(address, type,(String)(searchResults.elementAt(i)))));
		}
	}

	public void run() {
		SearchResult[] searchArray=new SearchResult[vector.size()];
		
		for (int i=0; i<searchArray.length; i++) {
			searchArray[i]=(SearchResult)(vector.elementAt(i));
		}
		
		addable.addSearchResults(searchArray);
		
		if(endFlag) {
			cleanUp();
			return;
		}
		
		int pointer=0;
		int current=0;
		final int MAX_OUTSTANDING=10;
		try { //This is a speed hack.
			if (vector.size()>0) {
				while (!endFlag) { //usefull.
					if (pointer<vector.size()) {
						SearchResult result=(SearchResult)(vector.elementAt(pointer));
						socket.out.writeInt(77);
						
						socket.out.write(type.getBytes());
						socket.out.writeUTF(result.getName());
						pointer++;
					}
					
					if(endFlag) {
						cleanUp();
						return;
					}
					
					if (socket.in.available()>0||(pointer-current>MAX_OUTSTANDING)||pointer>=vector.size()) {
						if (socket.in.readByte()!=1) return;
						
						if(endFlag) {
							cleanUp();
							return;
						}
						
						RobustMML mml;
						try {
							mml=new RobustMML(socket.in.readUTF());
						} catch (Exception ex) {
							return;
						}
						
						
						((MysterSearchResult)(vector.elementAt(current))).setMML(mml);
						
						addable.searchStats((SearchResult)(vector.elementAt(current)));
						
						current++;
						
						if (current>=vector.size()) {
							break;
						}
					}
				}
			}
			socket.out.writeInt(2);
			int whoCares=socket.in.read();
		} catch (IOException ex) {
			return;
		} finally {
			cleanUp();
		}
	}
	
	
	public void end() {
		flagToEnd();
		try {
			join();
		} catch (InterruptedException ex) {}
	}
	
	public void flagToEnd() {
		endFlag=true;
	}
	
	private void cleanUp() {
		try {
			socket.close();
		} catch (Exception ex) {}
	}
}