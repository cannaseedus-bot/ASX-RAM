import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class CanonicalJsonValidator {

    private CanonicalJsonValidator() {}

    public static String canonicalize(Object obj) {
        if (obj == null) return "null";

        if (obj instanceof Boolean) {
            return obj.toString();
        }

        if (obj instanceof Number) {
            return normalizeNumber(obj.toString());
        }

        if (obj instanceof String) {
            return "\"" + escape((String) obj) + "\"";
        }

        if (obj instanceof List<?>) {
            StringBuilder sb = new StringBuilder("[");
            List<?> list = (List<?>) obj;
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(canonicalize(list.get(i)));
            }
            return sb.append("]").toString();
        }

        if (obj instanceof Map<?, ?>) {
            Map<String, Object> sorted = new TreeMap<>();
            for (Map.Entry<?, ?> e : ((Map<?, ?>) obj).entrySet()) {
                sorted.put(e.getKey().toString(), e.getValue());
            }

            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, Object> e : sorted.entrySet()) {
                if (!first) sb.append(",");
                first = false;
                sb.append("\"").append(escape(e.getKey())).append("\":");
                sb.append(canonicalize(e.getValue()));
            }
            return sb.append("}").toString();
        }

        throw new IllegalArgumentException("Unsupported JSON type: " + obj.getClass());
    }

    private static String normalizeNumber(String n) {
        if (n.contains("e") || n.contains("E")) {
            throw new IllegalArgumentException("Scientific notation forbidden");
        }
        if (n.equals("-0")) {
            throw new IllegalArgumentException("Negative zero forbidden");
        }
        if (n.startsWith("0") && n.length() > 1 && !n.startsWith("0.")) {
            throw new IllegalArgumentException("Leading zero forbidden");
        }
        return n;
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public static String sha256Hex(String canonicalJson) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonicalJson.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
