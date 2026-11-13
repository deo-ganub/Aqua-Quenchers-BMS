package com.aquaquenchers.classifier;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Frozen preprocessing contract nfkc-lower-ln-1. No training occurs on device. */
public final class TextFeatures {
    private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}]+");
    private TextFeatures() { }
    public static String normalize(String remarks) {
        return Normalizer.normalize(remarks, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    }
    static String validate(String remarks) {
        if (remarks == null) throw new IllegalArgumentException("Enter remarks to request a suggestion.");
        StrictJson.validateUnicode(remarks);
        if (remarks.codePointCount(0, remarks.length()) > 500)
            throw new IllegalArgumentException("Use 500 characters or fewer.");
        String normalized = normalize(remarks);
        if (normalized.codePointCount(0, normalized.length()) > 500)
            throw new IllegalArgumentException("Use 500 characters or fewer.");
        if (normalized.codePoints().allMatch(cp -> Character.isWhitespace(cp) || Character.isSpaceChar(cp)))
            throw new IllegalArgumentException("Enter remarks to request a suggestion.");
        return normalized;
    }
    public static List<String> tokens(String remarks) {
        String normalized = validate(remarks);
        List<String> tokens = new ArrayList<>();
        Matcher match = TOKEN.matcher(normalized);
        while (match.find()) tokens.add(match.group());
        return Collections.unmodifiableList(tokens);
    }
    static Map<Integer, Double> vector(List<String> tokens, Model model) {
        Map<Integer, Double> values = new TreeMap<>();
        for (int i = 0; i < tokens.size(); i++) {
            add(values, model, tokens.get(i));
            if (i > 0) add(values, model, tokens.get(i - 1) + " " + tokens.get(i));
        }
        double squared = 0;
        for (Map.Entry<Integer, Double> term : values.entrySet()) {
            double value = term.getValue() * model.idf[term.getKey()];
            term.setValue(value);
            squared += value * value;
        }
        if (!Double.isFinite(squared)) throw new IllegalArgumentException("Invalid model package.");
        if (squared > 0) {
            double norm = Math.sqrt(squared);
            for (Map.Entry<Integer, Double> term : values.entrySet()) term.setValue(term.getValue() / norm);
        }
        return values;
    }
    private static void add(Map<Integer, Double> values, Model model, String term) {
        Integer index = model.vocabulary.get(term);
        if (index != null) values.put(index, values.getOrDefault(index, 0.0) + 1);
    }
}
