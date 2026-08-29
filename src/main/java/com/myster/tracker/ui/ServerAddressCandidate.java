package com.myster.tracker.ui;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import com.myster.net.MysterAddress;

/**
 * Syntactically recognized dotted IPv4 or multi-label DNS server address.
 *
 * <p>Parsing trims outer whitespace, accepts an optional decimal port, normalizes IPv4 octets,
 * lowercases DNS names, and omits the default Myster port from the stable lookup key. IPv6,
 * URLs, paths, malformed hostnames, and bare search words are rejected. Parsing never performs
 * DNS or other network work.
 */
public record ServerAddressCandidate(String host, int port) {
    private static final Pattern DNS_LABEL = Pattern.compile("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?");

    public ServerAddressCandidate {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Host cannot be blank");
        }
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException("Port is out of range");
        }
    }

    /**
     * Recognizes an unambiguous address candidate without resolving it.
     *
     * @param text search/address field contents
     * @return normalized candidate, or empty when the text should remain ordinary search input
     */
    public static Optional<ServerAddressCandidate> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }

        String candidate = text.trim();
        if (candidate.isEmpty() || candidate.chars().anyMatch(Character::isWhitespace)) {
            return Optional.empty();
        }
        if (candidate.contains("/") || candidate.contains("://")) {
            return Optional.empty();
        }

        int firstColon = candidate.indexOf(':');
        int lastColon = candidate.lastIndexOf(':');
        if (firstColon != lastColon) {
            return Optional.empty();
        }

        String host = firstColon < 0 ? candidate : candidate.substring(0, firstColon);
        int port = MysterAddress.DEFAULT_PORT;
        if (firstColon >= 0) {
            String portText = candidate.substring(firstColon + 1);
            if (portText.isEmpty() || !portText.chars().allMatch(Character::isDigit)) {
                return Optional.empty();
            }
            try {
                port = Integer.parseInt(portText);
            } catch (NumberFormatException _) {
                return Optional.empty();
            }
            if (port < 1 || port > 65_535) {
                return Optional.empty();
            }
        }

        Optional<String> normalizedHost = normalizeHost(host);
        if (normalizedHost.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ServerAddressCandidate(normalizedHost.get(), port));
    }

    /** Returns the normalized text passed to {@link MysterAddress#createMysterAddress(String)}. */
    public String addressText() {
        return port == MysterAddress.DEFAULT_PORT ? host : host + ":" + port;
    }

    /** Returns the stable normalized key used to deduplicate equivalent field values. */
    public String lookupKey() {
        return addressText();
    }

    private static Optional<String> normalizeHost(String rawHost) {
        if (rawHost.isEmpty()) {
            return Optional.empty();
        }

        if (rawHost.chars().allMatch(c -> Character.isDigit(c) || c == '.')) {
            return normalizeIpv4(rawHost);
        }

        String host = rawHost.toLowerCase(Locale.ROOT);
        if (host.length() > 253) {
            return Optional.empty();
        }
        String[] labels = host.split("\\.", -1);
        if (labels.length < 2 || Arrays.stream(labels).anyMatch(label -> !DNS_LABEL.matcher(label).matches())) {
            return Optional.empty();
        }
        return Optional.of(host);
    }

    private static Optional<String> normalizeIpv4(String host) {
        String[] octets = host.split("\\.", -1);
        if (octets.length != 4) {
            return Optional.empty();
        }

        int[] values = new int[4];
        for (int i = 0; i < octets.length; i++) {
            if (octets[i].isEmpty()) {
                return Optional.empty();
            }
            try {
                values[i] = Integer.parseInt(octets[i]);
            } catch (NumberFormatException _) {
                return Optional.empty();
            }
            if (values[i] < 0 || values[i] > 255) {
                return Optional.empty();
            }
        }
        return Optional.of(values[0] + "." + values[1] + "." + values[2] + "." + values[3]);
    }
}
