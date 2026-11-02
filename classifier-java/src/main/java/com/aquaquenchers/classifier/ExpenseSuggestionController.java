package com.aquaquenchers.classifier;

/** Thread-safe local suggestion state. Has no amount/date/network/financial write fields. */
public final class ExpenseSuggestionController {
    public static final String STALE = "Remarks changed. Request a new suggestion or choose a category manually.";
    public static final String APPLIED = "Suggestion applied. Review before saving.";
    public static final class Request {
        public final String remarks;
        private final long generation;
        private Request(String remarks, long generation) { this.remarks = remarks; this.generation = generation; }
    }
    private long generation;
    private String remarks = "";
    private String category;
    private String selectionSource = "manual";
    private String acceptedModelVersion;
    private ExpenseCategoryClassifier.Result suggestion;
    private String message = "";
    private boolean pending;

    public synchronized void setRemarks(String value) {
        if (value == null) value = "";
        if (value.equals(remarks)) return;
        boolean hadSuggestion = pending || suggestion != null || acceptedModelVersion != null;
        remarks = value;
        generation++;
        pending = false;
        suggestion = null;
        selectionSource = "manual";
        acceptedModelVersion = null;
        if (hadSuggestion) message = STALE;
    }
    public synchronized Request beginRequest() {
        TextFeatures.validate(remarks);
        generation++;
        pending = true;
        suggestion = null;
        message = "";
        return new Request(remarks, generation);
    }
    /** Call on UI executor after pure background inference; false means discard the response. */
    public synchronized boolean complete(Request request, ExpenseCategoryClassifier.Result result) {
        if (request == null || result == null || request.generation != generation || !request.remarks.equals(remarks)) return false;
        pending = false;
        suggestion = result;
        message = result.message();
        return true;
    }
    public synchronized void fail(Request request) {
        if (request != null && request.generation == generation) {
            pending = false;
            suggestion = null;
            message = "Category suggestion unavailable. Choose a category manually.";
        }
    }
    public synchronized boolean applySuggestion() {
        if (suggestion == null || suggestion.status != ExpenseCategoryClassifier.Status.SUGGESTED) return false;
        category = suggestion.suggestedCategory;
        selectionSource = "suggestion";
        acceptedModelVersion = suggestion.modelVersion;
        message = APPLIED;
        return true;
    }
    /** Call for manual dropdown changes and Choose manually, including dismissing pending results. */
    public synchronized void chooseManually(String value) {
        if (value != null && !Model.LABELS.contains(value)) throw new IllegalArgumentException("Choose a valid category.");
        generation++;
        pending = false;
        suggestion = null;
        category = value;
        selectionSource = "manual";
        acceptedModelVersion = null;
        message = "";
    }
    public synchronized String category() { return category; }
    public synchronized String selectionSource() { return selectionSource; }
    public synchronized String acceptedModelVersion() { return acceptedModelVersion; }
    public synchronized String message() { return message; }
    public synchronized boolean isPending() { return pending; }
}
