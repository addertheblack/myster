package com.myster.search.ui;

import com.general.mclist.*;
import com.myster.mml.*;
import com.myster.tracker.MysterServer;
import com.myster.tracker.IPListManagerSingleton;
import com.myster.search.SearchResult;

public class ClientGenericHandleObject implements ClientHandleObject {
	protected String[] 	headerarray={"File Name","File Size","Server", "Ping"};
	protected int[]		headerSize={300,70,150, 70};
	protected String[] 	keyarray={"n/a","size","n/a","n/a"};
	
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
		
		public GenericSearchItem(SearchResult s) {
			result=s;
			
			String hostAsString=result.getHostAddress().toString();
			MysterServer server=IPListManagerSingleton.getIPListManager().getQuickServerStats(hostAsString);
			serverString=new SortableString(server==null?hostAsString:(server.getServerIdentity().equals(hostAsString)?""+hostAsString:server.getServerIdentity()+" ("+hostAsString+")"));
			//The Three lines above can be combined into one really long line. I hope you appreciate this :-)
			ping=new SortablePing(result.getHostAddress());
		}
		
		public Sortable getValueOfColumn(int index) {
			switch (index) {
				case 0:
					return new SortableString(result.getName());
				case 1:
					String size=result.getMetaData(keyarray[1]);
					try {
						return (size==null?new SortableByte(-1):new SortableByte(Integer.parseInt(size)));	//I am in a one-line mood today.
					} catch (NumberFormatException ex) {
						return new SortableByte(-2);
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