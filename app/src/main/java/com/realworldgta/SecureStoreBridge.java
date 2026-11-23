package com.realworldgta;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.webkit.JavascriptInterface;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Stores local profile and conversations as AES-GCM ciphertext.
 * The AES key is generated inside Android Keystore and is not exportable.
 */
public final class SecureStoreBridge {
    private static final String KEY_ALIAS = "soulmate_ai_local_v1";
    private static final String PREFS = "soulmate_secure_store";
    private static final String DATA_KEY = "encrypted_state";
    private static final int MAX_STATE_BYTES = 2 * 1024 * 1024;

    private final SharedPreferences preferences;

    public SecureStoreBridge(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    @JavascriptInterface
    public synchronized String loadEncryptedState() {
        try {
            String encoded = preferences.getString(DATA_KEY, null);
            if (encoded == null || encoded.isEmpty()) return "";
            byte[] packed = Base64.decode(encoded, Base64.NO_WRAP);
            if (packed.length <= 12) return "";
            byte[] iv = new byte[12];
            byte[] cipherText = new byte[packed.length - 12];
            System.arraycopy(packed, 0, iv, 0, 12);
            System.arraycopy(packed, 12, cipherText, 0, cipherText.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (Exception error) {
            return "";
        }
    }

    @JavascriptInterface
    public synchronized boolean saveEncryptedState(String json) {
        if (json == null) return false;
        byte[] plain = json.getBytes(StandardCharsets.UTF_8);
        if (plain.length > MAX_STATE_BYTES) return false;
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
            byte[] iv = cipher.getIV();
            byte[] encrypted = cipher.doFinal(plain);
            byte[] packed = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, packed, 0, iv.length);
            System.arraycopy(encrypted, 0, packed, iv.length, encrypted.length);
            return preferences.edit()
                    .putString(DATA_KEY, Base64.encodeToString(packed, Base64.NO_WRAP))
                    .commit();
        } catch (Exception error) {
            return false;
        }
    }

    @JavascriptInterface
    public synchronized boolean clearEncryptedState() {
        return preferences.edit().remove(DATA_KEY).commit();
    }

    private SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (keyStore.containsAlias(KEY_ALIAS)) {
            return ((KeyStore.SecretKeyEntry) keyStore.getEntry(KEY_ALIAS, null)).getSecretKey();
        }

        KeyGenerator generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        return generator.generateKey();
    }
}
