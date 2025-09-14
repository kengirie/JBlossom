# JBlossom プロジェクト実装状況

## プロジェクト概要

JBlossomはSpring BootでBlossom Server (BUD01/BUD02) を実装するJavaプロジェクトです。

- **フレームワーク**: Spring Boot 3.5.5 + Java 21
- **ビルドツール**: Maven
- **データベース**: SQLite
- **認証**: Nostr (nostr-java v0.007.1-alpha)

## Phase 1実装完了項目

### ✅ 基盤実装（完了）

1. **依存関係設定** - `pom.xml:40-68`
   - Spring Boot Web & Validation
   - SQLite JDBC Driver
   - nostr-java v0.007.1-alpha

2. **設定ファイル** - `src/main/resources/application.yml:1-14`
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

3. **SQLiteデータベース初期化** - `src/main/java/io/github/kengirie/JBlossom/config/SQLiteConfig.java:14-71`
   - `blobs`テーブル（hash, size, type, uploaded, pubkey）
   - `accessed`テーブル（アクセスログ）
   - インデックス作成（pubkey, uploaded, timestamp）

4. **DTOクラス** 
   - `BlobDescriptor.java:6-67` - Blossom仕様のBlobDescriptor形式
   - `BlobMetadata.java:5-81` - 内部メタデータ管理用

5. **設定クラス**
   - `BlossomProperties.java:7-102` - 設定プロパティ管理
   - `WebConfig.java:9-29` - CORS設定
   - `StorageConfig.java:15-61` - ストレージディレクトリ初期化

6. **例外ハンドラー** - `GlobalExceptionHandler.java:13-58`
   - BlobNotFoundException (404)
   - UnauthorizedException (401)
   - 汎用例外処理

7. **テスト環境** - `NostrJavaIntegrationTest.java:8-23`
   - Spring Boot起動テスト

## アプリケーションの実行方法

### 1. 前提条件
- Java 21以上
- Git

### 2. 実行手順

```bash
# 1. プロジェクトクローン（既存の場合スキップ）
git clone <リポジトリURL>
cd JBlossom

# 2. アプリケーション起動
./mvnw spring-boot:run

# または、JARビルド後実行
./mvnw clean package
java -jar target/JBlossom-0.0.1-SNAPSHOT.jar
```

### 3. 実行確認
- **ポート**: 8080 (デフォルト)
- **データベース**: ./data/sqlite.db (自動作成)
- **ストレージ**: ./data/blobs (自動作成)

### 4. ログ確認
```bash
# アプリケーション起動時に以下が表示される
Started JBlossomApplication in X.XXX seconds
```

## 未実装項目（Phase 2以降）

### Phase 2: BUD01実装
- [ ] NostrAuthService実装（nostr-java使用）
- [ ] GET /<sha256>エンドポイント
- [ ] HEADリクエスト対応
- [ ] Range Requests実装
- [ ] ストレージサービス実装

### Phase 3: BUD02実装
- [ ] PUT /uploadエンドポイント
- [ ] DELETE /<sha256>エンドポイント  
- [ ] GET /list/<pubkey>エンドポイント
- [ ] 認証・認可システム

## アーキテクチャ詳細

### プロジェクト構造
```
src/main/java/io/github/kengirie/JBlossom/
├── JBlossomApplication.java        # メインクラス
├── config/                         # 設定クラス
│   ├── BlossomProperties.java      # プロパティ設定
│   ├── SQLiteConfig.java          # DB初期化
│   ├── StorageConfig.java         # ストレージ設定
│   └── WebConfig.java             # CORS設定
├── model/                         # データモデル
│   ├── BlobDescriptor.java        # API応答用
│   └── BlobMetadata.java          # 内部メタデータ
└── exception/                     # 例外処理
    ├── BlobNotFoundException.java
    ├── UnauthorizedException.java
    └── GlobalExceptionHandler.java
```

### データベーススキーマ
```sql
-- Blobメタデータ
CREATE TABLE blobs (
    hash TEXT(64) PRIMARY KEY,      -- SHA256ハッシュ
    size INTEGER NOT NULL,          -- ファイルサイズ
    type TEXT,                      -- MIME Type
    uploaded INTEGER NOT NULL,      -- アップロード時刻（Unix timestamp）
    pubkey TEXT(64)                 -- アップロード者の公開鍵
);

-- アクセスログ
CREATE TABLE accessed (
    blob TEXT(64) PRIMARY KEY,      -- Blobハッシュ
    timestamp INTEGER NOT NULL      -- アクセス時刻
);
```

## 技術仕様

- **Java**: 21 (LTS)
- **Spring Boot**: 3.5.5
- **Maven**: 3.9.11
- **SQLite**: 軽量ファイルDB
- **nostr-java**: v0.007.1-alpha (Nostr署名検証)

## セキュリティ考慮事項

- CORS設定済み（全オリジン許可）
- ファイルサイズ制限（100MB）
- SQLインジェクション対策（PreparedStatement使用予定）
- パストラバーサル対策（実装予定）

## パフォーマンス最適化

- SQLiteインデックス設定済み
- ストリーミング処理設計（実装予定）
- 軽量なアーキテクチャ（JPA/Hibernate未使用）

---

**現在の状態**: Phase 1完了 - 基盤実装とSpring Boot起動可能状態
**次のステップ**: Phase 2実装開始 - BUD01 GET /<sha256>エンドポイント実装