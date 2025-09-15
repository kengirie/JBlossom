# Phase 3: BUD02 Implementation Plan

## 概要

BUD02仕様に基づくBLOBアップロード・管理機能の実装計画。TypeScript版blossom-serverの実装パターンを参考に、Spring Boot + nostr-javaライブラリを使用してJava版を構築。

## BUD02仕様分析

### 実装対象エンドポイント

1. **PUT /upload** - バイナリデータアップロード
2. **DELETE /<sha256>** - BLOB削除（認証必須）
3. **GET /list/<pubkey>** - BLOB一覧取得

### BlobDescriptor形式
```json
{
  "url": "https://example.com/abc123...",
  "sha256": "abc123...",
  "size": 1024,
  "type": "image/png",
  "uploaded": 1234567890
}
```

## タスク詳細計画

### Task 7: PUT /upload エンドポイント実装

#### 7.1 UploadController基本実装
- **ファイル**: `src/main/java/io/github/kengirie/JBlossom/controller/UploadController.java`
- **機能**:
  - `@PutMapping("/upload")` エンドポイント
  - `@RequestBody MultipartFile` または `InputStream` でバイナリデータ受信
  - Content-Typeヘッダー解析
  - ファイルサイズ制限チェック

#### 7.2 アップロード認証機能
- **統合**: 既存の `NostrAuthService` 活用
- **認証フロー**:
  ```java
  // Authorization: Nostr <base64-encoded-event>
  AuthResult authResult = nostrAuthService.validateAuthEvent(authHeader, "upload");
  if (!authResult.isValid()) {
      throw new AuthenticationException(...);
  }
  ```
- **認証タグ検証**:
  - `t` タグ = "upload"
  - `x` タグ = 計算されたSHA256ハッシュ（オプション）
  - `expiration` タグによる有効期限チェック

#### 7.3 バイナリデータ処理
- **SHA256計算**: アップロード中にストリーミング計算
- **一時ファイル**: `/tmp` でのバッファリング
- **整合性チェック**: 計算ハッシュと期待値の比較
- **原子性**: 検証成功後に最終保存場所へ移動

#### 7.4 BlobDescriptor生成
- **レスポンス形式**:
  ```java
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public class BlobDescriptor {
      private String url;        // https://domain/{sha256}
      private String sha256;     // 64文字ハッシュ
      private Long size;         // バイト数
      private String type;       // MIME-type
      private Long uploaded;     // Unix timestamp
  }
  ```

#### 7.5 エラーハンドリング
- **401 Unauthorized**: 認証失敗、署名無効、期限切れ
- **409 Conflict**: 既存ファイルと異なる内容
- **413 Payload Too Large**: サイズ制限超過
- **422 Unprocessable Entity**: SHA256不一致

### Task 8: DELETE /<sha256> エンドポイント実装

#### 8.1 DeleteController基本実装
- **ファイル**: `src/main/java/io/github/kengirie/JBlossom/controller/DeleteController.java`
- **機能**:
  - `@DeleteMapping("/{sha256}")`
  - SHA256パスパラメータ検証
  - BLOB存在確認

#### 8.2 削除認証・認可
- **必須認証**: Authorization ヘッダー必須
- **認証タグ要件**:
  ```java
  // t=delete, x=<sha256> タグが必須
  if (!authResult.hasTag("t", "delete") || 
      !authResult.hasTag("x", sha256Hash)) {
      throw new AuthenticationException(INSUFFICIENT_PERMISSIONS);
  }
  ```
- **オーナーシップ検証**: 削除権限の確認

#### 8.3 削除処理
- **物理削除**: ファイルシステムからの削除
- **メタデータ削除**: SQLiteからの削除
- **原子性**: トランザクション内での削除
- **ログ記録**: 削除操作の監査ログ

#### 8.4 エラーハンドリング
- **401 Unauthorized**: 認証失敗
- **403 Forbidden**: 削除権限なし
- **404 Not Found**: BLOB不存在

### Task 9: GET /list/<pubkey> エンドポイント実装

#### 9.1 ListController基本実装
- **ファイル**: `src/main/java/io/github/kengirie/JBlossom/controller/ListController.java`
- **機能**:
  - `@GetMapping("/list/{pubkey}")`
  - 公開鍵形式検証（64文字hex）
  - クエリパラメータ処理

#### 9.2 フィルタリング・ページネーション
- **クエリパラメータ**:
  ```java
  @RequestParam(required = false) Long since;  // Unix timestamp
  @RequestParam(required = false) Long until;  // Unix timestamp
  @RequestParam(defaultValue = "50") int limit; // 最大100
  @RequestParam(defaultValue = "0") int offset;
  ```
- **SQLクエリ例**:
  ```sql
  SELECT * FROM blobs 
  WHERE pubkey = ? 
    AND uploaded >= ? 
    AND uploaded <= ? 
  ORDER BY uploaded DESC 
  LIMIT ? OFFSET ?
  ```

#### 9.3 レスポンス形式
- **JSON配列**: `BlobDescriptor[]`
- **ヘッダー**: `Content-Type: application/json`
- **空結果**: `[]` (404ではなく)

#### 9.4 認証制御
- **オプション認証**: 認証なしでもアクセス可能
- **プライベートリスト**: 認証時のみ非公開BLOBも表示（将来拡張）

### Task 10: StorageService拡張

#### 10.1 アップロード処理追加
```java
public BlobMetadata storeBlob(InputStream inputStream, 
                             String contentType, 
                             String uploaderPubkey) throws StorageException;
```

#### 10.2 削除処理追加
```java
public boolean deleteBlob(String sha256Hash, String requesterPubkey) throws StorageException;
```

#### 10.3 一覧取得処理追加
```java
public List<BlobMetadata> listBlobs(String pubkey, 
                                   Long since, 
                                   Long until, 
                                   int limit, 
                                   int offset);
```

#### 10.4 SHA256計算ユーティリティ
```java
public String calculateSHA256(InputStream inputStream) throws IOException;
```

### Task 11: SQLiteデータベース拡張

#### 11.1 テーブル構造更新
```sql
-- 既存のblobsテーブルにカラム追加
ALTER TABLE blobs ADD COLUMN pubkey TEXT(64);
ALTER TABLE blobs ADD COLUMN uploaded INTEGER;

-- インデックス追加
CREATE INDEX IF NOT EXISTS idx_blobs_pubkey ON blobs(pubkey);
CREATE INDEX IF NOT EXISTS idx_blobs_uploaded ON blobs(uploaded);
CREATE INDEX IF NOT EXISTS idx_blobs_pubkey_uploaded ON blobs(pubkey, uploaded);
```

#### 11.2 Repository層実装
- **ファイル**: `src/main/java/io/github/kengirie/JBlossom/repository/BlobRepository.java`
- **CRUD操作**: INSERT, SELECT, DELETE
- **複合クエリ**: pubkey + timestamp filtering

## 実装順序

### Week 1: Task 7 (PUT /upload)
1. **Day 1**: UploadController基本骨格
2. **Day 2**: 認証統合とバイナリ処理
3. **Day 3**: BlobDescriptor生成とエラーハンドリング

### Week 2: Task 8 (DELETE /<sha256>)
1. **Day 1**: DeleteController実装
2. **Day 2**: 認可ロジックと削除処理

### Week 3: Task 9 (GET /list/<pubkey>)
1. **Day 1**: ListController実装
2. **Day 2**: フィルタリングとページネーション

### Week 4: Task 10-11 (統合・最適化)
1. **Day 1**: StorageService拡張
2. **Day 2**: SQLite拡張とマイグレーション
3. **Day 3**: 統合テストと最適化

## TypeScript版との比較・参考点

### 認証パターン移植
**TypeScript (upload.ts)**:
```typescript
const requiredTags = ["t"];
if (upload.pubkey) requiredTags.push("x");

const authEvent = checkUpload("upload", config.upload);
if (!authEvent) throw new Error("Authentication required");
```

**Java版移植**:
```java
AuthResult authResult = nostrAuthService.validateAuthEvent(authHeader, "upload");
if (!authResult.isValid()) {
    throw new AuthenticationException(authResult.getErrorType(), authResult.getReason());
}

// x タグ検証 (オプション)
if (authResult.hasTag("x") && !authResult.getTagValue("x").equals(calculatedSHA256)) {
    throw new AuthenticationException(HASH_MISMATCH);
}
```

### エラーハンドリングパターン
**TypeScript**:
```typescript
if (!upload.file) {
  ctx.throw(400, "Missing file");
}
if (upload.size > maxSize) {
  ctx.throw(413, "File too large");
}
```

**Java版**:
```java
@ExceptionHandler(FileSizeLimitExceededException.class)
public ResponseEntity<ErrorResponse> handleFileSizeLimit(FileSizeLimitExceededException ex) {
    return ResponseEntity.status(413)
        .header("X-Reason", "File size exceeds limit")
        .body(new ErrorResponse("Payload Too Large", ex.getMessage()));
}
```

## セキュリティ考慮事項

### アップロード制限
- **ファイルサイズ**: application.ymlで設定可能
- **MIME-type制限**: 悪意あるファイルタイプの排除
- **Rate Limiting**: 同一IPからの連続アップロード制限

### 認証強化
- **Replay攻撃対策**: `created_at` + `expiration` の時間窓検証
- **署名検証**: nostr-java の `Schnorr.verify()` 使用
- **公開鍵検証**: hex形式および楕円曲線の有効性確認

### ファイル管理
- **パストラバーサル対策**: SHA256ベースのパス生成
- **権限分離**: 読み取り専用での実ファイルアクセス
- **完全性保証**: 保存後のSHA256再検証

## テスト戦略

### 単体テスト
- **Controller層**: MockMvcを使用したエンドポイントテスト
- **Service層**: モックを使用したビジネスロジックテスト
- **認証**: 有効/無効なNostrイベントでのテスト

### 統合テスト
- **エンドツーエンド**: 実際のファイルアップロード〜削除フロー
- **認証統合**: nostr-javaライブラリとの統合テスト
- **データベース**: SQLiteとの実際の読み書きテスト

### 負荷テスト
- **同時アップロード**: 並行処理の検証
- **大容量ファイル**: メモリ使用量の監視
- **データベース性能**: 大量BLOB環境での一覧取得性能

## パフォーマンス最適化

### ストリーミング処理
- **メモリ効率**: InputStreamベースのファイル処理
- **プログレッシブ処理**: SHA256計算とファイル書き込みの並行実行

### データベース最適化
- **インデックス戦略**: pubkey + uploaded の複合インデックス
- **クエリ最適化**: LIMIT/OFFSET のパフォーマンス改善
- **コネクションプール**: SQLite接続の効率化

### キャッシュ戦略
- **メタデータキャッシュ**: 頻繁にアクセスされるBLOB情報
- **MIME-type キャッシュ**: 拡張子ベースの型判定結果