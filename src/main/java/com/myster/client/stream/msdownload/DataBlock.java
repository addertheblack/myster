
package com.myster.client.stream.msdownload;

class DataBlock {
    /** Should be an even multiple of the block size, unless it's the last block.  */
    public final byte[] bytes;

    /** Place where we started reading. Should be an even multiple of the block size */
    public final long offset;

    public DataBlock(long offset, byte[] bytes) {
        this.offset = offset;
        this.bytes = bytes;
    }
}