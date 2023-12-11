/* 

 Title:			Myster Open Source
 Author:			Andrew Trumper
 Description:	Generic Myster Code
 
 This code is under GPL

 Copyright Andrew Trumper 2000-2001
 */

package com.myster.server.stream;

import java.io.IOException;

import com.myster.server.ConnectionContext;
import com.myster.server.ConnectionSection;

/**
 * This class implements the standard Myster Header for plugins developers and
 * myself.
 *  
 */

public abstract class ServerThread implements ConnectionSection {

    public Object getSectionObject() {//Usefull for sending event Dispatchers
                                      // and possibly other things!
        return null;//yipe!
    }

    public final void doSection(ConnectionContext context) throws IOException { //if
                                                                                // you
                                                                                // want
                                                                                // to
                                                                                // modify
                                                                                // this
                                                                                // implementation
                                                                                // inherit
                                                                                // from
                                                                                // ConnectionSection
        context.socket.out.write(1); //Tells the other end that the command is
                                     // good! (Standard Myster header)
        
        context.socket.out.flush(); // no waiting!

        section(context); //!!!!!
    }

    /**
     * over-ride this function to implement your custom Myster protocol section
     * (aka ConnectionSection)
     *  
     */
    public void section(ConnectionContext c) throws IOException {
        //to be completed by sub classes.
    }
}