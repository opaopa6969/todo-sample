# 答え — tramli basics

## 完成コード

### 1. `TodoState.java`(新規)

```java
package todo;

public enum TodoState {
    NEW(false, true),
    DOING(false, false),
    DONE(true, false),
    CANCELLED(true, false);

    private final boolean terminal;
    private final boolean initial;

    TodoState(boolean terminal, boolean initial) {
        this.terminal = terminal;
        this.initial = initial;
    }

    public boolean isTerminal() { return terminal; }
    public boolean isInitial()  { return initial; }
}
```

### 2. `TodoFlow.java`(新規)— 遷移表

```java
package todo;

import java.util.Map;

public final class TodoFlow {
    private static final Map<String, Map<TodoState, TodoState>> TRANSITIONS = Map.of(
        "start",    Map.of(TodoState.NEW,   TodoState.DOING),
        "complete", Map.of(TodoState.DOING, TodoState.DONE),
        "cancel",   Map.of(TodoState.NEW,   TodoState.CANCELLED,
                           TodoState.DOING, TodoState.CANCELLED)
    );

    private TodoFlow() {}

    public static TodoState next(TodoState current, String action) {
        if (current.isTerminal()) {
            throw new IllegalArgumentException("already terminal: " + current);
        }
        Map<TodoState, TodoState> byState = TRANSITIONS.get(action);
        if (byState == null) {
            throw new IllegalArgumentException("unknown action: " + action);
        }
        TodoState next = byState.get(current);
        if (next == null) {
            throw new IllegalArgumentException("invalid transition: " + action + " from " + current);
        }
        return next;
    }
}
```

### 3. `Todo.java` 改修

```java
package todo;

public class Todo {
    public final long id;
    public volatile String title;
    public volatile TodoState state;
    public final long createdAt;

    public Todo(long id, String title, long createdAt) {
        this.id = id;
        this.title = title;
        this.state = TodoState.NEW;   // initial
        this.createdAt = createdAt;
    }
}
```

### 4. `TodoStore.java` に `transition` 追加

```java
public Todo transition(String tenant, String user, long id, String action) {
    Map<Long, Todo> m = data.get(new Key(tenant, user));
    if (m == null) return null;
    Todo t = m.get(id);
    if (t == null) return null;
    t.state = TodoFlow.next(t.state, action);
    return t;
}
```

### 5. `TodoServlet.java` 改修(PUT 部分)

```java
private void updateScoped(String tenant, String user, long id, boolean canActOnOthers,
                          HttpServletRequest req, HttpServletResponse resp) throws IOException {
    Map<String, Object> body = readBody(req);
    String title  = (body.get("title")  instanceof String s) ? s : null;
    String action = (body.get("action") instanceof String a) ? a : null;

    // タイトル更新
    if (title != null) {
        Todo t = TodoStore.get().updateTitle(tenant, user, id, title);
        if (t == null && canActOnOthers) t = TodoStore.get().updateTitleInTenant(tenant, id, title);
        if (t == null) { error(resp, 404, "not found"); return; }
    }

    // 状態遷移
    if (action != null) {
        Todo t = TodoStore.get().transition(tenant, user, id, action);
        // (ADMIN 拡張は省略 — 演習で考える)
        if (t == null) { error(resp, 404, "not found"); return; }
        resp.getWriter().write(toJson(t));
        return;
    }

    // どちらも無ければ最後に取得して返す
    Todo t = TodoStore.get().findInTenant(tenant, id);
    if (t == null) { error(resp, 404, "not found"); return; }
    resp.getWriter().write(toJson(t));
}
```

### 6. `toJson` 改修

```java
private String toJson(Todo t) {
    return "{\"id\":" + t.id
         + ",\"title\":" + Json.escape(t.title)
         + ",\"state\":" + Json.escape(t.state.name())
         + ",\"createdAt\":" + t.createdAt + "}";
}
```

## 解説(対話)

### Map で遷移表書くのは tramli "らしい" か?

> **後輩**「これって tramli じゃなくて、ただの Map 遷移表ですよね?」

> **先輩**「**tramli の魂を真似した**コードだ。tramli ライブラリ自体を入れずに、思想だけ取ってる。本物の tramli にはこれに加えて:」

- **build()** 時の検証(到達不能 state、不正な initial/terminal)
- **requires/produces** によるデータ依存解析
- **Mermaid 自動生成**
- **plugin/SPI**

> **先輩**「が、`Map<action, Map<from, to>>` という **遷移を1箇所に集約する習慣** が一番大事だ。これを覚えれば switch-if 地獄から抜け出せる。」

### 不正遷移の build-time 検出

> **後輩**「Map で書いただけだと build-time じゃないですよね? run-time の例外ですよね?」

> **先輩**「正しい。本物の tramli は `FlowDefinition.build()` で **8項目** 検証する:」

```
1. すべての state に initial / terminal の整合性
2. 到達不能 state が無い
3. terminal から出て行く遷移が無い
4. 同じ key の transition が複数定義されてない
5. requires が前段の produces で満たされてる
6. branch の全分岐がカバーされてる
7. ...
```

> **先輩**「これを Java の generics + annotation processor でコンパイル時に検出する。それが本物の tramli。今回はそこまでやらないが、**集約と表化** という設計判断は同じ。」

### data flow graph

> **先輩**「tramli の真骨頂は `requires/produces`:」

```java
class StartProcessor implements StateProcessor {
    Set<Class<?>> requires() { return Set.of(TodoId.class); }
    Set<Class<?>> produces() { return Set.of(StartedAt.class); }
    void process(FlowContext ctx) { ... }
}
```

> **先輩**「これを書くと tramli が **どの processor がどのデータを必要としてるか** のグラフを作れる。OIDC auth flow みたいに 9 state × 5 processor の絡みでも、グラフを見れば一目瞭然。LLM に読ませるのも効くらしい。」

### 自動 Mermaid 生成

> **後輩**「Mermaid 図、自分で書いたら遷移増やすたびに更新するのめんどくさいですよね」

> **先輩**「そう。tramli は `FlowDefinition` から **Mermaid を自動生成** する。コードと図が **絶対にズレない**。これは大きい。」

```java
// 仮想的な使い方
String mermaid = todoFlow.toMermaid();
// stateDiagram-v2
//   [*] --> NEW
//   NEW --> DOING : start
//   ...
```

今回の答えコードでも、`TodoFlow` の `TRANSITIONS` map から似たことが reflection で書けるはず(演習として残す)。

## 学んだこと

- 状態遷移は **1箇所に集約**(switch を分散させない)
- terminal / initial を **enum 自身に書く**(別管理しない)
- 不正遷移は **可能な限り早く** 弾く(build-time が最強、それが無理なら型、それも無理なら一箇所に集約した run-time check)
- `Map<action, Map<from, to>>` は tramli 的書き方の **入門**。本物は build-time validation + dataflow graph を持つ
- **コード = 図** にすることで、説明と実装のズレが消える

## 次の lesson

[05-auth-viz-static](../../05-auth-viz-static/) — volta-auth-proxy の auth flow を tramli の流儀で書き、現在状態を **可視化** する。
