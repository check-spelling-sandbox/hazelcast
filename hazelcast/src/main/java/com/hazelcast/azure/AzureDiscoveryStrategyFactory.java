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

package com.hazelcast.azure;

import com.hazelcast.config.properties.PropertyDefinition;
import com.hazelcast.logging.ILogger;
import com.hazelcast.spi.discovery.DiscoveryNode;
import com.hazelcast.spi.discovery.DiscoveryStrategy;
import com.hazelcast.spi.discovery.DiscoveryStrategyFactory;
import com.hazelcast.spi.utils.RestClient;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Factory class which returns {@link AzureDiscoveryStrategy} to Discovery SPI
 */
public class AzureDiscoveryStrategyFactory implements DiscoveryStrategyFactory {

    @Override
    public Class<? extends DiscoveryStrategy> getDiscoveryStrategyType() {
        return AzureDiscoveryStrategy.class;
    }

    @Override
    public DiscoveryStrategy newDiscoveryStrategy(DiscoveryNode node, ILogger logger,
                                                  Map<String, Comparable> properties) {
        return new AzureDiscoveryStrategy(properties);
    }

    @Override
    public Collection<PropertyDefinition> getConfigurationProperties() {
        List<PropertyDefinition> result = new ArrayList<>();
        for (AzureProperties property : AzureProperties.values()) {
            result.add(property.getDefinition());
        }
        return result;
    }

    /**
     * Checks if Hazelcast is running on Azure.
     * <p>
     * To check if Hazelcast is running on Azure, we first check whether "internal.cloudapp.net" is present in the file
     * "/etc/resolv.conf". Such an approach is not officially documented but seems like a good enough heuristic to detect an
     * Azure Compute VM Instance. Since it's not the official method, we still need to make an API call to a local, non-routable
     * address http://169.254.169.254/metadata/instance.
     *
     * @return true if running on Azure Instance
     * @see <a href="https://docs.microsoft.com/en-us/azure/virtual-machines/linux/instance-metadata-service#metadata-apis">Azure
     * Instance Metadata API</a>
     */
    @Override
    public boolean isAutoDetectionApplicable() {
        return azureDnsServerConfigured() && azureInstanceMetadataAvailable();
    }

    private static boolean azureDnsServerConfigured() {
        return readFileContents("/etc/resolv.conf").contains("internal.cloudapp.net");
    }

    static String readFileContents(String fileName) {
        try {
            File file = new File(fileName);
            return Files.readString(file.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Could not get " + fileName, e);
        }
    }

    private static boolean azureInstanceMetadataAvailable() {
        return isEndpointAvailable("http://169.254.169.254/metadata/instance?api-version=2020-06-01");
    }


    static boolean isEndpointAvailable(String url) {
        return !RestClient.create(url, 1)
                .withRequestTimeoutSeconds(1)
                .withRetries(1)
                .withHeader("Metadata", "True")
                .get()
                .getBody()
                .isEmpty();
    }

    @Override
    public DiscoveryStrategyLevel discoveryStrategyLevel() {
        return DiscoveryStrategyLevel.CLOUD_VM;
    }
}
