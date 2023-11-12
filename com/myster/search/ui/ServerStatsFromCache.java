
package com.myster.search.ui;

import com.myster.net.MysterAddress;
import com.myster.tracker.MysterServer;

public interface ServerStatsFromCache {
    public MysterServer get(MysterAddress address);
}
