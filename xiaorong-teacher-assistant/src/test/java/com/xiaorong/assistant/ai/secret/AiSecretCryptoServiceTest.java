package com.xiaorong.assistant.ai.secret;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiSecretCryptoServiceTest {
    private static final String VALID_KEY = Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));

    @TempDir
    Path tempDir;

    @Test
    void encryptsAndDecryptsWithoutEmbeddingPlaintext() {
        AiSecretCryptoService service = new AiSecretCryptoService(VALID_KEY);

        AiSecretCryptoService.EncryptedSecret encrypted = service.encrypt("sk-user-secret");

        assertThat(encrypted.ciphertext()).doesNotContain("sk-user-secret");
        assertThat(encrypted.iv()).isNotBlank();
        assertThat(encrypted.keyVersion()).isEqualTo("v1");
        assertThat(service.decrypt(encrypted)).isEqualTo("sk-user-secret");
    }

    @Test
    void usesFreshIvForEveryEncryption() {
        AiSecretCryptoService service = new AiSecretCryptoService(VALID_KEY);

        AiSecretCryptoService.EncryptedSecret first = service.encrypt("same-secret");
        AiSecretCryptoService.EncryptedSecret second = service.encrypt("same-secret");

        assertThat(first.iv()).isNotEqualTo(second.iv());
        assertThat(first.ciphertext()).isNotEqualTo(second.ciphertext());
    }

    @Test
    void rejectsTamperedCiphertext() {
        AiSecretCryptoService service = new AiSecretCryptoService(VALID_KEY);
        AiSecretCryptoService.EncryptedSecret encrypted = service.encrypt("sk-user-secret");
        byte[] bytes = Base64.getDecoder().decode(encrypted.ciphertext());
        bytes[0] = (byte) (bytes[0] ^ 1);
        AiSecretCryptoService.EncryptedSecret tampered = new AiSecretCryptoService.EncryptedSecret(
                Base64.getEncoder().encodeToString(bytes), encrypted.iv(), encrypted.keyVersion());

        assertThatThrownBy(() -> service.decrypt(tampered))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("解密失败");
    }

    @Test
    void generatesAndReusesFileBackedMasterKeyWhenExplicitKeyIsMissing() throws Exception {
        Path keyFile = tempDir.resolve("ai/master.key");
        AiSecretCryptoService first = new AiSecretCryptoService("", keyFile);

        AiSecretCryptoService.EncryptedSecret encrypted = first.encrypt("sk-user-secret");
        AiSecretCryptoService second = new AiSecretCryptoService("", keyFile);

        assertThat(first.available()).isTrue();
        assertThat(Files.readString(keyFile)).isNotBlank();
        assertThat(second.decrypt(encrypted)).isEqualTo("sk-user-secret");
    }

    @Test
    void invalidExplicitKeyDoesNotFallBackToGeneratedFile() {
        Path keyFile = tempDir.resolve("ai/master.key");

        AiSecretCryptoService service = new AiSecretCryptoService("not-base64", keyFile);

        assertThat(service.available()).isFalse();
        assertThat(keyFile).doesNotExist();
    }

    @Test
    void unavailableWhenMasterKeyIsMissingMalformedOrWrongLength() {
        assertThat(new AiSecretCryptoService("").available()).isFalse();
        assertThat(new AiSecretCryptoService("not-base64").available()).isFalse();
        assertThat(new AiSecretCryptoService(Base64.getEncoder().encodeToString(new byte[16])).available()).isFalse();
        assertThatThrownBy(() -> new AiSecretCryptoService("").encrypt("secret"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("安全存储未启用");
    }
}
