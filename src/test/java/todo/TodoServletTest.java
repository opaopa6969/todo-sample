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

    @Test
    void deleteIsIsolatedByTenantBoundary() throws Exception {
        String ownerTenant = "del-owner-" + UUID.randomUUID();
        String otherTenant = "del-other-" + UUID.randomUUID();
        String user = "del-user-" + UUID.randomUUID();

        Exchange created = exchange("POST", null, ownerTenant, user, "{\"title\":\"killme\"}");
        assertEquals(201, created.status());
        String id = created.body().replaceFirst("^\\{\"id\":(\\d+).*$", "$1");

        Exchange crossDelete = exchange("DELETE", "/" + id, otherTenant, user, "");
        assertEquals(404, crossDelete.status());
        assertEquals("{\"error\":\"not found\"}", crossDelete.body());

        Exchange stillThere = exchange("GET", null, ownerTenant, user, "");
        assertEquals(200, stillThere.status());
        assertTrue(stillThere.body().contains("\"title\":\"killme\""));

        Exchange ownerDelete = exchange("DELETE", "/" + id, ownerTenant, user, "");
        assertEquals(204, ownerDelete.status());
        assertEquals("", ownerDelete.body());

        Exchange afterDelete = exchange("GET", null, ownerTenant, user, "");
        assertEquals(200, afterDelete.status());
        assertEquals("[]", afterDelete.body());
    }

    @Test
    void createRejectsBlankAndNonStringTitles() throws Exception {
        String tenant = "title-tenant-" + UUID.randomUUID();
        String user = "title-user-" + UUID.randomUUID();

        Exchange missing = exchange("POST", null, tenant, user, "{}");
        assertEquals(400, missing.status());
        assertEquals("{\"error\":\"title required\"}", missing.body());

        Exchange empty = exchange("POST", null, tenant, user, "{\"title\":\"\"}");
        assertEquals(400, empty.status());
        assertEquals("{\"error\":\"title required\"}", empty.body());

        Exchange blank = exchange("POST", null, tenant, user, "{\"title\":\"   \"}");
        assertEquals(400, blank.status());
        assertEquals("{\"error\":\"title required\"}", blank.body());

        Exchange numeric = exchange("POST", null, tenant, user, "{\"title\":123}");
        assertEquals(400, numeric.status());
        assertEquals("{\"error\":\"title required\"}", numeric.body());
    }

    @Test
    void updateAppliesPartialFieldsAndPreservesTheRest() throws Exception {
        String tenant = "put-tenant-" + UUID.randomUUID();
        String user = "put-user-" + UUID.randomUUID();

        Exchange created = exchange("POST", null, tenant, user, "{\"title\":\"original\"}");
        assertEquals(201, created.status());
        String id = created.body().replaceFirst("^\\{\"id\":(\\d+).*$", "$1");

        Exchange emptyBody = exchange("PUT", "/" + id, tenant, user, "{}");
        assertEquals(200, emptyBody.status());
        assertTrue(emptyBody.body().contains("\"title\":\"original\""));
        assertTrue(emptyBody.body().contains("\"done\":false"));

        Exchange toggleDone = exchange("PUT", "/" + id, tenant, user, "{\"done\":true}");
        assertEquals(200, toggleDone.status());
        assertTrue(toggleDone.body().contains("\"title\":\"original\""));
        assertTrue(toggleDone.body().contains("\"done\":true"));

        Exchange explicitFalse = exchange("PUT", "/" + id, tenant, user, "{\"done\":false}");
        assertEquals(200, explicitFalse.status());
        assertTrue(explicitFalse.body().contains("\"done\":false"));

        Exchange rename = exchange("PUT", "/" + id, tenant, user, "{\"title\":\"renamed\"}");
        assertEquals(200, rename.status());
        assertTrue(rename.body().contains("\"title\":\"renamed\""));
        assertTrue(rename.body().contains("\"done\":false"));
    }

    @Test
    void titleWithJsonMetacharactersSurvivesRoundTrip() throws Exception {
        String tenant = "escape-tenant-" + UUID.randomUUID();
        String user = "escape-user-" + UUID.randomUUID();
        String title = "quote\" backslash\\ newline\n tab\t end";
        String body = "{\"title\":" + Json.escape(title) + "}";

        Exchange created = exchange("POST", null, tenant, user, body);
        assertEquals(201, created.status());
        String expectedField = "\"title\":" + Json.escape(title);
        assertTrue(created.body().contains(expectedField));

        Exchange listed = exchange("GET", null, tenant, user, "");
        assertEquals(200, listed.status());
        assertTrue(listed.body().contains(expectedField));
    }

    @Test
    void deeplyNestedJsonIsRejectedBeforeStackOverflow() throws Exception {
        String tenant = "nest-tenant-" + UUID.randomUUID();
        String user = "nest-user-" + UUID.randomUUID();

        Exchange created = exchange("POST", null, tenant, user, "{\"title\":\"seed\"}");
        assertEquals(201, created.status());
        String id = created.body().replaceFirst("^\\{\"id\":(\\d+).*$", "$1");

        int overLimit = 500;
        StringBuilder nested = new StringBuilder();
        for (int k = 0; k < overLimit; k++) nested.append('[');
        nested.append("1");
        for (int k = 0; k < overLimit; k++) nested.append(']');
        assertTrue(nested.toString().getBytes(StandardCharsets.UTF_8).length < 65_536,
                "payload must stay under the 64 KiB body limit to prove the DoS is in-bounds");

        Exchange rejected = exchange("PUT", "/" + id, tenant, user, nested.toString());
        assertEquals(400, rejected.status());
        assertEquals("{\"error\":\"nesting too deep\"}", rejected.body());

        int underLimit = 50;
        StringBuilder shallow = new StringBuilder("{\"title\":\"ok\",\"deep\":[");
        for (int k = 0; k < underLimit; k++) shallow.append('[');
        shallow.append("1");
        for (int k = 0; k < underLimit; k++) shallow.append(']');
        shallow.append("]}");
        Exchange accepted = exchange("PUT", "/" + id, tenant, user, shallow.toString());
        assertEquals(200, accepted.status());
        assertTrue(accepted.body().contains("\"title\":\"ok\""));
    }

    @Test
    void putWithMalformedOrNonObjectJsonBodyReturnsClientError() throws Exception {
        String tenant = "badbody-tenant-" + UUID.randomUUID();
        String user = "badbody-user-" + UUID.randomUUID();

        Exchange created = exchange("POST", null, tenant, user, "{\"title\":\"seed\"}");
        assertEquals(201, created.status());
        String id = created.body().replaceFirst("^\\{\"id\":(\\d+).*$", "$1");

        Exchange broken = exchange("PUT", "/" + id, tenant, user, "{not json");
        assertEquals(400, broken.status());

        Exchange array = exchange("PUT", "/" + id, tenant, user, "[1,2,3]");
        assertEquals(400, array.status());
        assertEquals("{\"error\":\"body must be a JSON object\"}", array.body());
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
