/* 

	Title:			Myster Open Source
	Author:			Andrew Trumper
	Description:	Generic Myster Code
	
	This code is under GPL

Copyright Andrew Trumper 2000-2001
*/

package com.myster.server.stream;

import java.net.*;
import java.io.*;
import java.net.Socket;
import com.general.util.*;
import com.myster.mml.MML;
import com.myster.filemanager.*;
import com.myster.server.ConnectionContext;
import com.myster.util.MP3Header;
import helliker.id3.ID3v2Tag;
import helliker.id3.ID3v2Frames;
import helliker.id3.ID3v2FormatException;
import com.myster.type.MysterType;

public class FileInfoLister extends ServerThread {
	public static final int NUMBER=77;
	
	public int getSectionNumber() {
		return NUMBER;
	}
	
	/**
		in Filetype
		in FileName
	*/
	
	public void section(ConnectionContext context) throws IOException {
		String[] temp;

		DataInputStream in=new DataInputStream(context.socket.getInputStream());
		DataOutputStream out=new DataOutputStream(context.socket.getOutputStream());

		byte[] b=new byte[4];

		in.readFully(b);
		String filename=in.readUTF();
		File file=FileTypeListManager.getInstance().getFile(new MysterType(b),filename);
		
		MML mml=new MML();
		
		if (file!=null) {
			mml.put("/size", ""+file.length());				
			patchFunction(mml,file,b);
		}
		
		out.writeUTF(""+mml);
	}
	

	private void patchFunction2(MML mml, File file, byte[] b)  {
		if (!(new String(b)).equals("MPG3")) return;
		ID3v2Tag tag=null;
		try {tag=new ID3v2Tag(file, 0);} catch (Exception ex) {return;} 
		
		try {
			String temp=tag.getFrameDataString(ID3v2Frames.TITLE);
			if (temp!=null && !temp.equals("")) {
				mml.put("/ID3Name", temp);
			}
		} catch (ID3v2FormatException ex) {}
		
		try {
			String temp=tag.getFrameDataString(ID3v2Frames.COMPOSER);
			if (temp!=null && !temp.equals("")) {
				mml.put("/Artist", temp);
			} else {
				temp=tag.getFrameDataString(ID3v2Frames.LEAD_PERFORMERS);
				if (temp!=null && !temp.equals("")) {
					mml.put("/Artist", temp);
				}
			}
		} catch (ID3v2FormatException ex) {}
		
		try {
			String temp=tag.getFrameDataString(ID3v2Frames.ALBUM);
			if (temp!=null && !temp.equals("")) {
				mml.put("/Album", temp);
			}
		} catch (ID3v2FormatException ex) {}
		
		System.out.println(""+tag);

	}
	
	///*
	private void patchFunction(MML mml, File file, byte[] b)  {
		if (!(new String(b)).equals("MPG3")) return;
		MP3Header head=null;
		try {head=new MP3Header(file);} catch (Exception ex) {return;}
		
		mml.put("/BitRate", ""+head.getBitRate());
		mml.put("/Hz", ""+head.getSamplingRate());
		
		String temp=head.getMP3Name();
		if (temp!=null) {
			mml.put("/ID3Name", temp);
		} else {
			patchFunction2(mml,file,b);
			return;
		}
		
		temp=head.getArtist();
		if (temp!=null) {
			mml.put("/Artist", temp);
		}
		
		temp=head.getAlbum();
		if (temp!=null) {
			mml.put("/Album", temp);
		}
		
		head=null;		//go get 'em GC...
	}
	//*/
}