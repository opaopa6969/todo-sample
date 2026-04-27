package todo;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@WebServlet(urlPatterns = {"/todos", "/todos/*"})
public class TodoServlet extends HttpServlet {

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json; charset=utf-8");

        String tenant = "public";
        String user = "anonymous";

        Long id;
        try {
            id = parseId(req.getPathInfo());
        } catch (NumberFormatException e) {
            error(resp, 400, "invalid id");
            return;
        }

        try {
            switch (req.getMethod()) {
                case "GET":
                    if (id != null) { error(resp, 405, "method not allowed"); return; }
                    list(tenant, user, resp);
                    return;
                case "POST":
                    if (id != null) { error(resp, 405, "method not allowed"); return; }
                    create(tenant, user, req, resp);
                    return;
                case "PUT":
                    if (id == null) { error(resp, 405, "method not allowed"); return; }
                    update(tenant, user, id, req, resp);
                    return;
                case "DELETE":
                    if (id == null) { error(resp, 405, "method not allowed"); return; }
                    delete(tenant, user, id, resp);
                    return;
                default:
                    error(resp, 405, "method not allowed");
            }
        } catch (RuntimeException e) {
            error(resp, 400, e.getMessage() == null ? "bad request" : e.getMessage());
        }
    }

    private Long parseId(String pathInfo) {
        if (pathInfo == null || pathInfo.isEmpty() || pathInfo.equals("/")) return null;
        return Long.parseLong(pathInfo.substring(1));
    }

    private void list(String tenant, String user, HttpServletResponse resp) throws IOException {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (Todo t : TodoStore.get().list(tenant, user)) {
            if (!first) sb.append(',');
            first = false;
            sb.append(toJson(t));
        }
        sb.append(']');
        resp.getWriter().write(sb.toString());
    }

    private void create(String tenant, String user, HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Map<String, Object> body = readBody(req);
        Object title = body.get("title");
        if (!(title instanceof String) || ((String) title).isBlank()) {
            error(resp, 400, "title required");
            return;
        }
        Todo t = TodoStore.get().create(tenant, user, (String) title);
        resp.setStatus(201);
        resp.getWriter().write(toJson(t));
    }

    private void update(String tenant, String user, long id, HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Map<String, Object> body = readBody(req);
        String title = (body.get("title") instanceof String s) ? s : null;
        Boolean done = (body.get("done") instanceof Boolean b) ? b : null;
        Todo t = TodoStore.get().update(tenant, user, id, title, done);
        if (t == null) { error(resp, 404, "not found"); return; }
        resp.getWriter().write(toJson(t));
    }

    private void delete(String tenant, String user, long id, HttpServletResponse resp) throws IOException {
        if (!TodoStore.get().delete(tenant, user, id)) { error(resp, 404, "not found"); return; }
        resp.setStatus(204);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readBody(HttpServletRequest req) throws IOException {
        String s = new String(req.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (s.isBlank()) return Map.of();
        Object parsed = Json.parse(s);
        if (!(parsed instanceof Map)) throw new RuntimeException("body must be a JSON object");
        return (Map<String, Object>) parsed;
    }

    private String toJson(Todo t) {
        return "{\"id\":" + t.id
             + ",\"title\":" + Json.escape(t.title)
             + ",\"done\":" + t.done
             + ",\"createdAt\":" + t.createdAt + "}";
    }

    private void error(HttpServletResponse resp, int code, String msg) throws IOException {
        resp.setStatus(code);
        resp.getWriter().write("{\"error\":" + Json.escape(msg) + "}");
    }
}
