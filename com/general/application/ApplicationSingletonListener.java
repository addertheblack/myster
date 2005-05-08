package com.general.application;

/**
 * This is the callback used to notify whoever created this ApplicationSingleton that another
 * program is trying to connect.
 */

public interface ApplicationSingletonListener {
    /**
     * Another instance of this program has been launched with the args passed.
     * 
     * @param args
     *            that were passed to the new instance of this program.
     *  
     */
    public void requestLaunch(String[] args);

    /**
     * If the Server listening for connections on the remote port throws an exception this function
     * is called.If this is called you might want to close the instance of the ApplicationSingleton
     * and create another one... or panic..
     * 
     * @param ex
     *            exception thrown.
     *  
     */
    public void errored(Exception ex);
}