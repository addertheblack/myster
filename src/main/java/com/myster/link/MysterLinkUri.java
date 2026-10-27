package com.myster.link;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.myster.cid.ServerCid;
import com.myster.type.MysterType;

/**
 * Immutable version-one resource location. Server is required; file selectors require a type,
 * and names are opaque network references, not paths. Only server, type, and name are supported.
 */
public final class MysterLinkUri {
    public static final int MAX_URI_CHARS = 16 * 1024;

    public enum TargetKind {
        SERVER, TYPE, FILE
    }

    private static final Set<String> FIELDS = Set.of("server", "type", "name");

    private final ServerCid server;
    private final Optional<MysterType> type;
    private final Optional<String> name;

    private MysterLinkUri(ServerCid server, Optional<MysterType> type, Optional<String> name) {
        this.server = server;
        this.type = type;
        this.name = name;
    }

    /** Creates a canonical location, enforcing the same rules and bounds as parsing. */
    public static MysterLinkUri create(ServerCid server, Optional<MysterType> type,
                                      Optional<String> name) throws IOException {
        Objects.requireNonNull(server);
        Objects.requireNonNull(type);
        Objects.requireNonNull(name);

        if (name.isPresent() && type.isEmpty()) {
            throw new IOException("A file link requires a type and server.");
        }
        if (name.isPresent() && (name.get().isEmpty() || name.get().indexOf('\0') >= 0)) {
            throw new IOException("The file reference is empty or invalid.");
        }
        if (name.isPresent()) {
            encode(name.get());
        }

        MysterLinkUri result = new MysterLinkUri(server, type, name);
        if (result.toString().length() > MAX_URI_CHARS) {
            throw new IOException("Resource link is too long.");
        }

        return result;
    }

    /** Parses all fields before interpreting the hierarchy; invalid fields are never discarded. */
    public static MysterLinkUri parse(String text) throws IOException {
        Objects.requireNonNull(text);
        if (text.length() > MAX_URI_CHARS) {
            throw new IOException("Resource link is too long.");
        }

        URI uri = extractUri(text);

        Map<String, String> values = extractValues(uri);

        ServerCid server = new ServerCid(hex(values.get("server"), "server"));
        Optional<MysterType> type = values.containsKey("type")
                ? Optional.of(new MysterType(hex(values.get("type"), "type"))) : Optional.empty();
        Optional<String> name = values.containsKey("name")
                ? Optional.of(decode(values.get("name"))) : Optional.empty();

        return create(server, type, name);
    }

    private static Map<String, String> extractValues(URI uri) throws IOException {
        Map<String, String> values = new HashMap<>();
        if (uri.getRawQuery() == null) {
            throw new IOException("A server CID is required.");
        }

        for (String field : uri.getRawQuery().split("&", -1)) {
            int equals = field.indexOf('=');
            if (equals < 1) {
                throw new IOException("Invalid resource link field.");
            }

            String key = field.substring(0, equals);
            String value = field.substring(equals + 1);
            if (!FIELDS.contains(key) || value.isEmpty() || values.putIfAbsent(key, value) != null) {
                throw new IOException("Unknown, empty, or duplicate resource link field: " + key);
            }
        }
        return values;
    }

    private static URI extractUri(String text) throws IOException {
        URI uri;
        try {
            uri = new URI(text);
        } catch (URISyntaxException exception) {
            throw new IOException("Invalid resource link.", exception);
        }

        if (!"myster".equalsIgnoreCase(uri.getScheme()) || !"link".equals(uri.getRawAuthority())
                || !"/v1".equals(uri.getRawPath()) || uri.getRawFragment() != null
                || uri.getRawUserInfo() != null || uri.getPort() != -1) {
            throw new IOException("Unsupported resource link action or version.");
        }
        return uri;
    }

    private static byte[] hex(String value, String field) throws IOException {
        if (value == null || !value.matches("[0-9a-f]{32}")) {
            throw new IOException("Invalid or missing " + field + ": expected 32 lowercase hex characters.");
        }
        return HexFormat.of().parseHex(value);
    }

    private static String decode(String raw) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (ch == '%') {
                if (i + 2 >= raw.length()) {
                    throw new IOException("Invalid percent escape.");
                }

                int high = Character.digit(raw.charAt(++i), 16);
                int low = Character.digit(raw.charAt(++i), 16);
                if (high < 0 || low < 0) {
                    throw new IOException("Invalid percent escape.");
                }

                bytes.write(high * 16 + low);
            } else {
                if (ch > 127) {
                    throw new IOException("File references must be UTF-8 percent encoded.");
                }

                bytes.write(ch);
            }
        }

        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes.toByteArray())).toString();
        } catch (CharacterCodingException exception) {
            throw new IOException("Invalid UTF-8 file reference.", exception);
        }
    }

    private static String encode(String value) throws IOException {
        ByteBuffer bytes;
        try {
            bytes = StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(value));
        } catch (CharacterCodingException exception) {
            throw new IOException("Invalid Unicode file reference.", exception);
        }

        StringBuilder encoded = new StringBuilder();
        while (bytes.hasRemaining()) {
            int ch = bytes.get() & 255;
            if (ch >= 'a' && ch <= 'z' || ch >= 'A' && ch <= 'Z' || ch >= '0' && ch <= '9'
                    || ch == '-' || ch == '_' || ch == '.' || ch == '~') {
                encoded.append((char) ch);
            } else {
                encoded.append('%').append(HexFormat.of().withUpperCase().toHexDigits((byte) ch));
            }
        }

        return encoded.toString();
    }

    public ServerCid server() {
        return server;
    }

    public Optional<MysterType> type() {
        return type;
    }

    public Optional<String> name() {
        return name;
    }

    public TargetKind kind() {
        return name.isPresent() ? TargetKind.FILE : type.isPresent() ? TargetKind.TYPE : TargetKind.SERVER;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder("myster://link/v1?server=").append(server.asHex());
        type.ifPresent(value -> result.append("&type=").append(value.toHexString()));
        name.ifPresent(value -> {
            try {
                result.append("&name=").append(encode(value));
            } catch (IOException exception) {
                throw new IllegalStateException("Previously validated file reference", exception);
            }
        });

        return result.toString();
    }
}
