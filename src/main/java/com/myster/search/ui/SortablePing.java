package com.myster.search.ui;

import com.general.mclist.Sortable;
import com.general.thread.CallAdapter;
import com.myster.net.MysterAddress;
import com.myster.net.client.MysterProtocol;
import com.myster.net.client.ParamBuilder;
import com.myster.net.datagram.client.PingResponse;

public class SortablePing implements Sortable<Long> {
    public static final int NOTPINGED = 1000000;

    public static final int TIMEOUT = 1000001;

    private long number;

    public SortablePing(MysterProtocol protocol, MysterAddress address) {
        number = NOTPINGED;

        try {

            protocol.getDatagram().ping(new ParamBuilder(address)).addCallListener(new MyPingEventListener());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public synchronized Long getValue() {
        return number;
    }

    public synchronized boolean isLessThan(Sortable<?> temp) {
        if (temp == this)
            return false;
        if (!(temp instanceof SortablePing))
            return false;

        Long n = (Long) temp.getValue();
        if (number < n.longValue())
            return true;
        return false;
    }

    public synchronized boolean isGreaterThan(Sortable<?> temp) {
        if (temp == this)
            return false;
        if (!(temp instanceof SortablePing))
            return false;

        Long n = (Long) temp.getValue();
        if (number > n.longValue())
            return true;
        return false;
    }
    
    @Override
    public synchronized boolean equals(Object temp) {
        if (temp == this) {
            return true;
        }
        
        if (temp instanceof SortablePing sortablePing) {
            return number == sortablePing.getValue().longValue();
        } else {
            return false;
        }
    }

    public synchronized void setNumber(long temp) {
        number = temp;
    }

    public String toString() {
        int temp = (int) number;

        if (temp == NOTPINGED) {
            return "-";
        } else if (temp == TIMEOUT) {
            return "Timeout";
        } else {
            return temp + "ms";
        }
    }

    private class MyPingEventListener extends CallAdapter<PingResponse> {
        @Override
        public void handleResult(PingResponse pingResponse) {
            if (pingResponse.isTimeout()) {
                setNumber(TIMEOUT);
            } else {
                setNumber(pingResponse.pingTimeMs());
            }
        }
    }
}