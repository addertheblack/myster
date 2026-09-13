package com.myster.search;

/**
 * Receives hash-search results. Implementations must tolerate late callbacks after removal
 * from a {@link HashCrawlerManager}, checking whether their owning operation still accepts results.
 */
public interface HashSearchListener {
    void searchResult(MysterFileStub stub);
}
