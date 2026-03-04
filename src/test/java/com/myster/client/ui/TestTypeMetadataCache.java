package com.myster.client.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.myster.access.AccessList;
import com.myster.net.MysterAddress;
import com.myster.type.MysterType;

class TestTypeMetadataCache {

    private static final MysterType FAKE_TYPE = new MysterType(new byte[16]);
    private static final MysterAddress FAKE_ADDR;

    static {
        try {
            FAKE_ADDR = MysterAddress.createMysterAddress("127.0.0.1");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** Builds a cache backed by a fetcher that returns the given AccessList. */
    private TypeMetadataCache cacheWith(AccessList al) {
        return new TypeMetadataCache((addr, type) -> Optional.of(al));
    }

    /** Builds a cache whose fetcher always throws IOException. */
    private TypeMetadataCache failingCache() {
        return new TypeMetadataCache((addr, type) -> {
            throw new java.io.IOException("network error");
        });
    }

    /** Builds a cache whose fetcher returns empty (server has no access list). */
    private TypeMetadataCache emptyCache() {
        return new TypeMetadataCache((addr, type) -> Optional.empty());
    }

    private void awaitResolved(TypeMetadataCache cache, MysterType type) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        cache.resolveAsync(type, FAKE_ADDR, latch::countDown);
        assertTrue(latch.await(5, TimeUnit.SECONDS), "resolveAsync callback never fired");
    }

    // ── helpers to build a real AccessList ─────────────────────────────────────

    private static java.security.KeyPair edKeyPair;
    private static java.security.KeyPair rsaKeyPair;

    static {
        try {
            edKeyPair  = java.security.KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
            rsaKeyPair = java.security.KeyPairGenerator.getInstance("RSA").generateKeyPair();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private AccessList makeAccessList(String name) throws Exception {
        return AccessList.createGenesis(
                rsaKeyPair.getPublic(),
                edKeyPair,
                java.util.Collections.emptyList(),
                java.util.List.of("onramp.example.com:6669"),
                com.myster.access.Policy.defaultRestrictive(),
                name,
                "description",
                new String[]{"ext"},
                false);
    }

    // ── tests ───────────────────────────────────────────────────────────────────

    @Test
    void resolveSuccess_getDisplayNameReturnsName() throws Exception {
        AccessList al = makeAccessList("Movies");
        MysterType type = al.getMysterType();
        TypeMetadataCache cache = cacheWith(al);

        awaitResolved(cache, type);

        assertEquals("Movies", cache.getDisplayName(type));
    }

    @Test
    void resolveFailure_getDisplayNameReturnsHex() throws Exception {
        TypeMetadataCache cache = failingCache();

        awaitResolved(cache, FAKE_TYPE);

        assertEquals(FAKE_TYPE.toHexString(), cache.getDisplayName(FAKE_TYPE));
    }

    @Test
    void emptyResult_getDisplayNameReturnsHex() throws Exception {
        TypeMetadataCache cache = emptyCache();

        awaitResolved(cache, FAKE_TYPE);

        assertEquals(FAKE_TYPE.toHexString(), cache.getDisplayName(FAKE_TYPE));
    }

    @Test
    void duplicateResolveAsync_fetchCalledOnlyOnce() throws Exception {
        AtomicInteger callCount = new AtomicInteger(0);
        TypeMetadataCache cache = new TypeMetadataCache((addr, type) -> {
            callCount.incrementAndGet();
            return Optional.empty();
        });

        CountDownLatch latch = new CountDownLatch(1);
        cache.resolveAsync(FAKE_TYPE, FAKE_ADDR, latch::countDown);
        // second call before first completes — should be a no-op
        cache.resolveAsync(FAKE_TYPE, FAKE_ADDR, () -> {});
        assertTrue(latch.await(5, TimeUnit.SECONDS));

        // give a brief moment for any errant second fetch
        Thread.sleep(100);
        assertEquals(1, callCount.get(), "Fetcher should only be called once per type");
    }

    @Test
    void hasAttempted_falseBeforeResolve_trueAfter() throws Exception {
        TypeMetadataCache cache = emptyCache();

        assertFalse(cache.hasAttempted(FAKE_TYPE));
        awaitResolved(cache, FAKE_TYPE);
        assertTrue(cache.hasAttempted(FAKE_TYPE));
    }

    @Test
    void nullOrBlankName_fallsBackToHex() throws Exception {
        AccessList alBlankName = makeAccessList("   ");
        MysterType type = alBlankName.getMysterType();
        TypeMetadataCache cache = cacheWith(alBlankName);

        awaitResolved(cache, type);

        assertEquals(type.toHexString(), cache.getDisplayName(type));
    }
}





