package com.doffi4.doffisecure.security.webauthn

import com.doffi4.doffisecure.domain.model.Passkey
import com.doffi4.doffisecure.security.PasswordCrypto
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

/**
 * High-performance WebAuthn / FIDO2 cryptographic engine.
 * Handles:
 * - Generation and encryption of Passkey keypairs (ES256 / Ed25519)
 * - COSE_Key generation (CBOR)
 * - AttestationObject creation (fmt="none")
 * - AuthenticatorData generation and assertion signing (ECDSA P-256)
 */
class WebAuthnCryptoEngine(
    private val passwordCrypto: PasswordCrypto
) {

    companion object {
        const val ALG_ES256 = -7 // ECDSA with SHA-256 over P-256 curve
        const val ALG_EDDSA = -8 // EdDSA over Ed25519

        private const val FLAG_USER_PRESENT: Byte = 0x01
        private const val FLAG_USER_VERIFIED: Byte = 0x04
        private const val FLAG_ATTESTED_CREDENTIAL_DATA: Byte = 0x40

        fun sha256(data: ByteArray): ByteArray {
            return MessageDigest.getInstance("SHA-256").digest(data)
        }

        fun base64UrlEncode(bytes: ByteArray): String {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        }

        fun base64UrlDecode(str: String): ByteArray {
            return Base64.getUrlDecoder().decode(str.trim())
        }

        /**
         * Converts a BigInteger coordinate to an exact N-byte array,
         * stripping any leading sign byte (0x00) or padding with leading zeros.
         */
        fun toFixedLengthBytes(bigInt: BigInteger, length: Int): ByteArray {
            val raw = bigInt.toByteArray()
            if (raw.size == length) return raw
            val result = ByteArray(length)
            if (raw.size > length) {
                System.arraycopy(raw, raw.size - length, result, 0, length)
            } else {
                System.arraycopy(raw, 0, result, length - raw.size, raw.size)
            }
            return result
        }
    }

    /**
     * Generates a new Passkey keypair, encrypts the private key using the vault DEK,
     * and constructs the public COSE_Key structure.
     */
    fun generatePasskey(
        rpId: String,
        rpName: String,
        userId: ByteArray,
        userName: String,
        userDisplayName: String?,
        algorithm: Int = ALG_ES256,
        linkedPasswordId: Long? = null
    ): Passkey {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"), SecureRandom())
        val keyPair = kpg.generateKeyPair()

        val ecPublicKey = keyPair.public as ECPublicKey
        val ecPrivateKey = keyPair.private as ECPrivateKey

        val xBytes = toFixedLengthBytes(ecPublicKey.w.affineX, 32)
        val yBytes = toFixedLengthBytes(ecPublicKey.w.affineY, 32)

        // COSE_Key CBOR Map for ES256 (RFC 8152 / RFC 9052)
        // 1: 2 (kty: EC2)
        // 3: -7 (alg: ES256)
        // -1: 1 (crv: P-256)
        // -2: x coordinate (32 bytes)
        // -3: y coordinate (32 bytes)
        val coseKeyMap = linkedMapOf<Any, Any>(
            1 to 2,
            3 to ALG_ES256,
            -1 to 1,
            -2 to xBytes,
            -3 to yBytes
        )
        val publicKeyCose = CborEncoder.encode(coseKeyMap)

        // Encrypt the private key PKCS#8 bytes using vault envelope encryption
        val encryptedPrivateKey = passwordCrypto.encryptBytes(ecPrivateKey.encoded)

        // Generate a random 32-byte credentialId
        val credentialId = ByteArray(32).apply { SecureRandom().nextBytes(this) }

        return Passkey(
            id = 0,
            credentialId = credentialId,
            rpId = rpId,
            rpName = rpName,
            userId = userId,
            userName = userName,
            userDisplayName = userDisplayName,
            encryptedPrivateKey = encryptedPrivateKey,
            publicKeyCose = publicKeyCose,
            algorithm = algorithm,
            signCount = 0,
            linkedPasswordId = linkedPasswordId,
            createdAt = System.currentTimeMillis()
        )
    }

    /**
     * Builds the WebAuthn attestationObject (fmt="none") and authenticatorData
     * for a newly generated Passkey during registration.
     */
    fun createAttestation(
        rpId: String,
        passkey: Passkey
    ): AttestationResult {
        val rpIdHash = sha256(rpId.toByteArray(Charsets.UTF_8))
        val flags = (FLAG_USER_PRESENT.toInt() or FLAG_USER_VERIFIED.toInt() or FLAG_ATTESTED_CREDENTIAL_DATA.toInt()).toByte()
        val signCountBytes = ByteArray(4) // initial signCount is 0

        // Attested Credential Data:
        // AAGUID (16 bytes zeros for general software authenticator)
        val aaguid = ByteArray(16)
        val idLenBytes = byteArrayOf(
            ((passkey.credentialId.size ushr 8) and 0xFF).toByte(),
            (passkey.credentialId.size and 0xFF).toByte()
        )

        val authDataStream = ByteArrayOutputStream()
        authDataStream.write(rpIdHash)
        authDataStream.write(flags.toInt())
        authDataStream.write(signCountBytes)
        authDataStream.write(aaguid)
        authDataStream.write(idLenBytes)
        authDataStream.write(passkey.credentialId)
        authDataStream.write(passkey.publicKeyCose)

        val authenticatorData = authDataStream.toByteArray()

        // attestationObject is a CBOR Map: { "fmt": "none", "authData": bytes, "attStmt": {} }
        val attestationMap = linkedMapOf<String, Any>(
            "fmt" to "none",
            "authData" to authenticatorData,
            "attStmt" to emptyMap<String, Any>()
        )
        val attestationObject = CborEncoder.encode(attestationMap)

        return AttestationResult(
            authenticatorData = authenticatorData,
            attestationObject = attestationObject
        )
    }

    /**
     * Signs an authentication assertion (GetCredential / WebAuthn Assertion)
     * using the Passkey's decrypted private key.
     */
    fun createAssertion(
        rpId: String,
        passkey: Passkey,
        clientDataHash: ByteArray,
        newSignCount: Long
    ): AssertionResult {
        val rpIdHash = sha256(rpId.toByteArray(Charsets.UTF_8))
        val flags = (FLAG_USER_PRESENT.toInt() or FLAG_USER_VERIFIED.toInt()).toByte()

        val signCountBytes = byteArrayOf(
            ((newSignCount ushr 24) and 0xFF).toByte(),
            ((newSignCount ushr 16) and 0xFF).toByte(),
            ((newSignCount ushr 8) and 0xFF).toByte(),
            (newSignCount and 0xFF).toByte()
        )

        val authDataStream = ByteArrayOutputStream()
        authDataStream.write(rpIdHash)
        authDataStream.write(flags.toInt())
        authDataStream.write(signCountBytes)
        val authenticatorData = authDataStream.toByteArray()

        // Data to sign: authenticatorData || clientDataHash
        val dataToSign = ByteArray(authenticatorData.size + clientDataHash.size)
        System.arraycopy(authenticatorData, 0, dataToSign, 0, authenticatorData.size)
        System.arraycopy(clientDataHash, 0, dataToSign, authenticatorData.size, clientDataHash.size)

        // Decrypt private key
        val pkcs8Bytes = passwordCrypto.decryptBytes(passkey.encryptedPrivateKey)
        val keyFactory = KeyFactory.getInstance("EC")
        val privateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(pkcs8Bytes))

        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(privateKey)
        signature.update(dataToSign)
        val signatureBytes = signature.sign()

        return AssertionResult(
            authenticatorData = authenticatorData,
            signature = signatureBytes,
            userHandle = passkey.userId
        )
    }

    data class AttestationResult(
        val authenticatorData: ByteArray,
        val attestationObject: ByteArray
    )

    data class AssertionResult(
        val authenticatorData: ByteArray,
        val signature: ByteArray,
        val userHandle: ByteArray
    )
}
