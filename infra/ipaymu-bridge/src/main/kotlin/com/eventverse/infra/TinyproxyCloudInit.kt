package com.eventverse.infra

import java.util.Base64

/**
 * Teks cloud-init untuk tinyproxy egress gateway (TRD-PAY-001 FR-PAY-1).
 * Tanpa rahasia: proxy hanya mendengar di 127.0.0.1 dan hanya meneruskan
 * ke domain allowlist. Port 8888 tidak dibuka di Security List maupun iptables.
 *
 * `iptables` bawaan image Ubuntu 24.04 (hanya port 22) sengaja dibiarkan.
 */
object TinyproxyCloudInit {

    val ALLOWED_DOMAINS = listOf(
        "my.ipaymu.com",
        "sandbox.ipaymu.com",
        "api.ipify.org",
    )

    fun render(): String {
        val filter = ALLOWED_DOMAINS.joinToString("\n") { "^${it.replace(".", "\\.")}$" }
        val yaml = """
            #cloud-config
            package_update: true
            packages:
              - tinyproxy
            write_files:
              - path: /etc/tinyproxy/tinyproxy.conf
                permissions: '0644'
                content: |
                  User tinyproxy
                  Group tinyproxy
                  Listen 127.0.0.1
                  Port 8888
                  Timeout 600
                  LogLevel Info
                  MaxClients 50
                  Allow 127.0.0.1
                  ConnectPort 443
                  Filter "/etc/tinyproxy/filter"
                  FilterDefaultDeny Yes
                  FilterExtended Yes
              - path: /etc/tinyproxy/filter
                permissions: '0644'
                content: |
                  $filter
            runcmd:
              - systemctl enable tinyproxy
              - systemctl restart tinyproxy
        """.trimIndent()
        return Base64.getEncoder().encodeToString(yaml.toByteArray(Charsets.UTF_8))
    }
}
