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

package com.hazelcast.aws;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.net.HttpURLConnection;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.any;

@RunWith(MockitoJUnitRunner.class)
public class AwsEc2ApiTest {
    private static final String AUTHORIZATION_HEADER = "authorization-header";
    private static final String TOKEN = "IQoJb3JpZ2luX2VjEFIaDGV1LWNlbnRyYWwtMSJGM==";
    private static final AwsCredentials CREDENTIALS = AwsCredentials.builder()
        .setAccessKey("AKIDEXAMPLE")
        .setSecretKey("wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY")
        .setToken(TOKEN)
        .build();

    @Mock
    private AwsRequestSigner requestSigner;

    private AwsEc2Api awsEc2Api;

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(wireMockConfig().dynamicPort());

    @Before
    public void setUp() {
        given(requestSigner.authHeader(any(), any(), any(), any(), any(), any())).willReturn(AUTHORIZATION_HEADER);
        awsEc2Api = defaultAwsEc2Api();
    }

    private AwsEc2Api defaultAwsEc2Api() {
        return createAwsEc2Api("aws-test-cluster,another-tag-key", "cluster1");
    }

    private AwsEc2Api createAwsEc2Api(String tagKey, String tagValue) {
        String endpoint = String.format("http://localhost:%s", wireMockRule.port());
        Clock clock = Clock.fixed(Instant.ofEpochMilli(1585909518929L), ZoneId.systemDefault());
        AwsConfig awsConfig = AwsConfig.builder()
                .setSecurityGroupName("hazelcast")
                .setTagKey(tagKey)
                .setTagValue(tagValue)
                .build();
        return new AwsEc2Api(endpoint, awsConfig, requestSigner, clock);
    }

    @Test
    public void describeInstances() {
        // given
        String requestUrl = "/?Action=DescribeInstances"
            + "&Filter.1.Name=tag%3Aaws-test-cluster"
            + "&Filter.1.Value.1=cluster1"
            + "&Filter.2.Name=tag-key"
            + "&Filter.2.Value.1=another-tag-key"
            + "&Filter.3.Name=instance.group-name"
            + "&Filter.3.Value.1=hazelcast"
            + "&Filter.4.Name=instance-state-name&Filter.4.Value.1=running"
            + "&Version=2016-11-15";

        //language=XML
        String response = """
                <?xml version="1.0" encoding="UTF-8"?>
                <DescribeInstancesResponse xmlns="http://ec2.amazonaws.com/doc/2016-11-15/">
                    <reservationSet>
                        <item>
                            <instancesSet>
                                <item>
                                    <privateIpAddress>10.0.1.25</privateIpAddress>
                                    <ipAddress>54.93.121.213</ipAddress>
                                    <tagSet>
                                        <item>
                                            <key>kubernetes.io/cluster/openshift-cluster</key>
                                            <value>openshift-cluster-eu-central-1</value>
                                        </item>
                                        <item>
                                            <key>Name</key>
                                            <value>* OpenShift Node 1</value>
                                        </item>
                                    </tagSet>
                                </item>
                            </instancesSet>
                        </item>
                        <item>
                            <instancesSet>
                                <item>
                                    <privateIpAddress>172.31.14.42</privateIpAddress>
                                    <ipv6Address>2001:0DB8:C21A::1</ipv6Address>
                                    <tagSet>
                                        <item>
                                            <key>Name</key>
                                            <value>rafal-ubuntu-2</value>
                                        </item>
                                    </tagSet>
                                </item>
                            </instancesSet>
                        </item>
                    </reservationSet>
                </DescribeInstancesResponse>""";

        stubFor(get(urlEqualTo(requestUrl))
            .withHeader("X-Amz-Date", equalTo("20200403T102518Z"))
            .withHeader("Authorization", equalTo(AUTHORIZATION_HEADER))
            .withHeader("X-Amz-Security-Token", equalTo(TOKEN))
            .willReturn(aResponse().withStatus(HttpURLConnection.HTTP_OK).withBody(response)));

        // when
        Map<String, String> result = awsEc2Api.describeInstances(CREDENTIALS);

        // then
        assertEquals(3, result.size());
        assertEquals("54.93.121.213", result.get("10.0.1.25"));
        assertNull(result.get("172.31.14.42"));
        assertEquals("2001:0DB8:C21A::1", result.get("2001:0DB8:C21A::1"));
    }

    @Test
    public void describeInstancesNoPublicIpNoInstanceName() {
        // given
        String requestUrl = "/?Action=DescribeInstances"
            + "&Filter.1.Name=tag-value"
            + "&Filter.1.Value.1=some-tag-value"
            + "&Filter.2.Name=instance.group-name"
            + "&Filter.2.Value.1=hazelcast"
            + "&Filter.3.Name=instance-state-name&Filter.3.Value.1=running"
            + "&Version=2016-11-15";

        //language=XML
        String response = """
                <?xml version="1.0" encoding="UTF-8"?>
                <DescribeInstancesResponse xmlns="http://ec2.amazonaws.com/doc/2016-11-15/">
                    <reservationSet>
                        <item>
                            <instancesSet>
                                <item>
                                    <privateIpAddress>10.0.1.25</privateIpAddress>
                                </item>
                            </instancesSet>
                        </item>
                        <item>
                            <instancesSet>
                                <item>
                                    <privateIpAddress>172.31.14.42</privateIpAddress>
                                </item>
                            </instancesSet>
                        </item>
                    </reservationSet>
                </DescribeInstancesResponse>""";

        stubFor(get(urlEqualTo(requestUrl))
            .withHeader("X-Amz-Date", equalTo("20200403T102518Z"))
            .withHeader("Authorization", equalTo(AUTHORIZATION_HEADER))
            .withHeader("X-Amz-Security-Token", equalTo(TOKEN))
            .willReturn(aResponse().withStatus(HttpURLConnection.HTTP_OK).withBody(response)));

        // when
        Map<String, String> result = createAwsEc2Api(null, "some-tag-value").describeInstances(CREDENTIALS);

        // then
        assertEquals(2, result.size());
        assertNull(result.get("10.0.1.25"));
        assertNull(result.get("172.31.14.42"));
    }

    @Test
    public void describeNetworkInterfaces() {
        // given
        List<String> privateAddresses = asList("10.0.1.207", "10.0.1.82");

        String requestUrl = "/?Action=DescribeNetworkInterfaces"
            + "&Filter.1.Name=addresses.private-ip-address"
            + "&Filter.1.Value.1=10.0.1.207"
            + "&Filter.1.Value.2=10.0.1.82"
            + "&Version=2016-11-15";

        //language=XML
        String response = """
                <?xml version="1.0" encoding="UTF-8"?>
                <DescribeNetworkInterfacesResponse xmlns="http://ec2.amazonaws.com/doc/2016-11-15/">
                    <requestId>21bc9f93-2196-4107-87a3-9e5b2b3f29d9</requestId>
                    <networkInterfaceSet>
                        <item>
                            <availabilityZone>eu-central-1a</availabilityZone>
                            <privateIpAddress>10.0.1.207</privateIpAddress>
                            <association>
                                <publicIp>54.93.217.194</publicIp>
                            </association>
                        </item>
                        <item>
                            <availabilityZone>eu-central-1a</availabilityZone>
                            <privateIpAddress>10.0.1.82</privateIpAddress>
                            <ipv6AddressesSet>
                               <item>
                                   <ipv6Address>2001:db8:1234:1a2b::123</ipv6Address>
                               </item>
                           </ipv6AddressesSet>
                        </item>
                    </networkInterfaceSet>
                </DescribeNetworkInterfacesResponse>""";

        stubFor(get(urlEqualTo(requestUrl))
            .withHeader("X-Amz-Date", equalTo("20200403T102518Z"))
            .withHeader("Authorization", equalTo(AUTHORIZATION_HEADER))
            .withHeader("X-Amz-Security-Token", equalTo(TOKEN))
            .willReturn(aResponse().withStatus(HttpURLConnection.HTTP_OK).withBody(response)));

        // when
        Map<String, String> result = awsEc2Api.describeNetworkInterfaces(privateAddresses, CREDENTIALS);

        // then
        assertEquals(3, result.size());
        assertEquals("54.93.217.194", result.get("10.0.1.207"));
        assertNull(result.get("10.0.1.82"));
        assertEquals("2001:db8:1234:1a2b::123", result.get("2001:db8:1234:1a2b::123"));
    }

    @Test
    public void describeNetworkInterfacesException() {
        // given
        List<String> privateAddresses = asList("10.0.1.207", "10.0.1.82");

        String requestUrl = "/?Action=DescribeNetworkInterfaces"
                + "&Filter.1.Name=addresses.private-ip-address"
                + "&Filter.1.Value.1=10.0.1.207"
                + "&Filter.1.Value.2=10.0.1.82"
                + "&Version=2016-11-15";

        stubFor(get(urlEqualTo(requestUrl))
                .withHeader("X-Amz-Date", equalTo("20200403T102518Z"))
                .withHeader("Authorization", equalTo(AUTHORIZATION_HEADER))
                .withHeader("X-Amz-Security-Token", equalTo(TOKEN))
                .willReturn(aResponse().withStatus(HttpURLConnection.HTTP_INTERNAL_ERROR)));

        // when
        Map<String, String> result = awsEc2Api.describeNetworkInterfaces(privateAddresses, CREDENTIALS);

        // then
        assertEquals(2, result.size());
        assertNull(result.get("10.0.1.207"));
        assertNull(result.get("10.0.1.82"));
    }

    @Test
    public void describeNetworkInterfacesNoPublicIp() {
        // given
        List<String> privateAddresses = asList("10.0.1.207", "10.0.1.82");

        String requestUrl = "/?Action=DescribeNetworkInterfaces"
            + "&Filter.1.Name=addresses.private-ip-address"
            + "&Filter.1.Value.1=10.0.1.207"
            + "&Filter.1.Value.2=10.0.1.82"
            + "&Version=2016-11-15";

        //language=XML
        String response = """
                <?xml version="1.0" encoding="UTF-8"?>
                <DescribeNetworkInterfacesResponse xmlns="http://ec2.amazonaws.com/doc/2016-11-15/">
                    <networkInterfaceSet>
                        <item>
                            <privateIpAddress>10.0.1.207</privateIpAddress>
                        </item>
                        <item>
                            <privateIpAddress>10.0.1.82</privateIpAddress>
                        </item>
                    </networkInterfaceSet>
                </DescribeNetworkInterfacesResponse>""";

        stubFor(get(urlEqualTo(requestUrl))
            .withHeader("X-Amz-Date", equalTo("20200403T102518Z"))
            .withHeader("Authorization", equalTo(AUTHORIZATION_HEADER))
            .withHeader("X-Amz-Security-Token", equalTo(TOKEN))
            .willReturn(aResponse().withStatus(HttpURLConnection.HTTP_OK).withBody(response)));

        // when
        Map<String, String> result = awsEc2Api.describeNetworkInterfaces(privateAddresses, CREDENTIALS);

        // then
        assertEquals(2, result.size());
        assertTrue(result.containsKey("10.0.1.207"));
        assertNull(result.get("10.0.1.207"));
        assertTrue(result.containsKey("10.0.1.82"));
        assertNull(result.get("10.0.1.82"));
    }

    @Test
    public void describeNetworkInterfacesEmptyPrivateAddressList() {
        // given
        List<String> privateAddresses = Collections.emptyList();

        // when
        Map<String, String> result = awsEc2Api.describeNetworkInterfaces(privateAddresses, CREDENTIALS);

        // then
        assertEquals(0, result.size());

        verify(exactly(0), getRequestedFor(urlEqualTo("/?Action=DescribeNetworkInterfaces&Version=2016-11-15")));
    }

    @Test
    public void awsError() {
        // given
        int errorCode = HttpURLConnection.HTTP_UNAUTHORIZED;
        String errorMessage = "Error message retrieved from AWS";
        stubFor(get(urlMatching("/.*"))
                .willReturn(aResponse().withStatus(errorCode).withBody(errorMessage)));

        // when
        Exception exception = assertThrows(Exception.class, () -> awsEc2Api.describeInstances(CREDENTIALS));

        // then
        assertTrue(exception.getMessage().contains(Integer.toString(errorCode)));
        assertTrue(exception.getMessage().contains(errorMessage));
    }
}
