package com.myster.net.datagram;

/**
 * Constants for the Myster Secure Datagram (MSD) Rev C protocol fields and values.
 * These constants define the MessagePack field names and supported algorithms
 * used throughout the MSD encryption/decryption process.
 */
public class MSDConstants {
    
    // ========== CONTEXT STRINGS ==========
    
    /**
     * Context string used in MSD request signatures
     */
    public static final String MSD_REQUEST_CONTEXT = "MSD-REQ";
    
    /**
     * Context string used in MSD response signatures
     */
    public static final String MSD_RESPONSE_CONTEXT = "MSD-RES";
    
    // ========== SECTION 1 FIELDS (Encrypted Keying Info) ==========
    
    /**
     * Algorithm field in Section 1 - specifies symmetric cipher for Section 3
     */
    public static final String SECTION1_ALG = "/alg";
    
    /**
     * Symmetric key field in Section 1 - 32-byte random key for Section 3 encryption
     */
    public static final String SECTION1_KEY = "/key";
    
    /**
     * Nonce field in Section 1 - 12-byte random nonce for Section 3 encryption
     */
    public static final String SECTION1_NONCE = "/nonce";
    
    // ========== SECTION 2 FIELDS (Client Signature Block) ==========
    
    /**
     * Timestamp field in Section 2 - Unix milliseconds when packet was created
     */
    public static final String SECTION2_TIMESTAMP = "/ts";
    
    /**
     * Signature field in Section 2 - client's signature over the packet
     */
    public static final String SECTION2_SIGNATURE = "/sig";
    
    /**
     * Public key field in Section 2 - client's public key (optional if server knows it)
     */
    public static final String SECTION2_PUBLIC_KEY = "/pub";
    
    /**
     * Client identifier field in Section 2 - hash-based lookup hint for client's public key
     */
    public static final String SECTION2_CLIENT_ID = "/cid";
    
    /**
     * Signature algorithm field in Section 2 - algorithm used for signature
     */
    public static final String SECTION2_SIG_ALG = "/sig_alg";
    
    /**
     * Client ID algorithm field in Section 2 - algorithm used to generate CID
     */
    public static final String SECTION2_CID_ALG = "/cid_alg";
    
    /**
     * Server key identifier field in Section 2 - helps server select which private key to use
     */
    public static final String SECTION2_SERVER_KEY_ID = "/srv_kid";
    
    // ========== ALGORITHM VALUES ==========
    
    /**
     * ChaCha20-Poly1305 AEAD algorithm identifier
     */
    public static final String ALG_CHACHA20_POLY1305 = "chacha20poly1305";
    
    /**
     * AES-256-GCM AEAD algorithm identifier (future use)
     */
    public static final String ALG_AES_256_GCM = "aes-256-gcm";
    
    /**
     * Ed25519 signature algorithm identifier
     */
    public static final String SIG_ALG_ED25519 = "ed25519";
    
    /**
     * EdDSA signature algorithm identifier (generic EdDSA)
     */
    public static final String SIG_ALG_EDDSA = "EdDSA";
    
    /**
     * SHA-256 with RSA signature algorithm identifier
     */
    public static final String SIG_ALG_SHA256_RSA = "SHA256withRSA";
    
    /**
     * SHA-256 with ECDSA signature algorithm identifier
     */
    public static final String SIG_ALG_SHA256_ECDSA = "SHA256withECDSA";
    
    /**
     * SHA-256 truncated to 128 bits CID algorithm identifier
     */
    public static final String CID_ALG_SHA256_128 = "sha256-128";
    
    // ========== CRYPTO PARAMETERS ==========
    
    /**
     * Size of symmetric encryption key in bytes (256 bits)
     */
    public static final int KEY_SIZE = 32;
    
    /**
     * Size of nonce for ChaCha20-Poly1305 in bytes (96 bits)
     */
    public static final int NONCE_SIZE = 12;
    
    /**
     * Size of client identifier (CID) in bytes (128 bits)
     */
    public static final int CID_SIZE = 16;
    
    /**
     * Hash algorithm used for CID generation and signatures
     */
    public static final String HASH_ALGORITHM = "SHA-256";
    
    // ========== DEFAULT VALUES ==========
    
    /**
     * Default symmetric encryption algorithm
     */
    public static final String DEFAULT_SYMMETRIC_ALG = ALG_CHACHA20_POLY1305;
    
    /**
     * Default CID generation algorithm
     */
    public static final String DEFAULT_CID_ALG = CID_ALG_SHA256_128;
}