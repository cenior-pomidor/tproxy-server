package org.json;

import java.util.HashMap;
import java.util.Map;

/**
 * Minimal stand in for Android's bundled org.json, enough for the flat control objects the
 * bridge sends. Values are strings, numbers, booleans or null.
 */
public class JSONObject {

    private final Map<String, Object> values = new HashMap<>();

    public JSONObject(String source) throws JSONException {
        Parser parser = new Parser(source);
        parser.skipWhitespace();
        parser.expect('{');
        parser.skipWhitespace();
        if (parser.peek() == '}') {
            parser.next();
            return;
        }
        while (true) {
            parser.skipWhitespace();
            String key = parser.readString();
            parser.skipWhitespace();
            parser.expect(':');
            parser.skipWhitespace();
            values.put(key, parser.readValue());
            parser.skipWhitespace();
            char character = parser.next();
            if (character == '}') {
                break;
            }
            if (character != ',') {
                throw new JSONException("unexpected " + character);
            }
        }
    }

    public String optString(String name) {
        Object value = values.get(name);
        return value instanceof String ? (String) value : (value == null ? "" : String.valueOf(value));
    }

    public int optInt(String name) {
        Object value = values.get(name);
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    public long optLong(String name) {
        Object value = values.get(name);
        return value instanceof Number ? ((Number) value).longValue() : 0;
    }

    private static final class Parser {

        private final String source;
        private int offset;

        private Parser(String source) {
            this.source = source;
        }

        private void skipWhitespace() {
            while (offset < source.length() && Character.isWhitespace(source.charAt(offset))) {
                offset++;
            }
        }

        private char peek() throws JSONException {
            if (offset >= source.length()) {
                throw new JSONException("unexpected end of input");
            }
            return source.charAt(offset);
        }

        private char next() throws JSONException {
            char value = peek();
            offset++;
            return value;
        }

        private void expect(char expected) throws JSONException {
            if (next() != expected) {
                throw new JSONException("expected " + expected);
            }
        }

        private String readString() throws JSONException {
            expect('"');
            StringBuilder builder = new StringBuilder();
            while (true) {
                char character = next();
                if (character == '"') {
                    return builder.toString();
                }
                if (character == '\\') {
                    char escape = next();
                    switch (escape) {
                        case 'n': builder.append('\n'); break;
                        case 't': builder.append('\t'); break;
                        case 'r': builder.append('\r'); break;
                        case 'b': builder.append('\b'); break;
                        case 'f': builder.append('\f'); break;
                        case 'u':
                            builder.append((char) Integer.parseInt(source.substring(offset, offset + 4), 16));
                            offset += 4;
                            break;
                        default: builder.append(escape);
                    }
                    continue;
                }
                builder.append(character);
            }
        }

        private Object readValue() throws JSONException {
            char character = peek();
            if (character == '"') {
                return readString();
            }
            if (character == '{' || character == '[') {
                throw new JSONException("nested values are not supported by the stub");
            }
            int start = offset;
            while (offset < source.length() && ",}] \t\r\n".indexOf(source.charAt(offset)) < 0) {
                offset++;
            }
            String token = source.substring(start, offset);
            if (token.equals("true")) {
                return Boolean.TRUE;
            }
            if (token.equals("false")) {
                return Boolean.FALSE;
            }
            if (token.equals("null")) {
                return null;
            }
            try {
                if (token.indexOf('.') >= 0 || token.indexOf('e') >= 0 || token.indexOf('E') >= 0) {
                    return Double.valueOf(token);
                }
                return Long.valueOf(token);
            } catch (NumberFormatException e) {
                throw new JSONException("bad number " + token);
            }
        }
    }
}
