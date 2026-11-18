package com.example.vpn

import com.example.data.local.ServerProfileEntity
import com.wireguard.config.Config
import com.wireguard.config.Interface
import com.wireguard.config.Peer
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.UUID

object WireGuardConfigHelper {

    private const val STRICT_DUAL_STACK_DNS =
        "1.1.1.1, 1.0.0.1, 2606:4700:4700::1111, 2606:4700:4700::1001"

    fun parseConfToProfile(
        inputStream: InputStream,
        fileName: String
    ): Result<ServerProfileEntity> {
        return try {
            val rawText = inputStream.bufferedReader().use { it.readText() }
            val parsed = Config.parse(ByteArrayInputStream(rawText.toByteArray(Charsets.UTF_8)))
            val peer = parsed.peers.firstOrNull()
                ?: return Result.failure(IllegalArgumentException("WireGuard .conf contains no [Peer] section"))
            val endpoint = peer.endpoint.map { it.toString() }.orElse("Unknown Endpoint")
            val dnsList = parsed.`interface`.dnsServers.joinToString(", ") { it.hostAddress ?: "" }
                .ifBlank { STRICT_DUAL_STACK_DNS }
            val allAddrs = parsed.`interface`.addresses.map { it.toString() }
            val v4 = allAddrs.firstOrNull { !it.contains(":") } ?: "10.8.0.2/32"
            val v6 = allAddrs.firstOrNull { it.contains(":") } ?: "fd80:4e58:5200::2/128"
            val cleanName = fileName.removeSuffix(".conf").ifBlank { "Imported WireGuard" }

            Result.success(
                ServerProfileEntity(
                    id = "conf_${UUID.randomUUID()}",
                    nodeCode = "CONF",
                    name = cleanName,
                    countryName = "WireGuard Config",
                    countryCode = "WG",
                    city = endpoint.substringBefore(':'),
                    endpoint = endpoint,
                    serverPublicKey = peer.publicKey.toBase64(),
                    dnsServers = dnsList,
                    tier = "free",
                    loadPercent = 0,
                    sourceType = "IMPORTED_CONF",
                    healthStatus = "local",
                    rawConfig = rawText,
                    assignedAddress = v4,
                    assignedIpv6 = v6,
                    allowedIps = peer.allowedIps.joinToString(", ") { it.toString() }
                        .ifBlank { "0.0.0.0/0, ::/0" }
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun buildWireGuardConfig(
        profile: ServerProfileEntity,
        privateKeyBase64: String,
        assignedIpv4: String,
        assignedIpv6: String,
        customDns: String,
        dnsLeakProtectionEnabled: Boolean,
        mtu: Int,
        excludedPackages: Set<String>,
        wgQuickConfigFromBackend: String? = null
    ): Config {
        val effectiveDns = if (dnsLeakProtectionEnabled) {
            customDns.ifBlank { profile.dnsServers.ifBlank { STRICT_DUAL_STACK_DNS } }
        } else {
            customDns.ifBlank { profile.dnsServers }
        }

        if (!wgQuickConfigFromBackend.isNullOrBlank()) {
            val injectedRaw = injectPrivateKeyIntoWgQuick(wgQuickConfigFromBackend, privateKeyBase64)
            val parsedResult = runCatching {
                Config.parse(ByteArrayInputStream(injectedRaw.toByteArray(Charsets.UTF_8)))
            }.getOrNull()
            if (parsedResult != null && parsedResult.peers.isNotEmpty()) {
                return applyPolicyOverrides(
                    baseConfig = parsedResult,
                    fallbackPrivateKey = privateKeyBase64,
                    effectiveDns = effectiveDns,
                    dnsLeakProtectionEnabled = dnsLeakProtectionEnabled,
                    mtu = mtu,
                    excludedPackages = excludedPackages
                )
            }
        }

        if (profile.sourceType == "IMPORTED_CONF" && profile.rawConfig.isNotBlank()) {
            val baseConfig = Config.parse(ByteArrayInputStream(profile.rawConfig.toByteArray(Charsets.UTF_8)))
            return applyPolicyOverrides(
                baseConfig = baseConfig,
                fallbackPrivateKey = baseConfig.`interface`.keyPair.privateKey.toBase64(),
                effectiveDns = effectiveDns,
                dnsLeakProtectionEnabled = dnsLeakProtectionEnabled,
                mtu = mtu,
                excludedPackages = excludedPackages
            )
        }

        val dualStackAddresses = listOf(assignedIpv4, assignedIpv6)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(", ")

        val iface = Interface.Builder()
            .parsePrivateKey(privateKeyBase64)
            .parseAddresses(dualStackAddresses.ifBlank { "10.8.0.2/32, fd80:4e58:5200::2/128" })
            .parseDnsServers(effectiveDns.ifBlank { STRICT_DUAL_STACK_DNS })
            .setMtu(mtu)
            .apply {
                if (excludedPackages.isNotEmpty()) {
                    excludeApplications(excludedPackages)
                }
            }
            .build()

        val peer = Peer.Builder()
            .parsePublicKey(profile.serverPublicKey)
            .parseEndpoint(profile.endpoint)
            .parseAllowedIPs(
                if (dnsLeakProtectionEnabled) "0.0.0.0/0, ::/0"
                else profile.allowedIps.ifBlank { "0.0.0.0/0, ::/0" }
            )
            .setPersistentKeepalive(25)
            .build()

        return Config.Builder()
            .setInterface(iface)
            .addPeer(peer)
            .build()
    }

    private fun injectPrivateKeyIntoWgQuick(wgQuick: String, privateKeyBase64: String): String {
        val lines = wgQuick.lines()
        val hasPrivateKey = lines.any { it.trim().startsWith("PrivateKey", ignoreCase = true) && it.contains("=") }
        if (hasPrivateKey) return wgQuick
        val out = StringBuilder()
        var inserted = false
        for (line in lines) {
            out.appendLine(line)
            if (!inserted && line.trim().equals("[Interface]", ignoreCase = true)) {
                out.appendLine("PrivateKey = $privateKeyBase64")
                inserted = true
            }
        }
        return out.toString()
    }

    private fun applyPolicyOverrides(
        baseConfig: Config,
        fallbackPrivateKey: String,
        effectiveDns: String,
        dnsLeakProtectionEnabled: Boolean,
        mtu: Int,
        excludedPackages: Set<String>
    ): Config {
        val ifaceBuilder = Interface.Builder().apply {
            parsePrivateKey(fallbackPrivateKey)
            val addr = baseConfig.`interface`.addresses.joinToString(", ") { it.toString() }
            if (addr.isNotBlank()) parseAddresses(addr)
            val dns = if (dnsLeakProtectionEnabled) {
                effectiveDns.ifBlank { STRICT_DUAL_STACK_DNS }
            } else {
                baseConfig.`interface`.dnsServers.joinToString(", ") { it.hostAddress ?: "" }
                    .ifBlank { effectiveDns }
            }
            if (dns.isNotBlank()) parseDnsServers(dns)
            setMtu(mtu)
            if (excludedPackages.isNotEmpty()) {
                excludeApplications(excludedPackages)
            }
        }
        val cfgBuilder = Config.Builder().setInterface(ifaceBuilder.build())
        baseConfig.peers.forEach { basePeer ->
            if (dnsLeakProtectionEnabled) {
                val enforcedPeer = Peer.Builder()
                    .setPublicKey(basePeer.publicKey)
                    .apply {
                        basePeer.endpoint.ifPresent { setEndpoint(it) }
                        basePeer.preSharedKey.ifPresent { setPreSharedKey(it) }
                    }
                    .parseAllowedIPs("0.0.0.0/0, ::/0")
                    .setPersistentKeepalive(basePeer.persistentKeepalive.orElse(25))
                    .build()
                cfgBuilder.addPeer(enforcedPeer)
            } else {
                cfgBuilder.addPeer(basePeer)
            }
        }
        return cfgBuilder.build()
    }
}
