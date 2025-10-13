package com.myster.net.datagram;

/**
 * This class contains all the transport codes, transaction codes and datagram packet error codes. 
 */
public class DatagramConstants {
    // ========== TRANSPORT CODES ==========
    
    /**
     * Transaction protocol number used for all Myster datagram transactions
     */
    public static final short TRANSACTION_PROTOCOL_NUMBER = 1234;
    
    /**
     * Transport code for encrypted packets - "STLS" (Start TLS) as 32-bit int
     */
    public static final int STLS_CODE = 0x53544C53; // "STLS" - Start TLS connection section
    
    /**
     * Transport code for ping packets - 'P', 'I' in network byte order
     */
    public static final short PING_TRANSPORT_CODE = 20553;
    
    /**
     * Transport code for pong packets - 'P', 'O' in network byte order  
     */
    public static final short PONG_TRANSPORT_CODE = 20559;
    
    // ========== TRANSACTION CODES ==========
    
    /**
     * Transaction code for top ten file requests
     */
    public static final int TOP_TEN_TRANSACTION_CODE = 10;
    
    /**
     * Transaction code for search requests
     */
    public static final int SEARCH_TRANSACTION_CODE = 35;
    
    /**
     * Transaction code for type listing requests
     */
    public static final int TYPE_TRANSACTION_CODE = 74;
    
    /**
     * Transaction code for file statistics requests
     */
    public static final int FILE_STATS_TRANSACTION_CODE = 77;
    
// DOES NOT EXIST FOR DATAGRAM
//    /**
//     * Transaction code for directory listing requests
//     */
//    public static final int TYPE_LISTING_TRANSACTION_CODE = 78;
    
    /**
     * Transaction code for server statistics requests
     */
    public static final int SERVER_STATS_TRANSACTION_CODE = 101;
    
    /**
     * Transaction code for hash-based search requests
     */
    public static final int SEARCH_HASH_TRANSACTION_CODE = 150;
    
    /**
     * Transaction code for instant messaging
     */
    public static final int IM_TRANSACTION_CODE = 1111;
    
    // ========== ERROR CODES ==========
    
    /**
     * No error occurred - successful transaction
     */
    public static final byte NO_ERROR = 0;
    
    /**
     * Transaction type is unknown/unsupported by the server
     */
    public static final byte TRANSACTION_TYPE_UNKNOWN = 1;
}