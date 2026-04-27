# 問題 — RBAC

## やること

ADMIN/OWNER は **同じテナント内の他人の todo** を読み書き削除できるようにする。MEMBER は自分の todo だけ。

## 要件変更

これまで「自分の todo だけ見える」だったのを以下に変更:

| 操作 | 自分の todo | 同テナントの他人の todo |
|---|---|---|
| GET / POST / PUT / DELETE | 全 role 可 | ADMIN / OWNER のみ |

具体的には:

- `GET /todos` — 自分の todo を返す(現状通り)
- `GET /todos?user=other` — `?user=` クエリでターゲット指定。ADMIN/OWNER のみ可
- `PUT /todos/{id}` — id がどのユーザのものでも、**同テナント内なら** ADMIN/OWNER は更新OK
- `DELETE /todos/{id}` — 同上

権限不足は **403 forbidden**。

## 設計判断ポイント

ストアは `(tenant, user) → todos`。
**ADMIN が他人の todo を触る**には、user 単位ではなく tenant 単位で全 todo を引く必要がある。

→ `TodoStore` に新メソッドを足すか、既存メソッドのままで頑張るか?

## ヒント

- `TodoStore` に `listAllInTenant(tenant)` / `findInTenant(tenant, id)` 等を生やすのが素直
- `id` は今 `AtomicLong` で **全テナント横断のグローバル連番** なので、tenant 内のどこに id があるかは tenant 内の全 user バケットを線形探索すればわかる(N が小さい想定)
- role check ヘルパ:`boolean canActOnOthers(String role) { return "ADMIN".equals(role) || "OWNER".equals(role); }`
- 認証なし(role=null)は他人触れない(anonymous は anonymous 同士のみ)

## 検証

```bash
# alice@tnt_a (MEMBER) が todo 作る
curl -s -d '{"title":"alice作業"}' -H "Content-Type: application/json" \
     -H "X-Volta-User-Id: alice" -H "X-Volta-Tenant-Id: tnt_a" -H "X-Volta-Role: MEMBER" \
     http://localhost:7743/todos
# → id=1 が返る

# bob@tnt_a (MEMBER) は alice の todo を見たり消したりできない
curl -s -o /dev/null -w "%{http_code}\n" -X DELETE \
     -H "X-Volta-User-Id: bob" -H "X-Volta-Tenant-Id: tnt_a" -H "X-Volta-Role: MEMBER" \
     http://localhost:7743/todos/1
# → 403

# bob が ?user=alice 指定で取得しようとしても拒否
curl -s -o /dev/null -w "%{http_code}\n" \
     -H "X-Volta-User-Id: bob" -H "X-Volta-Tenant-Id: tnt_a" -H "X-Volta-Role: MEMBER" \
     "http://localhost:7743/todos?user=alice"
# → 403

# carol@tnt_a (ADMIN) は alice の todo を消せる
curl -s -o /dev/null -w "%{http_code}\n" -X DELETE \
     -H "X-Volta-User-Id: carol" -H "X-Volta-Tenant-Id: tnt_a" -H "X-Volta-Role: ADMIN" \
     http://localhost:7743/todos/1
# → 204

# carol が tnt_b の todo は触れない(別テナント)
curl -s -o /dev/null -w "%{http_code}\n" -X DELETE \
     -H "X-Volta-User-Id: carol" -H "X-Volta-Tenant-Id: tnt_b" -H "X-Volta-Role: ADMIN" \
     http://localhost:7743/todos/1
# → 404 (tnt_b には存在しない)
```

書けたら [答え](../答え/) へ。
