package dev.cao.finch.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 凭据加密器（F.1）：用 Android Keystore 生成**不可导出**的 AES/GCM 密钥，敏感凭据加密后落盘。
 * 密文格式：`"v1:" + Base64(iv(12) || ciphertext+tag)`；不带前缀的值视为旧版明文，
 * 由 SettingsStore 惰性迁移（读到原文顺手加密回写）。
 * 健壮性约定：加密遇 Keystore 不可用抛异常交调用方降级（回写明文，下次读取再迁移）；
 * 解密失败（换机恢复出的密文、密钥丢失、被篡改）返回 null 交调用方清坏值——绝不崩溃。
 */
object CredentialCipher {
    private const val TAG = "CredentialCipher"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "finch_credential_key"
    private const val PREFIX = "v1:"
    private const val GCM_IV_BYTES = 12
    private const val GCM_TAG_BITS = 128

    private val lock = Any()

    /** 该值是否为本加密器产出的密文（统一 "v1:" 前缀区分新旧格式） */
    fun isEncrypted(raw: String): Boolean = raw.startsWith(PREFIX)

    /** 加密为 "v1:"+Base64(iv+ciphertext)；Keystore 不可用时抛异常，由调用方决定降级 */
    fun encrypt(plain: String): String = synchronized(lock) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv // Android Keystore GCM 固定 12 字节随机 IV
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        PREFIX + Base64.encodeToString(iv + ct, Base64.NO_WRAP)
    }

    /** 解密密文；格式不对 / 密钥丢失 / 校验失败返回 null（调用方清坏值），不抛不崩 */
    fun decrypt(payload: String): String? {
        if (!isEncrypted(payload)) return null
        return try {
            synchronized(lock) {
                val blob = Base64.decode(payload.substring(PREFIX.length), Base64.NO_WRAP)
                val iv = blob.copyOfRange(0, GCM_IV_BYTES)
                val ct = blob.copyOfRange(GCM_IV_BYTES, blob.size)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
                String(cipher.doFinal(ct), Charsets.UTF_8)
            }
        } catch (e: Exception) {
            Log.w(TAG, "凭据解密失败（密钥丢失或密文损坏）", e)
            null
        }
    }

    /** 取（或首用时生成）Keystore 内的 AES 密钥：不可导出、不落文件、GCM 随机 IV */
    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return gen.generateKey()
    }
}
