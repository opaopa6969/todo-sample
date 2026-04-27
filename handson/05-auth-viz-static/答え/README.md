# 答え — auth-viz

## 完成コード

### 1. `AuthState.java`(新規)

```java
package todo;

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

    private final boolean terminal;
    private final boolean initial;

    AuthState(boolean terminal, boolean initial) {
        this.terminal = terminal;
        this.initial = initial;
    }

    public boolean isTerminal() { return terminal; }
    public boolean isInitial()  { return initial; }
}
```

### 2. `AuthFlow.java`(新規)

```java
package todo;

import java.util.LinkedHashMap;
import java.util.Map;

public final class AuthFlow {

    public record Transition(AuthState from, AuthState to, String event) {}

    private static final Transition[] TRANSITIONS = {
        new Transition(AuthState.UNAUTHENTICATED,    AuthState.LOGIN_REDIRECT,    "start_login"),
        new Transition(AuthState.LOGIN_REDIRECT,     AuthState.LOGIN_PENDING,     "redirected"),
        new Transition(AuthState.LOGIN_PENDING,      AuthState.CALLBACK_RECEIVED, "idp_callback"),
        new Transition(AuthState.CALLBACK_RECEIVED,  AuthState.USER_RESOLVED,     "token_exchange"),
        new Transition(AuthState.USER_RESOLVED,      AuthState.SESSION_CREATED,   "no_mfa"),
        new Transition(AuthState.USER_RESOLVED,      AuthState.MFA_PENDING,       "mfa_required"),
        new Transition(AuthState.MFA_PENDING,        AuthState.SESSION_CREATED,   "mfa_verified"),
        new Transition(AuthState.SESSION_CREATED,    AuthState.COMPLETE,          "ready"),
        new Transition(AuthState.LOGIN_PENDING,      AuthState.FAILED,            "timeout_or_error"),
        new Transition(AuthState.CALLBACK_RECEIVED,  AuthState.FAILED,            "bad_token"),
        new Transition(AuthState.MFA_PENDING,        AuthState.FAILED,            "bad_otp"),
        new Transition(AuthState.SESSION_CREATED,    AuthState.EXPIRED,           "session_timeout"),
    };

    private AuthFlow() {}

    public static String toMermaid() {
        StringBuilder sb = new StringBuilder("stateDiagram-v2\n");
        for (AuthState s : AuthState.values()) {
            if (s.isInitial()) sb.append("    [*] --> ").append(s.name()).append("\n");
        }
        for (Transition t : TRANSITIONS) {
            sb.append("    ").append(t.from().name())
              .append(" --> ").append(t.to().name())
              .append(" : ").append(t.event()).append("\n");
        }
        for (AuthState s : AuthState.values()) {
            if (s.isTerminal()) sb.append("    ").append(s.name()).append(" --> [*]\n");
        }
        sb.append("    classDef current fill:#dbeafe,stroke:#1e40af,stroke-width:3px\n");
        sb.append("    class CURRENT_STATE current\n");
        return sb.toString();
    }
}
```

### 3. `AuthVizServlet.java`(新規)

```java
package todo;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebServlet("/auth-flow.mermaid")
public class AuthVizServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("text/plain; charset=utf-8");
        resp.getWriter().write(AuthFlow.toMermaid());
    }
}
```

### 4. `src/main/webapp/auth-viz.html`(新規)

```html
<!doctype html>
<html lang="ja">
<head>
<meta charset="utf-8">
<title>volta auth flow</title>
<style>
  body { font-family: system-ui, sans-serif; max-width: 920px; margin: 2em auto; padding: 0 1em; }
  header { display:flex; justify-content:space-between; align-items:baseline; }
  .pill {
    display:inline-block; padding:.2em .6em; border-radius:1em;
    background:#fef3c7; font-size:.9em;
  }
  .pill.auth { background:#dbeafe; }
  #diagram { margin-top: 1em; }
</style>
</head>
<body>
<header>
  <h1>volta auth flow</h1>
  <span id="status" class="pill">checking...</span>
</header>
<div id="diagram"></div>

<script type="module">
import mermaid from "https://cdn.jsdelivr.net/npm/mermaid@10/dist/mermaid.esm.min.mjs";
mermaid.initialize({ startOnLoad: false, theme: "default" });

const status = document.getElementById('status');
const diagram = document.getElementById('diagram');

let cachedTemplate = null;

async function loadTemplate() {
  if (cachedTemplate) return cachedTemplate;
  cachedTemplate = await fetch('/auth-flow.mermaid').then(r => r.text());
  return cachedTemplate;
}

function inferState(me) {
  if (!me.authenticated) return 'UNAUTHENTICATED';
  if (!me.role) return 'USER_RESOLVED';
  return 'COMPLETE';
}

async function tick() {
  const me = await fetch('/me').then(r => r.json());
  const state = inferState(me);

  status.textContent = me.authenticated
    ? `${me.user}@${me.tenant} (${me.role || 'no-role'}) → ${state}`
    : `unauthenticated → ${state}`;
  status.classList.toggle('auth', me.authenticated);

  const template = await loadTemplate();
  const src = template.replace('CURRENT_STATE', state);

  // re-render
  diagram.innerHTML = `<pre class="mermaid">${src}</pre>`;
  await mermaid.run({ querySelector: '.mermaid' });
}

tick();
setInterval(tick, 2000);
</script>
</body>
</html>
```

## 解説(対話)

### tramli 思想の応用

> **後輩**「lesson 04 の `Map<action, Map<from, to>>` から書き方が変わってますよね?」

> **先輩**「`Transition` レコードの配列にした。**遷移ごとに event 名 + from + to を1行で書ける** から、Mermaid 出力が直で書きやすい。lesson 04 の Map 形式は **遷移を引く** のが速い、配列形式は **遷移を全列挙** するのが速い。**用途で形を変える**のもtramli的な考え。」

> **後輩**「本物の tramli はどっちなんですか?」

> **先輩**「両方持ってる。`FlowDefinition` 内部では index も map も走査もできるよう正規化される。これは『**宣言は1箇所、引き方は複数**』という設計。」

### Mermaid 自動生成のうまみ

> **先輩**「`AuthFlow.java` を一行追加すると `/auth-flow.mermaid` のレスポンスが自動更新される。**ドキュメントとコードがズレない**。これが tramli 思想の実用的なメリット。」

```mermaid
flowchart LR
    Code[AuthFlow.java<br/>遷移を1箇所に宣言] --> Mermaid[/auth-flow.mermaid]
    Code --> Java[Javaの遷移ロジック]
    Mermaid --> HTML[/auth-viz.html<br/>図に描画]
    Java --> Logic[実遷移処理]
    style Code fill:#fef3c7
```

### state 推定の限界

> **後輩**「`LOGIN_REDIRECT` とか `MFA_PENDING` はハイライトされない…」

> **先輩**「**当然だ**。downstream(=todo-sample)からは proxy 内部の状態は見えない。本物の tramli-viz は volta-auth-proxy の **WebSocket** で内部遷移をストリームする。それまでの状態は外から推定できない。」

> **後輩**「じゃあ `/auth-viz` は何の役に立つんですか?」

> **先輩**「3つ:」

1. **教育**: auth flow の全体像を **コードと図で同時に** 見せられる
2. **観測**: 「downstream が認証済みと認識してるか」が一目で分かる(`UNAUTHENTICATED` か `COMPLETE`)
3. **下地**: tramli-viz が公開されたら、ここに WebSocket を繋ぐだけで完成版になる

### 拡張アイデア

> **先輩**「次の lesson でやるかは別として、こういう発展がある:」

- `/me` のレスポンスに `authState` フィールドを追加して、proxy 側の真実値を直接渡す(volta が対応してれば)
- Mermaid に **遷移履歴** を表示する(Server-Sent Events で /me の変化をストリーム)
- 失敗状態(`FAILED` / `EXPIRED`)の判定を追加(セッション失効のヘッダ等)

## 検証

```bash
mvn jetty:run
```

1. http://localhost:7743/auth-viz.html — `UNAUTHENTICATED` がハイライト
2. 別タブで /auth-flow.mermaid を確認:

```bash
curl http://localhost:7743/auth-flow.mermaid
```

3. /me を proxy のフリで叩いて推定状態の確認:

```bash
curl http://localhost:7743/me                                 # → UNAUTHENTICATED
curl -H "X-Volta-User-Id: alice" http://localhost:7743/me     # → USER_RESOLVED
curl -H "X-Volta-User-Id: alice" -H "X-Volta-Role: MEMBER" \
     http://localhost:7743/me                                 # → COMPLETE
```

(ブラウザのハイライトを動的に変えるには volta が前段にいるか、ブラウザ拡張でヘッダ付与が必要)

## 学んだこと

- 遷移表 + Mermaid 自動生成 = **コードと図がズレない**
- downstream から見える state には **限界がある**(proxy 内部は別)
- それでも **見える範囲を可視化する** だけで、認証の体感が劇的に変わる
- tramli 本体を入れずとも、**1箇所宣言・複数引き方** の思想は使える

## 次

[99-roadmap](../../99-roadmap/) — SSE で擬似リアルタイム化、Webhook receiver、招待フロー、本物の tramli-viz への接続。
