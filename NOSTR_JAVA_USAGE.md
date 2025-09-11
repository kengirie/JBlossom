# nostr-java ライブラリ使用ガイド

## GenericEventクラスの使用方法

nostr-javaライブラリの`nostr.event.impl.GenericEvent`クラスを使用してNostrイベントを処理します。

### 基本的なインポート

```java
import nostr.event.impl.GenericEvent;
import nostr.base.PublicKey;
import nostr.base.Signature;
import nostr.event.BaseTag;
import com.fasterxml.jackson.databind.ObjectMapper;
```

### 1. JSONからGenericEventへのデシリアライゼーション

```java
@Service
public class NostrAuthService {
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    public GenericEvent parseNostrEvent(String eventJson) throws JsonProcessingException {
        return objectMapper.readValue(eventJson, GenericEvent.class);
    }
    
    // Base64デコードしてからパース
    public GenericEvent parseBase64Event(String base64Event) throws JsonProcessingException {
        String eventJson = new String(Base64.getDecoder().decode(base64Event));
        return objectMapper.readValue(eventJson, GenericEvent.class);
    }
}
```

### 2. イベント検証

```java
public boolean validateAuthEvent(GenericEvent event) {
    // kind 24242 チェック（Blossom認証用）
    if (event.getKind() != 24242) {
        return false;
    }
    
    // 基本的な検証（id, pubkey, signature等）
    try {
        event.validate();
    } catch (AssertionError e) {
        log.warn("Event validation failed: {}", e.getMessage());
        return false;
    }
    
    // 署名済みかチェック
    if (!event.isSigned()) {
        log.warn("Event is not signed");
        return false;
    }
    
    return true;
}
```

### 3. タグ操作

```java
public class EventTagHelper {
    
    // 特定のタグ値を取得
    public String getTagValue(GenericEvent event, String tagName) {
        try {
            BaseTag tag = event.getTag(tagName);
            return tag.getValue().toString();
        } catch (Exception e) {
            return null; // タグが存在しない場合
        }
    }
    
    // 必須タグを取得（存在しない場合は例外）
    public String getRequiredTagValue(GenericEvent event, String tagName) {
        BaseTag tag = event.requireTag(tagName);
        return tag.getValue().toString();
    }
    
    // 有効期限チェック
    public boolean isExpired(GenericEvent event) {
        String expirationStr = getTagValue(event, "expiration");
        if (expirationStr == null) return false;
        
        try {
            long expiration = Long.parseLong(expirationStr);
            long currentTime = Instant.now().getEpochSecond();
            return currentTime > expiration;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    // Blossom認証で必要なタグの取得
    public String getUrlFromEvent(GenericEvent event) {
        return getTagValue(event, "u"); // URLタグ
    }
    
    public String getMethodFromEvent(GenericEvent event) {
        return getTagValue(event, "method"); // HTTPメソッドタグ
    }
}
```

### 4. イベント作成（必要な場合）

```java
public GenericEvent createBlossomAuthEvent(PublicKey pubKey, String url, String method) {
    // 新しいイベント作成
    GenericEvent event = new GenericEvent(pubKey, 24242);
    
    // 必要なタグを追加
    event.addTag(BaseTag.create("u", url));
    event.addTag(BaseTag.create("method", method));
    
    // 有効期限を設定（現在時刻から1時間後）
    long expiration = Instant.now().getEpochSecond() + 3600;
    event.addTag(BaseTag.create("expiration", String.valueOf(expiration)));
    
    // イベント情報を更新（id, created_atを設定）
    event.update();
    
    return event;
}
```

### 5. 完全なBlossom認証サービス例

```java
@Service
public class NostrAuthService {
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    public boolean validateBlossomAuthEvent(String authHeader, String expectedUrl, String expectedMethod) {
        try {
            // Base64デコード
            String eventJson = new String(Base64.getDecoder().decode(authHeader));
            
            // JSONをGenericEventにパース
            GenericEvent event = objectMapper.readValue(eventJson, GenericEvent.class);
            
            // 基本検証
            if (!validateAuthEvent(event)) {
                return false;
            }
            
            // URLチェック
            String eventUrl = getTagValue(event, "u");
            if (!expectedUrl.equals(eventUrl)) {
                log.warn("URL mismatch: expected {}, got {}", expectedUrl, eventUrl);
                return false;
            }
            
            // HTTPメソッドチェック
            String eventMethod = getTagValue(event, "method");
            if (!expectedMethod.equals(eventMethod)) {
                log.warn("Method mismatch: expected {}, got {}", expectedMethod, eventMethod);
                return false;
            }
            
            // 有効期限チェック
            if (isExpired(event)) {
                log.warn("Event has expired");
                return false;
            }
            
            // 署名検証は既にvalidate()で実行済み
            return true;
            
        } catch (Exception e) {
            log.error("Failed to validate auth event", e);
            return false;
        }
    }
    
    // ユーティリティメソッド群
    private boolean validateAuthEvent(GenericEvent event) {
        if (event.getKind() != 24242) return false;
        
        try {
            event.validate();
            return event.isSigned();
        } catch (AssertionError e) {
            log.warn("Event validation failed: {}", e.getMessage());
            return false;
        }
    }
    
    private String getTagValue(GenericEvent event, String tagName) {
        try {
            BaseTag tag = event.getTag(tagName);
            return tag.getValue().toString();
        } catch (Exception e) {
            return null;
        }
    }
    
    private boolean isExpired(GenericEvent event) {
        String expirationStr = getTagValue(event, "expiration");
        if (expirationStr == null) return false;
        
        try {
            long expiration = Long.parseLong(expirationStr);
            return Instant.now().getEpochSecond() > expiration;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
```

### 6. GenericEventの主要メソッド

| メソッド | 説明 |
|---------|------|
| `getKind()` | イベントのkindを取得 |
| `getPubKey()` | 公開鍵を取得 |
| `getSignature()` | 署名を取得 |
| `getCreatedAt()` | 作成日時を取得 |
| `getContent()` | コンテンツを取得 |
| `getTags()` | 全タグを取得 |
| `getTag(String code)` | 特定のタグを取得 |
| `requireTag(String code)` | 必須タグを取得（なければ例外） |
| `addTag(BaseTag tag)` | タグを追加 |
| `isSigned()` | 署名済みかチェック |
| `validate()` | イベントを検証 |
| `update()` | id, created_atを更新 |
| `toBech32()` | Bech32形式に変換 |

### 7. 注意点

- **Jackson設定**: nostr-javaライブラリは内部でJacksonを使用しているため、追加設定は不要
- **署名検証**: `validate()`メソッドが自動的に署名検証も行う
- **タグアクセス**: 存在しないタグにアクセスする場合は例外処理が必要
- **スレッドセーフティ**: GenericEventはスレッドセーフではないため、同時アクセス時は注意

### 8. Blossom Server実装での使用例

```java
@RestController
public class BlobController {
    
    @Autowired
    private NostrAuthService nostrAuthService;
    
    @GetMapping("/{hash}")
    public ResponseEntity<Resource> getBlob(
            @PathVariable String hash,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            HttpServletRequest request) {
        
        // 認証チェック（必要な場合）
        if (authHeader != null) {
            String requestUrl = request.getRequestURL().toString();
            if (!nostrAuthService.validateBlossomAuthEvent(authHeader, requestUrl, "GET")) {
                throw new UnauthorizedException("Invalid authentication");
            }
        }
        
        // Blobを返す処理...
        return ResponseEntity.ok(blobResource);
    }
}
```

この使用ガイドに従うことで、nostr-javaライブラリのGenericEventクラスを効果的に活用できます。