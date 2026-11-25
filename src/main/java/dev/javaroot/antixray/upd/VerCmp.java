package dev.javaroot.antixray.upd;

public final class VerCmp {

    private VerCmp() {
    }

    public static boolean isNewer(String cur, String latest) {
        int[] cParts = parseParts(cur);
        int[] lParts = parseParts(latest);
        if (cParts == null || lParts == null) {
            return false;
        }
        int len = Math.max(cParts.length, lParts.length);
        for (int i = 0; i < len; i++) {
            int c = i < cParts.length ? cParts[i] : 0;
            int l = i < lParts.length ? lParts[i] : 0;
            if (l != c) {
                return l > c;
            }
        }
        return false;
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim();
        if (s.length() > 1 && (s.charAt(0) == 'v' || s.charAt(0) == 'V')) {
            return s.substring(1).trim();
        }
        return s;
    }

    public static int[] parseParts(String raw) {
        String norm = normalize(raw);
        if (norm.isEmpty()) {
            return null;
        }
        int cut = norm.length();
        int dash = norm.indexOf('-');
        if (dash >= 0) {
            cut = dash;
        }
        int plus = norm.indexOf('+');
        if (plus >= 0 && plus < cut) {
            cut = plus;
        }
        String core = norm.substring(0, cut).trim();
        if (core.isEmpty()) {
            return null;
        }

        String[] tokens = core.split("\\.", -1);
        int[] parts = new int[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            if (!isDigits(tokens[i])) {
                return null;
            }
            try {
                parts[i] = Integer.parseInt(tokens[i]);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return parts;
    }

    private static boolean isDigits(String token) {
        if (token.isEmpty()) {
            return false;
        }
        for (int i = 0; i < token.length(); i++) {
            if (!Character.isDigit(token.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
