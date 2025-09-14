# Phase 2: BUD01実装詳細計画

## 概要

BUD01仕様とTypeScript版blossom-serverの実装を参考に、Spring BootでのBUD01実装を詳細に計画する。

## BUD01仕様要約（GitHub buds/01.mdから）

### 必須実装項目

1. **CORS対応**
   - `Access-Control-Allow-Origin: *` 全レスポンスに設定
   - Preflight OPTIONS対応
   - `Access-Control-Allow-Headers: Authorization, *`
   - `Access-Control-Allow-Methods: GET, HEAD, PUT, DELETE`

2. **エラーレスポンス**
   - HTTP >= 400で `X-Reason` ヘッダー設定可

3. **認証イベント（kind 24242）**
   - Base64エンコードされたNostr event
   - `Authorization: Nostr <base64_event>` ヘッダー
   - 必須フィールド検証：`kind`, `created_at`, `expiration`, `t`タグ

4. **GET /<sha256>エンドポイント**
   - ファイル拡張子対応（`.pdf`, `.png`等）
   - Content-Type自動検出
   - Range Requests対応（RFC 7233）
   - オプション認証（`t=get`タグ検証）

5. **HEAD /<sha256>エンドポイント**
   - GETと同一だがbodyなし
   - `accept-ranges: bytes`, `content-length`設定

## TypeScript版実装分析

### 参考実装構造（src/api/）

1. **fetch.ts** - GET /<sha256>実装
   - Range Requests対応（koa-range middleware）
   - MIME type検出（mime library）
   - ストレージ検索→アップストリーム検索→HTTP取得
   - バックグラウンド保存処理

2. **has.ts** - HEAD /<sha256>実装
   - DB検索のみでシンプル
   - Content-TypeとContent-Length設定

3. **認証システム**
   - Nostr署名検証（@noble/secp256k1）
   - kind 24242イベント処理
   - `t`タグによる操作分類

## Java Spring Boot実装詳細

### Phase 2タスク分解

#### Task 1: Nostr認証サービス実装

**目的**: kind 24242認証イベントの検証

**実装ファイル**: `src/main/java/io/github/kengirie/JBlossom/service/NostrAuthService.java`

```java
@Service
public class NostrAuthService {
    
    // nostr-java使用
    public AuthResult validateAuthEvent(String authHeader, String requiredAction) {
        // 1. Base64デコード
        // 2. JSON→GenericEventパース
        // 3. kind 24242検証
        // 4. created_at過去チェック
        // 5. expiration未来チェック
        // 6. tタグ検証（get/upload/delete/list）
        // 7. Schnorr署名検証
        // 8. xタグ検証（sha256専用の場合）
        // 9. AuthResult返却
    }
    
    // 署名検証用
    private boolean verifyEventSignature(GenericEvent event) {
        // Schnorr.verify()使用
    }
    
    // タグ検証用
    private boolean validateTags(GenericEvent event, String action, String hash) {
        // tタグ、xタグ、expirationタグ検証
    }
}
```

**依存関係**: nostr-java v0.007.1-alpha（既存）

**テスト項目**:
- 有効な認証イベント検証
- 期限切れイベント拒否
- 不正署名拒否
- 不正なtタグ拒否

#### Task 2: ストレージサービス実装

**目的**: ファイル管理、MIME検出、Range Requests

**実装ファイル**: `src/main/java/io/github/kengirie/JBlossom/service/StorageService.java`

```java
@Service
public class StorageService {
    
    // ファイル存在確認
    public Optional<BlobMetadata> findBlob(String sha256) {
        // SQLite検索 + ファイル存在確認
    }
    
    // ファイル読み取り（Range Request対応）
    public BlobContent readBlob(String sha256, HttpServletRequest request) {
        // Range headerパース
        // 部分読み取り対応
        // InputStreamResource返却
    }
    
    // MIME Type検出
    public String detectMimeType(String sha256, String extension) {
        // 1. DBから取得
        // 2. 拡張子から推測
        // 3. デフォルト: application/octet-stream
    }
    
    // SQLiteアクセス時刻更新
    public void updateAccessTime(String sha256) {
        // accessedテーブル更新
    }
}
```

**依存関係**: 
- Apache Tika（MIME検出）- 新規追加必要
- Commons IO（Range処理）- 新規追加必要

#### Task 3: GET /<sha256>コントローラー実装

**実装ファイル**: `src/main/java/io/github/kengirie/JBlossom/controller/BlobController.java`

```java
@RestController
public class BlobController {
    
    @GetMapping("/{hash:.+}")
    public ResponseEntity<Resource> getBlob(
        @PathVariable String hash,
        @RequestHeader(value = "Authorization", required = false) String auth,
        @RequestHeader(value = "Range", required = false) String range,
        HttpServletRequest request) {
        
        // 1. SHA256ハッシュ抽出（正規表現）
        // 2. 拡張子抽出
        // 3. 認証チェック（オプション）
        // 4. ストレージ検索
        // 5. MIME Type設定
        // 6. Range Request対応
        // 7. アクセス時刻更新
        // 8. ResponseEntity返却
    }
    
    // Range Request用ヘルパー
    private ResponseEntity<Resource> buildRangeResponse(
        BlobContent content, String range, String mimeType) {
        // 206 Partial Content対応
        // Content-Range, Accept-Ranges設定
    }
}
```

**特徴**:
- パス変数で全拡張子対応（`{hash:.+}`）
- オプション認証（Authorization header）
- Range Request完全対応
- 適切なHTTPステータス（200, 206, 404, 401）

#### Task 4: HEAD /<sha256>コントローラー実装

**実装**: BlobControllerに追加

```java
@HeadMapping("/{hash:.+}")
public ResponseEntity<Void> hasBlob(
    @PathVariable String hash,
    @RequestHeader(value = "Authorization", required = false) String auth,
    HttpServletRequest request) {
    
    // 1. SHA256抽出
    // 2. 認証チェック（オプション）
    // 3. ストレージ検索（メタデータのみ）
    // 4. Content-Type, Content-Length設定
    // 5. accept-ranges: bytes設定
    // 6. bodyなしResponseEntity返却
}
```

#### Task 5: Range Request完全実装

**目的**: RFC 7233準拠の部分取得

**実装詳細**:
```java
@Component
public class RangeRequestHandler {
    
    public RangeResponse parseRange(String rangeHeader, long fileSize) {
        // Range: bytes=0-1023, 2048-4095 解析
        // 複数範囲対応
        // boundary生成（multipart/byteranges）
    }
    
    public ResponseEntity<Resource> buildPartialResponse(
        Resource resource, List<Range> ranges) {
        // 206 Partial Content
        // Content-Range設定
        // multipart対応（複数範囲の場合）
    }
}
```

#### Task 6: エラーハンドリング強化

**実装**: 既存GlobalExceptionHandlerに追加

```java
@ExceptionHandler(RangeNotSatisfiableException.class)
public ResponseEntity<Map<String, String>> handleRangeError(
    RangeNotSatisfiableException ex) {
    // 416 Range Not Satisfiable
    // X-Reason header設定
}

@ExceptionHandler(AuthenticationException.class) 
public ResponseEntity<Map<String, String>> handleAuthError(
    AuthenticationException ex) {
    // 401 Unauthorized  
    // X-Reason header設定
}
```

## 新規依存関係（pom.xml追加）

```xml
<!-- MIME Type検出 -->
<dependency>
    <groupId>org.apache.tika</groupId>
    <artifactId>tika-core</artifactId>
    <version>2.9.1</version>
</dependency>

<!-- Range Request処理 -->
<dependency>
    <groupId>commons-io</groupId>
    <artifactId>commons-io</artifactId>
    <version>2.15.1</version>
</dependency>
```

## 実装順序

### Week 1: 基盤サービス
1. **Day 1-2**: NostrAuthService実装
   - nostr-java統合
   - 認証イベント検証ロジック
   - 単体テスト作成

2. **Day 3-4**: StorageService実装
   - ファイル管理機能
   - MIME検出
   - SQLiteアクセス

### Week 2: エンドポイント実装
3. **Day 5-6**: BlobController基本実装
   - GET /<sha256>基本機能
   - HEAD /<sha256>実装
   - 認証統合

4. **Day 7**: Range Request実装
   - RangeRequestHandler
   - 部分取得対応
   - multipart/byteranges

## TypeScript版との相違点

### Java特有の利点
- **型安全性**: コンパイル時チェック
- **Spring Boot統合**: 自動設定、DI
- **nostr-java**: ネイティブSchnorr署名検証
- **SQLite直接アクセス**: 軽量、高速

### 考慮事項
- **メモリ効率**: InputStreamベースの処理
- **非同期**: @Asyncでバックグラウンド処理
- **例外処理**: 統一されたエラーレスポンス

## セキュリティ考慮

1. **認証強化**: Nostr署名の完全検証
2. **パストラバーサル対策**: ファイルパス検証
3. **DoS対策**: Range Request制限
4. **SQLインジェクション**: PreparedStatement使用

## パフォーマンス最適化

1. **ストリーミング**: 大ファイル対応
2. **キャッシュ**: 頻繁アクセスファイル
3. **並行処理**: 非同期I/O活用
4. **DB最適化**: SQLiteインデックス活用

## テスト戦略

### 単体テスト
- NostrAuthService: 認証ロジック
- StorageService: ファイル操作
- RangeRequestHandler: Range解析

### 統合テスト  
- 認証フロー全体
- Range Request完全シナリオ
- エラーハンドリング

### 負荷テスト
- 大ファイル転送
- 同時アクセス
- Range Request性能

## 完了基準

1. **機能完全性**: BUD01仕様100%準拠
2. **互換性**: TypeScript版との完全互換
3. **性能**: 同等以上のレスポンス時間
4. **テストカバレッジ**: 90%以上
5. **セキュリティ**: 脆弱性なし

---

**Phase 2完了後の状態**: 
完全なBUD01準拠Blossom Server（GET/HEADエンドポイント）として動作可能