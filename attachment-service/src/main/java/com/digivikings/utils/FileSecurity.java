package com.digivikings.utils;

import org.apache.tika.Tika;

import java.text.Normalizer;
import java.util.Set;

public final class FileSecurity {
    private FileSecurity() {}

    private static final Tika TIKA = new Tika();
    private static final Set<String> ALLOWED = Set.of(
            "application/pdf", "image/png", "image/jpeg"
    );

    public static String sanitizeFilename(String original) {
        if (original == null || original.isBlank()) return "file";
        String n = Normalizer.normalize(original, Normalizer.Form.NFKC);
        n = n.replaceAll("[\\r\\n\"]", "_");
        n = n.replaceAll("[^\\p{L}\\p{N}._ -]", "_");
        n = n.trim();
        return n.length() > 120 ? n.substring(0, 120) : n;
    }

    public static String sniffAndValidateMime(byte[] headBytes) {
        String detected = TIKA.detect(headBytes);
        if (!ALLOWED.contains(detected)) {
            throw new IllegalArgumentException("Unsupported content type: " + detected);
        }
        return detected;
    }
}

