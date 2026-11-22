package app.farvater.storage;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class ProfileCipher {
    private static final byte VERSION = 1;
    private static final int IV_BYTES = 12;
    private static final int TAG_BYTES = 16;
    private static final int KEY_BYTES = 32;
    private static final int HEADER_BYTES = 1 + IV_BYTES + KEY_BYTES + TAG_BYTES + IV_BYTES;
    private static final String AES_GCM = "AES/GCM/NoPadding";
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final String SETTINGS = "pref/settings";
    private static byte[] settingsHeader;
    private static SecretKey settingsDataKey;

    private ProfileCipher() {}

    public static void initialize(java.io.File directory) {
        DeviceKey.initialize(directory);
    }

    public static byte[] seal(String label, byte[] clear) throws GeneralSecurityException {
        byte[] aad = associatedData(label);
        if (SETTINGS.equals(label)) {
            synchronized (ProfileCipher.class) {
                if (settingsDataKey == null) {
                    byte[] key = new byte[KEY_BYTES];
                    try {
                        RANDOM.nextBytes(key);
                        settingsHeader = wrap(aad, key);
                        settingsDataKey = new SecretKeySpec(key, "AES");
                    } finally {
                        Arrays.fill(key, (byte) 0);
                    }
                }
                return encrypt(clear, aad, settingsHeader, settingsDataKey);
            }
        }
        byte[] key = new byte[KEY_BYTES];
        try {
            RANDOM.nextBytes(key);
            return encrypt(clear, aad, wrap(aad, key), new SecretKeySpec(key, "AES"));
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }

    private static byte[] wrap(byte[] aad, byte[] key) throws GeneralSecurityException {
        Cipher wrapper = encryptor(DeviceKey.get(true), aad);
        return ByteBuffer.allocate(HEADER_BYTES - IV_BYTES).put(VERSION)
                .put(wrapper.getIV()).put(wrapper.doFinal(key)).array();
    }

    private static byte[] encrypt(byte[] clear, byte[] aad, byte[] header, SecretKey key)
            throws GeneralSecurityException {
        Cipher data = encryptor(key, aad);
        byte[] out = new byte[Math.addExact(HEADER_BYTES, data.getOutputSize(clear.length))];
        ByteBuffer.wrap(out).put(header).put(data.getIV());
        data.doFinal(clear, 0, clear.length, out, HEADER_BYTES);
        return out;
    }

    public static byte[] open(String label, byte[] sealed) throws GeneralSecurityException {
        if (sealed.length < HEADER_BYTES + TAG_BYTES || sealed[0] != VERSION) {
            throw new GeneralSecurityException("Invalid encrypted profile");
        }
        byte[] aad = associatedData(label);
        Cipher wrapper = decryptor(DeviceKey.get(false), aad, sealed, 1);
        byte[] key = wrapper.doFinal(sealed, 1 + IV_BYTES, KEY_BYTES + TAG_BYTES);
        try {
            SecretKey dataKey = new SecretKeySpec(key, "AES");
            Cipher data = decryptor(dataKey, aad, sealed, HEADER_BYTES - IV_BYTES);
            byte[] clear = data.doFinal(sealed, HEADER_BYTES, sealed.length - HEADER_BYTES);
            if (SETTINGS.equals(label)) {
                synchronized (ProfileCipher.class) {
                    settingsDataKey = dataKey;
                    settingsHeader = Arrays.copyOf(sealed, HEADER_BYTES - IV_BYTES);
                }
            }
            return clear;
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }

    private static byte[] associatedData(String label) {
        return ("farvater/profiles/v1/" + label).getBytes(StandardCharsets.UTF_8);
    }

    private static Cipher encryptor(SecretKey key, byte[] aad) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(AES_GCM);
        cipher.init(Cipher.ENCRYPT_MODE, key);
        if (cipher.getIV().length != IV_BYTES) throw new GeneralSecurityException("Invalid GCM nonce");
        cipher.updateAAD(aad);
        return cipher;
    }

    private static Cipher decryptor(SecretKey key, byte[] aad, byte[] sealed, int offset)
            throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(AES_GCM);
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, sealed, offset, IV_BYTES));
        cipher.updateAAD(aad);
        return cipher;
    }
}
