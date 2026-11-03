package com.tbd.forkfront.companion;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Stores a service pairing token encrypted with AndroidKeystore AES-GCM. */
public final class CompanionCredentials {
  private static final String PREF = "companion_connection";
  private static final String ALIAS = "nethack_ai_companion_pairing_v1";
  private static final String URL_KEY = "https_endpoint";
  private static final String TOKEN_KEY = "encrypted_pairing_token";
  private static final String IV_KEY = "pairing_iv";
  private CompanionCredentials() {}

  private static SharedPreferences prefs(Context context) {
    return context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
  }

  public static boolean isValidEndpoint(String endpoint) {
    if (endpoint == null || endpoint.length() > 350) return false;
    try {
      Uri uri = Uri.parse(endpoint.trim());
      return "https".equalsIgnoreCase(uri.getScheme())
          && uri.getHost() != null && !uri.getHost().isEmpty()
          && uri.getUserInfo() == null
          && uri.getFragment() == null
          && uri.getPort() >= -1 && !endpoint.contains(" ");
    } catch (Exception e) { return false; }
  }

  public static String getEndpoint(Context context) {
    return prefs(context).getString(URL_KEY, "");
  }

  public static boolean isConfigured(Context context) {
    SharedPreferences p = prefs(context);
    return isValidEndpoint(p.getString(URL_KEY, ""))
        && p.contains(TOKEN_KEY) && p.contains(IV_KEY);
  }

  private static SecretKey getOrCreateKey() throws Exception {
    KeyStore store = KeyStore.getInstance("AndroidKeyStore");
    store.load(null);
    if (store.containsAlias(ALIAS)) {
      return ((KeyStore.SecretKeyEntry) store.getEntry(ALIAS, null)).getSecretKey();
    }
    KeyGenerator generator = KeyGenerator.getInstance(
        KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
    generator.init(new KeyGenParameterSpec.Builder(ALIAS,
        KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
        .setKeySize(256)
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setRandomizedEncryptionRequired(true)
        .build());
    return generator.generateKey();
  }

  public static void save(Context context, String endpoint, String pairingToken)
      throws Exception {
    if (!isValidEndpoint(endpoint)) throw new IllegalArgumentException("Use an HTTPS URL");
    if (pairingToken == null || pairingToken.trim().length() < 32)
      throw new IllegalArgumentException("Pairing token must be at least 32 characters");
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
    byte[] encrypted = cipher.doFinal(pairingToken.trim().getBytes(StandardCharsets.UTF_8));
    boolean written = prefs(context).edit()
        .putString(URL_KEY, endpoint.trim())
        .putString(TOKEN_KEY, Base64.encodeToString(encrypted, Base64.NO_WRAP))
        .putString(IV_KEY, Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
        .commit();
    if (!written) throw new IllegalStateException("Could not save connection settings");
  }

  public static String readToken(Context context) throws Exception {
    SharedPreferences p = prefs(context);
    byte[] iv = Base64.decode(p.getString(IV_KEY, ""), Base64.NO_WRAP);
    byte[] data = Base64.decode(p.getString(TOKEN_KEY, ""), Base64.NO_WRAP);
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(128, iv));
    return new String(cipher.doFinal(data), StandardCharsets.UTF_8);
  }

  public static void clear(Context context) {
    prefs(context).edit().clear().apply();
  }
}
