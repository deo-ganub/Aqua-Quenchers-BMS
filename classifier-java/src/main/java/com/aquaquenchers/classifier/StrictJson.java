package com.aquaquenchers.classifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small bounded JSON reader. No reflection, filesystem or network interpretation. */
final class StrictJson {
    private final String text;
    private int at;
    private int nodes;
    private StrictJson(String text) { this.text = text; }

    static Object parse(String text) {
        StrictJson parser = new StrictJson(text);
        Object result = parser.value(0);
        parser.space();
        if (parser.at != text.length()) throw invalid();
        return result;
    }

    private Object value(int depth) {
        if (depth > 12 || ++nodes > 65000) throw invalid();
        space();
        if (at >= text.length()) throw invalid();
        char c = text.charAt(at);
        if (c == '"') return string();
        if (c == '{') {
            at++;
            Map<String, Object> map = new LinkedHashMap<>();
            space();
            if (take('}')) return map;
            do {
                space();
                if (at >= text.length() || text.charAt(at) != '"') throw invalid();
                String key = string();
                space();
                if (!take(':') || map.containsKey(key)) throw invalid();
                map.put(key, value(depth + 1));
                space();
                if (take('}')) return map;
            } while (take(','));
            throw invalid();
        }
        if (c == '[') {
            at++;
            List<Object> list = new ArrayList<>();
            space();
            if (take(']')) return list;
            do {
                list.add(value(depth + 1));
                space();
                if (take(']')) return list;
            } while (take(','));
            throw invalid();
        }
        if (text.startsWith("true", at)) { at += 4; return Boolean.TRUE; }
        if (text.startsWith("false", at)) { at += 5; return Boolean.FALSE; }
        if (text.startsWith("null", at)) { at += 4; return null; }
        return number();
    }

    private Double number() {
        int start = at;
        take('-');
        if (take('0')) {
            if (at < text.length() && digit(text.charAt(at))) throw invalid();
        } else {
            if (at >= text.length() || text.charAt(at) < '1' || text.charAt(at) > '9') throw invalid();
            while (at < text.length() && digit(text.charAt(at))) at++;
        }
        if (take('.')) {
            int digits = at;
            while (at < text.length() && digit(text.charAt(at))) at++;
            if (digits == at) throw invalid();
        }
        if (take('e') || take('E')) {
            if (!take('+')) take('-');
            int digits = at;
            while (at < text.length() && digit(text.charAt(at))) at++;
            if (digits == at) throw invalid();
        }
        if (at - start > 128) throw invalid();
        try {
            double result = Double.parseDouble(text.substring(start, at));
            if (!Double.isFinite(result)) throw invalid();
            return result;
        } catch (NumberFormatException ex) { throw invalid(); }
    }

    private String string() {
        if (!take('"')) throw invalid();
        StringBuilder value = new StringBuilder();
        boolean ended = false;
        while (at < text.length()) {
            char c = text.charAt(at++);
            if (c == '"') { ended = true; break; }
            if (c < 0x20) throw invalid();
            if (c == '\\') {
                if (at >= text.length()) throw invalid();
                char escape = text.charAt(at++);
                switch (escape) {
                    case '"': case '\\': case '/': c = escape; break;
                    case 'b': c = '\b'; break;
                    case 'f': c = '\f'; break;
                    case 'n': c = '\n'; break;
                    case 'r': c = '\r'; break;
                    case 't': c = '\t'; break;
                    case 'u':
                        if (at + 4 > text.length()) throw invalid();
                        int cp = 0;
                        for (int i = 0; i < 4; i++) {
                            int hex = Character.digit(text.charAt(at++), 16);
                            if (hex < 0) throw invalid();
                            cp = (cp << 4) | hex;
                        }
                        c = (char) cp;
                        break;
                    default: throw invalid();
                }
            }
            value.append(c);
            if (value.length() > 8192) throw invalid();
        }
        if (!ended) throw invalid();
        String result = value.toString();
        validateUnicode(result);
        return result;
    }

    static void validateUnicode(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (++i >= text.length() || !Character.isLowSurrogate(text.charAt(i))) throw invalid();
            } else if (Character.isLowSurrogate(c)) throw invalid();
        }
    }
    private void space() {
        while (at < text.length()) {
            char c = text.charAt(at);
            if (c != ' ' && c != '\n' && c != '\r' && c != '\t') break;
            at++;
        }
    }
    private boolean take(char c) {
        if (at < text.length() && text.charAt(at) == c) { at++; return true; }
        return false;
    }
    private static boolean digit(char c) { return c >= '0' && c <= '9'; }
    static IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid model package."); }
}
