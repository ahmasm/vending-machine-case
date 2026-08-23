package io.github.ahmasm.vending.machine.application.command;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;

public final class CanonicalCommandFingerprint {

    private CanonicalCommandFingerprint() {}

    public static String hash(String... values) {
        var canonical = String.join("|", Arrays.stream(values)
                .map(CanonicalCommandFingerprint::field)
                .toList());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static String field(String value) {
        return value.length() + ":" + value;
    }
}
