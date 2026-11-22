package app.farvater.storage;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

final class DeviceKey {
    private static final String PROVIDER = "AndroidKeyStore";
    private static final String ALIAS = "farvater.profiles.v1";

    private static SecretKey cached;
    private static java.io.File marker;

    static synchronized void initialize(java.io.File directory) {
        marker = new java.io.File(directory, "profile-key-v1");
    }

    private DeviceKey() {}

    static synchronized SecretKey get(boolean create) throws GeneralSecurityException {
        if (cached != null) return cached;
        KeyStore store = KeyStore.getInstance(PROVIDER);
        try {
            store.load(null);
        } catch (java.io.IOException e) {
            throw new GeneralSecurityException("Cannot open Android Keystore", e);
        }
        if (store.containsAlias(ALIAS)) {
            cached = (SecretKey) store.getKey(ALIAS, null);
            if (cached == null) throw new GeneralSecurityException("Profile key unavailable");
            return cached;
        }
        if (!create || (marker != null && marker.exists())) throw new GeneralSecurityException("Profile key unavailable");
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER);
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build());
        SecretKey key = generator.generateKey();
        if (marker != null) {
            try {
                marker.createNewFile();
            } catch (java.io.IOException error) {
                throw new GeneralSecurityException("Cannot record key creation", error);
            }
        }
        cached = key;
        return key;
    }
}
