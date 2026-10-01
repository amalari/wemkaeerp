package com.eventverse.infra

import com.pulumi.core.Output
import com.pulumi.Pulumi
import com.pulumi.core.TypeShape

/** Gabungkan satu pasangan key→Output ke dalam Output-of-map tanpa nested Output. */
@Suppress("UNCHECKED_CAST")
private fun mergeMapEntry(
    map: Output<Map<String, String>>,
    key: String,
    value: Output<String>,
): Output<Map<String, String>> =
    Output.all(
        listOf(map as Output<Any>, value as Output<Any>),
    ).applyValue { resolved ->
        (resolved[0] as Map<String, String>) + (key to resolved[1] as String)
    }

/**
 * Entry point stack Pulumi iPaymu bridge (TRD-PAY-001).
 * Pulumi Java SDK dipakai dari Kotlin (SDK Kotlin-native tidak ada di Maven Central).
 *
 * Konfigurasi stack (lihat runbook TRD §5.3):
 * ```
 * pulumi config set ociCompartmentId "<OCID_COMPARTMENT>"
 * pulumi config set cfAccountId "<CF_ACCOUNT_ID>"
 * pulumi config set cfZoneId "<CF_ZONE_ID>"
 * pulumi config set domain "wemakeerp.com"
 * pulumi config set --path 'developers[0]' achmad
 * pulumi config set --path 'sshPublicKeys[0]' "$(cat ~/.ssh/id_ed25519.pub)"
 * ```
 */
fun main() {
    Pulumi.run { ctx ->
        val config = ctx.config()

        val cfg = BridgeConfig(
            cfAccountId = config.require("cfAccountId"),
            cfZoneId = config.require("cfZoneId"),
            domain = config.require("domain"),
            developers = config.getObject("developers", TypeShape.list(String::class.java))
                .orElseThrow { error("pulumi config set --path 'developers[0]' <nama> wajib diisi") },
            sshPublicKeys = config.getObject("sshPublicKeys", TypeShape.list(String::class.java))
                .orElse(emptyList()),
            shape = config.get("shape").orElse(BridgeConfig.DEFAULT_SHAPE),
            enableOci = config.getBoolean("enableOci").orElse(false),
        )
        // Hanya diverifikasi setelah flag — pesan error lebih jelas.
        val ociCompartmentId = if (cfg.enableOci) {
            config.require("ociCompartmentId")
        } else ""

        val egressIpOutput: Output<String>? = if (cfg.enableOci) {
            buildOciEgressGateway(cfg.copy(ociCompartmentId = ociCompartmentId)).egressIp
        } else null

        val tunnels = cfg.developers.associateWith { dev ->
            buildCloudflareWebhookTunnel(cfg, dev)
        }

        // webhookUrl deterministik dari config — tidak butuh output resource.
        val webhookUrls: Map<String, String> = tunnels.mapValues { (dev, _) ->
            "https://ipaymu-hook-$dev.${cfg.domain}$WEBHOOK_PATH"
        }

        var tunnelTokens: Output<Map<String, String>> = Output.of(emptyMap<String, String>())
        for ((dev, tunnel) in tunnels) {
            tunnelTokens = mergeMapEntry(tunnelTokens, dev, tunnel.token)
        }

        ctx.export("egressEnabled", Output.of(cfg.enableOci))
        if (egressIpOutput != null) {
            ctx.export("egressIp", egressIpOutput)
            ctx.export("sshForwardCommand", egressIpOutput.applyValue { ip ->
                "ssh -N -L 8888:127.0.0.1:8888 ubuntu@$ip"
            })
        }
        ctx.export("webhookUrls", Output.of(webhookUrls))
        // SECRET — hanya tampil dengan `pulumi stack output tunnelTokens --show-secrets`.
        ctx.export("tunnelTokens", tunnelTokens.asSecret())
    }
}
