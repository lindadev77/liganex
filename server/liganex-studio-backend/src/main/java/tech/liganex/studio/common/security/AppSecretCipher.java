package tech.liganex.studio.common.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.AEADBadTagException;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 开放平台 appsecret 的可逆加密（AES-256-GCM）。
 *
 * <p>为什么可逆：MCP 调用方需要用 appsecret 作为 HMAC-SHA256 密钥，服务端必须能还原明文来验签，
 * 因此不能用 BCrypt 这类单向哈希（见 V5 迁移说明）。
 *
 * <p>主密钥 {@code LIGANEX_APP_SECRET_MASTER_KEY}（base64 32 字节）必须由环境变量/启动参数注入（ADR-0007）。
 * 未注入时回退到内置 dev 密钥并告警 —— 该密钥仅用于本地联调，禁止在生产使用。
 *
 * <p>输出格式：base64( 12 字节 IV || 密文+认证标签 )，便于单列存储与解析。
 */
@Slf4j
@Component
public class AppSecretCipher {

    private static final String ALGO = "AES/GCM/NoPadding";
    private static final int IV_LEN = 12;
    private static final int TAG_LEN_BITS = 128;
    private static final int KEY_LEN_BYTES = 32;

    /** dev 回退密钥（24 字节随机盐 + 8 字节固定），仅本地联调用，生产必须覆盖 */
    private static final byte[] DEV_MASTER_KEY = buildDevKey();

    private final SecretKeySpec masterKey;
    private final boolean usingDevKey;

    public AppSecretCipher(@Value("${liganex.open.app-secret-master-key:}") String masterKeyB64) {
        byte[] raw = decodeMasterKey(masterKeyB64);
        if (raw == null) {
            this.masterKey = new SecretKeySpec(DEV_MASTER_KEY, "AES");
            this.usingDevKey = true;
            log.warn("AppSecretCipher 使用内置 dev 主密钥（LIGANEX_APP_SECRET_MASTER_KEY 未配置），仅限本地联调，生产环境请注入主密钥");
        } else {
            this.masterKey = new SecretKeySpec(raw, "AES");
            this.usingDevKey = false;
        }
    }

    public boolean isUsingDevKey() {
        return usingDevKey;
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_LEN];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.ENCRYPT_MODE, masterKey, new GCMParameterSpec(TAG_LEN_BITS, iv));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + ct.length)
                    .put(iv).put(ct).array());
        } catch (BadPaddingException | IllegalBlockSizeException e) {
            throw new IllegalStateException("appsecret 加密失败", e);
        } catch (Exception e) {
            throw new IllegalStateException("appsecret 加密初始化失败", e);
        }
    }

    public String decrypt(String ciphertextB64) {
        try {
            byte[] data = Base64.getDecoder().decode(ciphertextB64);
            if (data.length <= IV_LEN) {
                throw new IllegalArgumentException("密文长度异常");
            }
            byte[] iv = new byte[IV_LEN];
            byte[] ct = new byte[data.length - IV_LEN];
            System.arraycopy(data, 0, iv, 0, IV_LEN);
            System.arraycopy(data, IV_LEN, ct, 0, ct.length);
            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.DECRYPT_MODE, masterKey, new GCMParameterSpec(TAG_LEN_BITS, iv));
            return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
        } catch (AEADBadTagException e) {
            throw new IllegalStateException("appsecret 解密失败：认证标签不匹配（主密钥可能不匹配）", e);
        } catch (BadPaddingException | IllegalBlockSizeException e) {
            throw new IllegalStateException("appsecret 解密失败", e);
        } catch (Exception e) {
            throw new IllegalStateException("appsecret 解密初始化失败", e);
        }
    }

    /** 生成一个新的随机 appsecret（32 字节，base64 输出） */
    public static String generateSecret() {
        byte[] raw = new byte[KEY_LEN_BYTES];
        new SecureRandom().nextBytes(raw);
        return Base64.getEncoder().encodeToString(raw);
    }

    private static byte[] decodeMasterKey(String masterKeyB64) {
        if (masterKeyB64 == null || masterKeyB64.isBlank()) {
            return null;
        }
        try {
            byte[] raw = Base64.getDecoder().decode(masterKeyB64);
            if (raw.length != KEY_LEN_BYTES) {
                log.warn("LIGANEX_APP_SECRET_MASTER_KEY 长度应为 32 字节（base64 后 {}），当前为 {} 字节",
                        Base64.getEncoder().encodeToString(new byte[KEY_LEN_BYTES]).length(), raw.length);
                return null;
            }
            return raw;
        } catch (IllegalArgumentException ex) {
            log.warn("LIGANEX_APP_SECRET_MASTER_KEY 不是合法 base64，将使用 dev 密钥");
            return null;
        }
    }

    private static byte[] buildDevKey() {
        byte[] salt = "liganex-dev-only-master-key".getBytes(StandardCharsets.UTF_8);
        byte[] key = new byte[KEY_LEN_BYTES];
        for (int i = 0; i < KEY_LEN_BYTES; i++) {
            key[i] = (byte) (salt[i % salt.length] ^ (i * 31));
        }
        return key;
    }
}
