package com.guyi.access.util;

import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Locks down the wire format the existing PHP/SDK clients expect: base64(iv + tag + ciphertext).
 * Java's own GCM output is ciphertext||tag, so this test re-assembles the payload the way a PHP
 * client does (openssl tag separated from the ciphertext) and fails if the ordering regresses.
 */
class AesUtilTest {

    private static final String HEX_KEY =
            "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f";
    private static final int IV_LEN = 12;
    private static final int TAG_LEN = 16;

    /** Independent decoding of the documented layout, mimicking the PHP client side. */
    private static String decryptAsPhpClient(String payload, String hexKey) throws Exception {
        byte[] raw = Base64.getDecoder().decode(payload);
        byte[] iv = Arrays.copyOfRange(raw, 0, IV_LEN);
        byte[] tag = Arrays.copyOfRange(raw, IV_LEN, IV_LEN + TAG_LEN);
        byte[] ciphertext = Arrays.copyOfRange(raw, IV_LEN + TAG_LEN, raw.length);

        // PHP hands openssl_encrypt the plain ciphertext and the tag separately, so the client
        // reassembles ciphertext||tag before Java-style GCM decryption.
        byte[] ciphertextWithTag = new byte[ciphertext.length + TAG_LEN];
        System.arraycopy(ciphertext, 0, ciphertextWithTag, 0, ciphertext.length);
        System.arraycopy(tag, 0, ciphertextWithTag, ciphertext.length, TAG_LEN);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(hexToBytes(hexKey), "AES"),
                new GCMParameterSpec(TAG_LEN * 8, iv));
        return new String(cipher.doFinal(ciphertextWithTag), StandardCharsets.UTF_8);
    }

    @Test
    void encryptProducesPhpCompatibleLayout() throws Exception {
        String json = "{\"code\":200,\"msg\":\"OK\",\"data\":{\"expire_time\":\"2026-12-31 23:59:59\"}}";

        String payload = AesUtil.encrypt(json, HEX_KEY);

        assertEquals(json, decryptAsPhpClient(payload, HEX_KEY),
                "payload must be base64(iv + tag + ciphertext) for existing clients");
    }

    @Test
    void decryptRoundTripsOwnOutput() throws Exception {
        String json = "{\"code\":200,\"msg\":\"验证通过\"}";

        String payload = AesUtil.encrypt(json, HEX_KEY);

        assertEquals(json, AesUtil.decrypt(payload, HEX_KEY));
    }

    @Test
    void tagIsNotLeftAtTheEndOfThePayload() throws Exception {
        byte[] raw = Base64.getDecoder().decode(AesUtil.encrypt("{}", HEX_KEY));
        byte[] iv = Arrays.copyOfRange(raw, 0, IV_LEN);
        byte[] tag = Arrays.copyOfRange(raw, IV_LEN, IV_LEN + TAG_LEN);

        // Truncating the final ciphertext bytes must break authentication: if the tag were still
        // trailing the payload instead of sitting at offset 12, this would decrypt unnoticed.
        byte[] tampered = Arrays.copyOfRange(raw, 0, raw.length - 1);
        byte[] ciphertext = Arrays.copyOfRange(tampered, IV_LEN + TAG_LEN, tampered.length);
        byte[] ciphertextWithTag = new byte[ciphertext.length + TAG_LEN];
        System.arraycopy(ciphertext, 0, ciphertextWithTag, 0, ciphertext.length);
        System.arraycopy(tag, 0, ciphertextWithTag, ciphertext.length, TAG_LEN);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(hexToBytes(HEX_KEY), "AES"),
                new GCMParameterSpec(TAG_LEN * 8, iv));
        assertThrows(Exception.class, () -> cipher.doFinal(ciphertextWithTag));
    }

    @Test
    void rejectsTruncatedPayload() {
        assertFalse(Base64.getEncoder().encodeToString(new byte[]{1, 2, 3}).isEmpty());
        assertThrows(Exception.class, () -> AesUtil.decrypt(
                Base64.getEncoder().encodeToString(new byte[]{1, 2, 3}), HEX_KEY));
    }

    private static byte[] hexToBytes(String hex) {
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < hex.length(); i += 2) {
            out[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return out;
    }
}
