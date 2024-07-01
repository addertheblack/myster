package com.myster.server;

import java.io.IOException;

/**
 * This interface should be used by plugin developers who want to create an
 * add-on to the Myster server 1) from scatch or 2) that violates or abuses the
 * Myster protocol specs.
 * 
 * (It goes without saying that this is not a good thing)
 * 
 * Everyone else should inherit from "ServerThread". ServerThread contains
 * usefull utilities classes.
 * 
 * @author Andrew Trumper
 *  
 */

public interface ConnectionSection {

    /**
     * does the IO for this conneciton section. Implementators must assume that
     * the proper connection has been sent but should not assume that the "is a
     * good section" byte has been sent... Mostly because it hasn't. see ServerThread.
     * 
     * @see ConnectionContext for what is sent in the conneciton context.
     * @see com.myster.server.stream.ServerStreamHandler for a more high level ConnectionSection object to
     *      over-ride
     * @param context
     *            the context associated with this stream (includes the "socket"
     *            and some other goodies)
     * @throws IOException
     *             if an IO error occured or if the stream is now in an
     *             indeterminate state and should be closed
     */
    public void doSection(ConnectionContext context) throws IOException;

    /**
     * Returns the connection section number for this ConnectionSection object.
     * 
     * @return The conneciton section number for this conneciton section.
     */

    public int getSectionNumber();

    /**
     * This routine is called just BEFORE a connection section is executed. The
     * object returned by this object is passed along with the event informing
     * ConnectionManagerEventListeners that someone has requested this section.
     * This mechanism is used by the Download connection section to pass a
     * secondairy, synchronous event dispatcher so the code receiving the new of
     * someone requesting a download connection section can attach a listener
     * for download related events.
     * <p>
     * In other words we use this function to create and pass an event object
     * that clients can use to attach a DownloadListener to to listen for events
     * during that particular download.
     * 
     * @return Any object you want to attach to the ConnectionManager "connect"
     *         event. (Usually you want to attach a dispatcher to allow clients
     *         to attach listeners to)
     *  
     */
    public Object getSectionObject();
}