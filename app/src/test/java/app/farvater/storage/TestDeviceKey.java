package app.farvater.storage;

import java.security.GeneralSecurityException;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

@Implements(value = DeviceKey.class, isInAndroidSdk = false)
public class TestDeviceKey {
    private static SecretKey key;
    public static boolean unavailable;
    @Implementation
    protected static synchronized SecretKey get(boolean create) throws GeneralSecurityException {
        if (unavailable) throw new GeneralSecurityException("Test key unavailable");
        if (key == null && create) {
            KeyGenerator generator = KeyGenerator.getInstance("AES");
            generator.init(256);
            key = generator.generateKey();
        }
        if (key == null) throw new GeneralSecurityException("Missing test key");
        return key;
    }
}
