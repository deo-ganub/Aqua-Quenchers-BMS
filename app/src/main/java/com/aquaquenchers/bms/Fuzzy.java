package com.aquaquenchers.bms;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Typo-tolerant customer search. A customer matches when every word typed is found in the
 * name (as a word start, inside a word, or with a small typo) or the digits match the
 * contact number. Higher scores are better matches; -1 means no match.
 */
public final class Fuzzy {
    private Fuzzy() { }

    /** Lowercase, remove accents (ñ → n) and keep only letters, digits and single spaces. */
    static String normalize(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
        return n.replaceAll("[^a-z0-9]+", " ").trim();
    }

    /** Edit distance with adjacent swaps (Damerau–Levenshtein, optimal string alignment). */
    static int distance(String a, String b) {
        int[][] d = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) d[i][0] = i;
        for (int j = 0; j <= b.length(); j++) d[0][j] = j;
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1))
                    d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
            }
        }
        return d[a.length()][b.length()];
    }

    /** Typos allowed for a typed word: none up to 3 letters, 1 up to 6, then 2. */
    static int allowedTypos(int length) { return length <= 3 ? 0 : length <= 6 ? 1 : 2; }

    /** Best score of one typed word against the words of a name; -1 if it matches none. */
    private static int wordScore(String q, String[] words) {
        int best = -1, typos = allowedTypos(q.length());
        for (String w : words) {
            if (w.equals(q)) best = Math.max(best, 100);
            else if (w.startsWith(q)) best = Math.max(best, 80);
            else if (w.contains(q)) best = Math.max(best, 60);
            else if (typos > 0) {
                // Compare with the whole word and with its start, so "mari" also finds "mariaa".
                int full = distance(q, w);
                int prefix = w.length() > q.length() ? distance(q, w.substring(0, q.length())) : full;
                int dist = Math.min(full, prefix);
                if (dist <= typos) best = Math.max(best, 40 - 10 * dist);
            }
        }
        return best;
    }

    /** Match score of a search against a customer's name and contact number; -1 = no match. */
    public static int score(String query, String name, String contact) {
        String q = normalize(query);
        if (q.isEmpty()) return 0;
        String digits = query.replaceAll("[^0-9]", "");
        if (!digits.isEmpty() && digits.length() == q.replace(" ", "").length()) {
            String c = contact == null ? "" : contact.replaceAll("[^0-9]", "");
            if (c.equals(digits)) return 100;
            if (c.startsWith(digits)) return 90;
            return c.contains(digits) ? 70 : -1;
        }
        String n = normalize(name);
        String compactQ = q.replace(" ", ""), compactN = n.replace(" ", "");
        if (compactN.equals(compactQ)) return 100;
        if (compactN.startsWith(compactQ)) return 95;
        if (compactN.contains(compactQ)) return 85;
        String[] words = n.isEmpty() ? new String[0] : n.split(" ");
        int total = 0, count = 0;
        for (String part : q.split(" ")) {
            int s = wordScore(part, words);
            if (s < 0) return -1;
            total += s; count++;
        }
        return Math.min(80, total / count);
    }

    public static boolean matches(String query, String name, String contact) { return score(query, name, contact) >= 0; }
}
