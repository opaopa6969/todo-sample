# MCP 化調査 — todo-sample（Phase 1）

## 概要

`todo-sample` は [volta-auth-proxy](https://github.com/opaopa6969/volta-auth-proxy) のヘッダ信頼モデルを学ぶための **最小 multi-tenant todo API サンプル**。

- Java 17 / Maven / Jakarta Servlet 5 のみ（フレームワーク依存なし、JSON も自前）
- ストレージは **インメモリ**（`ConcurrentHashMap`）、永続化なし。プロセス再起動でデータは消える
- `X-Volta-Tenant-Id` / `X-Volta-User-Id` ヘッダで `(tenant, user)` 軸のデータ分離を実現。未設定時は `public` / `anonymous` 共有バケットにフォールバック
- `handson/` に 6 章の対話型レッスン（ForwardAuth → ヘッダ識別 → whoami → RBAC → tramli state machine → auth flow 可視化）を同梱

## 判定と理由

**判定: `skip`（MCP 化しない）**

- 本体は教育用サンプル。データはプロセス内インメモリで再起動で消えるため、MCP tool として露出しても agent が安定して使える能力にならない
- 認証・テナント分離・永続化の責務は意図的に外部（volta-auth-proxy, 永続化層）に追い出されており、todo CRUD を MCP に包んでもその本質は外部依存のまま
- 教材価値は handson/ の手順・概念解説にあり、本体の API を agent が呼びたい状況は薄い
- handson の内容を resource/skill として配る意義はあるが、それは「todo-sample 固有の手順」ではなく volta-auth-proxy / tramli 側の skill に統合するほうが自然

## 公開候補

| kind | name | io / uri | 副作用 | 長時間 | 備考 |
|---|---|---|---|---|---|
| tool | `todo_crud` | `{tenant,user} + {title,done} → todo` | write | no | `TodoServlet#service`。採用しない（インメモリ・非永続のため agent が安定利用できない） |
| resource | `spec` | `todo://spec` | read | no | `docs/spec-todo-api.md` に既存。参照用候補 |
| resource | `guide` | `todo://guide` | read | no | `README.md` + `handson/README.md`。教材案内 |
| skill | `forwardauth-headers` | — | none | no | ForwardAuth + ヘッダ信頼モデルの手順。locality: global（volta-auth-proxy 系全体で汎用） |
| skill | `tramli-on-todo` | — | none | no | todo lifecycle で tramli を学ぶ手順。locality: repo（この repo の handson 04 に紐付く） |

## 組み合わせ例

採用しないため現実的な組み合わせ例はない。仮に MCP 化するなら `todo_sample__todo_crud` → `auth_proxy__whoami` でユーザを確定してから書き込む絵は描けるが、インメモリ・非永続のため agent の作業成果が安定せず実用性に乏しい。

## 依存と協調

| 相手 repo | 方向 | 能力 | 現存 | 備考 |
|---|---|---|---|---|
| `volta-auth-proxy` | depends_on | `X-Volta-User-Id` / `X-Volta-Tenant-Id` / `X-Volta-Role` ヘッダ付与（ForwardAuth） | yes | catalog に存在（`operational_status: retired`）。todo-sample はこの proxy の後段で動く前提。ローカルは `curl -H` で模倣。proxy 側の MCP 入口の有無は未調査 |
| `tramli` | depends_on | 制約付き state machine（handson 04 の題材） | yes | catalog に library として存在（MCP 入口なし）。handson で概念的に使うだけで実行時依存ではない |
| `volta-auth-console` | provides_to | （直接依存なし） | yes | 同じ auth 系エコシステムの参考記載。console は auth-proxy を管理 |

**協調が必要か:** このフェーズでは issue を立てない。todo-sample 自体が MCP 入口を提供しない（skip）ため、協調は発生しない。volta-auth-proxy 側が MCP 入口を持つかは別途調査対象。

## ライブラリのサーバ化

該当しない（service 型だが MCP 化を skip）。新規 MCP サーバ化は行わない。

## リスク

- **インメモリ・非永続**: MCP tool として露出してもプロセス再起動でデータが消え、agent の作業成果が安定しない
- **ヘッダ偽装**: 認証をアプリ外に依存しているため、MCP サーバとして直接露出すると `X-Volta-User-Id` 偽装で tenant/user をなりすませる（教材では意図された挙動だが MCP 公開時はリスク）
- **volta-auth-proxy の retired 状態**: catalog 上 retired とされており、todo-sample の本番経路としての前提が現在有効か不明

## 持ち主への質問

1. todo-sample を volta 上で常駐公開する予定があるか（現状はローカル教材に見える）
2. volta-auth-proxy の `retired` は一時的か恒久的か。これによって todo-sample の本番での位置づけが変わる
3. `handson/` の内容を volta-mcp の skill/resource として配る意義があるか、それとも repo 内完結でよいか
