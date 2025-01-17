// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.athenz.instanceproviderservice;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.athenz.identityprovider.api.IdentityType;
import com.yahoo.vespa.athenz.identityprovider.api.SignedIdentityDocument;
import com.yahoo.vespa.athenz.identityprovider.api.VespaUniqueInstanceId;
import com.yahoo.vespa.athenz.identityprovider.client.IdentityDocumentSigner;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.config.AthenzProviderServiceConfig;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Allocation;
import com.yahoo.vespa.hosted.provision.node.Generation;
import com.yahoo.vespa.hosted.provision.node.IP;
import com.yahoo.vespa.hosted.provision.node.Nodes;
import com.yahoo.vespa.hosted.provision.testutils.MockNodeFlavors;
import org.junit.Test;

import java.util.Optional;
import java.util.Set;

import static com.yahoo.vespa.hosted.athenz.instanceproviderservice.TestUtils.getAthenzProviderConfig;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author valerijf
 */
public class IdentityDocumentGeneratorTest {

    private static final Zone ZONE = new Zone(SystemName.cd, Environment.dev, RegionName.from("us-north-1"));

    @Test
    public void generates_valid_identity_document()  {
        String parentHostname = "docker-host";
        String containerHostname = "docker-container";

        ApplicationId appid = ApplicationId.from(
                TenantName.from("tenant"), ApplicationName.from("application"), InstanceName.from("default"));
        Allocation allocation = new Allocation(appid,
                                               ClusterMembership.from("container/default/0/0", Version.fromString("1.2.3"), Optional.empty()),
                                               new NodeResources(1, 1, 1, 1),
                                               Generation.initial(),
                                               false);
        Node parentNode = Node.create("ostkid",
                                      IP.Config.ofEmptyPool(Set.of("127.0.0.1")),
                                      parentHostname,
                                      new MockNodeFlavors().getFlavorOrThrow("default"),
                                      NodeType.host).build();
        Node containerNode = Node.reserve(Set.of("::1"),
                                          containerHostname,
                                          parentHostname,
                                          new MockNodeFlavors().getFlavorOrThrow("default").resources(),
                                          NodeType.tenant)
                .allocation(allocation).build();
        NodeRepository nodeRepository = mock(NodeRepository.class);
        Nodes nodes = mock(Nodes.class);
        when(nodeRepository.nodes()).thenReturn(nodes);

        when(nodes.node(eq(parentHostname))).thenReturn(Optional.of(parentNode));
        when(nodes.node(eq(containerHostname))).thenReturn(Optional.of(containerNode));
        AutoGeneratedKeyProvider keyProvider = new AutoGeneratedKeyProvider();

        String dnsSuffix = "vespa.dns.suffix";
        AthenzProviderServiceConfig config = getAthenzProviderConfig("domain", "service", dnsSuffix);
        IdentityDocumentGenerator identityDocumentGenerator =
                new IdentityDocumentGenerator(config, nodeRepository, ZONE, keyProvider);
        SignedIdentityDocument signedIdentityDocument = identityDocumentGenerator.generateSignedIdentityDocument(containerHostname, IdentityType.TENANT);

        // Verify attributes
        assertEquals(containerHostname, signedIdentityDocument.instanceHostname());

        String environment = "dev";
        String region = "us-north-1";

        VespaUniqueInstanceId expectedProviderUniqueId =
                new VespaUniqueInstanceId(0, "default", "default", "application", "tenant", region, environment, IdentityType.TENANT);
        assertEquals(expectedProviderUniqueId, signedIdentityDocument.providerUniqueId());

        // Validate that container ips are present
        assertTrue(signedIdentityDocument.ipAddresses().contains("::1"));

        IdentityDocumentSigner signer = new IdentityDocumentSigner();

        // Validate signature
        assertTrue(signer.hasValidSignature(signedIdentityDocument, keyProvider.getPublicKey(0)));
    }
}
