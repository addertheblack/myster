package com.general.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.prefs.Preferences;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestApplicationContext {
    @TempDir
    Path temporary;

    @Test
    void publishesImmediatelyAndForwardingClientDoesNotRemoveOwnerPreferences() throws Exception {
        exercise("forward", false);
    }

    @Test
    void failedBindAndClosePreserveExistingPreferences() throws Exception {
        exercise("failed", true);
    }

    private void exercise(String mode, boolean separatePreferences) throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0, 12, InetAddress.getLocalHost())) {
            port = reservation.getLocalPort();
        }
        Process owner = launch("owner", port, temporary.resolve("owner"));
        try {
            CompletableFuture<String> ready = CompletableFuture.supplyAsync(() -> {
                try {
                    return owner.inputReader().readLine();
                } catch (IOException exception) {
                    throw new RuntimeException(exception);
                }
            });
            assertEquals("READY", ready.get(10, TimeUnit.SECONDS));
            Process client = launch(mode, port,
                    temporary.resolve(separatePreferences ? "client" : "owner"));
            try {
                assertTrue(client.waitFor(10, TimeUnit.SECONDS), "Second JVM did not complete");
                assertEquals(0, client.exitValue(), client.errorReader().readAllAsString());
            } finally {
                client.destroyForcibly();
            }
            owner.getOutputStream().write(1);
            owner.getOutputStream().flush();
            assertTrue(owner.waitFor(10, TimeUnit.SECONDS), "Owner did not shut down");
            assertEquals(0, owner.exitValue(), owner.errorReader().readAllAsString());
        } finally {
            owner.destroyForcibly();
        }
    }

    private Process launch(String mode, int port, Path preferencesRoot) throws IOException {
        return new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Djava.awt.headless=true",
                "-Djava.util.prefs.userRoot=" + preferencesRoot,
                "-Djava.util.prefs.syncInterval=3600",
                "-cp", System.getProperty("java.class.path"),
                Probe.class.getName(), mode, Integer.toString(port)).start();
    }

    /** Separate JVM so preference caching cannot make publication checks pass accidentally. */
    public static class Probe {
        public static void main(String[] args) throws Exception {
            ApplicationSingletonListener listener = new ApplicationSingletonListener() {
                public void requestLaunch(String[] forwarded) {}
                public void errored(Exception exception) {}
            };
            Preferences parent = Preferences.userNodeForPackage(ApplicationContext.class);
            ApplicationContext context = new ApplicationContext(Integer.parseInt(args[1]),
                    listener, new String[] {"test-launch"});
            try {
                switch (args[0]) {
                    case "owner" -> {
                        if (!context.start()) throw new AssertionError("Owner did not start");
                        System.out.println("READY");
                        System.in.read();
                        context.close();
                        if (parent.nodeExists("startup")) {
                            throw new AssertionError("Owner did not remove startup preferences");
                        }
                    }
                    case "forward" -> {
                        if (!parent.nodeExists("startup")) {
                            throw new AssertionError("Startup preferences were not published");
                        }
                        if (context.start()) throw new AssertionError("Client became owner");
                        context.close();
                        if (!parent.nodeExists("startup")) {
                            throw new AssertionError("Forwarding client removed owner preferences");
                        }
                    }
                    case "failed" -> {
                        Preferences node = parent.node("startup");
                        node.putInt("password", -123);
                        node.putInt("port", -1);
                        node.flush();
                        try {
                            context.start();
                            throw new AssertionError("Occupied port should fail to bind");
                        } catch (IOException expected) {
                            context.close();
                        }
                        if (!parent.nodeExists("startup")
                                || node.getInt("password", 0) != -123
                                || node.getInt("port", 0) != -1) {
                            throw new AssertionError("Failed startup changed existing preferences");
                        }
                    }
                    default -> throw new AssertionError(args[0]);
                }
            } finally {
                context.close();
            }
        }
    }
}
