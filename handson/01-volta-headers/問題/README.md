# 問題 — volta-headers

## やること

`src/main/java/todo/TodoServlet.java` の冒頭、固定値で書かれている tenant / user を **volta-auth-proxy のヘッダから読む** ように書き換える。

## 起点

開く: `src/main/java/todo/TodoServlet.java`

`service()` の頭にこう書いてある:

```java
String tenant = "public";
String user = "anonymous";
```

## 要件

1. `X-Volta-Tenant-Id` ヘッダから tenant を取る
2. `X-Volta-User-Id` ヘッダから user を取る
3. ヘッダが無い/空なら `public` / `anonymous` にフォールバック(認証なしモード)

## ヒント

- Servlet API は `req.getHeader("X-Foo")` で取れる
- 値が空文字列のケースも忘れずに(`String.isBlank()` 使える)
- 順番は「ヘッダから読む → 無ければデフォルト」

## 検証

書き換え後にビルド + 起動し、[親の README の検証ヒント](../README.md#検証ヒント) を実行して、

- ヘッダ無し → `[]`(public/anonymous バケット)
- alice@tnt_a で書き込み → alice@tnt_a でしか見えない
- alice@tnt_b では見えない
- bob@tnt_a では見えない

を確認できれば合格。

書けたら [答え](../答え/) で答え合わせ。
