package com.autulio.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class ScriptDraftSnapshot(
    val names: List<String>,
    val contents: Map<String, String>
)

class SecureScriptDraftStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        ENCRYPTED_PREFERENCES,
        Context.MODE_PRIVATE
    )
    private val legacyPreferences = context.applicationContext.getSharedPreferences(
        LEGACY_PREFERENCES,
        Context.MODE_PRIVATE
    )

    fun load(): ScriptDraftSnapshot {
        preferences.getString(DATA_KEY, null)?.let { encrypted ->
            return decryptSnapshot(encrypted)
        }

        val namesJson = runCatching {
            JSONArray(legacyPreferences.getString(LEGACY_NAMES_KEY, "[]") ?: "[]")
        }.getOrNull()
        val names = buildList<String> {
            if (namesJson != null) {
                for (index in 0 until namesJson.length()) {
                    val name = namesJson.optString(index).trim()
                    if (name.isNotEmpty() && none { it.equals(name, ignoreCase = true) }) {
                        add(name)
                    }
                }
            }
        }
        val contents = linkedMapOf<String, String>()
        legacyPreferences.all.forEach { (key, value) ->
            if (key.startsWith(LEGACY_DRAFT_PREFIX) && value is String) {
                contents[key.removePrefix(LEGACY_DRAFT_PREFIX)] = value
            }
        }
        val snapshot = ScriptDraftSnapshot(names, contents)

        if (legacyPreferences.all.isNotEmpty()) {
            writeEncrypted(snapshot, synchronous = true)
            legacyPreferences.edit().clear().commit()
        }
        return snapshot
    }

    fun save(names: List<String>, contents: Map<String, String>) {
        writeEncrypted(ScriptDraftSnapshot(names, contents), synchronous = false)
    }

    private fun writeEncrypted(snapshot: ScriptDraftSnapshot, synchronous: Boolean) {
        val root = JSONObject()
        root.put("names", JSONArray(snapshot.names))
        val drafts = JSONObject()
        snapshot.contents.forEach { (name, code) -> drafts.put(name, code) }
        root.put("contents", drafts)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        cipher.updateAAD(ASSOCIATED_DATA)
        val encrypted = cipher.doFinal(root.toString().toByteArray(Charsets.UTF_8))
        val iv = cipher.iv
        require(iv.size == GCM_IV_LENGTH_BYTES)

        val payload = ByteArray(iv.size + encrypted.size)
        System.arraycopy(iv, 0, payload, 0, iv.size)
        System.arraycopy(encrypted, 0, payload, iv.size, encrypted.size)
        val encoded = Base64.encodeToString(payload, Base64.NO_WRAP)
        val editor = preferences.edit().putString(DATA_KEY, encoded)
        if (synchronous) {
            check(editor.commit()) { "Unable to persist encrypted script drafts." }
        } else {
            editor.apply()
        }
    }

    private fun decryptSnapshot(encoded: String): ScriptDraftSnapshot {
        val payload = Base64.decode(encoded, Base64.NO_WRAP)
        require(payload.size > GCM_IV_LENGTH_BYTES + GCM_TAG_LENGTH_BYTES / 8)

        val iv = payload.copyOfRange(0, GCM_IV_LENGTH_BYTES)
        val ciphertext = payload.copyOfRange(GCM_IV_LENGTH_BYTES, payload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        )
        cipher.updateAAD(ASSOCIATED_DATA)
        val root = JSONObject(cipher.doFinal(ciphertext).toString(Charsets.UTF_8))

        val namesJson = root.optJSONArray("names") ?: JSONArray()
        val names = buildList<String> {
            for (index in 0 until namesJson.length()) {
                val name = namesJson.optString(index).trim()
                if (name.isNotEmpty() && none { it.equals(name, ignoreCase = true) }) {
                    add(name)
                }
            }
        }
        val draftsJson = root.optJSONObject("contents") ?: JSONObject()
        val contents = linkedMapOf<String, String>()
        val keys = draftsJson.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            contents[key] = draftsJson.optString(key, "")
        }
        return ScriptDraftSnapshot(names, contents)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "autulio_script_drafts_aes_gcm_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
        const val GCM_TAG_LENGTH_BYTES = GCM_TAG_LENGTH_BITS / 8
        const val GCM_IV_LENGTH_BYTES = 12
        val ASSOCIATED_DATA = "AUTU-LIO|script-drafts|v1".toByteArray(Charsets.UTF_8)
        const val ENCRYPTED_PREFERENCES = "autulio_encrypted_admin_drafts"
        const val LEGACY_PREFERENCES = "autulio_admin_drafts"
        const val DATA_KEY = "encrypted_payload_v1"
        const val LEGACY_NAMES_KEY = "scripts"
        const val LEGACY_DRAFT_PREFIX = "draft:"
    }
}
