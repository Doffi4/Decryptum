package com.doffi4.doffisecure.domain.model

import org.junit.Assert.*
import org.junit.Test

class DomainUtilsTest {

    @Test
    fun `test url normalization removes scheme, ports, paths and parameters`() {
        val parsed = DomainUtils.parse("https://user:secret@client.roblox.com:8080/login/oauth?redirect_uri=abc#anchor")
        assertEquals("client.roblox.com", parsed.host)
        assertEquals("roblox.com", parsed.apexDomain)
        assertFalse(parsed.isLocalNetwork)
    }

    @Test
    fun `test url-encoded string decoding`() {
        val parsed = DomainUtils.parse("https://my%20domain.com/path%20test")
        assertEquals("my domain.com", parsed.host)
    }

    @Test
    fun `test apex domain extraction for multi-subdomains`() {
        val p1 = DomainUtils.parse("anixartd.swiftsoft.com")
        assertEquals("anixartd.swiftsoft.com", p1.host)
        assertEquals("swiftsoft.com", p1.apexDomain)

        val p2 = DomainUtils.parse("deep.sub.api.github.com")
        assertEquals("deep.sub.api.github.com", p2.host)
        assertEquals("github.com", p2.apexDomain)
    }

    @Test
    fun `test compound ccTLD apex domain resolution`() {
        val p1 = DomainUtils.parse("https://auth.service.co.uk:443")
        assertEquals("auth.service.co.uk", p1.host)
        assertEquals("service.co.uk", p1.apexDomain)

        val p2 = DomainUtils.parse("sub.portal.spb.ru")
        assertEquals("sub.portal.spb.ru", p2.host)
        assertEquals("portal.spb.ru", p2.apexDomain)

        val p3 = DomainUtils.parse("service.co.uk")
        assertEquals("service.co.uk", p3.host)
        assertNull("Already apex domain, so apexDomain should be null", p3.apexDomain)
    }

    @Test
    fun `test root domain returns null apexDomain`() {
        val p1 = DomainUtils.parse("roblox.com")
        assertEquals("roblox.com", p1.host)
        assertNull(p1.apexDomain)

        val p2 = DomainUtils.parse("google.com")
        assertEquals("google.com", p2.host)
        assertNull(p2.apexDomain)
    }

    @Test
    fun `test local network addresses detection`() {
        assertTrue(DomainUtils.parse("192.168.1.1").isLocalNetwork)
        assertTrue(DomainUtils.parse("http://192.168.0.254:8080/admin").isLocalNetwork)
        assertTrue(DomainUtils.parse("10.0.0.1").isLocalNetwork)
        assertTrue(DomainUtils.parse("172.16.5.10").isLocalNetwork)
        assertTrue(DomainUtils.parse("172.31.255.1").isLocalNetwork)
        assertFalse(DomainUtils.parse("172.32.1.1").isLocalNetwork) // Outside class B
        assertTrue(DomainUtils.parse("127.0.0.1").isLocalNetwork)
        assertTrue(DomainUtils.parse("localhost").isLocalNetwork)
        assertTrue(DomainUtils.parse("http://localhost:3000/").isLocalNetwork)
        assertTrue(DomainUtils.parse("my-home-server.local").isLocalNetwork)
        assertTrue(DomainUtils.parse("router.lan").isLocalNetwork)
        assertTrue(DomainUtils.parse("router").isLocalNetwork)
    }

    @Test
    fun `test empty and blank input`() {
        val p1 = DomainUtils.parse(null)
        assertEquals("", p1.host)
        assertNull(p1.apexDomain)
        assertFalse(p1.isLocalNetwork)

        val p2 = DomainUtils.parse("   ")
        assertEquals("", p2.host)
        assertNull(p2.apexDomain)
        assertFalse(p2.isLocalNetwork)
    }
}
