# 答え — whoami

## 完成コード

新規ファイル: `src/main/java/todo/MeServlet.java`

```java
package todo;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebServlet("/me")
public class MeServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json; charset=utf-8");

        String rawUser   = req.getHeader("X-Volta-User-Id");
        String rawTenant = req.getHeader("X-Volta-Tenant-Id");
        String rawRole   = req.getHeader("X-Volta-Role");

        boolean authenticated = rawUser != null && !rawUser.isBlank();

        String user   = authenticated ? rawUser : "anonymous";
        String tenant = (rawTenant != null && !rawTenant.isBlank()) ? rawTenant : "public";
        String role   = (rawRole != null && !rawRole.isBlank()) ? rawRole : null;

        StringBuilder sb = new StringBuilder();
        sb.append("{\"user\":").append(Json.escape(user))
          .append(",\"tenant\":").append(Json.escape(tenant))
          .append(",\"role\":").append(role == null ? "null" : Json.escape(role))
          .append(",\"authenticated\":").append(authenticated)
          .append("}");
        resp.getWriter().write(sb.toString());
    }
}
```

POST/PUT/DELETE 等は `HttpServlet` のデフォルト挙動で 405 が返るので明示実装不要。

## 解説(対話)

> **後輩**「`TodoServlet` と同じヘッダ抽出ロジックを2箇所に書きましたけど、重複してますよね?」

> **先輩**「**今は許す**。重複が3箇所目になったら共通化を考える(rule of three)。今はまだ2箇所なので、共通化のほうがコストが高い。」

> **後輩**「Helper クラス作りたくなりました」

> **先輩**「YAGNI。共通化するならどんな API になるか書いてみろ:」

```java
// 例: 切り出した場合
public record AuthContext(String tenant, String user, String role, boolean authenticated) {
    public static AuthContext from(HttpServletRequest req) {
        String rawUser   = req.getHeader("X-Volta-User-Id");
        String rawTenant = req.getHeader("X-Volta-Tenant-Id");
        String rawRole   = req.getHeader("X-Volta-Role");
        boolean auth = rawUser != null && !rawUser.isBlank();
        return new AuthContext(
            (rawTenant != null && !rawTenant.isBlank()) ? rawTenant : "public",
            auth ? rawUser : "anonymous",
            (rawRole != null && !rawRole.isBlank()) ? rawRole : null,
            auth
        );
    }
}
```

> **先輩**「これを使うかどうかは lesson 03 の RBAC で `role` を見る必要が出た時に再判断する。**まだ早い**。」

## なぜ `null` 表記を直書き?

```java
sb.append(",\"role\":").append(role == null ? "null" : Json.escape(role))
```

`Json.escape(null)` を呼ぶと NPE。JSON で `null` を表現するためには文字列 `null`(クォート無し)を直接書く必要がある。

`Json.escape("null")` だと `"null"`(文字列)になっちゃう。**JSON null** とは別物。

## アクセス制御の確認

> **後輩**「`/me` 誰でも叩けますよね? OK?」

> **先輩**「OK。**自分自身の情報しか返さない** から。むしろ proxy 通ってないと `anonymous` しか返らない = 情報漏洩リスクは無い。」

> **後輩**「他人の `/me` は?」

> **先輩**「無い。設計上 `/me` は path に user_id を含めない。`GET /users/alice` みたいな admin API は別 endpoint(role check 必須)。」

## 検証

```bash
mvn jetty:run

# anonymous
curl -s http://localhost:7743/me | jq
# {
#   "user": "anonymous",
#   "tenant": "public",
#   "role": null,
#   "authenticated": false
# }

# 認証あり(proxy のフリ)
curl -s -H "X-Volta-User-Id: alice" \
        -H "X-Volta-Tenant-Id: tnt_a" \
        -H "X-Volta-Role: MEMBER" \
        http://localhost:7743/me | jq
# {
#   "user": "alice",
#   "tenant": "tnt_a",
#   "role": "MEMBER",
#   "authenticated": true
# }

# 不正メソッド
curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost:7743/me
# 405
```

## 学んだこと

- `/me` は認証層を **コードから見える形** にする標準パターン
- 重複コードは **3箇所目で初めて共通化** を考える(rule of three)
- 認証された値と認証されていない値の **両方を返す** ことでデバッグしやすくする(`authenticated: false` がアプリ側で見える)
- **public な path** ⇔ **public な情報** の対応(自分の情報なら晒してOK)

## 次の lesson

[03-rbac](../../03-rbac/) — `X-Volta-Role` を使って「ADMIN しか削除できない」を実装する。
