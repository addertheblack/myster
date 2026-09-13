
package com.myster.search;

import com.myster.hash.FileHash;
import com.myster.type.MysterType;

/** Used by MSDownload. The HashCrawlerManager crawls the Myster Network looking for the FileHash */
public interface HashCrawlerManager {
    void addHash(MysterType type, FileHash hash, HashSearchListener listener);

    /**
     * Requests removal of a hash listener. Removal may be asynchronous and does not guarantee
     * that callbacks have stopped: an active crawl can retain the listener in its search snapshot
     * and deliver results even after removal is processed. Listeners must tolerate late results
     * and check whether their owning operation still accepts them.
     */
    void removeHash(MysterType type, FileHash hash, HashSearchListener listener);
}
