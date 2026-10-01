package com.guyi.access.util;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encryption utility, compatible with PHP openssl_encrypt/openssl_decrypt.
 * PHP side: openssl_encrypt($json, 'aes-256-gcm', $key, OPENSSL_RAW_DATA, $iv, $tag)
 * Encrypted payload format: base64(iv + tag + ciphertext)
 */
public class AesUtil {

    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 16;
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    /** AppKey is a 64-character hex string, i.e. the 32 bytes AES-256 needs. */
    private static final int KEY_HEX_LENGTH = 64;

    public static String encrypt(String plaintext, String hexKey) throws Exception {
        byte[] key = hexStringToByteArray(hexKey);
        byte[] iv = new byte[GCM_IV_LENGTH];
        new SecureRandom().nextBytes(iv);

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);

        // Java appends the GCM authentication tag to the ciphertext output
        byte[] ciphertextWithTag = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        if (ciphertextWithTag.length < GCM_TAG_LENGTH) {
            throw new IllegalStateException("AES-GCM output shorter than the authentication tag");
        }
        int ciphertextLength = ciphertextWithTag.length - GCM_TAG_LENGTH;

        // Wire format is iv + tag + ciphertext (matches PHP openssl_encrypt clients); Java's
        // ciphertext||tag layout must therefore be re-split, not copied as-is.
        byte[] combined = new byte[GCM_IV_LENGTH + GCM_TAG_LENGTH + ciphertextLength];
        System.arraycopy(iv, 0, combined, 0, GCM_IV_LENGTH);
        System.arraycopy(ciphertextWithTag, ciphertextLength, combined, GCM_IV_LENGTH, GCM_TAG_LENGTH);
        System.arraycopy(ciphertextWithTag, 0, combined, GCM_IV_LENGTH + GCM_TAG_LENGTH, ciphertextLength);

        return Base64.getEncoder().encodeToString(combined);
    }

    public static String decrypt(String encryptedBase64, String hexKey) throws Exception {
        byte[] key = hexStringToByteArray(hexKey);
        byte[] combined = Base64.getDecoder().decode(encryptedBase64);
        if (combined.length < GCM_IV_LENGTH + GCM_TAG_LENGTH) {
            throw new IllegalArgumentException("Encrypted payload is too short");
        }

        byte[] iv = new byte[GCM_IV_LENGTH];
        System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);

        // Wire format is iv + tag + ciphertext
        byte[] tag = new byte[GCM_TAG_LENGTH];
        System.arraycopy(combined, GCM_IV_LENGTH, tag, 0, GCM_TAG_LENGTH);

        byte[] ciphertext = new byte[combined.length - GCM_IV_LENGTH - GCM_TAG_LENGTH];
        System.arraycopy(combined, GCM_IV_LENGTH + GCM_TAG_LENGTH, ciphertext, 0, ciphertext.length);

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);

        // Java's doFinal expects ciphertext||tag, so re-attach the tag
        byte[] ciphertextWithTag = new byte[ciphertext.length + GCM_TAG_LENGTH];
        System.arraycopy(ciphertext, 0, ciphertextWithTag, 0, ciphertext.length);
        System.arraycopy(tag, 0, ciphertextWithTag, ciphertext.length, GCM_TAG_LENGTH);

        byte[] plaintext = cipher.doFinal(ciphertextWithTag);
        return new String(plaintext, StandardCharsets.UTF_8);
    }

    private static byte[] hexStringToByteArray(String hex) {
        if (hex == null || hex.length() != KEY_HEX_LENGTH) {
            throw new IllegalArgumentException("AppKey 必须是 " + KEY_HEX_LENGTH + " 位十六进制字符串");
        }
        byte[] data = new byte[KEY_HEX_LENGTH / 2];
        for (int i = 0; i < KEY_HEX_LENGTH; i += 2) {
            int high = Character.digit(hex.charAt(i), 16);
            int low = Character.digit(hex.charAt(i + 1), 16);
            if (high < 0 || low < 0) {
                // Character.digit returns -1 for non-hex input, which used to be folded silently into
                // a wrong key and produce a payload no client could decrypt. Fail loudly instead so
                // the caller can exercise its plaintext fallback.
                throw new IllegalArgumentException("AppKey 含有非十六进制字符");
            }
            data[i / 2] = (byte) ((high << 4) + low);
        }
        return data;
    }
}
