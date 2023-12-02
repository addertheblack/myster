
package com.myster.search;

import com.myster.hash.FileHash;
import com.myster.type.MysterType;

public interface HashCrawlerManager {
    public void addHash(MysterType type, FileHash hash, HashSearchListener listener);

    public void removeHash(MysterType type, FileHash hash, HashSearchListener listener);
}
