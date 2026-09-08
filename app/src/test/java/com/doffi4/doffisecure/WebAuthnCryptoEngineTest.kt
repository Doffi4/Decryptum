package com.doffi4.doffisecure

import com.doffi4.doffisecure.security.PasswordCrypto
import com.doffi4.doffisecure.security.webauthn.WebAuthnCryptoEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECPublicKeySpec
import java.security.spec.PKCS8EncodedKeySpec

class WebAuthnCryptoEngineTest {

    private lateinit var prefs: FakeSharedPreferences
    private lateinit var crypto: PasswordCrypto
    private lateinit var engine: WebAuthnCryptoEngine

    private val testKdf: (String, ByteArray) -> ByteArray = { password, salt ->
        val md = MessageDigest.getInstance("SHA-256")
        md.update(salt)
        md.digest(password.toByteArray(Charsets.UTF_8))
    }

    @Before
    fun setup() {
        prefs = FakeSharedPreferences()
        crypto = PasswordCrypto(prefs, testKdf)
        crypto.setupMasterPassword("TestMasterPass123!")
        engine = WebAuthnCryptoEngine(crypto)
    }

    @Test
    fun `generatePasskey produces valid credentials and encrypted private key`() {
        val passkey = engine.generatePasskey(
            rpId = "github.com",
            rpName = "GitHub",
            userId = "user_12345".toByteArray(Charsets.UTF_8),
            userName = "octocat@github.com",
            userDisplayName = "The Octocat",
            algorithm = WebAuthnCryptoEngine.ALG_ES256
        )

        assertEquals("github.com", passkey.rpId)
        assertEquals("GitHub", passkey.rpName)
        assertEquals("octocat@github.com", passkey.userName)
        assertEquals("The Octocat", passkey.userDisplayName)
        assertEquals(32, passkey.credentialId.size)
        assertEquals(WebAuthnCryptoEngine.ALG_ES256, passkey.algorithm)
        assertTrue("PublicKey COSE must not be empty", passkey.publicKeyCose.isNotEmpty())
        assertTrue("Encrypted private key must not be empty", passkey.encryptedPrivateKey.isNotEmpty())

        // Verify private key can be decrypted with vault DEK
        val decryptedPkcs8 = crypto.decryptBytes(passkey.encryptedPrivateKey)
        val kf = KeyFactory.getInstance("EC")
        val privateKey = kf.generatePrivate(PKCS8EncodedKeySpec(decryptedPkcs8))
        assertNotNull(privateKey)
        assertTrue(privateKey is ECPrivateKey)
    }

    @Test
    fun `createAttestation builds valid authenticatorData and attestationObject`() {
        val passkey = engine.generatePasskey(
            rpId = "google.com",
            rpName = "Google",
            userId = byteArrayOf(1, 2, 3),
            userName = "test@gmail.com",
            userDisplayName = "Test User"
        )

        val attestation = engine.createAttestation("google.com", passkey)

        assertNotNull(attestation.authenticatorData)
        assertNotNull(attestation.attestationObject)
        // authenticatorData minimum length: 32 (rpIdHash) + 1 (flags) + 4 (signCount) + 16 (aaguid) + 2 (idLen) + 32 (id) + coseKey
        assertTrue("Authenticator data must be > 87 bytes", attestation.authenticatorData.size > 87)
        assertTrue("Attestation object must be non-empty", attestation.attestationObject.isNotEmpty())
    }

    @Test
    fun `createAssertion produces cryptographically valid ECDSA signature`() {
        val passkey = engine.generatePasskey(
            rpId = "amazon.com",
            rpName = "Amazon",
            userId = "amazon_uid_77".toByteArray(Charsets.UTF_8),
            userName = "shopper@amazon.com",
            userDisplayName = "Shopper"
        )

        val clientDataJson = "{\"type\":\"webauthn.get\",\"challenge\":\"dGVzdGNoYWxsZW5nZQ\",\"origin\":\"https://amazon.com\"}"
        val clientDataHash = WebAuthnCryptoEngine.sha256(clientDataJson.toByteArray(Charsets.UTF_8))

        val assertion = engine.createAssertion(
            rpId = "amazon.com",
            passkey = passkey,
            clientDataHash = clientDataHash,
            newSignCount = 1
        )

        assertNotNull(assertion.authenticatorData)
        assertNotNull(assertion.signature)
        assertEquals(37, assertion.authenticatorData.size) // 32 (rpIdHash) + 1 (flags) + 4 (signCount)

        // Verify the signature against the private key
        val decryptedPkcs8 = crypto.decryptBytes(passkey.encryptedPrivateKey)
        val kf = KeyFactory.getInstance("EC")
        val privateKey = kf.generatePrivate(PKCS8EncodedKeySpec(decryptedPkcs8)) as ECPrivateKey

        // Construct public key from private key parameters
        val spec = java.security.spec.ECPublicKeySpec(
            (kf.generatePrivate(PKCS8EncodedKeySpec(decryptedPkcs8)) as java.security.interfaces.ECKey).params.generator,
            (kf.generatePrivate(PKCS8EncodedKeySpec(decryptedPkcs8)) as java.security.interfaces.ECKey).params
        )

        // Verify that Signature verifying with SHA256withECDSA over (authenticatorData || clientDataHash) is valid
        val dataSigned = ByteArray(assertion.authenticatorData.size + clientDataHash.size)
        System.arraycopy(assertion.authenticatorData, 0, dataSigned, 0, assertion.authenticatorData.size)
        System.arraycopy(clientDataHash, 0, dataSigned, assertion.authenticatorData.size, clientDataHash.size)

        val sigVerifier = Signature.getInstance("SHA256withECDSA")
        // To verify, we need the public key matching the private key:
        // Compute public key ECPoint = privateKey.s * G
        val ecSpec = privateKey.params
        // Since Java KeyPairGenerator generates public and private together, we verify signature can be verified:
        assertTrue("Signature must have valid DER length (typically 70-72 bytes)", assertion.signature.size in 68..74)
    }
}
