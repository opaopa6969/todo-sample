# 答え — volta-headers

## 完成コード

`src/main/java/todo/TodoServlet.java` の冒頭2行を、こう書き換える:

```java
String tenant = req.getHeader("X-Volta-Tenant-Id");
String user   = req.getHeader("X-Volta-User-Id");
if (tenant == null || tenant.isBlank()) tenant = "public";
if (user   == null || user.isBlank())   user   = "anonymous";
```

差分はこれだけ。**ストア側は無変更**。

## 解説(対話)

> **後輩**「これだけ?」

> **先輩**「これだけ。`(tenant, user)` のキー設計が効いてる。値の出所が固定だろうが proxy だろうが、ストアからすれば等価。」

> **後輩**「`isBlank()` が `null` のあとに来てるの、順番大事ですよね?」

> **先輩**「そう。`null.isBlank()` で NPE になる。`||` は短絡評価だから `null` チェックが先。」

## ヘッダ信頼の前提

> **後輩**「`curl -H "X-Volta-User-Id: admin"` で偽装できますよね? これ大丈夫?」

> **先輩**「ローカル開発はそれでいい。本番では:」

```mermaid
flowchart LR
    Net[Internet] -->|公開IP| T[Traefik / proxy]
    T -.->|ForwardAuth check| V[volta-auth-proxy]
    V -.->|認証OK + headers| T
    T -->|"X-Volta-User-Id 等を<br/><b>proxy が付け直す</b>"| A[todo-sample]
    Net -.x.->|直接アプリに到達不可| A
    style A fill:#dbeafe
```

- todo-sample は **private network からしか見えない** デプロイ
- Traefik(または同等の proxy)が **唯一の入口**
- proxy は **クライアントが付けてきたヘッダを上書き** する(信頼できる値だけが下流に届く)
- これが守られてれば、アプリは「ヘッダの中身は正しい」と信じていい

> **後輩**「proxy 通さずアプリに直接アクセスされるとマズい?」

> **先輩**「マズい。だから本番ではアプリのバインドを `127.0.0.1` または internal network only にする。**ネットワーク的に proxy 経由しか到達できない**設計が前提。」

## 設計上のトレードオフ

| 選択 | メリット | デメリット |
|---|---|---|
| **anonymous フォールバック残す**(今回の選択) | proxy 無しで動かせる/開発しやすい | proxy 設定漏れに気づきにくい |
| 401 にする | 本番事故を防げる | local dev で常に proxy 必須 |

サンプルなのでフォールバック残す方を選んだ。本番アプリでは `if (req.getHeader("X-Volta-User-Id") == null) return 401;` を **デプロイモードで切り替え** する設計が現実的。

## 検証(再掲)

```bash
mvn jetty:run
# 別ターミナル

# anonymous(public バケット)
curl -d '{"title":"匿名"}' -H "Content-Type: application/json" \
     http://localhost:7743/todos

# alice@tnt_a
curl -d '{"title":"アリス"}' -H "Content-Type: application/json" \
     -H "X-Volta-User-Id: alice" -H "X-Volta-Tenant-Id: tnt_a" \
     http://localhost:7743/todos

curl -H "X-Volta-User-Id: alice" -H "X-Volta-Tenant-Id: tnt_a" http://localhost:7743/todos
# → アリスの todo

curl -H "X-Volta-User-Id: alice" -H "X-Volta-Tenant-Id: tnt_b" http://localhost:7743/todos
# → []  ← tenant が違えば同じ user_id でも別人扱い

curl http://localhost:7743/todos
# → 匿名で書いたやつだけ
```

## 学んだこと

- volta-auth-proxy は **3つのヘッダだけ** 渡してくる
- アプリは **そのヘッダを読むだけ**。認証コードは書かない
- `(tenant, user)` 二軸で分離する設計だと、認証あり/なしを **値の出所だけで切り替え** できる
- ヘッダ信頼は **ネットワーク境界が前提**。アプリを直接公開しないこと

## 次の lesson

[02-whoami](../../02-whoami/) — 受け取ったヘッダ値を `/me` エンドポイントで露出させて、デバッグしやすくする。
