/*
 * Copyright (c) 2008-2025, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.kubernetes;

import com.hazelcast.config.NetworkConfig;
import com.hazelcast.instance.impl.ClusterTopologyIntentTracker;
import com.hazelcast.kubernetes.KubernetesClient.Endpoint;
import com.hazelcast.logging.ILogger;
import com.hazelcast.cluster.Address;
import com.hazelcast.spi.discovery.DiscoveryNode;
import com.hazelcast.spi.discovery.SimpleDiscoveryNode;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

import static com.hazelcast.internal.util.HostnameUtil.getLocalHostname;

class KubernetesApiEndpointResolver
        extends HazelcastKubernetesDiscoveryStrategy.EndpointResolver {

    private final String serviceName;
    private final String serviceLabel;
    private final String serviceLabelValue;
    private final String podLabel;
    private final String podLabelValue;
    private final Boolean resolveNotReadyAddresses;
    private final int port;
    private final KubernetesClient client;

    KubernetesApiEndpointResolver(ILogger logger, KubernetesConfig config, ClusterTopologyIntentTracker tracker) {
        this(logger, config.getServiceName(), config.getServicePort(), config.getServiceLabelName(),
                config.getServiceLabelValue(), config.getPodLabelName(), config.getPodLabelValue(),
                config.isResolveNotReadyAddresses(), new KubernetesClient(config, tracker));
    }

    /**
     * Used externally only for testing
     */
    KubernetesApiEndpointResolver(ILogger logger, String serviceName, int port,
                                  String serviceLabel, String serviceLabelValue, String podLabel, String podLabelValue,
                                  Boolean resolveNotReadyAddresses, KubernetesClient client) {
        super(logger);
        this.serviceName = serviceName;
        this.port = port;
        this.serviceLabel = serviceLabel;
        this.serviceLabelValue = serviceLabelValue;
        this.podLabel = podLabel;
        this.podLabelValue = podLabelValue;
        this.resolveNotReadyAddresses = resolveNotReadyAddresses;
        this.client = client;
    }

    @Override
    List<DiscoveryNode> resolveNodes() {
        if (serviceName != null && !serviceName.isEmpty()) {
            logger.fine("Using service name to discover nodes.");
            return getSimpleDiscoveryNodes(client.endpointsByName(serviceName));
        } else if (serviceLabel != null && !serviceLabel.isEmpty()) {
            logger.fine("Using service label to discover nodes.");
            return getSimpleDiscoveryNodes(client.endpointsByServiceLabel(serviceLabel, serviceLabelValue));
        } else if (podLabel != null && !podLabel.isEmpty()) {
            logger.fine("Using pod label to discover nodes.");
            return getSimpleDiscoveryNodes(client.endpointsByPodLabel(podLabel, podLabelValue));
        }
        return getSimpleDiscoveryNodes(client.endpoints());
    }

    private List<DiscoveryNode> getSimpleDiscoveryNodes(List<Endpoint> endpoints) {
        List<DiscoveryNode> discoveredNodes = new ArrayList<>();
        for (Endpoint address : endpoints) {
            addAddress(discoveredNodes, address);
        }
        return discoveredNodes;
    }

    private void addAddress(List<DiscoveryNode> discoveredNodes, Endpoint endpoint) {
        if (Boolean.TRUE.equals(resolveNotReadyAddresses) || endpoint.isReady()) {
            Address privateAddress = createAddress(endpoint.getPrivateAddress(), this::port);
            Address publicAddress = createAddress(endpoint.getPublicAddress(), this::portPublic);
            discoveredNodes
                    .add(new SimpleDiscoveryNode(privateAddress, publicAddress, endpoint.getAdditionalProperties()));
            if (logger.isFinestEnabled()) {
                logger.finest("Found node service with addresses (private, public): %s, %s ", privateAddress,
                        publicAddress);
            }
        }
    }

    private Address createAddress(KubernetesClient.EndpointAddress address,
            ToIntFunction<KubernetesClient.EndpointAddress> portResolver) {
        if (address == null) {
            return null;
        }
        String ip = address.getIp();
        InetAddress inetAddress = mapAddress(ip);
        int port = portResolver.applyAsInt(address);
        return new Address(inetAddress, port);
    }

    private int port(KubernetesClient.EndpointAddress address) {
        if (this.port > 0) {
            return this.port;
        }
        if (address.getPort() != null) {
            return address.getPort();
        }
        return NetworkConfig.DEFAULT_PORT;
    }

    // For the public IP address the discovered port should be preferred over the configured one
    private int portPublic(KubernetesClient.EndpointAddress address) {
        if (address.getPort() != null) {
            return address.getPort();
        }
        if (this.port > 0) {
            return this.port;
        }
        return NetworkConfig.DEFAULT_PORT;
    }

    @Override
    String resolveCurrentZone() {
        try {
            String zone = client.zone(podName());
            if (zone != null) {
                logger.info(String.format("Kubernetes plugin discovered availability zone: %s", zone));
                return zone;
            }
        } catch (Exception e) {
            // only log the exception and the message, Hazelcast should still start
            logger.finest(e);
        }
        logger.info("Cannot fetch the current zone, ZONE_AWARE feature is disabled");
        return "unknown";
    }

    @Override
    String resolveCurrentNodeName() {
        try {
            String nodeName = client.nodeName(podName());
            if (nodeName != null) {
                logger.info(String.format("Kubernetes plugin discovered node name: %s", nodeName));
                return nodeName;
            }
        } catch (Exception e) {
            // only log the exception and the message, Hazelcast should still start
            logger.finest(e);
        }
        logger.warning("Cannot fetch name of the node, NODE_AWARE feature is disabled");
        return "unknown";
    }

    private String podName() {
        String podName = System.getenv("POD_NAME");
        if (podName == null) {
            podName = getLocalHostname();
        }
        return podName;
    }

    @Override
    void start() {
        client.start();
    }

    @Override
    void destroy() {
        client.destroy();
    }
}
