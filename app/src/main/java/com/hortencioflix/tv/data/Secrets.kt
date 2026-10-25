package com.hortencioflix.tv.data

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Guarda chaves e tokens cifrados com uma chave que fica no Android Keystore (não sai do aparelho). */
object Secrets {
    private const val ALIAS = "hortencioflix_secrets"
    private const val PREFIX = "enc1:"

    private fun key(): SecretKey? {
        if (Build.VERSION.SDK_INT < 23) return null
        return try {
            val ks = KeyStore.getInstance("AndroidKeyStore")
            ks.load(null)
            (ks.getKey(ALIAS, null) as? SecretKey) ?: run {
                val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
                g.init(
                    KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build()
                )
                g.generateKey()
            }
        } catch (e: Throwable) { null }
    }

    fun isEncrypted(s: String) = s.startsWith(PREFIX)

    /** Cifra o texto; se o aparelho não suportar, devolve o texto como está. */
    fun encrypt(plain: String): String {
        if (plain.isEmpty() || isEncrypted(plain)) return plain
        val k = key() ?: return plain
        return try {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.ENCRYPT_MODE, k)
            PREFIX + Hex.enc(c.iv + c.doFinal(plain.toByteArray(Charsets.UTF_8)))
        } catch (e: Throwable) { plain }
    }

    /** Decifra; texto antigo (sem cifra) passa tal e qual. Se a chave se perdeu, devolve vazio. */
    fun decrypt(stored: String): String {
        if (!isEncrypted(stored)) return stored
        val k = key() ?: return ""
        return try {
            val raw = Hex.dec(stored.removePrefix(PREFIX))
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(128, raw, 0, 12))
            String(c.doFinal(raw, 12, raw.size - 12), Charsets.UTF_8)
        } catch (e: Throwable) { "" }
    }
}

object Hex {
    fun enc(b: ByteArray): String {
        val sb = StringBuilder(b.size * 2)
        for (x in b) { val v = x.toInt() and 0xff; sb.append("0123456789abcdef"[v shr 4]).append("0123456789abcdef"[v and 15]) }
        return sb.toString()
    }
    fun dec(s: String): ByteArray = ByteArray(s.length / 2) { ((Character.digit(s[it * 2], 16) shl 4) or Character.digit(s[it * 2 + 1], 16)).toByte() }
}

/** Cópia de segurança protegida por palavra-passe (PBKDF2 + AES-GCM). Funciona em qualquer aparelho. */
object BackupCrypto {
    private const val ITER = 120_000

    private fun derive(pass: String, salt: ByteArray): javax.crypto.spec.SecretKeySpec {
        val f = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        val k = f.generateSecret(javax.crypto.spec.PBEKeySpec(pass.toCharArray(), salt, ITER, 256))
        return javax.crypto.spec.SecretKeySpec(k.encoded, "AES")
    }

    fun encrypt(json: String, pass: String): String {
        val rnd = java.security.SecureRandom()
        val salt = ByteArray(16).also { rnd.nextBytes(it) }
        val iv = ByteArray(12).also { rnd.nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, derive(pass, salt), GCMParameterSpec(128, iv))
        val data = c.doFinal(json.toByteArray(Charsets.UTF_8))
        return org.json.JSONObject().put("app", "hortencioflix").put("enc", 1)
            .put("salt", Hex.enc(salt)).put("iv", Hex.enc(iv)).put("data", Hex.enc(data)).toString()
    }

    /** Devolve o JSON original, ou null se a palavra-passe estiver errada. */
    fun decrypt(wrapper: org.json.JSONObject, pass: String): String? = try {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, derive(pass, Hex.dec(wrapper.getString("salt"))), GCMParameterSpec(128, Hex.dec(wrapper.getString("iv"))))
        String(c.doFinal(Hex.dec(wrapper.getString("data"))), Charsets.UTF_8)
    } catch (e: Exception) { null }
}
