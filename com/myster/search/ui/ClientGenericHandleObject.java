package com.myster.search.ui;

import com.general.mclist.*;
import com.myster.mml.*;
import com.myster.tracker.MysterServer;
import com.myster.tracker.IPListManagerSingleton;
import com.myster.search.SearchResult;
import com.myster.net.*;

public class ClientGenericHandleObject implements ClientHandleObject {
	protected String[] 	headerarray={"File Name","File Size","Server", "Ping"};
	protected int[]		headerSize={300,70,150, 70};
	protected String[] 	keyarray={"n/a","/size","n/a","n/a"};
	
	public String getHeader(int index) {
		return headerarray[index];
	}
	
	public int getHeaderSize(int index) {
		return headerSize[index];
	}
		
	public int getNumberOfColumns() {
		return headerarray.length;
	}
	
	public MCListItemInterface getMCListItem(SearchResult s) { //factory... chugga chugga...
		return new GenericSearchItem(s);
	}
	
	protected class GenericSearchItem extends MCListItemInterface {
		SearchResult result;
		SortableString serverString;
		SortablePing ping;
		SortableString sortableName;
		
		SortableByte sortableSize;
		
		private final SortableByte NOT_IN=new SortableByte(-1);
		private final SortableByte NUMBER_ERR=new SortableByte(-2);
		
		public GenericSearchItem(SearchResult s) {
			result=s;
			
			MysterAddress hostAsAddress=result.getHostAddress();
			String hostAsString=hostAsAddress.toString();
			MysterServer server=IPListManagerSingleton.getIPListManager().getQuickServerStats(hostAsAddress);
			serverString=new SortableString(server==null?hostAsString:(server.getServerIdentity().equals(hostAsString)?""+hostAsString:server.getServerIdentity()+" ("+hostAsString+")"));
			//The Three lines above can be combined into one really long line. I hope you appreciate this :-)
			ping=new SortablePing(result.getHostAddress());
			sortableName=new SortableString(result.getName());
		}
		
		public Sortable getValueOfColumn(int index) {
			switch (index) {
				case 0:
					return sortableName;
				case 1:
					String size=result.getMetaData(keyarray[1]);
					try {
						if (size!=null && sortableSize==null) {
							sortableSize=new SortableByte(Integer.parseInt(size));
						}
					
						return (size==null?NOT_IN:sortableSize);	//I am in a one-line mood today.
					} catch (NumberFormatException ex) {
						return NUMBER_ERR;
					}
				case 2:
					return serverString;
				case 3:
					return ping;
				default:
					throw new RuntimeException("Requested a column that doens't exist.");
			}
		}
	
		public Object getObject() {
			return result;
		}
	}
}