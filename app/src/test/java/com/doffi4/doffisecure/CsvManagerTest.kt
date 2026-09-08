package com.doffi4.doffisecure

import com.doffi4.doffisecure.security.CsvManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test

class CsvManagerTest {

    @Test
    fun parseCsv_googlePasswordsFormat_success() {
        val csv = """
            name,url,username,password,note
            Google,https://accounts.google.com,user@gmail.com,secret123,my note
            GitHub,https://github.com,octocat,gitpass456,
        """.trimIndent()

        val passwords = CsvManager.parseCsv(csv)
        assertEquals(2, passwords.size)

        assertEquals("Google", passwords[0].service)
        assertEquals("user@gmail.com", passwords[0].username)
        assertEquals("secret123", passwords[0].password)
        assertEquals("https://accounts.google.com", passwords[0].url)

        assertEquals("GitHub", passwords[1].service)
        assertEquals("octocat", passwords[1].username)
        assertEquals("gitpass456", passwords[1].password)
    }

    @Test
    fun parseCsv_semicolonDelimiterAndRussianHeaders_success() {
        val csv = """
            сервис;ссылка;логин;пароль
            Яндекс;https://passport.yandex.ru;yandex_user;P@ssw0rd!
            ВКонтакте;https://vk.com;+380991234567;vkpass
        """.trimIndent()

        val passwords = CsvManager.parseCsv(csv)
        assertEquals(2, passwords.size)

        assertEquals("Яндекс", passwords[0].service)
        assertEquals("yandex_user", passwords[0].username)
        assertEquals("P@ssw0rd!", passwords[0].password)

        assertEquals("ВКонтакте", passwords[1].service)
        assertEquals("+380991234567", passwords[1].username)
        assertEquals("vkpass", passwords[1].password)
    }

    @Test
    fun parseCsv_utf8Bom_strippedSuccessfully() {
        val csvWithBom = "\uFEFFname,url,username,password\nGoogle,https://google.com,user,pass"
        val passwords = CsvManager.parseCsv(csvWithBom)
        assertEquals(1, passwords.size)
        assertEquals("Google", passwords[0].service)
        assertEquals("pass", passwords[0].password)
    }

    @Test
    fun parseCsv_multilineQuotedFields_handledCleanly() {
        val csv = """
            name,url,username,password,note
            ServiceA,https://a.com,userA,passA,"line 1
            line 2
            line 3"
            ServiceB,https://b.com,userB,passB,"single line"
        """.trimIndent()

        val passwords = CsvManager.parseCsv(csv)
        assertEquals(2, passwords.size)
        assertEquals("ServiceA", passwords[0].service)
        assertEquals("ServiceB", passwords[1].service)
    }

    @Test
    fun parseCsv_firefoxFormatWithoutServiceName_extractsDomain() {
        val csv = """
            url,username,password
            https://myaccount.google.com/security,user@google.com,p@ss123
            http://sub.example.org:8080/path,admin,secretAdmin
        """.trimIndent()

        val passwords = CsvManager.parseCsv(csv)
        assertEquals(2, passwords.size)
        assertEquals("myaccount.google.com", passwords[0].service)
        assertEquals("sub.example.org", passwords[1].service)
    }

    @Test
    fun parseStream_jsonBitwardenFormat_success() {
        val json = """
            {
              "items": [
                {
                  "name": "Bitwarden Account",
                  "login": {
                    "username": "user@vault.com",
                    "password": "strongPassword123",
                    "uris": [{"uri": "https://vault.bitwarden.com"}]
                  }
                }
              ]
            }
        """.trimIndent()

        val passwords = CsvManager.parseStream(json.byteInputStream(Charsets.UTF_8))
        assertEquals(1, passwords.size)
        assertEquals("Bitwarden Account", passwords[0].service)
        assertEquals("user@vault.com", passwords[0].username)
        assertEquals("strongPassword123", passwords[0].password)
    }

    @Test
    fun parseCsv_missingPasswordColumn_throwsException() {
        val csv = "name,url,username\nGoogle,https://google.com,user"
        assertThrows(IllegalArgumentException::class.java) {
            CsvManager.parseCsv(csv)
        }
    }

    @Test
    fun parseCsvVault_withPasskeyColumns_parsesPasswordsAndPasskeys() {
        val credId = java.util.Base64.getEncoder().encodeToString("test-credential-id-123".toByteArray())
        val privKey = java.util.Base64.getEncoder().encodeToString("test-private-key-bytes".toByteArray())
        val pubCose = java.util.Base64.getEncoder().encodeToString("test-public-cose-bytes".toByteArray())

        val csv = """
            service,username,password,url,createdAt,passkey_rp_id,passkey_credential_id,passkey_private_key,passkey_public_key_cose,passkey_algorithm,passkey_sign_count
            Google,user@gmail.com,secret123,https://accounts.google.com,1700000000000,google.com,$credId,$privKey,$pubCose,-7,5
            GitHub,octocat,,https://github.com,1700000001000,github.com,$credId,$privKey,$pubCose,-7,0
        """.trimIndent()

        val vaultData = CsvManager.parseCsvVault(csv)
        assertEquals(2, vaultData.passwords.size)
        assertEquals(2, vaultData.passkeys.size)

        // Password 1 has both password and passkey
        val p1 = vaultData.passwords[0]
        assertEquals("Google", p1.service)
        assertEquals("secret123", p1.password)

        val pk1 = vaultData.passkeys[0]
        assertEquals("google.com", pk1.rpId)
        assertEquals("user@gmail.com", pk1.userName)
        assertEquals("test-credential-id-123", String(pk1.credentialId))
        assertEquals(-7, pk1.algorithm)
        assertEquals(5L, pk1.signCount)

        // Password 2 is standalone passkey (empty password)
        val p2 = vaultData.passwords[1]
        assertEquals("GitHub", p2.service)
        assertEquals("", p2.password)
        assertEquals("https://github.com", p2.url)

        val pk2 = vaultData.passkeys[1]
        assertEquals("github.com", pk2.rpId)
        assertEquals("octocat", pk2.userName)
        assertEquals(0L, pk2.signCount)
    }

    @Test
    fun parseCsv_withPasskeyColumns_returnsPasswordsForLegacyCaller() {
        val credId = java.util.Base64.getEncoder().encodeToString("test-credential-id-123".toByteArray())
        val csv = """
            service,username,password,url,createdAt,passkey_rp_id,passkey_credential_id,passkey_private_key,passkey_public_key_cose,passkey_algorithm,passkey_sign_count
            Google,user@gmail.com,secret123,https://accounts.google.com,1700000000000,google.com,$credId,,,,
        """.trimIndent()

        val passwords = CsvManager.parseCsv(csv)
        assertEquals(1, passwords.size)
        assertEquals("Google", passwords[0].service)
        assertEquals("secret123", passwords[0].password)
    }

    @Test
    fun parseCsv_withTotpColumn_parsesTotpSuccessfully() {
        val csv = """
            service,username,password,url,createdAt,totp
            GitHub,octocat,gitpass123,https://github.com,1700000000000,JBSWY3DPEHPK3PXP
        """.trimIndent()

        val passwords = CsvManager.parseCsv(csv)
        assertEquals(1, passwords.size)
        assertEquals("GitHub", passwords[0].service)
        assertEquals("octocat", passwords[0].username)
        assertEquals("gitpass123", passwords[0].password)
        assertEquals("JBSWY3DPEHPK3PXP", passwords[0].totpSecret)
    }

    @Test
    fun parseCsv_withTotpSynonyms_parsesTotpCorrectly() {
        // Test synonyms: otp, 2fa, secret, authenticator
        val csvOtp = "name,username,password,otp\nService1,user1,pass1,SECRET_OTP_KEY"
        assertEquals("SECRET_OTP_KEY", CsvManager.parseCsv(csvOtp)[0].totpSecret)

        val csv2fa = "name,username,password,2fa\nService2,user2,pass2,SECRET_2FA_KEY"
        assertEquals("SECRET_2FA_KEY", CsvManager.parseCsv(csv2fa)[0].totpSecret)

        val csvSecret = "name,username,password,secret\nService3,user3,pass3,SECRET_KEY_3"
        assertEquals("SECRET_KEY_3", CsvManager.parseCsv(csvSecret)[0].totpSecret)

        val csvAuth = "name,username,password,authenticator\nService4,user4,pass4,AUTH_SECRET_4"
        assertEquals("AUTH_SECRET_4", CsvManager.parseCsv(csvAuth)[0].totpSecret)
    }

    @Test
    fun parseStream_bitwardenJsonWithTotp_success() {
        val json = """
            {
              "items": [
                {
                  "name": "Bitwarden Account",
                  "login": {
                    "username": "user@vault.com",
                    "password": "strongPassword123",
                    "uris": [{"uri": "https://vault.bitwarden.com"}],
                    "totp": "JBSWY3DPEHPK3PXP"
                  }
                }
              ]
            }
        """.trimIndent()

        val passwords = CsvManager.parseStream(json.byteInputStream(Charsets.UTF_8))
        assertEquals(1, passwords.size)
        assertEquals("Bitwarden Account", passwords[0].service)
        assertEquals("user@vault.com", passwords[0].username)
        assertEquals("strongPassword123", passwords[0].password)
        assertEquals("JBSWY3DPEHPK3PXP", passwords[0].totpSecret)
    }

    @Test
    fun parseStream_genericJsonArrayWithTotp_success() {
        val json = """
            [
              {
                "service": "Generic Service",
                "username": "gen_user",
                "password": "gen_password",
                "totp": "MY_GENERIC_TOTP_SECRET"
              }
            ]
        """.trimIndent()

        val passwords = CsvManager.parseStream(json.byteInputStream(Charsets.UTF_8))
        assertEquals(1, passwords.size)
        assertEquals("Generic Service", passwords[0].service)
        assertEquals("MY_GENERIC_TOTP_SECRET", passwords[0].totpSecret)
    }

    @Test
    fun parseCsv_bitwardenCsvFormatWithLoginTotp_success() {
        val csv = """
            folder,favorite,type,name,notes,fields,reprompt,login_uri,login_username,login_password,login_totp
            Social,0,login,Twitter,,0,0,https://twitter.com,tweetuser,tweetpass123,JBSWY3DPEHPK3PXP
        """.trimIndent()

        val passwords = CsvManager.parseCsv(csv)
        assertEquals(1, passwords.size)
        assertEquals("Twitter", passwords[0].service)
        assertEquals("tweetuser", passwords[0].username)
        assertEquals("tweetpass123", passwords[0].password)
        assertEquals("https://twitter.com", passwords[0].url)
        assertEquals("JBSWY3DPEHPK3PXP", passwords[0].totpSecret)
    }

    @Test
    fun parseCsv_keepassAnd1PasswordTotpSynonyms_success() {
        // KeePass TimeOtp-Secret-Base32
        val csvKeePass = """
            "Title","User Name","Password","URL","Notes","TimeOtp-Secret-Base32"
            "Server Access","admin","rootPass!","https://server.internal","","KEEPASS_TOTP_KEY"
        """.trimIndent()
        val kpPasswords = CsvManager.parseCsv(csvKeePass)
        assertEquals(1, kpPasswords.size)
        assertEquals("Server Access", kpPasswords[0].service)
        assertEquals("KEEPASS_TOTP_KEY", kpPasswords[0].totpSecret)

        // 1Password one-time password
        val csv1Pass = """
            Title,Username,Password,URL,one-time password
            Cloud,admin@cloud.com,cloudpass,https://cloud.com,1PASS_TOTP_KEY
        """.trimIndent()
        val onePassList = CsvManager.parseCsv(csv1Pass)
        assertEquals(1, onePassList.size)
        assertEquals("Cloud", onePassList[0].service)
        assertEquals("1PASS_TOTP_KEY", onePassList[0].totpSecret)
    }

    @Test
    fun parseStream_bitwardenJsonWithCreationDate_preservesTimestamp() {
        val json = """
            {
              "items": [
                {
                  "name": "Historical Account",
                  "creationDate": "2021-06-15T10:30:00.000Z",
                  "login": {
                    "username": "hist@vault.com",
                    "password": "histPassword",
                    "totp": "HISTORICAL_TOTP"
                  }
                }
              ]
            }
        """.trimIndent()

        val passwords = CsvManager.parseStream(json.byteInputStream(Charsets.UTF_8))
        assertEquals(1, passwords.size)
        assertEquals(java.time.Instant.parse("2021-06-15T10:30:00.000Z").toEpochMilli(), passwords[0].createdAt)
        assertEquals("HISTORICAL_TOTP", passwords[0].totpSecret)
    }
}
