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

package com.hazelcast.spring.boot;

import com.hazelcast.config.Config;
import com.hazelcast.config.MapConfig;
import com.hazelcast.config.MetadataPolicy;
import com.hazelcast.config.NetworkConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.io.ClassPathResource;

@SpringBootApplication
public class BootHazelcastApplication {

    public static void main(String[] args) {
            SpringApplication.run(BootHazelcastApplication.class, args);
        }

    @Bean
    public PropertySourcesPlaceholderConfigurer propertyPlaceholderConfigurer() {
        var propertyPlaceholderConfigurer = new PropertySourcesPlaceholderConfigurer();
        propertyPlaceholderConfigurer.setLocation(new ClassPathResource("hazelcast-default.properties"));
        return propertyPlaceholderConfigurer;
    }

    @Bean
    public Config config() {
        Config config = new Config();
        config.setClusterName("spring-cluster");

        config.getJetConfig().setEnabled(true);

        NetworkConfig networkConfig = config.getNetworkConfig();
        networkConfig.setPort(5801);
        networkConfig.setPortAutoIncrement(true);
        networkConfig.getInterfaces().addInterface("127.0.0.1");
        networkConfig.getJoin().getAutoDetectionConfig().setEnabled(false);
        networkConfig.getJoin().getMulticastConfig().setEnabled(false);

        config.setProperty("hazelcast.merge.first.run.delay.seconds", "5");
        config.setProperty("hazelcast.merge.next.run.delay.seconds", "5");
        config.setProperty("hazelcast.partition.count", "277");

        var mapConfig = new MapConfig("testMap");
        mapConfig.setBackupCount(2);
        mapConfig.setReadBackupData(true);
        mapConfig.setMetadataPolicy(MetadataPolicy.OFF);
        config.addMapConfig(mapConfig);
        var map1Config = new MapConfig("map1");
        map1Config.setBackupCount(2);
        map1Config.setReadBackupData(true);
        map1Config.setMetadataPolicy(MetadataPolicy.OFF);
        config.addMapConfig(map1Config);
        return config;
    }
}
