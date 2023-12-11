
package com.myster.client.net;

import java.io.IOException;

import com.myster.net.MysterSocket;

public interface StandardStreamSection<T> {
    T doSection(MysterSocket socket) throws IOException;
}