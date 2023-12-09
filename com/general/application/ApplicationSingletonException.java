package com.general.application;

import java.io.IOException;

/**
 * This type of IOException is thrown when the other instance of the program was contacted but
 * refused the connection invocation request for whatever reason.
 */
class ApplicationSingletonException extends IOException {

    private final int result;

    /**
     * Builds a new ApplicationSingletonException.
     * 
     * @param string
     * @param result
     */
    public ApplicationSingletonException(String string, int result) {
        super(string);
        this.result = result;

    }

    /**
     * Use this to get the error code response that caused this exception.
     * 
     * @return the error code returned by the other invocation.
     */
    public int getResult() {
        return result;
    }
}