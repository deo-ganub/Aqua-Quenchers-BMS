package com.aquaquenchers.classifier;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/** Dependency-free executable checks; failure exits nonzero regardless of Java assertion settings. */
public final class ClassifierSelfTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        byte[] bytes = fixture().getBytes(StandardCharsets.UTF_8);
        Model model = Model.load(bytes, Model.digest(bytes), true);
        ExpenseCategoryClassifier classifier = new ExpenseCategoryClassifier(model);
        check(model.vocabularySize() == 1, "vocabulary size");
        ExpenseCategoryClassifier.Result fuel = classifier.suggest("ＦＵＥＬ");
        check(fuel.status == ExpenseCategoryClassifier.Status.SUGGESTED && "Fuel".equals(fuel.suggestedCategory), "NFKC suggestion");
        check(fuel.modelScore > .99 && fuel.reason == ExpenseCategoryClassifier.Reason.NONE, "result contract");
        check(classifier.suggest("zzzxq").reason == ExpenseCategoryClassifier.Reason.NO_KNOWN_TERMS, "unknown fallback");
        check(ExpenseCategoryClassifier.unavailable().suggest("fuel").status == ExpenseCategoryClassifier.Status.UNAVAILABLE, "missing model");
        check(ExpenseCategoryClassifier.fromPackage(bytes, "0".repeat(64), true).suggest("fuel").status == ExpenseCategoryClassifier.Status.UNAVAILABLE, "invalid package fallback");
        throwsInput(() -> classifier.suggest(" "), "Enter remarks to request a suggestion.");
        throwsInput(() -> classifier.suggest("a".repeat(501)), "Use 500 characters or fewer.");
        throwsInput(() -> classifier.suggest("\uFDFA".repeat(30)), "Use 500 characters or fewer.");
        check(TextFeatures.tokens("\uD83D\uDE00".repeat(500)).isEmpty(), "codepoint length not UTF16 units");
        throwsInput(() -> classifier.suggest("\uD83D\uDE00".repeat(501)), "Use 500 characters or fewer.");
        throwsInvalid(() -> TextFeatures.tokens("\uD800"));
        check(classifier.suggest("a ".repeat(129)).reason == ExpenseCategoryClassifier.Reason.TOO_MANY_TOKENS, "token limit no truncation");
        check(classifier.suggest("a ".repeat(128)).reason == ExpenseCategoryClassifier.Reason.NO_KNOWN_TERMS, "token boundary");
        check(TextFeatures.tokens("ＡＢＣ gasolina-paraⅧ １２").equals(List.of("abc", "gasolina", "paraviii", "12")), "letter number preprocessing");
        Map<Integer, Double> repeated = TextFeatures.vector(TextFeatures.tokens("fuel fuel"), model);
        check(repeated.size() == 1 && repeated.get(0) == 1, "raw TF L2 normalization");
        double[] p = classifier.probabilities(repeated);
        double sum = 0; for (double v : p) sum += v;
        check(Math.abs(sum - 1) < 1e-12, "softmax sum");
        String tie = fixture().replace("[9]", "[0]").replace("\"top_score\":0.7,\"top_gap\":0.15", "\"top_score\":0,\"top_gap\":0");
        byte[] tieBytes = tie.getBytes(StandardCharsets.UTF_8);
        check("Fuel".equals(new ExpenseCategoryClassifier(Model.load(tieBytes, Model.digest(tieBytes), true)).suggest("fuel").suggestedCategory), "stable label order tie");
        String low = fixture().replace("[9]", "[0]");
        byte[] lowBytes = low.getBytes(StandardCharsets.UTF_8);
        ExpenseCategoryClassifier.Result uncertain = new ExpenseCategoryClassifier(Model.load(lowBytes, Model.digest(lowBytes), true)).suggest("fuel");
        check(uncertain.reason == ExpenseCategoryClassifier.Reason.LOW_SCORE && uncertain.modelScore == null && uncertain.suggestedCategory == null, "uncertainty contract");
        throwsInvalid(() -> Model.load(bytes, "0".repeat(64), true));
        throwsInvalid(() -> Model.load(bytes, Model.digest(bytes), false));
        for (String invalid : List.of(
                fixture().replace("\"schema_version\":1", "\"schema_version\":1,\"schema_version\":1"),
                fixture().replace("\"schema_version\":1", "\"extra\":true,\"schema_version\":1"),
                fixture().replace("\"Fuel\"", "\"Unknown\""),
                fixture().replace("\"idf\":[1]", "\"idf\":[0]"),
                fixture().replace("\"idf\":[1]", "\"idf\":[1,2]"),
                fixture().replace("[9]", "[1e999]"),
                fixture().replace("\"top_gap\":0.15", "\"top_gap\":2"),
                fixture().replace("\"synthetic\":true", "\"synthetic\":1"),
                fixture().replace("\"fuel\"", "\"FUEL\""),
                fixture().replace("\"biases\":[0,0,0,0,0,0,0]", "\"biases\":[0,0]"))) {
            byte[] bad = invalid.getBytes(StandardCharsets.UTF_8);
            throwsInvalid(() -> Model.load(bad, Model.digest(bad), true));
        }
        throwsInvalid(() -> Model.load(new byte[Model.MAX_BYTES + 1], "0".repeat(64), true));
        byte[] badUtf8 = {(byte)0xFF};
        throwsInvalid(() -> Model.load(badUtf8, Model.digest(badUtf8), true));
        throwsInvalid(() -> StrictJson.parse("{\"x\":01}"));
        throwsInvalid(() -> StrictJson.parse("{\"x\":\"\\uD800\"}"));
        ExpenseSuggestionController controller = new ExpenseSuggestionController();
        controller.chooseManually("Water");
        controller.setRemarks("fuel");
        ExpenseSuggestionController.Request request = controller.beginRequest();
        check(controller.complete(request, fuel), "complete current response");
        check("Water".equals(controller.category()) && controller.acceptedModelVersion() == null, "receiving never applies");
        check(controller.applySuggestion() && "Fuel".equals(controller.category()), "apply local selection");
        check("suggestion".equals(controller.selectionSource()) && "TEST-SYNTHETIC".equals(controller.acceptedModelVersion()), "accepted provenance only");
        check(ExpenseSuggestionController.APPLIED.equals(controller.message()), "review message");
        controller.chooseManually("Maintenance");
        check(!controller.applySuggestion() && "manual".equals(controller.selectionSource()), "manual override priority");
        request = controller.beginRequest();
        controller.setRemarks("fuel updated");
        check(!controller.complete(request, fuel) && !controller.applySuggestion(), "stale async discard");
        check(ExpenseSuggestionController.STALE.equals(controller.message()), "stale message");
        controller.setRemarks("fuel");
        request = controller.beginRequest();
        controller.chooseManually("Others");
        check(!controller.complete(request, fuel) && "Others".equals(controller.category()), "inflight response after manual choice");
        request=controller.beginRequest();controller.cancelPending();check(!controller.complete(request,fuel)&&!controller.applySuggestion(),"recreated view cancels inaccessible pending response");
        Path golden = args.length > 0 ? Paths.get(args[0]) : Paths.get("classifier/fixtures/golden.json");
        if (Files.isRegularFile(golden)) golden(golden);
        else throw new IllegalStateException("Golden parity fixture missing: " + golden);
        System.out.println("PASS: " + checks + " Java classifier assertions (synthetic correctness only; no real-data or device gates).");
    }
    @SuppressWarnings("unchecked")
    private static void golden(Path path) throws Exception {
        Map<String, Object> root = (Map<String, Object>) StrictJson.parse(Files.readString(path));
        Path modelPath = path.getParent().resolve((String) root.get("model_path"));
        Model model = Model.load(Files.readAllBytes(modelPath), (String) root.get("sha256"), true);
        ExpenseCategoryClassifier classifier = new ExpenseCategoryClassifier(model);
        for (Object item : (List<?>) root.get("cases")) {
            Map<String, Object> vector = (Map<String, Object>) item;
            String remarks = (String) vector.get("remarks");
            List<String> tokens = TextFeatures.tokens(remarks);
            check(tokens.equals(vector.get("tokens")), "Python/Java tokens");
            Map<Integer, Double> features = TextFeatures.vector(tokens, model);
            List<?> expected = (List<?>) vector.get("features");
            check(features.size() == expected.size(), "Python/Java feature count");
            for (Object entry : expected) {
                List<?> values = (List<?>) entry;
                int index = ((Number) values.get(0)).intValue();
                check(features.containsKey(index) && Math.abs(features.get(index) - ((Number) values.get(1)).doubleValue()) <= 1e-6, "Python/Java feature");
            }
            Object expectedProbabilities = vector.get("probabilities");
            if (expectedProbabilities instanceof List<?>) {
                double[] actual = classifier.probabilities(features);
                List<?> probabilities = (List<?>) expectedProbabilities;
                for (int k = 0; k < 7; k++) check(Math.abs(actual[k] - ((Number) probabilities.get(k)).doubleValue()) <= 1e-6, "Python/Java scores");
            }
            ExpenseCategoryClassifier.Result result = classifier.suggest(remarks);
            check(result.status.name().equals(vector.get("status")), "Python/Java status");
            check(java.util.Objects.equals(result.suggestedCategory, vector.get("category")), "Python/Java category");
            check(result.reason.name().equals(vector.get("reason")), "Python/Java reason");
        }
    }
    static String fixture() {
        return "{\"schema_version\":1,\"model_version\":\"TEST-SYNTHETIC\",\"preprocessing_version\":\"nfkc-lower-ln-1\","
                + "\"labels\":[\"Fuel\",\"Electricity\",\"Water\",\"Maintenance\",\"Supplies\",\"Salary\",\"Others\"],"
                + "\"vocabulary\":[\"fuel\"],\"idf\":[1],\"weights\":[[9],[0],[0],[0],[0],[0],[0]],"
                + "\"biases\":[0,0,0,0,0,0,0],\"thresholds\":{\"top_score\":0.7,\"top_gap\":0.15},"
                + "\"provenance\":{\"source_commit\":\"UNCOMMITTED\",\"dataset_sha256\":\"" + "0".repeat(64)
                + "\",\"split_sha256\":\"" + "0".repeat(64) + "\",\"synthetic\":true}}";
    }
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; }
    private static void throwsInvalid(Runnable action) {
        try { action.run(); throw new AssertionError("Expected invalid input/package rejection"); }
        catch (IllegalArgumentException expected) { checks++; }
    }
    private static void throwsInput(Runnable action, String message) {
        try { action.run(); throw new AssertionError("Expected input rejection"); }
        catch (IllegalArgumentException expected) { check(message.equals(expected.getMessage()), "exact validation message"); }
    }
}
