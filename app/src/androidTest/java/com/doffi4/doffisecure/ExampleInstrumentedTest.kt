package com.doffi4.doffisecure

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.spec.MGF1ParameterSpec
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource

@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {


    @Test
    fun testRsaOaepWithSpec() {
        val alias = "test_rsa_with_spec"
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)

        val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore")
        kpg.initialize(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA1)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
                .setKeySize(2048)
                .build()
        )
        kpg.generateKeyPair()

        val pubKey = keyStore.getCertificate(alias).publicKey
        val privKey = keyStore.getKey(alias, null) as PrivateKey

        val plaintext = ByteArray(32) { (it + 5).toByte() }

        val spec = OAEPParameterSpec(
            "SHA-256",
            "MGF1",
            MGF1ParameterSpec.SHA1,
            PSource.PSpecified.DEFAULT
        )

        val encCipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
        encCipher.init(Cipher.ENCRYPT_MODE, pubKey, spec)
        val ciphertext = encCipher.doFinal(plaintext)

        val decCipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
        decCipher.init(Cipher.DECRYPT_MODE, privKey, spec)
        val decrypted = decCipher.doFinal(ciphertext)

        assertArrayEquals(plaintext, decrypted)
    }
}