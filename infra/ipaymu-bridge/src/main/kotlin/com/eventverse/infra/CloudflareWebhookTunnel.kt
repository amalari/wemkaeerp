package com.eventverse.infra

import com.pulumi.cloudflare.CloudflareFunctions
import com.pulumi.cloudflare.DnsRecordArgs
import com.pulumi.cloudflare.ZeroTrustTunnelCloudflaredArgs
import com.pulumi.cloudflare.ZeroTrustTunnelCloudflaredConfigArgs
import com.pulumi.cloudflare.inputs.GetZeroTrustTunnelCloudflaredTokenArgs
import com.pulumi.cloudflare.inputs.ZeroTrustTunnelCloudflaredConfigConfigArgs
import com.pulumi.cloudflare.inputs.ZeroTrustTunnelCloudflaredConfigConfigIngressArgs
import com.pulumi.random.RandomBytesArgs

/** Path webhook yang satu-satunya diloloskan tunnel (FR-PAY-2.2). */
const val WEBHOOK_PATH = "/api/payment/ipaymu/notify"

/** Service catch-all: semua path & hostname lain dijawab 404 di edge (AC-PAY-7). */
private const val CATCH_ALL_SERVICE = "http_status:404"

/**
 * Komponen Cloudflare Tunnel (TRD-PAY-001 Komponen 2) — satu tunnel per developer
 * (FR-PAY-2.1): token tidak boleh dijalankan di dua laptop sekaligus karena
 * Cloudflare akan membagi callback secara acak.
 */
data class CloudflareWebhookTunnelOutputs(
    /** `https://ipaymu-hook-<dev>.<domain>/api/payment/ipaymu/notify` */
    val webhookUrl: String,
    /** Token `cloudflared tunnel run --token` — SECRET. */
    val token: com.pulumi.core.Output<String>,
)

fun buildCloudflareWebhookTunnel(
    cfg: BridgeConfig,
    developer: String,
): CloudflareWebhookTunnelOutputs {
    // Secret tunnel: 32 byte acak, base64 (TRD §4.2 Komponen 2 poin 1).
    val secret = com.pulumi.random.RandomBytes(
        "ipaymu-tunnel-secret-$developer",
        RandomBytesArgs.builder().length(32).build(),
    )

    val tunnel = com.pulumi.cloudflare.ZeroTrustTunnelCloudflared(
        "ipaymu-tunnel-$developer",
        ZeroTrustTunnelCloudflaredArgs.builder()
            .accountId(cfg.cfAccountId)
            .name("ipaymu-hook-$developer")
            .tunnelSecret(secret.base64())
            // Ingress dikelola remote via ZeroTrustTunnelCloudflaredConfig,
            // bukan file config lokal di laptop developer (FR-PAY-2.3).
            .configSrc("cloudflare")
            .build(),
    )

    // Tepat dua aturan (FR-PAY-2.2): path webhook → localhost:8081, sisanya 404.
    // Catch-all = rule terakhir TANPA hostname/path (konvensi config cloudflared).
    com.pulumi.cloudflare.ZeroTrustTunnelCloudflaredConfig(
        "ipaymu-tunnel-config-$developer",
        ZeroTrustTunnelCloudflaredConfigArgs.builder()
            .accountId(cfg.cfAccountId)
            .tunnelId(tunnel.id())
            .config(
                ZeroTrustTunnelCloudflaredConfigConfigArgs.builder()
                    .ingresses(
                        ZeroTrustTunnelCloudflaredConfigConfigIngressArgs.builder()
                            .hostname("ipaymu-hook-$developer.${cfg.domain}")
                            .path("^$WEBHOOK_PATH${'$'}")
                            .service("http://localhost:8081")
                            .build(),
                        ZeroTrustTunnelCloudflaredConfigConfigIngressArgs.builder()
                            .service(CATCH_ALL_SERVICE)
                            .build(),
                    )
                    .build(),
            )
            .build(),
    )

    com.pulumi.cloudflare.DnsRecord(
        "ipaymu-hook-dns-$developer",
        DnsRecordArgs.builder()
            .zoneId(cfg.cfZoneId)
            .name("ipaymu-hook-$developer")
            .type("CNAME")
            .content(tunnel.id().applyValue { "$it.cfargotunnel.com" })
            .proxied(true)
            .ttl(1.0) // 1 = auto (wajib saat proxied)
            .build(),
    )

    val token = CloudflareFunctions.getZeroTrustTunnelCloudflaredToken(
        GetZeroTrustTunnelCloudflaredTokenArgs.builder()
            .accountId(cfg.cfAccountId)
            .tunnelId(tunnel.id())
            .build(),
    ).applyValue { it.token() }

    return CloudflareWebhookTunnelOutputs(
        webhookUrl = "https://ipaymu-hook-$developer.${cfg.domain}$WEBHOOK_PATH",
        token = token,
    )
}
