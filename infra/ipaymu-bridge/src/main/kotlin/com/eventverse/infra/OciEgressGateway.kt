package com.eventverse.infra

import com.pulumi.oci.Core.inputs.GetImagesArgs
import com.pulumi.oci.Core.inputs.GetPrivateIpsArgs
import com.pulumi.oci.Core.inputs.GetVnicAttachmentsArgs
import com.pulumi.oci.Core.inputs.InstanceCreateVnicDetailsArgs
import com.pulumi.oci.Core.inputs.InstanceShapeConfigArgs
import com.pulumi.oci.Core.inputs.InstanceSourceDetailsArgs
import com.pulumi.oci.Core.inputs.RouteTableRouteRuleArgs
import com.pulumi.oci.Core.inputs.SecurityListEgressSecurityRuleArgs
import com.pulumi.oci.Core.inputs.SecurityListIngressSecurityRuleArgs
import com.pulumi.oci.Core.inputs.SecurityListIngressSecurityRuleTcpOptionsArgs
import com.pulumi.oci.Identity.inputs.GetAvailabilityDomainsArgs
import com.pulumi.resources.CustomResourceOptions

/**
 * Komponen egress gateway OCI (TRD-PAY-001 Komponen 1):
 * VCN + IGW + Route Table + Security List (ingress hanya SSH) + Subnet publik,
 * VM A1 Flex dengan cloud-init tinyproxy, dan Reserved Public IP yang
 * di-`protect` agar whitelist iPaymu tidak hilang saat redeploy.
 */
data class OciEgressGatewayOutputs(val egressIp: com.pulumi.core.Output<String>)

fun buildOciEgressGateway(cfg: BridgeConfig): OciEgressGatewayOutputs {
    val vcn = com.pulumi.oci.Core.Vcn(
        "ipaymu-vcn",
        com.pulumi.oci.Core.VcnArgs.builder()
            .compartmentId(cfg.ociCompartmentId)
            .cidrBlocks(listOf("10.0.0.0/16"))
            .displayName("ipaymu-bridge-vcn")
            .dnsLabel("ipaymuvcn")
            .build(),
    )

    val igw = com.pulumi.oci.Core.InternetGateway(
        "ipaymu-igw",
        com.pulumi.oci.Core.InternetGatewayArgs.builder()
            .compartmentId(cfg.ociCompartmentId)
            .vcnId(vcn.id())
            .enabled(true)
            .displayName("ipaymu-bridge-igw")
            .build(),
    )

    val routeTable = com.pulumi.oci.Core.RouteTable(
        "ipaymu-rt",
        com.pulumi.oci.Core.RouteTableArgs.builder()
            .compartmentId(cfg.ociCompartmentId)
            .vcnId(vcn.id())
            .displayName("ipaymu-bridge-rt")
            .routeRules(
                listOf(
                    RouteTableRouteRuleArgs.builder()
                        .networkEntityId(igw.id())
                        .destination("0.0.0.0/0")
                        .destinationType("CIDR_BLOCK")
                        .build(),
                ),
            )
            .build(),
    )

    // Ingress HANYA TCP 22 — port proxy 8888 tidak dibuka (FR-PAY-1.1, AC-PAY-2).
    val securityList = com.pulumi.oci.Core.SecurityList(
        "ipaymu-sl",
        com.pulumi.oci.Core.SecurityListArgs.builder()
            .compartmentId(cfg.ociCompartmentId)
            .vcnId(vcn.id())
            .displayName("ipaymu-bridge-sl")
            .ingressSecurityRules(
                listOf(
                    SecurityListIngressSecurityRuleArgs.builder()
                        .protocol("6") // TCP
                        .source("0.0.0.0/0")
                        .sourceType("CIDR_BLOCK")
                        .tcpOptions(
                            SecurityListIngressSecurityRuleTcpOptionsArgs.builder()
                                .min(22)
                                .max(22)
                                .build(),
                        )
                        .build(),
                ),
            )
            .egressSecurityRules(
                listOf(
                    SecurityListEgressSecurityRuleArgs.builder()
                        .protocol("all")
                        .destination("0.0.0.0/0")
                        .destinationType("CIDR_BLOCK")
                        .build(),
                ),
            )
            .build(),
    )

    val subnet = com.pulumi.oci.Core.Subnet(
        "ipaymu-subnet",
        com.pulumi.oci.Core.SubnetArgs.builder()
            .compartmentId(cfg.ociCompartmentId)
            .vcnId(vcn.id())
            .cidrBlock("10.0.1.0/24")
            .routeTableId(routeTable.id())
            .securityListIds(securityList.id().applyValue { listOf(it) })
            .dnsLabel("ipaymudev")
            .displayName("ipaymu-bridge-subnet")
            .build(),
    )

    // Lookup availability domain & image — jangan OCID manual (TRD §4.2 poin 3).
    val ads = com.pulumi.oci.Identity.IdentityFunctions.getAvailabilityDomains(
        GetAvailabilityDomainsArgs.builder()
            .compartmentId(cfg.ociCompartmentId)
            .build(),
    )
    val availabilityDomain = ads.applyValue { it.availabilityDomains().first().name() }

    val images = com.pulumi.oci.Core.CoreFunctions.getImages(
        GetImagesArgs.builder()
            .compartmentId(cfg.ociCompartmentId)
            .operatingSystem("Canonical Ubuntu")
            .operatingSystemVersion("24.04")
            .shape(cfg.shape)
            .sortBy("TIMECREATED")
            .sortOrder("DESC")
            .build(),
    )
    val imageId: com.pulumi.core.Output<String> =
        images.applyValue { result -> result.images().first().id() }

    val instance = com.pulumi.oci.Core.Instance(
        "ipaymu-egress-vm",
        com.pulumi.oci.Core.InstanceArgs.builder()
            .availabilityDomain(availabilityDomain)
            .compartmentId(cfg.ociCompartmentId)
            .shape(cfg.shape)
            .displayName("ipaymu-egress-gateway")
            .shapeConfig(
                InstanceShapeConfigArgs.builder()
                    .ocpus(1.0)
                    .memoryInGbs(6.0)
                    .build(),
            )
            .sourceDetails(
                InstanceSourceDetailsArgs.builder()
                    .sourceType("image")
                    .sourceId(imageId)
                    .build(),
            )
            .createVnicDetails(
                InstanceCreateVnicDetailsArgs.builder()
                    // Wajib false: Reserved IP tidak bisa dipasang bila VNIC sudah
                    // punya ephemeral public IP (TRD §4.2 poin 4).
                    .assignPublicIp("false")
                    .subnetId(subnet.id())
                    .hostnameLabel("ipaymu-egress")
                    .displayName("ipaymu-egress-vnic")
                    .build(),
            )
            .metadata(
                mapOf(
                    "ssh_authorized_keys" to cfg.sshPublicKeys.joinToString("\n"),
                    "user_data" to TinyproxyCloudInit.render(),
                ),
            )
            .build(),
    )

    // Private IP utama VNIC → tempat Reserved IP diikat.
    val vnicAttachments = com.pulumi.oci.Core.CoreFunctions.getVnicAttachments(
        GetVnicAttachmentsArgs.builder()
            .instanceId(instance.id())
            .compartmentId(cfg.ociCompartmentId)
            .build(),
    )
    val vnicId = vnicAttachments.applyValue { it.vnicAttachments().first().vnicId() }

    val privateIps = com.pulumi.oci.Core.CoreFunctions.getPrivateIps(
        GetPrivateIpsArgs.builder()
            .vnicId(vnicId)
            .build(),
    )
    val privateIpId = privateIps.applyValue { it.privateIps().first().id() }

    // Reserved Public IP dengan protect = true (AC-PAY-8): `pulumi destroy`
    // berhenti di resource ini; mengganti VM tidak mengganti IP whitelist iPaymu.
    val publicIp = com.pulumi.oci.Core.PublicIp(
        "ipaymu-reserved-ip",
        com.pulumi.oci.Core.PublicIpArgs.builder()
            .compartmentId(cfg.ociCompartmentId)
            .lifetime("RESERVED")
            .displayName("ipaymu-egress-ip")
            .privateIpId(privateIpId)
            .build(),
        CustomResourceOptions.builder().protect(true).build(),
    )

    return OciEgressGatewayOutputs(egressIp = publicIp.ipAddress().applyValue { it ?: "" })
}
