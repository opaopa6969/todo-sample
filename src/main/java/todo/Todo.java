package todo;

public class Todo {
    public final long id;
    public volatile String title;
    public volatile boolean done;
    public final long createdAt;

    public Todo(long id, String title, long createdAt) {
        this.id = id;
        this.title = title;
        this.done = false;
        this.createdAt = createdAt;
    }
}
