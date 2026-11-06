package com.example.aquaquencherspos;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Legacy fixture parser retained for regression coverage. Production expense
 * inference uses ClassifierAssets and the canonical classifier-java module.
 * Legacy TF-IDF plus multiclass logistic regression. This class only runs
 * a released model. No model ships yet, so the app reports "Category suggestion
 * unavailable" until a validated expense_model.json is added under assets.
 *
 * Model file fields: version, labels, vocabulary (token to index), idf, weights
 * (one row per label), intercepts and min_confidence.
 */
public final class ExpenseClassifier {

    /** The fixed label order every released model must use (B-03.3). */
    static final List<String> LABELS = Arrays.asList(
            "Fuel", "Electricity", "Water", "Maintenance", "Supplies", "Salary", "Others");

    private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}]+");

    /** A category, or null when the prediction is uncertain or the words are unknown. */
    public static final class Prediction {
        public final String category;

        Prediction(String category) {
            this.category = category;
        }
    }

    final String version;
    private final Map<String, Integer> vocabulary;
    private final double[] idf;
    private final double[][] weights;
    private final double[] intercepts;
    private final double minConfidence;

    private ExpenseClassifier(String version, Map<String, Integer> vocabulary, double[] idf,
                              double[][] weights, double[] intercepts, double minConfidence) {
        this.version = version;
        this.vocabulary = vocabulary;
        this.idf = idf;
        this.weights = weights;
        this.intercepts = intercepts;
        this.minConfidence = minConfidence;
    }

    /** Parses and validates a model. Anything malformed is rejected, never partly used. */
    public static ExpenseClassifier parse(String json) throws JSONException {
        JSONObject m = new JSONObject(json);
        String version = m.getString("version");
        if (version.isEmpty()) {
            throw new JSONException("Missing model version.");
        }
        JSONArray labels = m.getJSONArray("labels");
        if (labels.length() != LABELS.size()) {
            throw new JSONException("Wrong label count.");
        }
        for (int i = 0; i < labels.length(); i++) {
            if (!LABELS.get(i).equals(labels.getString(i))) {
                throw new JSONException("Wrong label order.");
            }
        }
        JSONObject vocab = m.getJSONObject("vocabulary");
        JSONArray idfJson = m.getJSONArray("idf");
        int size = idfJson.length();
        if (size == 0 || vocab.length() != size) {
            throw new JSONException("Vocabulary and IDF sizes differ.");
        }
        Map<String, Integer> vocabulary = new HashMap<>();
        for (java.util.Iterator<String> it = vocab.keys(); it.hasNext(); ) {
            String token = it.next();
            int index = vocab.getInt(token);
            if (index < 0 || index >= size) {
                throw new JSONException("Vocabulary index out of range.");
            }
            vocabulary.put(token, index);
        }
        double[] idf = finiteArray(idfJson, size);
        JSONArray rows = m.getJSONArray("weights");
        if (rows.length() != LABELS.size()) {
            throw new JSONException("Wrong weight rows.");
        }
        double[][] weights = new double[rows.length()][];
        for (int i = 0; i < rows.length(); i++) {
            weights[i] = finiteArray(rows.getJSONArray(i), size);
        }
        double[] intercepts = finiteArray(m.getJSONArray("intercepts"), LABELS.size());
        double minConfidence = m.getDouble("min_confidence");
        if (Double.isNaN(minConfidence) || minConfidence < 0 || minConfidence > 1) {
            throw new JSONException("Invalid confidence threshold.");
        }
        return new ExpenseClassifier(version, vocabulary, idf, weights, intercepts, minConfidence);
    }

    private static double[] finiteArray(JSONArray array, int expected) throws JSONException {
        if (array.length() != expected) {
            throw new JSONException("Wrong array size.");
        }
        double[] out = new double[expected];
        for (int i = 0; i < expected; i++) {
            out[i] = array.getDouble(i);
            if (Double.isNaN(out[i]) || Double.isInfinite(out[i])) {
                throw new JSONException("Non-finite model value.");
            }
        }
        return out;
    }

    public Prediction predict(String remarks) {
        double[] features = new double[idf.length];
        boolean known = false;
        Matcher matcher = TOKEN.matcher(remarks.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            Integer index = vocabulary.get(matcher.group());
            if (index != null) {
                features[index] += 1;
                known = true;
            }
        }
        if (!known) {
            return new Prediction(null);
        }
        double norm = 0;
        for (int i = 0; i < features.length; i++) {
            features[i] *= idf[i];
            norm += features[i] * features[i];
        }
        norm = Math.sqrt(norm);
        for (int i = 0; i < features.length; i++) {
            features[i] /= norm;
        }

        double[] scores = new double[LABELS.size()];
        double max = Double.NEGATIVE_INFINITY;
        int best = 0;
        for (int label = 0; label < scores.length; label++) {
            double score = intercepts[label];
            for (int i = 0; i < features.length; i++) {
                score += weights[label][i] * features[i];
            }
            scores[label] = score;
            if (score > max) {
                max = score;
                best = label;
            }
        }
        double sum = 0;
        for (double score : scores) {
            sum += Math.exp(score - max);
        }
        double confidence = 1.0 / sum;
        return new Prediction(confidence >= minConfidence ? LABELS.get(best) : null);
    }
}
