package com.myster.search.ui;

import com.general.mclist.*;
import com.myster.search.SearchResult;

public interface ClientHandleObject {

	public int getNumberOfColumns();
	public String getHeader(int index) ;
	public int getHeaderSize(int index) ; //returns the size of a column
	public MCListItemInterface getMCListItem(SearchResult s) ;
	
}