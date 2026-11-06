package com.example.aquaquencherspos;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import org.json.JSONException;
import org.junit.Test;

/**
 * Checks the inference code and the model validation with a tiny hand-made fixture.
 * The fixture says nothing about real accuracy: there is no trained model yet.
 */
public class ExpenseClassifierTest {

    private static final String LABELS =
            "[\"Fuel\",\"Electricity\",\"Water\",\"Maintenance\",\"Supplies\",\"Salary\",\"Others\"]";

    private static String model(String labels, String weightsFirstRow, double minConfidence) {
        return "{\"version\":\"fixture-1\",\"labels\":" + labels + ","
                + "\"vocabulary\":{\"gasolina\":0,\"kuryente\":1},\"idf\":[1.0,1.0],"
                + "\"weights\":[" + weightsFirstRow + ",[0,8],[0,0],[0,0],[0,0],[0,0],[0,0]],"
                + "\"intercepts\":[0,0,0,0,0,0,0],\"min_confidence\":" + minConfidence + "}";
    }

    @Test
    public void predictsKnownWords() throws JSONException {
        ExpenseClassifier c = ExpenseClassifier.parse(model(LABELS, "[8,0]", 0.5));
        assertEquals("fixture-1", c.version);
        assertEquals("Fuel", c.predict("Gasolina para sa motor").category);
        assertEquals("Electricity", c.predict("bayad kuryente").category);
    }

    @Test
    public void unknownWordsGiveNoSuggestion() throws JSONException {
        ExpenseClassifier c = ExpenseClassifier.parse(model(LABELS, "[8,0]", 0.5));
        assertNull(c.predict("something else entirely").category);
    }

    @Test
    public void lowConfidenceGivesNoSuggestion() throws JSONException {
        ExpenseClassifier c = ExpenseClassifier.parse(model(LABELS, "[8,0]", 0.99999));
        assertNull(c.predict("gasolina").category);
    }

    @Test
    public void rejectsWrongLabelOrder() {
        String swapped = "[\"Electricity\",\"Fuel\",\"Water\",\"Maintenance\",\"Supplies\",\"Salary\",\"Others\"]";
        assertThrows(JSONException.class, () -> ExpenseClassifier.parse(model(swapped, "[8,0]", 0.5)));
    }

    @Test
    public void rejectsWrongDimensions() {
        assertThrows(JSONException.class, () -> ExpenseClassifier.parse(model(LABELS, "[8,0,1]", 0.5)));
    }

    @Test
    public void rejectsNonFiniteValues() {
        assertThrows(JSONException.class, () -> ExpenseClassifier.parse(model(LABELS, "[\"NaN\",0]", 0.5)));
    }
}
