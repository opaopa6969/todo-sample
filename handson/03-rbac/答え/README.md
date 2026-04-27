# 答え — RBAC

## 完成コード

### `TodoStore.java` 追加メソッド

```java
public List<Todo> listAllInTenant(String tenant) {
    List<Todo> out = new ArrayList<>();
    for (var entry : data.entrySet()) {
        if (entry.getKey().tenant().equals(tenant)) {
            out.addAll(entry.getValue().values());
        }
    }
    out.sort(Comparator.comparingLong(t -> t.createdAt));
    return out;
}

public Todo findInTenant(String tenant, long id) {
    for (var entry : data.entrySet()) {
        if (entry.getKey().tenant().equals(tenant)) {
            Todo t = entry.getValue().get(id);
            if (t != null) return t;
        }
    }
    return null;
}

public boolean deleteInTenant(String tenant, long id) {
    for (var entry : data.entrySet()) {
        if (entry.getKey().tenant().equals(tenant)) {
            if (entry.getValue().remove(id) != null) return true;
        }
    }
    return false;
}

public Todo updateInTenant(String tenant, long id, String title, Boolean done) {
    Todo t = findInTenant(tenant, id);
    if (t == null) return null;
    if (title != null) t.title = title;
    if (done != null) t.done = done;
    return t;
}
```

### `TodoServlet.java` 改修

```java
@Override
protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    resp.setContentType("application/json; charset=utf-8");

    String tenant = req.getHeader("X-Volta-Tenant-Id");
    String user   = req.getHeader("X-Volta-User-Id");
    String role   = req.getHeader("X-Volta-Role");
    if (tenant == null || tenant.isBlank()) tenant = "public";
    if (user   == null || user.isBlank())   user   = "anonymous";
    boolean canActOnOthers = "ADMIN".equals(role) || "OWNER".equals(role);

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
                String target = req.getParameter("user");
                if (target != null && !target.equals(user)) {
                    if (!canActOnOthers) { error(resp, 403, "forbidden"); return; }
                    listUser(tenant, target, resp);
                } else {
                    listUser(tenant, user, resp);
                }
                return;
            case "POST":
                if (id != null) { error(resp, 405, "method not allowed"); return; }
                create(tenant, user, req, resp);
                return;
            case "PUT":
                if (id == null) { error(resp, 405, "method not allowed"); return; }
                updateScoped(tenant, user, id, canActOnOthers, req, resp);
                return;
            case "DELETE":
                if (id == null) { error(resp, 405, "method not allowed"); return; }
                deleteScoped(tenant, user, id, canActOnOthers, resp);
                return;
            default:
                error(resp, 405, "method not allowed");
        }
    } catch (RuntimeException e) {
        error(resp, 400, e.getMessage() == null ? "bad request" : e.getMessage());
    }
}

private void listUser(String tenant, String user, HttpServletResponse resp) throws IOException {
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

private void updateScoped(String tenant, String user, long id, boolean canActOnOthers,
                          HttpServletRequest req, HttpServletResponse resp) throws IOException {
    Map<String, Object> body = readBody(req);
    String title = (body.get("title") instanceof String s) ? s : null;
    Boolean done = (body.get("done") instanceof Boolean b) ? b : null;

    Todo t = TodoStore.get().update(tenant, user, id, title, done);
    if (t != null) { resp.getWriter().write(toJson(t)); return; }

    if (canActOnOthers) {
        t = TodoStore.get().updateInTenant(tenant, id, title, done);
        if (t != null) { resp.getWriter().write(toJson(t)); return; }
        error(resp, 404, "not found");
        return;
    }

    if (TodoStore.get().findInTenant(tenant, id) != null) {
        error(resp, 403, "forbidden");
    } else {
        error(resp, 404, "not found");
    }
}

private void deleteScoped(String tenant, String user, long id, boolean canActOnOthers,
                          HttpServletResponse resp) throws IOException {
    if (TodoStore.get().delete(tenant, user, id)) { resp.setStatus(204); return; }

    if (canActOnOthers) {
        if (TodoStore.get().deleteInTenant(tenant, id)) { resp.setStatus(204); return; }
        error(resp, 404, "not found");
        return;
    }

    if (TodoStore.get().findInTenant(tenant, id) != null) {
        error(resp, 403, "forbidden");
    } else {
        error(resp, 404, "not found");
    }
}
```

## 解説(対話)

> **後輩**「`update` と `delete` で、まず自分のバケット試して、ダメなら role check して、ダメなら 403/404 切り分け、って3段階。なんでこんな複雑?」

> **先輩**「**自分の操作はなるべく role 関係なく動かしたい** からだ。MEMBER でも自分の todo は更新できる。`update` を呼んで成功したらそれで終わり、失敗時だけ role を見る。」

> **後輩**「最初から `findInTenant` して role check で良くないですか?」

> **先輩**「速度の話じゃない。**一番頻度が高いケース(=自分の操作)を1分岐で済ませる** ほうがコードが読みやすい。エッジケースを後ろに集める原則。」

### 403 と 404 の使い分け

> **後輩**「他人の todo を MEMBER が消そうとした時、403 と 404 どっち?」

> **先輩**「議論があるところ。:
>   - **404 で隠す**: 「そんな id ない」と言って **存在自体を隠す**(security through obscurity 寄り)
>   - **403 で正直に**: 「あるけど触らせない」と教える
>
> 今回は **403** にした。理由は学習目的(挙動を読みやすい)+ 同テナント内なら id の存在は隠す価値が薄い(GET で見える)から。」

> **後輩**「別テナントだと 404 ですね」

> **先輩**「そう。別テナントの id 存在は **絶対に漏らさない**。tenant 越境は情報漏洩。」

## 設計上の懸念(あえて晒す)

> **先輩**「このコード、**問題がある**。気づくか?」

> **後輩**「えー…」

> **先輩**「`/todos?user=other` の時、その `other` が tenant 内に **存在するか** をチェックしてない。存在しないユーザ名指定でも空 list 返してる。」

> **後輩**「漏洩じゃないですよね?」

> **先輩**「微妙なライン。tenant 内のユーザ enumeration が問題なら塞ぐ。今回はサンプルなので**塞がない**が、設計レビューでは指摘される。実装の前に「何が情報漏洩か」を定義する習慣をつけろ。」

## 検証

```bash
# alice (MEMBER) で投稿
curl -s -d '{"title":"alice"}' -H "Content-Type: application/json" \
     -H "X-Volta-User-Id: alice" -H "X-Volta-Tenant-Id: tnt_a" -H "X-Volta-Role: MEMBER" \
     http://localhost:7743/todos
# → id=1

# bob (MEMBER) は alice の todo を消せない
curl -s -o /dev/null -w "%{http_code}\n" -X DELETE \
     -H "X-Volta-User-Id: bob" -H "X-Volta-Tenant-Id: tnt_a" -H "X-Volta-Role: MEMBER" \
     http://localhost:7743/todos/1
# → 403

# bob は ?user=alice で取得もできない
curl -s -o /dev/null -w "%{http_code}\n" \
     -H "X-Volta-User-Id: bob" -H "X-Volta-Tenant-Id: tnt_a" -H "X-Volta-Role: MEMBER" \
     "http://localhost:7743/todos?user=alice"
# → 403

# carol (ADMIN) なら消せる
curl -s -o /dev/null -w "%{http_code}\n" -X DELETE \
     -H "X-Volta-User-Id: carol" -H "X-Volta-Tenant-Id: tnt_a" -H "X-Volta-Role: ADMIN" \
     http://localhost:7743/todos/1
# → 204

# 別テナントは ADMIN でも 404
curl -s -o /dev/null -w "%{http_code}\n" -X DELETE \
     -H "X-Volta-User-Id: carol" -H "X-Volta-Tenant-Id: tnt_b" -H "X-Volta-Role: ADMIN" \
     http://localhost:7743/todos/999
# → 404
```

## 学んだこと

- **認証は proxy、認可はアプリ**。`X-Volta-Role` の解釈はアプリ側のビジネス
- 認可は **頻度の高いケースを最短経路に**(自分の操作 → 1分岐)
- **403 と 404 の使い分け** は情報漏洩ポリシーの問題。先に決める
- **tenant 越境は絶対禁止**。tenant_id の比較を必ず通す

## 次の lesson

[04-tramli-basics](../../04-tramli-basics/) — 状態遷移を **ビルド時に検証** する tramli の基礎を、todo の lifecycle で学ぶ。
