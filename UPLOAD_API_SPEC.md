# PUT /upload エンドポイント仕様書

## 概要

JBlossomサーバーのBUD02準拠BLOBアップロードエンドポイント。バイナリデータをSHA256ハッシュベースで保存し、Nostr認証による制御を提供。

## エンドポイント

- **URL**: `PUT /upload`
- **Content-Type**: `multipart/form-data` または任意のbinary形式
- **認証**: Nostr kind 24242イベント（設定により必須/オプション）

## リクエスト形式

### 1. MultipartFile アップロード
```http
PUT /upload
Content-Type: multipart/form-data
Authorization: Nostr <base64-encoded-event>
X-SHA-256: d8346875f65e726689b5b4a4823714333aaf82127007ab6d926b18a2256503fb

--boundary
Content-Disposition: form-data; name="file"; filename="test.txt"
Content-Type: text/plain

[ファイル内容]
```

### 2. Raw Binary アップロード
```http
PUT /upload
Content-Type: application/octet-stream
Authorization: Nostr <base64-encoded-event>
Content-Length: 1024

[バイナリデータ]
```

## リクエストヘッダー

| ヘッダー | 必須 | 説明 |
|---------|-----|------|
| `Authorization` | 設定依存 | `Nostr <base64_event>` 形式のNostr認証 |
| `Content-Type` | 任意 | MIMEタイプ（自動検出も可能） |
| `X-SHA-256` | 任意 | 期待されるSHA256ハッシュ（整合性チェック用） |

## 設定パラメータ

| パラメータ | デフォルト | 説明 |
|-----------|-----------|------|
| `blossom.upload.max-file-size` | 104857600 (100MB) | 最大ファイルサイズ |
| `blossom.upload.require-auth` | true | 認証必須フラグ |

## 成功レスポンス

### HTTP 201 Created
```json
{
  "url": "https://example.com/d8346875f65e726689b5b4a4823714333aaf82127007ab6d926b18a2256503fb",
  "sha256": "d8346875f65e726689b5b4a4823714333aaf82127007ab6d926b18a2256503fb",
  "size": 1024,
  "type": "text/plain",
  "uploaded": 1640995200
}
```

**BlobDescriptorフィールド:**
- `url`: アクセス用URL（`<base_url>/<sha256>`）
- `sha256`: 計算されたSHA256ハッシュ（64文字hex）
- `size`: ファイルサイズ（バイト）
- `type`: 検出されたMIMEタイプ
- `uploaded`: アップロード時刻（Unixタイムスタンプ）

## エラーレスポンス

### 認証エラー (HTTP 401 Unauthorized)

#### 1. 認証ヘッダー不足
**条件**: `require-auth=true` かつ `Authorization` ヘッダーなし
```json
{
  "error": "Unauthorized",
  "message": "Authorization header required for upload",
  "authErrorType": "MISSING_AUTH"
}
```
**ヘッダー**: `WWW-Authenticate: Nostr`

#### 2. 無効な認証形式
**条件**: `Authorization` が `Nostr ` で開始しない
```json
{
  "error": "Unauthorized", 
  "message": "Invalid Authorization scheme, expected 'Nostr'",
  "authErrorType": "INVALID_FORMAT"
}
```

#### 3. Base64デコードエラー
**条件**: 不正なBase64エンコード
```json
{
  "error": "Unauthorized",
  "message": "Invalid event format: Illegal base64 character",
  "authErrorType": "INVALID_FORMAT"
}
```

#### 4. 無効なNostrイベント
**条件**: kind != 24242
```json
{
  "error": "Unauthorized",
  "message": "Invalid kind, expected 24242", 
  "authErrorType": "INVALID_KIND"
}
```

#### 5. 必須フィールド不足
**条件**: pubkey, created_at, content, sig フィールド欠如
```json
{
  "error": "Unauthorized",
  "message": "Missing pubkey field",
  "authErrorType": "INVALID_FORMAT"
}
```

#### 6. 無効な公開鍵形式
**条件**: pubkey が64文字hex形式でない
```json
{
  "error": "Unauthorized", 
  "message": "Invalid pubkey format, expected 64-char hex",
  "authErrorType": "INVALID_FORMAT"
}
```

#### 7. 未来タイムスタンプ
**条件**: `created_at` > 現在時刻 + 60秒
```json
{
  "error": "Unauthorized",
  "message": "created_at must be in the past",
  "authErrorType": "TIMESTAMP_FUTURE" 
}
```

#### 8. 有効期限切れ
**条件**: 現在時刻 > `expiration` タグ値
```json
{
  "error": "Unauthorized",
  "message": "Event has expired",
  "authErrorType": "EVENT_EXPIRED"
}
```

#### 9. 必須タグ不足
**条件**: `t`, `expiration` タグ欠如
```json
{
  "error": "Unauthorized", 
  "message": "Missing or invalid 't' tag for action: upload",
  "authErrorType": "INVALID_ACTION"
}
```

#### 10. 無効なアクション
**条件**: `t` タグ値が `upload` でない
```json
{
  "error": "Unauthorized",
  "message": "Missing or invalid 't' tag for action: upload", 
  "authErrorType": "INVALID_ACTION"
}
```

#### 11. 無効な署名
**条件**: Schnorr署名検証失敗
```json
{
  "error": "Unauthorized",
  "message": "Invalid signature format",
  "authErrorType": "INVALID_SIGNATURE"
}
```

#### 12. ハッシュ不一致 (認証イベント)
**条件**: 認証イベントの `x` タグと計算SHA256が不一致
```json
{
  "error": "Unauthorized",
  "message": "Auth event x tag mismatch: expected abc123..., calculated def456...",
  "authErrorType": "HASH_MISMATCH"
}
```
**注意**: この場合、保存されたファイルは自動削除される

### ストレージエラー

#### 1. ファイルサイズ超過 (HTTP 413 Payload Too Large)
**条件**: ファイルサイズ > `max-file-size`
```json
{
  "error": "Storage Error",
  "message": "File size 209715200 exceeds maximum allowed size 104857600"
}
```

#### 2. 空ファイル (HTTP 400 Bad Request)
**条件**: ファイルサイズ <= 0
```json
{
  "error": "Storage Error", 
  "message": "File is empty or size could not be determined"
}
```

#### 3. SHA256不一致 (HTTP 422 Unprocessable Entity)
**条件**: `X-SHA-256` ヘッダーと計算SHA256が不一致
```json
{
  "error": "Storage Error",
  "message": "SHA256 mismatch: expected abc123..., calculated def456..."
}
```
**注意**: この場合、保存されたファイルは自動削除される

#### 4. ストレージエラー (HTTP 500 Internal Server Error)
**条件**: ディスク容量不足、権限エラーなど
```json
{
  "error": "Storage Error",
  "message": "Failed to store blob: No space left on device"
}
```

## 処理フロー

### 1. 認証フェーズ
1. **認証設定確認**: `require-auth` または `Authorization` ヘッダー存在
2. **ヘッダー形式検証**: `Nostr <base64_event>` 形式
3. **Base64デコード**: 認証イベントJSON抽出
4. **Nostrイベント検証**:
   - kind = 24242
   - 必須フィールド存在（pubkey, created_at, content, sig）
   - pubkey形式（64文字hex）
   - タイムスタンプ有効性
   - 必須タグ存在（t=upload, expiration）
   - 署名検証（現在は基本チェックのみ）

### 2. ファイル処理フェーズ
1. **データソース判定**: MultipartFile vs Raw binary
2. **ファイルサイズ検証**: 上限チェック・空ファイルチェック
3. **ストリーミング保存**: SHA256計算とファイル書き込み並行実行
4. **整合性検証**: 
   - `X-SHA-256` ヘッダーとの照合
   - 認証イベント `x` タグとの照合
5. **メタデータ保存**: SQLiteデータベース登録

### 3. レスポンス生成フェーズ
1. **BlobDescriptor作成**: URL, SHA256, サイズ, タイプ, アップロード時刻
2. **HTTP 201 Created** レスポンス返却

## CORS対応

すべてのエラーレスポンスに以下のCORSヘッダーが付与：
```
Access-Control-Allow-Origin: *
Access-Control-Expose-Headers: X-Reason, WWW-Authenticate
```

## セキュリティ機能

### 1. ファイル整合性保証
- ストリーミングSHA256計算
- 期待ハッシュとの照合
- 不一致時の自動ファイル削除

### 2. 重複ファイル処理
- 同一SHA256の既存ファイル検出
- 重複時は既存メタデータを返却

### 3. 認証イベント検証
- Nostr BIP-340 準拠（Schnorr署名）
- 時間窓制御（created_at + expiration）
- アクション制限（t=upload）
- オプショナルハッシュ制約（x タグ）

### 4. リソース保護
- ファイルサイズ制限
- 認証必須設定
- 自動クリーンアップ（エラー時）

## パフォーマンス特性

### 1. メモリ効率
- ストリーミング処理（8KB バッファ）
- 大容量ファイル対応
- 一時ファイル使用

### 2. 原子性保証
- 一時ファイル→最終位置移動
- データベーストランザクション
- エラー時自動ロールバック

### 3. 並行処理安全性
- SHA256計算とファイル書き込み並行
- データベース競合回避
- スレッドセーフ実装

## 制限事項

1. **署名検証**: 現在は基本的な形式チェックのみ（完全なSchnorr検証は今後実装）
2. **ファイル形式制限**: 現在は制限なし（MIMEタイプフィルタリングは今後追加可能）
3. **Rate Limiting**: 現在未実装（今後のバージョンで追加予定）

## 設定例

```yaml
blossom:
  upload:
    max-file-size: 52428800  # 50MB
    require-auth: true
  storage:
    path: ./data/blobs
  database:
    path: ./data/sqlite.db
```