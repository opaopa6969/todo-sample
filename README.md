# todo-sample

[volta-auth-proxy](https://github.com/opaopa6969/volta-auth-proxy) を後ろから挿せるように作った、最小の multi-tenant todo API サンプル。

- Java 17 / Maven / Jakarta Servlet 5 のみ(他フレームワーク無し)
- ストレージはインメモリ
- tenant × user の2軸でデータを分離
- `X-Volta-Tenant-Id` / `X-Volta-User-Id` ヘッダを読んでテナント分離を実現
- ヘッダ未設定時は `tenant=public, user=anonymous` の共有バケットに落ちる(2ch+名無し的)
- volta-auth-proxy を前段に置けばそのままテナント分離が効く

## 起動

```bash
mvn jetty:run
# http://localhost:7743/   ← 7-7-4-3 = ななし(名無し)+3
```

## API

| Method | Path | Body | 動作 |
|---|---|---|---|
| GET    | `/todos`      | — | 自分の todo 一覧 |
| POST   | `/todos`      | `{"title":"..."}` | 作成 → 201 |
| PUT    | `/todos/{id}` | `{"title":"...","done":true}`(部分更新可) | 更新 |
| DELETE | `/todos/{id}` | — | 削除 → 204 |

ユーザは `(tenant, user)` の組で識別される。`X-Volta-Tenant-Id` / `X-Volta-User-Id` ヘッダを読んで分離し、未設定時は `public` / `anonymous` にフォールバックする。

リクエストボディは最大 64 KiB に制限される。超過時は `413 Request Entity Too Large` を返す。

```bash
curl -d '{"title":"買い物"}' -H "Content-Type: application/json" http://localhost:7743/todos
# → {"id":1,"title":"買い物","done":false,"createdAt":...}
```

## volta-auth-proxy と繋ぐ

`TodoServlet#service` は既に `X-Volta-Tenant-Id` / `X-Volta-User-Id` を読む実装になっている。
volta-auth-proxy を前段に置くだけでテナント分離が有効になる。コードの変更は不要。

```java
// 現状の実装(ヘッダを読む)
String tenant = req.getHeader("X-Volta-Tenant-Id");
String user   = req.getHeader("X-Volta-User-Id");
if (tenant == null || tenant.isBlank()) tenant = "public";
if (user   == null || user.isBlank())   user   = "anonymous";
```

ストア側 (`TodoStore`) は `(tenant, user)` をキーにしているのでこのまま動く。

## 構成

```
src/main/
├── java/todo/
│   ├── Todo.java          # データクラス
│   ├── TodoStore.java     # ConcurrentHashMap<(tenant,user), Map<id,Todo>>
│   ├── Json.java          # 自前の JSON parser/escape
│   └── TodoServlet.java   # /todos, /todos/* を1つで処理
└── webapp/
    └── index.html         # 動作確認用の最小 UI
```

## ハンズオン

[handson/](handson/) で **対話形式 + Mermaid** の lesson が読める。volta-auth-proxy 連携、RBAC、tramli の制約付き state machine、auth flow 可視化までを手を動かして学ぶ構成。

```
handson/
├── 00-overview/         ← ForwardAuth とヘッダ信頼モデル
├── 01-volta-headers/    ← 認証なし → ヘッダで user/tenant 識別
├── 02-whoami/           ← /me で受け取った値を露出
├── 03-rbac/             ← X-Volta-Role で認可
├── 04-tramli-basics/    ← tramli 思想を todo lifecycle で学ぶ
├── 05-auth-viz-static/  ← auth flow を Mermaid で可視化
└── 99-roadmap/          ← SSE / Webhook / 招待 / 本物 tramli
```

## 設計メモ

- **依存は `jakarta.servlet-api` だけ。** JSON も自前。
- **永続化なし(インメモリのみ)。** プロセス再起動でデータは消える。複数インスタンスで起動しても状態は共有されない。本番用途には向かない。差し替える場合は `TodoStore` の1ファイルで完結。
- **anonymous 共有バケット** は、proxy を外した時に「公開掲示板」的に振る舞う。意図された挙動。
- 認証は **アプリで持たない** 方針(volta-auth-proxy に寄せる)。アプリはヘッダを読むだけ。
