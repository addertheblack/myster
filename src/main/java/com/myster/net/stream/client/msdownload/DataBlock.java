
package com.myster.net.stream.client.msdownload;

class DataBlock {
    /** Should be an even multiple of the block size, unless it's the last block.  */
    public final byte[] bytes;

    /** Place where we started reading. Should be an even multiple of the block size (unless last block)*/
    public final long offset;

    public DataBlock(long offset, byte[] bytes) {
        this.offset = offset;
        this.bytes = bytes;
    }
}