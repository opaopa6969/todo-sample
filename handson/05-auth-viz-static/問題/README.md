# 問題 — auth-viz

## やること

3つを実装:

### 1. AuthFlow 定義(Java)

`src/main/java/todo/AuthFlow.java` を作り、auth state を tramli 流に宣言:

```java
public enum AuthState {
    UNAUTHENTICATED(false, true),
    LOGIN_REDIRECT(false, false),
    LOGIN_PENDING(false, false),
    CALLBACK_RECEIVED(false, false),
    USER_RESOLVED(false, false),
    MFA_PENDING(false, false),
    SESSION_CREATED(false, false),
    COMPLETE(true, false),
    FAILED(true, false),
    EXPIRED(true, false);

    // ... isTerminal() / isInitial()
}
```

`AuthFlow` に `Map<event, Map<from, to>>` の形で遷移表を宣言。`toMermaid()` メソッドを実装し、`stateDiagram-v2` 形式の文字列を返す。

### 2. AuthVizServlet(Java)

`GET /auth-flow.mermaid` で `AuthFlow.toMermaid()` の結果を `text/plain` で返す。

```bash
curl http://localhost:7743/auth-flow.mermaid
# stateDiagram-v2
#   [*] --> UNAUTHENTICATED
#   UNAUTHENTICATED --> LOGIN_REDIRECT : start_login
#   ...
```

### 3. /auth-viz HTML ページ

`src/main/webapp/auth-viz.html` を作り:

- ページ読み込み時に `/auth-flow.mermaid` を fetch して [mermaid.js](https://github.com/mermaid-js/mermaid) で描画
- 2秒ごとに `/me` を fetch
- `/me` の結果から **現在の AuthState を推定**
- 該当 state のノードに **CSS クラスを付けてハイライト**(色を変える)

### 状態推定ルール

| `/me` レスポンス | 表示する state |
|---|---|
| `authenticated: false` | `UNAUTHENTICATED` |
| `authenticated: true`, `role: null` | `USER_RESOLVED` |
| `authenticated: true`, `role` あり | `COMPLETE` |

## ヒント

### `toMermaid()` の出力例

```
stateDiagram-v2
    [*] --> UNAUTHENTICATED
    UNAUTHENTICATED --> LOGIN_REDIRECT : start_login
    LOGIN_REDIRECT --> LOGIN_PENDING : redirected
    LOGIN_PENDING --> CALLBACK_RECEIVED : idp_callback
    CALLBACK_RECEIVED --> USER_RESOLVED : token_exchange
    USER_RESOLVED --> SESSION_CREATED : no_mfa
    USER_RESOLVED --> MFA_PENDING : mfa_required
    MFA_PENDING --> SESSION_CREATED : mfa_verified
    SESSION_CREATED --> COMPLETE : ready
    LOGIN_PENDING --> FAILED : timeout_or_error
    CALLBACK_RECEIVED --> FAILED : bad_token
    MFA_PENDING --> FAILED : bad_otp
    SESSION_CREATED --> EXPIRED : session_timeout
    COMPLETE --> [*]
    FAILED --> [*]
    EXPIRED --> [*]
    classDef current fill:#dbeafe,stroke:#1e40af,stroke-width:3px
    class CURRENT_STATE current
```

最後の `class CURRENT_STATE current` は **placeholder**。フロント側で「実際の現在状態名」に書き換えてから mermaid.js に渡す。

### Mermaid CDN

```html
<script type="module">
import mermaid from "https://cdn.jsdelivr.net/npm/mermaid@10/dist/mermaid.esm.min.mjs";
mermaid.initialize({ startOnLoad: false });
</script>
```

### 状態推定 (JS)

```js
function inferState(me) {
  if (!me.authenticated) return 'UNAUTHENTICATED';
  if (!me.role) return 'USER_RESOLVED';
  return 'COMPLETE';
}
```

## 検証

```bash
mvn jetty:run
```

ブラウザで http://localhost:7743/auth-viz.html

- ヘッダ無しなので `UNAUTHENTICATED` がハイライト

別タブで:

```bash
# proxy のフリで todo を叩く
curl -H "X-Volta-User-Id: alice" -H "X-Volta-Tenant-Id: tnt_a" -H "X-Volta-Role: MEMBER" \
     http://localhost:7743/todos
```

…ブラウザは結局自分自身のヘッダしか送れないので、**ブラウザからのハイライト変化を見るには** `/me` を擬似的にヘッダ付きで叩く必要がある。簡単な確認方法:

```bash
# /me を curl で確認(ブラウザでなく)
curl -H "X-Volta-User-Id: alice" -H "X-Volta-Tenant-Id: tnt_a" -H "X-Volta-Role: MEMBER" \
     http://localhost:7743/me
# → authenticated:true, role:MEMBER → COMPLETE 推定
```

ブラウザでは UNAUTHENTICATED 固定になる。volta が前段にあるとブラウザにヘッダが付くので、その時 `COMPLETE` に切り替わる体験ができる(本番デモでは proxy 介在が必須)。

書けたら [答え](../答え/) へ。
