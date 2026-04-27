package todo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class Json {
    private final String s;
    private int i;

    private Json(String s) { this.s = s; }

    static Object parse(String s) {
        Json j = new Json(s);
        j.skipWs();
        Object v = j.value();
        j.skipWs();
        if (j.i != s.length()) throw new RuntimeException("trailing data");
        return v;
    }

    private Object value() {
        skipWs();
        char c = peek();
        if (c == '{') return object();
        if (c == '[') return array();
        if (c == '"') return string();
        if (c == 't' || c == 'f') return bool();
        if (c == 'n') { expectKw("null"); return null; }
        return number();
    }

    private Map<String, Object> object() {
        expect('{');
        Map<String, Object> m = new LinkedHashMap<>();
        skipWs();
        if (peek() == '}') { i++; return m; }
        while (true) {
            skipWs();
            String k = string();
            skipWs();
            expect(':');
            m.put(k, value());
            skipWs();
            char c = s.charAt(i++);
            if (c == ',') continue;
            if (c == '}') return m;
            throw new RuntimeException("expected , or }");
        }
    }

    private List<Object> array() {
        expect('[');
        List<Object> a = new ArrayList<>();
        skipWs();
        if (peek() == ']') { i++; return a; }
        while (true) {
            a.add(value());
            skipWs();
            char c = s.charAt(i++);
            if (c == ',') continue;
            if (c == ']') return a;
            throw new RuntimeException("expected , or ]");
        }
    }

    private String string() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (true) {
            char c = s.charAt(i++);
            if (c == '"') return sb.toString();
            if (c == '\\') {
                char e = s.charAt(i++);
                switch (e) {
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case 'u':
                        sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                        break;
                    default: throw new RuntimeException("bad escape");
                }
            } else {
                sb.append(c);
            }
        }
    }

    private Boolean bool() {
        if (s.startsWith("true", i))  { i += 4; return Boolean.TRUE; }
        if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
        throw new RuntimeException("bad bool");
    }

    private void expectKw(String kw) {
        if (!s.startsWith(kw, i)) throw new RuntimeException("expected " + kw);
        i += kw.length();
    }

    private Object number() {
        int start = i;
        if (peek() == '-') i++;
        while (i < s.length() && "0123456789.eE+-".indexOf(s.charAt(i)) >= 0) i++;
        String num = s.substring(start, i);
        if (num.contains(".") || num.contains("e") || num.contains("E")) return Double.parseDouble(num);
        return Long.parseLong(num);
    }

    private void skipWs() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
    }

    private char peek() { return s.charAt(i); }

    private void expect(char c) {
        if (i >= s.length() || s.charAt(i++) != c) throw new RuntimeException("expected " + c);
    }

    static String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 2);
        sb.append('"');
        for (int k = 0; k < s.length(); k++) {
            char c = s.charAt(k);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        sb.append('"');
        return sb.toString();
    }
}
