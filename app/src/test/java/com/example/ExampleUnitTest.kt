package com.example

import com.example.vpn.WireGuardConfigHelper
import com.wireguard.crypto.KeyPair
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class ExampleUnitTest {

    @Test
    fun `normalizeProductionHttpsUrl rejects localhost placeholders and http`() {
        assertNull(NexoraConfig.normalizeProductionHttpsUrl(""))
        assertNull(NexoraConfig.normalizeProductionHttpsUrl("   "))
        assertNull(NexoraConfig.normalizeProductionHttpsUrl("http://vpn.nexora.net"))
        assertNull(NexoraConfig.normalizeProductionHttpsUrl("https://localhost:8080"))
        assertNull(NexoraConfig.normalizeProductionHttpsUrl("https://127.0.0.1:8080"))
        assertNull(NexoraConfig.normalizeProductionHttpsUrl("https://10.0.2.2:8080"))
        assertNull(NexoraConfig.normalizeProductionHttpsUrl("https://api.yourdomain.com"))
        assertNull(NexoraConfig.normalizeProductionHttpsUrl("https://api.example.com/v1"))

        assertEquals(
            "https://api.nexora-prod.net",
            NexoraConfig.normalizeProductionHttpsUrl("  https://api.nexora-prod.net/  ")
        )
        assertTrue(NexoraConfig.isValidProductionHttpsUrl("https://control.nexora.sa"))
        assertFalse(NexoraConfig.isValidProductionHttpsUrl("http://control.nexora.sa"))
    }

    @Test
    fun `parseConfToProfile parses valid dual stack WireGuard conf`() {
        val clientKp = KeyPair()
        val serverKp = KeyPair()
        val conf = """
            [Interface]
            PrivateKey = ${clientKp.privateKey.toBase64()}
            Address = 10.8.0.10/32, fd80:4e58:5200::10/128
            DNS = 1.1.1.1, 2606:4700:4700::1111

            [Peer]
            PublicKey = ${serverKp.publicKey.toBase64()}
            Endpoint = 198.51.100.10:51820
            AllowedIPs = 0.0.0.0/0, ::/0
            PersistentKeepalive = 25
        """.trimIndent()

        val result = WireGuardConfigHelper.parseConfToProfile(
            ByteArrayInputStream(conf.toByteArray(Charsets.UTF_8)),
            "riyadh-sa01.conf"
        )
        assertTrue(result.isSuccess)
        val profile = result.getOrThrow()
        assertEquals("riyadh-sa01", profile.name)
        assertEquals("198.51.100.10:51820", profile.endpoint)
        assertEquals("198.51.100.10", profile.publicIp)
        assertEquals(51820, profile.serverPort)
        assertEquals(serverKp.publicKey.toBase64(), profile.serverPublicKey)
        assertEquals("10.8.0.10/32", profile.assignedAddress)
        assertEquals("fd80:4e58:5200:0:0:0:0:10/128", profile.assignedIpv6)
    }

    @Test
    fun `parseConfToProfile rejects missing endpoint`() {
        val clientKp = KeyPair()
        val serverKp = KeyPair()
        val conf = """
            [Interface]
            PrivateKey = ${clientKp.privateKey.toBase64()}
            Address = 10.8.0.10/32

            [Peer]
            PublicKey = ${serverKp.publicKey.toBase64()}
            AllowedIPs = 0.0.0.0/0
        """.trimIndent()

        val result = WireGuardConfigHelper.parseConfToProfile(
            ByteArrayInputStream(conf.toByteArray(Charsets.UTF_8)),
            "broken.conf"
        )
        assertTrue(result.isFailure)
    }

    @Test
    fun `buildWireGuardConfig enforces dual stack allowed IPs when leak protection is on`() {
        val clientKp = KeyPair()
        val serverKp = KeyPair()
        val conf = """
            [Interface]
            PrivateKey = ${clientKp.privateKey.toBase64()}
            Address = 10.8.0.12/32, fd80:4e58:5200::12/128
            DNS = 1.1.1.1

            [Peer]
            PublicKey = ${serverKp.publicKey.toBase64()}
            Endpoint = 198.51.100.20:51820
            AllowedIPs = 10.0.0.0/8
        """.trimIndent()

        val profile = WireGuardConfigHelper.parseConfToProfile(
            ByteArrayInputStream(conf.toByteArray(Charsets.UTF_8)),
            "test-node.conf"
        ).getOrThrow()

        val built = WireGuardConfigHelper.buildWireGuardConfig(
            profile = profile,
            privateKeyBase64 = clientKp.privateKey.toBase64(),
            assignedIpv4 = profile.assignedAddress,
            assignedIpv6 = profile.assignedIpv6,
            customDns = "1.1.1.1, 2606:4700:4700::1111",
            dnsLeakProtectionEnabled = true,
            ipv6LeakProtectionEnabled = true,
            mtu = 1420,
            excludedPackages = emptySet()
        )
        assertNotNull(built)
        val allowed = built.peers.first().allowedIps.map { it.toString() }
        assertTrue(allowed.contains("0.0.0.0/0"))
        assertTrue(allowed.any { it == "::/0" || it == "0:0:0:0:0:0:0:0/0" })
    }
}
