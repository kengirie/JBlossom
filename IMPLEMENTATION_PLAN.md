# JBlossom - Spring Boot Implementation Plan

## プロジェクト概要

Java Spring BootでBlossom Server (BUD01/BUD02) を実装するためのマスタープラン

## 現在のプロジェクト分析

**プロジェクト構成:**
- Spring Boot 3.5.5 + Java 21
- Maven ベース
- パッケージ: `io.github.kengirie.JBlossom`
- **現状:** 基本的なSpring Bootアプリケーションの骨格のみ

## Blossom仕様概要

### BUD01: サーバー要件とBLOB取得
- **GET /<sha256>**: Blob取得、ファイル拡張子サポート、MIME-type設定
- **認証システム**: Nostr kind 24242イベント、`t`タグ、expiration タグの検証
- **CORS設定**: `Access-Control-Allow-Origin: *`など
- **HEADリクエストサポート**: Blob存在確認
- **Range Requests**: 部分取得サポート

### BUD02: BLOB アップロード・管理
- **PUT /upload**: バイナリデータの非改変アップロード、BlobDescriptor レスポンス
- **DELETE /<sha256>**: 認証必須の削除機能
- **GET /list/<pubkey>**: since/untilクエリパラメータサポート
- **BlobDescriptor形式**: url, sha256, size, type, uploadedフィールド

## Spring Boot実装アーキテクチャ

### プロジェクト構造
```
src/main/java/io/github/kengirie/JBlossom/
├── JBlossomApplication.java
├── config/
│   ├── WebConfig.java          # CORS, セキュリティ設定
│   ├── NostrConfig.java        # Nostr関連設定
│   └── StorageConfig.java      # ストレージ設定
├── controller/
│   ├── BlobController.java     # BUD01: GET /<sha256>
│   ├── UploadController.java   # BUD02: PUT /upload
│   ├── ListController.java     # BUD02: GET /list/<pubkey>
│   └── DeleteController.java   # BUD02: DELETE /<sha256>
├── service/
│   ├── BlobService.java        # Blob管理サービス
│   ├── NostrAuthService.java   # Nostr認証サービス
│   ├── StorageService.java     # ストレージ操作
│   └── ValidationService.java  # バリデーション
├── model/
│   ├── BlobDescriptor.java     # BlobDescriptor DTO
│   ├── NostrEvent.java         # Nostr Event DTO
│   └── BlobMetadata.java       # Blob メタデータ
├── repository/
│   ├── BlobRepository.java     # SQLite直接アクセス
│   └── AccessLogRepository.java # アクセスログ管理
└── exception/
    ├── BlobNotFoundException.java
    ├── UnauthorizedException.java
    └── GlobalExceptionHandler.java
```

### 必要な依存関係

```xml
<!-- pom.xmlに追加 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>

<!-- SQLite データベース -->
<dependency>
    <groupId>org.xerial</groupId>
    <artifactId>sqlite-jdbc</artifactId>
</dependency>

<!-- nostr-java ライブラリ群 -->
<dependency>
    <groupId>xyz.tcheeric</groupId>
    <artifactId>nostr-java-crypto</artifactId>
    <version>0.2.2</version>
</dependency>
<dependency>
    <groupId>xyz.tcheeric</groupId>
    <artifactId>nostr-java-event</artifactId>
    <version>0.2.2</version>
</dependency>
<dependency>
    <groupId>xyz.tcheeric</groupId>
    <artifactId>nostr-java-util</artifactId>
    <version>0.2.2</version>
</dependency>
```

### nostr-java ライブラリ分析結果

**利用可能な機能:**

1. **署名検証機能** - ✅ 完全対応
   - `nostr.crypto.schnorr.Schnorr.verify()` - Schnorr署名検証
   - secp256k1楕円曲線対応
   - BIP-340準拠のSchnorr署名検証

2. **JSONパース機能** - ✅ 完全対応
   - `nostr.event.impl.GenericEvent` - Nostr Event の完全実装
   - Jackson を使用した JSON シリアライゼーション/デシリアライゼーション
   - タグ、コンテンツ、署名などすべてのフィールド対応

3. **イベント管理** - ✅ 完全対応
   - kind 24242 認証イベントサポート
   - タグ管理（t, expiration, x タグ等）
   - 有効期限チェック
   - 公開鍵・署名のHEX検証

**削除可能な依存関係:**
- ~~jackson-databind~~ (nostr-java-eventに含まれる)
- ~~bcprov-jdk18on~~ (nostr-java-cryptoに含まれる)
- ~~spring-boot-starter-data-jpa~~ (SQLite直接アクセスのため不要)
- ~~H2 Database~~ (SQLiteを使用)

## BUD01実装詳細

### 1. BlobController (GET /<sha256>)
- Range Requestsサポート
- ファイル拡張子処理
- MIME-type自動検出
- キャッシュヘッダー設定
- 404エラーハンドリング

### 2. NostrAuthService
- `GenericEvent` を使用した kind 24242イベント検証
- `Schnorr.verify()` による署名検証
- 有効期限チェック（`created_at` + `expiration`タグ）
- タグ検証（t, expiration, x）
- Base64デコード処理
- 公開鍵のHEX形式検証

### 3. StorageService
- ローカルファイルストレージ (`./data/blobs/`)
- SHA256ベースのファイル管理
- SQLiteでのメタデータ管理
- ファイル整合性チェック

## BUD02実装詳細

### 1. UploadController (PUT /upload)
- マルチパート/バイナリアップロード
- SHA256検証
- BlobDescriptor生成
- 認証必須制御
- ファイルサイズ制限

### 2. ListController (GET /list/<pubkey>)
- ページネーション
- since/untilパラメータ
- JSON配列レスポンス
- 認証制御

### 3. DeleteController (DELETE /<sha256>)
- 認証必須
- オーナー検証
- 物理削除処理
- 削除権限チェック

## 実装フェーズ

### Phase 1: 基盤実装 (1-2日)
1. pom.xml依存関係追加（nostr-java + SQLite ライブラリ含む）
2. 基本設定ファイル（CORS、SQLite設定）
3. SQLiteデータベース初期化
4. DTOクラス作成
5. 例外ハンドラー実装
6. nostr-java ライブラリの統合テスト

### Phase 2: BUD01実装 (2-3日)
1. **NostrAuthService実装** - nostr-java使用
   - `GenericEvent` による認証イベントパース
   - `Schnorr.verify()` による署名検証
   - タグ検証ロジック
2. **GET /<sha256>エンドポイント実装**
3. **ストレージサービス実装**
4. **HEADリクエスト対応**

### Phase 3: BUD02実装 (3-4日)
1. **PUT /uploadエンドポイント実装**
   - nostr-java による認証統合
2. **DELETE /<sha256>エンドポイント実装**
3. **GET /list/<pubkey>エンドポイント実装**
4. **バリデーション強化**

### Phase 4: 最適化・テスト (1-2日)
1. パフォーマンス最適化
2. エラーハンドリング改善
3. 単体・統合テスト作成（nostr-java機能含む）
4. セキュリティ監査

## TypeScript実装との主な違い

### Java/Spring + nostr-java の利点
- 型安全性（コンパイル時チェック）
- JVMのパフォーマンス最適化
- 豊富なエンタープライズ機能
- Spring Bootの自動設定
- **nostr-java による完全なNostr対応**
  - 署名検証のハードウェア最適化
  - BIP-340準拠のSchnorr署名
  - 包括的なイベント管理

### 考慮点
- バイナリデータ処理（InputStreamベース）
- メモリ効率的なファイルアップロード
- **SQLite直接アクセス**によるメタデータ管理
- **nostr-java との統合**
  - Spring の Dependency Injection と nostr-java クラス
  - Jackson との JSON シリアライゼーション統合

## セキュリティ考慮事項

### 認証・認可
- **nostr-java による Nostr署名検証**
  - `Schnorr.verify()` を使用した検証
  - `GenericEvent` による認証イベント処理
- 公開鍵形式の検証（hex形式）- nostr-java標準バリデーション使用
- 有効期限の厳密なチェック

### ファイル処理
- SHA256整合性チェック
- ファイルサイズ制限
- MIME-type検証
- パストラバーサル攻撃対策

### ネットワーク
- CORS設定の適切な実装
- Rate Limiting
- Request サイズ制限

## パフォーマンス最適化

### ファイル処理
- ストリーミング処理でメモリ使用量削減
- 非同期I/O処理
- キャッシュ戦略

### データベース
- SQLiteインデックス最適化
- 単一ファイルアクセスの最適化
- 軽量なクエリ設計

## 設定ファイル例

### application.yml
```yaml
spring:
  application:
    name: JBlossom

blossom:
  database:
    path: ./data/sqlite.db
  storage:
    path: ./data/blobs
    max-file-size: 100MB
  cors:
    allowed-origins: "*"
    allowed-methods: GET,HEAD,POST,PUT,DELETE,OPTIONS
    allowed-headers: Authorization,Content-Type,X-SHA-256
```

## SQLite + nostr-java使用例

### データベース初期化
```java
@Configuration
public class SQLiteConfig {
    @Value("${blossom.database.path}")
    private String databasePath;
    
    @Bean
    public Connection sqliteConnection() throws SQLException {
        String url = "jdbc:sqlite:" + databasePath;
        Connection conn = DriverManager.getConnection(url);
        
        // テーブル作成
        conn.createStatement().execute("""
            CREATE TABLE IF NOT EXISTS blobs (
                hash TEXT(64) PRIMARY KEY,
                size INTEGER NOT NULL,
                type TEXT,
                uploaded INTEGER NOT NULL,
                pubkey TEXT(64)
            )
        """);
        
        conn.createStatement().execute("""
            CREATE TABLE IF NOT EXISTS accessed (
                blob TEXT(64) PRIMARY KEY,
                timestamp INTEGER NOT NULL
            )
        """);
        
        return conn;
    }
}
```

### 認証イベントのパース・検証例
```java
@Service
public class NostrAuthService {
    
    public boolean validateAuthEvent(String authHeader) {
        // Base64デコード
        String eventJson = new String(Base64.getDecoder().decode(authHeader));
        
        // GenericEventにデシリアライズ
        GenericEvent event = objectMapper.readValue(eventJson, GenericEvent.class);
        
        // kind 24242 チェック
        if (event.getKind() != 24242) {
            return false;
        }
        
        // 署名検証
        byte[] pubkey = HexFormat.of().parseHex(event.getPubKey().toString());
        byte[] signature = HexFormat.of().parseHex(event.getSignature().toString());
        byte[] message = event.serialize().getBytes();
        
        return Schnorr.verify(message, pubkey, signature);
    }
}
```

## 次のステップ

1. **Phase 1の基盤実装から開始** - SQLite + nostr-java ライブラリ統合
2. 各フェーズ完了後に動作検証
3. TypeScript版との互換性テスト
4. **SQLiteパフォーマンス測定**
5. 本家blossom-serverとの機能比較テスト

このプランに基づいて段階的に実装を進めることで、**軽量なSQLite + nostr-java ライブラリを活用した**BUD01/BUD02仕様に完全準拠したSpring Boot版Blossomサーバーを効率的に構築できます。

## 本家TypeScript版との主な違い

### 軽量化されたアーキテクチャ
- **SQLite**: 単一ファイル、設定不要
- **Direct JDBC**: JPA/Hibernateを使わない軽量アクセス
- **ファイルシステム**: メタデータのみDB、実ファイルは別保存

### Java固有の利点
- **型安全性**: コンパイル時エラーチェック
- **JVMパフォーマンス**: ガベージコレクション最適化
- **nostr-java統合**: ネイティブSchnorr署名検証