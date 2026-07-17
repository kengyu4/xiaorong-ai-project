package com.xiaorong.assistant.ai.secret;

import com.xiaorong.assistant.config.XiaorongProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.EnumSet;
import java.util.Set;
// 密钥加密服务
@Service
public class AiSecretCryptoService {
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String KEY_VERSION = "v1";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecureRandom secureRandom = new SecureRandom();
    private final byte[] masterKey;

    @Autowired
    public AiSecretCryptoService(XiaorongProperties properties) {
        this(properties.getAi().getSecretMasterKey(), toPath(properties.getAi().getSecretMasterKeyFile()));
    }

    AiSecretCryptoService(String encodedMasterKey) {
        this(encodedMasterKey, null);
    }

    AiSecretCryptoService(String encodedMasterKey, Path masterKeyFile) {
        this.masterKey = encodedMasterKey == null || encodedMasterKey.isBlank()
                ? loadOrCreateMasterKey(masterKeyFile)
                : decodeMasterKey(encodedMasterKey);
    }

    public boolean available() {
        return masterKey != null;
    }

    public EncryptedSecret encrypt(String plaintext) {
        requireAvailable();
        if (plaintext == null || plaintext.isBlank()) {
            throw new IllegalArgumentException("API Key 不能为空");
        }
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(masterKey, "AES"), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return new EncryptedSecret(
                    Base64.getEncoder().encodeToString(encrypted),
                    Base64.getEncoder().encodeToString(iv),
                    KEY_VERSION
            );
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("API Key 加密失败", ex);
        }
    }

    public String decrypt(EncryptedSecret encrypted) {
        requireAvailable();
        if (encrypted == null || !KEY_VERSION.equals(encrypted.keyVersion())) {
            throw new IllegalStateException("API Key 解密失败：不支持的密钥版本");
        }
        try {
            byte[] iv = Base64.getDecoder().decode(encrypted.iv());
            byte[] ciphertext = Base64.getDecoder().decode(encrypted.ciphertext());
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(masterKey, "AES"), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalStateException("API Key 解密失败", ex);
        }
    }

    private void requireAvailable() {
        if (!available()) {
            throw new IllegalStateException("AI Key 安全存储未启用，请配置 32 字节 Base64 主密钥");
        }
    }


    private byte[] loadOrCreateMasterKey(Path masterKeyFile) {
        if (masterKeyFile == null) {
            return null;
        }
        try {
            if (Files.exists(masterKeyFile)) {
                return decodeMasterKey(Files.readString(masterKeyFile, StandardCharsets.UTF_8));
            }
            Path parent = masterKeyFile.toAbsolutePath().normalize().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            byte[] generated = new byte[32];
            secureRandom.nextBytes(generated);
            String encoded = Base64.getEncoder().encodeToString(generated);
            createPrivateKeyFile(masterKeyFile, encoded);
            return generated;
        } catch (FileAlreadyExistsException ex) {
            try {
                return decodeMasterKey(Files.readString(masterKeyFile, StandardCharsets.UTF_8));
            } catch (IOException | SecurityException readEx) {
                return null;
            }
        } catch (IOException | SecurityException ex) {
            return null;
        }
    }

    private void createPrivateKeyFile(Path masterKeyFile, String encoded) throws IOException {
        Set<StandardOpenOption> options = EnumSet.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        FileAttribute<?>[] attributes = privateFileAttributes(masterKeyFile);
        try (SeekableByteChannel channel = Files.newByteChannel(masterKeyFile, options, attributes)) {
            channel.write(ByteBuffer.wrap(encoded.getBytes(StandardCharsets.UTF_8)));
        }
    }

    private FileAttribute<?>[] privateFileAttributes(Path masterKeyFile) {
        Path parent = masterKeyFile.toAbsolutePath().normalize().getParent();
        try {
            if (parent != null && Files.getFileStore(parent).supportsFileAttributeView("posix")) {
                Set<PosixFilePermission> permissions = EnumSet.of(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE
                );
                return new FileAttribute<?>[]{PosixFilePermissions.asFileAttribute(permissions)};
            }
        } catch (IOException | SecurityException ignored) {
            // Non-POSIX filesystems continue to rely on host directory ACLs.
        }
        return new FileAttribute<?>[0];
    }

    private static Path toPath(String value) {
        return value == null || value.isBlank() ? null : Path.of(value.trim());
    }

    private byte[] decodeMasterKey(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(value.trim());
            return decoded.length == 32 ? decoded : null;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public record EncryptedSecret(
            String ciphertext,
            String iv,
            String keyVersion
    ) {
    }
}

