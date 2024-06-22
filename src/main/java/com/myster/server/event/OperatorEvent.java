/**
 * a somewhat useless class for now.
 */

package com.myster.server.event;

import com.myster.net.MysterAddress;

public class OperatorEvent extends ServerEvent {
    public OperatorEvent(  MysterAddress address) {
        super(address, -1);
    }
}