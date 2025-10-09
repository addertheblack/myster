package com.myster.net.datagram;

import java.io.IOException;
import java.security.PublicKey;
import java.util.Optional;

import com.myster.identity.Identity;

/**
 * Contains an encrypt packet and decrypt packet which work on byte[]
 */
public class DatagramEncryptUtil {
    /**
     * @param payload to encrypt
     * @param serverKey to use to encode the sync encryption key
     * @param clientIdentity if present to use to sign. If not present then don't sign the packet.
     * @return ecrypted request packet
     */
    public static byte[] encryptPacket(byte[] payload, PublicKey serverKey, Optional<Identity> clientIdentity) {
        return new byte[] {};
    }
    
    public static byte[] decryptResultPacket(byte[] resultPacket) {
        return new byte[] {};
    }
    
    public static byte[] decryptResultPacket(byte[] resultPacket, byte[] syncDecryptKey) {
        return new byte[] {};
    }
    
    /**
     * Result of decrypting the request packet
     */
    public static class R {
        public final Optional<PublicKey> publicKey;
        public final Optional<Long> keyHash; // might be the wrong type, byte[] might be more appropriate
        public final byte[] payload;
        public final byte[] syncDecryptKey;
        
        // potentially more stuff...
        
        public R() {
            this(Optional.empty(), Optional.empty(), new byte[] {}, new byte[] {});
        }
        
        private R(Optional<PublicKey> publicKey, Optional<Long> keyHash, byte[] payload, byte[] syncDecryptKey) {
            this.publicKey = publicKey;
            this.keyHash = keyHash;
            this.payload = payload;
            this.syncDecryptKey = syncDecryptKey;
        }

        public R withPublicKey(Optional<PublicKey> publicKey) {
            return new R(publicKey, this.keyHash, this.payload, this.syncDecryptKey);
        }

        public R withKeyHash(Optional<Long> keyHash) {
            return new R(this.publicKey, keyHash, this.payload, this.syncDecryptKey);
        }

        public R withPayload(byte[] payload) {
            return new R(this.publicKey, this.keyHash, payload, this.syncDecryptKey);
        }
        
        public R withSyncDecryptKey(byte[] syncDecryptKey) {
            return new R(this.publicKey, this.keyHash, this.payload, syncDecryptKey);
        }
    }
    
    public interface Lookup {
        Optional<PublicKey> findPublicKey(long keyHash);
    }
    
    // In my world all parsing or format errors are IOExceptions. It's a Myster coding convention.
    public static class DecryptionException extends IOException {
        // constructors TODO
    }
    
    /**
     * Gets a request packet and th
     * @param encryptedRequestPacket
     * @param l
     * @return
     */
    public static R decryptRequestPacket(byte[] encryptedRequestPacket, Lookup l) throws DecryptionException {
        return null;
    }
}
