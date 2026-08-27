package todo;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TodoServletTest {
    private final TodoServlet servlet = new TodoServlet();

    @Test
    void todoCannotBeAccessedAcrossTenantBoundary() throws Exception {
        String ownerTenant = "owner-" + UUID.randomUUID();
        String otherTenant = "other-" + UUID.randomUUID();
        String user = "user-" + UUID.randomUUID();

        Exchange created = exchange("POST", null, ownerTenant, user, "{\"title\":\"private\"}");
        assertEquals(201, created.status());
        String id = created.body().replaceFirst("^\\{\"id\":(\\d+).*$", "$1");

        Exchange listed = exchange("GET", null, otherTenant, user, "");
        assertEquals(200, listed.status());
        assertEquals("[]", listed.body());

        Exchange updated = exchange("PUT", "/" + id, otherTenant, user, "{\"done\":true}");
        assertEquals(404, updated.status());
        assertEquals("{\"error\":\"not found\"}", updated.body());
    }

    @Test
    void missingAndBlankHeadersUsePublicAnonymousFallback() throws Exception {
        String title = "fallback-" + UUID.randomUUID();

        Exchange created = exchange("POST", null, null, null, "{\"title\":\"" + title + "\"}");
        assertEquals(201, created.status());

        Exchange listed = exchange("GET", null, " ", "", "");
        assertEquals(200, listed.status());
        assertTrue(listed.body().contains("\"title\":\"" + title + "\""));
    }

    @Test
    void malformedAndUnknownIdsReturnClientErrors() throws Exception {
        Exchange malformed = exchange("PUT", "/not-a-number", "tenant", "user", "{}");
        assertEquals(400, malformed.status());
        assertEquals("{\"error\":\"invalid id\"}", malformed.body());

        Exchange unknown = exchange("PUT", "/9223372036854775807", "tenant", "user", "{}");
        assertEquals(404, unknown.status());
        assertEquals("{\"error\":\"not found\"}", unknown.body());
    }

    @Test
    void bodySizeLimitAccepts64KiBAndRejectsTheNextByte() throws Exception {
        String prefix = "{\"title\":\"";
        String suffix = "\"}";
        String bodyAtLimit = prefix + "a".repeat(65_536 - prefix.length() - suffix.length()) + suffix;

        Exchange accepted = exchange("POST", null, "limit-tenant", "limit-user", bodyAtLimit);
        assertEquals(65_536, bodyAtLimit.getBytes(StandardCharsets.UTF_8).length);
        assertEquals(201, accepted.status());

        Exchange rejected = exchange("POST", null, "limit-tenant", "limit-user", bodyAtLimit + " ");
        assertEquals(65_537, (bodyAtLimit + " ").getBytes(StandardCharsets.UTF_8).length);
        assertEquals(413, rejected.status());
        assertEquals("{\"error\":\"request body too large\"}", rejected.body());
    }

    private Exchange exchange(String method, String pathInfo, String tenant, String user, String body)
            throws Exception {
        byte[] input = body.getBytes(StandardCharsets.UTF_8);
        Map<String, String> headers = Map.of(
                "X-Volta-Tenant-Id", tenant == null ? "__missing__" : tenant,
                "X-Volta-User-Id", user == null ? "__missing__" : user);
        HttpServletRequest request = (HttpServletRequest) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (proxy, called, args) -> switch (called.getName()) {
                    case "getMethod" -> method;
                    case "getPathInfo" -> pathInfo;
                    case "getHeader" -> {
                        String value = headers.get(args[0]);
                        yield "__missing__".equals(value) ? null : value;
                    }
                    case "getInputStream" -> inputStream(input);
                    default -> defaultValue(called.getReturnType());
                });

        ResponseCapture capture = new ResponseCapture();
        HttpServletResponse response = (HttpServletResponse) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{HttpServletResponse.class},
                (proxy, called, args) -> switch (called.getName()) {
                    case "setStatus" -> {
                        capture.status = (int) args[0];
                        yield null;
                    }
                    case "getWriter" -> capture.writer;
                    default -> defaultValue(called.getReturnType());
                });

        servlet.service(request, response);
        capture.writer.flush();
        return new Exchange(capture.status, capture.body.toString());
    }

    private static ServletInputStream inputStream(byte[] body) {
        ByteArrayInputStream input = new ByteArrayInputStream(body);
        return new ServletInputStream() {
            @Override
            public boolean isFinished() {
                return input.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
                throw new UnsupportedOperationException("async reads are not used by the servlet");
            }

            @Override
            public int read() {
                return input.read();
            }

            @Override
            public int read(byte[] bytes, int offset, int length) {
                return input.read(bytes, offset, length);
            }
        };
    }

    private static Object defaultValue(Class<?> type) {
        if (type == void.class) return null;
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        if (type == char.class) return '\0';
        throw new IllegalArgumentException("unsupported primitive: " + type);
    }

    private record Exchange(int status, String body) {}

    private static final class ResponseCapture {
        private int status = 200;
        private final StringWriter body = new StringWriter();
        private final PrintWriter writer = new PrintWriter(body);
    }
}
