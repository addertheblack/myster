/* 

 Title:			Myster Open Source
 Author:			Andrew Trumper
 Description:	Generic Myster Code
 
 This code is under GPL

 Copyright Andrew Trumper 2000-2001
 */

package com.myster.search;

/**
 *  
 */

public interface SearchResultListener {

    /**
     * Is called when The search is over.
     */
    public void searchOver();

    /**
     *  
     */
    public boolean addSearchResults(SearchResult[] s);

    /**
     * The addable interface should use repaint() to tell the list to update
     * following a change to one of its items. Think of it as a sort of refresh.
     * 
     * This routine has no guarentee of being called.
     * 
     * There is no gurentee of there being any meta-data before this routine is
     * called.
     */
    public void searchStats(SearchResult s);

    /**
     * tells the object a new adding session has begun.
     */
    public void startSearch();
}