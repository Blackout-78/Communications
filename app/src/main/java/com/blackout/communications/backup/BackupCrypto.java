package com.blackout.communications.backup;

import java.io.ByteArrayOutputStream;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Passphrase encryption for backup files. Layout: "CMBK1" + 16-byte salt + 12-byte IV + AES-256-GCM
 * ciphertext (with its 16-byte tag). The key comes from the passphrase with PBKDF2-HMAC-SHA256.
 * A wrong passphrase or a changed file fails the GCM tag check, so it is never read as data.
 */
final class BackupCrypto {
    private static final byte[] MAGIC = {'C', 'M', 'B', 'K', '1'};
    private static final int SALT = 16, IV = 12, ITERATIONS = 310_000;
    static final int MIN_PASSPHRASE = 8;

    private BackupCrypto() {}

    static boolean isEncrypted(byte[] data) {
        if (data.length < MAGIC.length + SALT + IV + 16) return false;
        for (int i = 0; i < MAGIC.length; i++) if (data[i] != MAGIC[i]) return false;
        return true;
    }

    static byte[] encrypt(byte[] plain, char[] pass) throws GeneralSecurityException {
        SecureRandom rnd = new SecureRandom();
        byte[] salt = new byte[SALT], iv = new byte[IV];
        rnd.nextBytes(salt);
        rnd.nextBytes(iv);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, key(pass, salt), new GCMParameterSpec(128, iv));
        c.updateAAD(MAGIC);
        byte[] ct = c.doFinal(plain);
        ByteArrayOutputStream out = new ByteArrayOutputStream(MAGIC.length + SALT + IV + ct.length);
        out.write(MAGIC, 0, MAGIC.length);
        out.write(salt, 0, SALT);
        out.write(iv, 0, IV);
        out.write(ct, 0, ct.length);
        return out.toByteArray();
    }

    /** Returns null when the passphrase is wrong or the file was changed. */
    static byte[] decrypt(byte[] data, char[] pass) {
        try {
            if (!isEncrypted(data)) return null;
            byte[] salt = Arrays.copyOfRange(data, MAGIC.length, MAGIC.length + SALT);
            byte[] iv = Arrays.copyOfRange(data, MAGIC.length + SALT, MAGIC.length + SALT + IV);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key(pass, salt), new GCMParameterSpec(128, iv));
            c.updateAAD(MAGIC);
            return c.doFinal(data, MAGIC.length + SALT + IV, data.length - MAGIC.length - SALT - IV);
        } catch (GeneralSecurityException e) {
            return null;
        }
    }

    private static SecretKeySpec key(char[] pass, byte[] salt) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(pass, salt, ITERATIONS, 256);
        try {
            byte[] k = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return new SecretKeySpec(k, "AES");
        } finally {
            spec.clearPassword();
        }
    }
}
