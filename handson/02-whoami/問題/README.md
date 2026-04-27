# 問題 — whoami

## やること

`GET /me` エンドポイントを生やし、proxy から渡ってきた認証情報を JSON で返す。

## 要件

エンドポイント: `GET /me`

レスポンス例(認証あり):

```json
{
  "user": "alice",
  "tenant": "tnt_a",
  "role": "MEMBER",
  "authenticated": true
}
```

レスポンス例(ヘッダ無し = anonymous):

```json
{
  "user": "anonymous",
  "tenant": "public",
  "role": null,
  "authenticated": false
}
```

ルール:
- `authenticated` は `X-Volta-User-Id` ヘッダがあるかどうかで判断
- `role` は `X-Volta-Role` ヘッダの値(無ければ `null`)
- 他のメソッド(POST 等)は 405

## ヒント

- 新しいサーブレットクラスを作る:`@WebServlet("/me")` で MeServlet.java
- Lesson 01 で書いた tenant/user 抽出ロジックは **再利用**したい(関数に切り出すか、両サーブレットで似たコードを書くか) — どちらでも可
- JSON は `Json.escape()` を使えば書きやすい(`null` の扱いに注意)
- レスポンス Content-Type は `application/json; charset=utf-8`

## 設計判断ポイント

> **後輩**「ヘッダ抽出ロジック、`TodoServlet` と `MeServlet` で重複しません?」

→ どう解決するか考えてから 答え/ 見ること。重複を **そのままにする** か、**ヘルパに切り出す** かは設計判断。

## 検証

```bash
curl http://localhost:7743/me                       # anonymous
curl -H "X-Volta-User-Id: alice" -H "X-Volta-Tenant-Id: tnt_a" -H "X-Volta-Role: MEMBER" \
     http://localhost:7743/me                       # 認証あり
curl -X POST http://localhost:7743/me               # 405 method not allowed
```

書けたら [答え](../答え/) へ。
