package com.garbageguard.app.notify

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.garbageguard.app.BuildConfig
import com.garbageguard.app.data.FirebaseConfig
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Where the Firebase settings live on the phone. The password is encrypted
 * with a key held in the Android Keystore, so it is never written to disk in
 * the clear and never leaves the device. Two sets are kept: what the user
 * last saved, and the last set that passed a test (the "working" set).
 */
object CloudStore {
    private const val PREFS = "gg_cloud"
    private const val KEY_ALIAS = "gg_cloud_key"
    private const val URLS = "local_urls"

    /** The team's tested set, from local.properties at build time. Empty when none was provided. */
    fun defaults() = FirebaseConfig(BuildConfig.FB_URL, BuildConfig.FB_KEY, BuildConfig.FB_EMAIL, BuildConfig.FB_PASSWORD)

    /** What to use: the saved set, or the built-in defaults when nothing has been saved. */
    fun current(context: Context): FirebaseConfig = read(context, "saved") ?: defaults()

    fun working(context: Context): FirebaseConfig? = read(context, "good")

    fun save(context: Context, cfg: FirebaseConfig, passedTest: Boolean) {
        write(context, "saved", cfg)
        if (passedTest) write(context, "good", cfg)
    }

    /** The Pi's local addresses, remembered so the way back to Wi-Fi is known even when Firebase is not reachable. */
    fun knownUrls(context: Context): List<String> =
        prefs(context).getString(URLS, "").orEmpty().split('\n').filter { it.isNotBlank() }

    fun rememberUrls(context: Context, urls: List<String>) {
        if (urls.isNotEmpty()) prefs(context).edit().putString(URLS, urls.distinct().joinToString("\n")).apply()
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun read(context: Context, slot: String): FirebaseConfig? {
        val p = prefs(context)
        val url = p.getString("${slot}_url", null) ?: return null
        return FirebaseConfig(
            url = url,
            key = p.getString("${slot}_key", "").orEmpty(),
            email = p.getString("${slot}_email", "").orEmpty(),
            password = open(p.getString("${slot}_pw", "").orEmpty()),
        )
    }

    private fun write(context: Context, slot: String, cfg: FirebaseConfig) {
        prefs(context).edit()
            .putString("${slot}_url", cfg.url.trim())
            .putString("${slot}_key", cfg.key.trim())
            .putString("${slot}_email", cfg.email.trim())
            .putString("${slot}_pw", seal(cfg.password))
            .apply()
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val maker = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        maker.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return maker.generateKey()
    }

    private fun seal(plain: String): String = runCatching {
        if (plain.isEmpty()) return ""
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val out = cipher.doFinal(plain.toByteArray())
        Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(out, Base64.NO_WRAP)
    }.getOrDefault("")

    private fun open(sealed: String): String = runCatching {
        val parts = sealed.split(':')
        if (parts.size != 2) return ""
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
        String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)))
    }.getOrDefault("")
}