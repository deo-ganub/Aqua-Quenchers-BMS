package com.aquaquenchers.classifier;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Immutable validated parameters. Expected digest must come from reviewed signed application code. */
public final class Model {
    public static final int MAX_BYTES = 2 * 1024 * 1024;
    public static final String PREPROCESSING_VERSION = "nfkc-lower-ln-1";
    public static final List<String> LABELS = Collections.unmodifiableList(Arrays.asList(
            "Fuel", "Electricity", "Water", "Maintenance", "Supplies", "Salary", "Others"));
    final String version;
    final Map<String, Integer> vocabulary;
    final double[] idf;
    final double[][] weights;
    final double[] biases;
    final double topScore;
    final double topGap;
    public final boolean synthetic;

    private Model(String version, Map<String, Integer> vocabulary, double[] idf,
                  double[][] weights, double[] biases, double topScore, double topGap,
                  boolean synthetic) {
        this.version = version;
        this.vocabulary = Collections.unmodifiableMap(vocabulary);
        this.idf = idf;
        this.weights = weights;
        this.biases = biases;
        this.topScore = topScore;
        this.topGap = topGap;
        this.synthetic = synthetic;
    }

    public String version() { return version; }
    public int vocabularySize() { return vocabulary.size(); }

    public static Model load(byte[] json, String trustedSha256, boolean allowSynthetic) {
        if (json == null || json.length == 0 || json.length > MAX_BYTES
                || trustedSha256 == null || !trustedSha256.matches("[0-9a-f]{64}")) throw invalid();
        byte[] bytes = json.clone();
        if (!digest(bytes).equals(trustedSha256)) throw invalid();
        String content;
        try {
            content = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException ex) { throw invalid(); }
        Map<String, Object> root = object(StrictJson.parse(content), "schema_version", "model_version",
                "preprocessing_version", "labels", "vocabulary", "idf", "weights", "biases", "thresholds", "provenance");
        if (number(root.get("schema_version")) != 1
                || !PREPROCESSING_VERSION.equals(string(root.get("preprocessing_version")))) throw invalid();
        String version = string(root.get("model_version"));
        if (!version.matches("[A-Za-z0-9._-]{1,64}")) throw invalid();
        if (!LABELS.equals(list(root.get("labels")))) throw invalid();
        List<?> terms = list(root.get("vocabulary"));
        if (terms.isEmpty() || terms.size() > 3000) throw invalid();
        Map<String, Integer> vocabulary = new HashMap<>();
        for (int i = 0; i < terms.size(); i++) {
            String term = string(terms.get(i));
            if (term.codePointCount(0, term.length()) > 500
                    || !term.equals(TextFeatures.normalize(term))
                    || !term.matches("[\\p{L}\\p{N}]+(?: [\\p{L}\\p{N}]+)?")
                    || vocabulary.put(term, i) != null) throw invalid();
        }
        double[] idf = vector(root.get("idf"), terms.size());
        for (double value : idf) if (value <= 0) throw invalid();
        List<?> rows = list(root.get("weights"));
        if (rows.size() != 7) throw invalid();
        double[][] weights = new double[7][];
        for (int i = 0; i < 7; i++) weights[i] = vector(rows.get(i), terms.size());
        double[] biases = vector(root.get("biases"), 7);
        Map<String, Object> policy = object(root.get("thresholds"), "top_score", "top_gap");
        double topScore = number(policy.get("top_score"));
        double topGap = number(policy.get("top_gap"));
        if (topScore < 0 || topScore > 1 || topGap < 0 || topGap > 1) throw invalid();
        Map<String, Object> provenance = object(root.get("provenance"), "source_commit", "dataset_sha256", "split_sha256", "synthetic");
        Object syntheticValue = provenance.get("synthetic");
        if (!(syntheticValue instanceof Boolean)) throw invalid();
        boolean synthetic = (Boolean) syntheticValue;
        if (synthetic && !allowSynthetic) throw invalid();
        String commit = string(provenance.get("source_commit"));
        if (!commit.matches("(?:[0-9a-f]{40}|UNCOMMITTED)") || (!synthetic && commit.equals("UNCOMMITTED"))) throw invalid();
        for (String key : Arrays.asList("dataset_sha256", "split_sha256"))
            if (!string(provenance.get(key)).matches("[0-9a-f]{64}")) throw invalid();
        return new Model(version, vocabulary, idf, weights, biases, topScore, topGap, synthetic);
    }

    public static String digest(byte[] bytes) {
        try {
            byte[] result = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder hex = new StringBuilder(64);
            for (byte item : result) hex.append(String.format(java.util.Locale.ROOT, "%02x", item & 255));
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 unavailable", ex); }
    }

    private static double[] vector(Object value, int size) {
        List<?> values = list(value);
        if (values.size() != size) throw invalid();
        double[] result = new double[size];
        for (int i = 0; i < size; i++) result[i] = number(values.get(i));
        return result;
    }
    static List<?> list(Object value) {
        if (!(value instanceof List<?>)) throw invalid();
        return (List<?>) value;
    }
    static String string(Object value) {
        if (!(value instanceof String)) throw invalid();
        return (String) value;
    }
    static double number(Object value) {
        if (!(value instanceof Number)) throw invalid();
        double result = ((Number) value).doubleValue();
        if (!Double.isFinite(result)) throw invalid();
        return result;
    }
    @SuppressWarnings("unchecked")
    static Map<String, Object> object(Object value, String... keys) {
        if (!(value instanceof Map<?, ?>)) throw invalid();
        Map<String, Object> map = (Map<String, Object>) value;
        Set<String> expected = new HashSet<>(Arrays.asList(keys));
        if (!map.keySet().equals(expected)) throw invalid();
        return map;
    }
    private static IllegalArgumentException invalid() { return StrictJson.invalid(); }
}
