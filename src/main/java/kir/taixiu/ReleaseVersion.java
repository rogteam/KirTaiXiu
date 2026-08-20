package kir.taixiu;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ReleaseVersion {
    private static final Pattern TAG = Pattern.compile("\\\"tag_name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern URL = Pattern.compile("\\\"html_url\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");

    private ReleaseVersion() { }

    static String tagFromJson(String json) {
        Matcher matcher = TAG.matcher(json == null ? "" : json);
        return matcher.find() ? matcher.group(1) : "";
    }

    static String urlFromJson(String json, String fallback) {
        Matcher matcher = URL.matcher(json == null ? "" : json);
        return matcher.find() ? matcher.group(1).replace("\\/", "/") : fallback;
    }

    static int compare(String left, String right) {
        int[] a = numbers(left), b = numbers(right);
        for (int index = 0; index < Math.max(a.length, b.length); index++) {
            int av = index < a.length ? a[index] : 0, bv = index < b.length ? b[index] : 0;
            if (av != bv) return Integer.compare(av, bv);
        }
        return 0;
    }

    private static int[] numbers(String version) {
        String clean = version == null ? "" : version.trim().replaceFirst("^[vV]", "").split("[-+]", 2)[0];
        String[] parts = clean.split("\\.");
        int[] numbers = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try { numbers[i] = Integer.parseInt(parts[i]); } catch (NumberFormatException ignored) { numbers[i] = 0; }
        }
        return numbers;
    }
}
