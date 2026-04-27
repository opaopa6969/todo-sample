package todo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class TodoStore {
    private static final TodoStore INSTANCE = new TodoStore();

    public static TodoStore get() {
        return INSTANCE;
    }

    private record Key(String tenant, String user) {}

    private final Map<Key, Map<Long, Todo>> data = new ConcurrentHashMap<>();
    private final AtomicLong seq = new AtomicLong();

    public List<Todo> list(String tenant, String user) {
        Map<Long, Todo> m = data.get(new Key(tenant, user));
        if (m == null) return List.of();
        List<Todo> out = new ArrayList<>(m.values());
        out.sort(Comparator.comparingLong(t -> t.createdAt));
        return out;
    }

    public Todo create(String tenant, String user, String title) {
        long id = seq.incrementAndGet();
        Todo t = new Todo(id, title, System.currentTimeMillis());
        data.computeIfAbsent(new Key(tenant, user), k -> new ConcurrentHashMap<>()).put(id, t);
        return t;
    }

    public Todo update(String tenant, String user, long id, String title, Boolean done) {
        Map<Long, Todo> m = data.get(new Key(tenant, user));
        if (m == null) return null;
        Todo t = m.get(id);
        if (t == null) return null;
        if (title != null) t.title = title;
        if (done != null) t.done = done;
        return t;
    }

    public boolean delete(String tenant, String user, long id) {
        Map<Long, Todo> m = data.get(new Key(tenant, user));
        if (m == null) return false;
        return m.remove(id) != null;
    }
}
