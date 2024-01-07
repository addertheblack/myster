
package com.myster;

import java.util.logging.*;

public class CustomConsoleHandler extends ConsoleHandler {
    public CustomConsoleHandler() {
        super();
        setOutputStream(System.out);
    }
}