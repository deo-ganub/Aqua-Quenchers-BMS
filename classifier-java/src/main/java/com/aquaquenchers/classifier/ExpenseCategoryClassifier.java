package com.aquaquenchers.classifier;

import java.util.List;
import java.util.Map;

/** Pure local inference. Suggestion cannot persist an expense or change a form. */
public final class ExpenseCategoryClassifier {
    public enum Status { SUGGESTED, MANUAL_REQUIRED, UNAVAILABLE }
    public enum Reason { NONE, LOW_SCORE, NO_KNOWN_TERMS, TOO_MANY_TOKENS, MODEL_UNAVAILABLE }
    public static final class Result {
        public final Status status;
        public final String suggestedCategory;
        public final Double modelScore;
        public final String modelVersion;
        public final Reason reason;
        private Result(Status status, String category, Double score, String version, Reason reason) {
            this.status = status;
            this.suggestedCategory = category;
            this.modelScore = score;
            this.modelVersion = version;
            this.reason = reason;
        }
        public String message() {
            if (status == Status.SUGGESTED) return "Suggested category: " + suggestedCategory;
            if (status == Status.UNAVAILABLE) return "Category suggestion unavailable. Choose a category manually.";
            return "No reliable suggestion. Choose a category manually.";
        }
    }
    private final Model model;
    public ExpenseCategoryClassifier(Model model) { this.model = model; }
    public static ExpenseCategoryClassifier unavailable() { return new ExpenseCategoryClassifier(null); }
    public static ExpenseCategoryClassifier fromPackage(byte[] bytes, String trustedSha256, boolean allowSynthetic) {
        try { return new ExpenseCategoryClassifier(Model.load(bytes, trustedSha256, allowSynthetic)); }
        catch (IllegalArgumentException ex) { return unavailable(); }
    }
    public Result suggest(String remarks) {
        String normalized = TextFeatures.validate(remarks);
        if (model == null) return unavailableResult();
        try {
            List<String> tokens = TextFeatures.tokens(normalized);
            if (tokens.size() > 128) return manual(Reason.TOO_MANY_TOKENS);
            Map<Integer, Double> features = TextFeatures.vector(tokens, model);
            if (features.isEmpty()) return manual(Reason.NO_KNOWN_TERMS);
            double[] scores = probabilities(features);
            int top = 0;
            for (int i = 1; i < 7; i++) if (scores[i] > scores[top]) top = i;
            double second = -1;
            for (int i = 0; i < 7; i++) if (i != top && scores[i] > second) second = scores[i];
            if (scores[top] < model.topScore || scores[top] - second < model.topGap) return manual(Reason.LOW_SCORE);
            return new Result(Status.SUGGESTED, Model.LABELS.get(top), scores[top], model.version, Reason.NONE);
        } catch (RuntimeException ex) { return unavailableResult(); }
    }
    double[] probabilities(Map<Integer, Double> features) {
        double[] scores = model.biases.clone();
        double maximum = Double.NEGATIVE_INFINITY;
        for (int k = 0; k < 7; k++) {
            for (Map.Entry<Integer, Double> term : features.entrySet()) scores[k] += model.weights[k][term.getKey()] * term.getValue();
            if (!Double.isFinite(scores[k])) throw StrictJson.invalid();
            maximum = Math.max(maximum, scores[k]);
        }
        double total = 0;
        for (int k = 0; k < 7; k++) { scores[k] = Math.exp(scores[k] - maximum); total += scores[k]; }
        for (int k = 0; k < 7; k++) scores[k] /= total;
        return scores;
    }
    private Result manual(Reason reason) { return new Result(Status.MANUAL_REQUIRED, null, null, model.version, reason); }
    private static Result unavailableResult() { return new Result(Status.UNAVAILABLE, null, null, null, Reason.MODEL_UNAVAILABLE); }
}
