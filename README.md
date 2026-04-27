# todo-sample

[volta-auth-proxy](https://github.com/opaopa6969/volta-auth-proxy) を後ろから挿せるように作った、最小の multi-tenant todo API サンプル。

- Java 17 / Maven / Jakarta Servlet 5 のみ(他フレームワーク無し)
- ストレージはインメモリ
- tenant × user の2軸でデータを分離
- 認証なしで動かすと `tenant=public, user=anonymous` の共有バケットに落ちる(2ch+名無し的)
- volta-auth-proxy を前段に置けば、`X-Volta-Tenant-Id` / `X-Volta-User-Id` を読むよう **2行書き換える** だけでテナント分離が効く

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

ユーザは `(tenant, user)` の組で識別される。認証なし時はどちらも固定値(`public` / `anonymous`)。

```bash
curl -d '{"title":"買い物"}' -H "Content-Type: application/json" http://localhost:7743/todos
# → {"id":1,"title":"買い物","done":false,"createdAt":...}
```

## volta-auth-proxy と繋ぐ

`TodoServlet#service` の **このブロックだけ** 書き換える:

```java
// 認証なし(現状)
String tenant = "public";
String user   = "anonymous";

// volta-auth-proxy 経由(置換後)
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
- **永続化なし。** プロセス再起動で消える。差し替える場合は `TodoStore` の1ファイルで完結。
- **anonymous 共有バケット** は、proxy を外した時に「公開掲示板」的に振る舞う。意図された挙動。
- 認証は **アプリで持たない** 方針(volta-auth-proxy に寄せる)。アプリはヘッダを読むだけ。
