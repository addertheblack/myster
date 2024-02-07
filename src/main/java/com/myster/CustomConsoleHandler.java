
package com.myster;

import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.StreamHandler;

public class CustomConsoleHandler extends StreamHandler {
    public CustomConsoleHandler() {
        final String where = LogManager.getLogManager().getProperty(getClass().getName() + ".output");
        
        setOutputStream(where.equals( "err" ) ? System.err : System.out);
    }

    /**
     * Publish a {@code LogRecord}.
     * <p>
     * The logging request was made initially to a {@code Logger} object,
     * which initialized the {@code LogRecord} and forwarded it here.
     *
     * @param  record  description of the log event. A null record is
     *                 silently ignored and is not published
     */
    @Override
    public void publish(LogRecord record) {
        super.publish(record);
        flush();
    }

    /**
     * Override {@code StreamHandler.close} to do a flush but not
     * to close the output stream.  That is, we do <b>not</b>
     * close {@code System.err}.
     */
    @Override
    public void close() {
        flush();
    }
}
