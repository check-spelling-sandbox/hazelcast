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

package com.hazelcast.client.impl.connection.tcp;

import com.hazelcast.client.AuthenticationException;
import com.hazelcast.client.ClientNotAllowedInClusterException;
import com.hazelcast.client.HazelcastClientNotActiveException;
import com.hazelcast.client.HazelcastClientOfflineException;
import com.hazelcast.client.LoadBalancer;
import com.hazelcast.client.UnsupportedClusterVersionException;
import com.hazelcast.client.UnsupportedRoutingModeException;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.client.config.ClientConnectionStrategyConfig.ReconnectMode;
import com.hazelcast.client.config.ClientNetworkConfig;
import com.hazelcast.client.config.ClientTpcConfig;
import com.hazelcast.client.config.ConnectionRetryConfig;
import com.hazelcast.client.config.RoutingMode;
import com.hazelcast.client.impl.clientside.CandidateClusterContext;
import com.hazelcast.client.impl.clientside.ClientLoggingService;
import com.hazelcast.client.impl.clientside.ClusterDiscoveryService;
import com.hazelcast.client.impl.clientside.HazelcastClientInstanceImpl;
import com.hazelcast.client.impl.clientside.LifecycleServiceImpl;
import com.hazelcast.client.impl.clientside.SubsetMembersView;
import com.hazelcast.client.impl.connection.AddressProvider;
import com.hazelcast.client.impl.connection.Addresses;
import com.hazelcast.client.impl.connection.ClientConnection;
import com.hazelcast.client.impl.connection.ClientConnectionManager;
import com.hazelcast.client.impl.management.ClientConnectionProcessListener;
import com.hazelcast.client.impl.management.ClientConnectionProcessListenerRegistry;
import com.hazelcast.client.impl.protocol.AuthenticationStatus;
import com.hazelcast.client.impl.protocol.ClientMessage;
import com.hazelcast.client.impl.protocol.codec.ClientAuthenticationCodec;
import com.hazelcast.client.impl.protocol.codec.ClientAuthenticationCustomCodec;
import com.hazelcast.client.impl.spi.ClientClusterService;
import com.hazelcast.client.impl.spi.impl.ClientExecutionServiceImpl;
import com.hazelcast.client.impl.spi.impl.ClientInvocation;
import com.hazelcast.client.impl.spi.impl.ClientInvocationFuture;
import com.hazelcast.client.impl.spi.impl.ClientPartitionServiceImpl;
import com.hazelcast.cluster.Address;
import com.hazelcast.cluster.Member;
import com.hazelcast.cluster.MembershipEvent;
import com.hazelcast.cluster.MembershipListener;
import com.hazelcast.config.InvalidConfigurationException;
import com.hazelcast.core.HazelcastException;
import com.hazelcast.core.LifecycleEvent.LifecycleState;
import com.hazelcast.function.BiFunctionEx;
import com.hazelcast.instance.BuildInfoProvider;
import com.hazelcast.internal.networking.Channel;
import com.hazelcast.internal.networking.ChannelErrorHandler;
import com.hazelcast.internal.networking.ChannelInitializer;
import com.hazelcast.internal.networking.nio.NioNetworking;
import com.hazelcast.internal.nio.ConnectionListener;
import com.hazelcast.internal.nio.ConnectionType;
import com.hazelcast.internal.serialization.InternalSerializationService;
import com.hazelcast.internal.util.AddressUtil;
import com.hazelcast.internal.util.EmptyStatement;
import com.hazelcast.internal.util.IterableUtil;
import com.hazelcast.internal.util.RuntimeAvailableProcessors;
import com.hazelcast.internal.util.UuidUtil;
import com.hazelcast.internal.util.executor.LoggingScheduledExecutor;
import com.hazelcast.internal.util.executor.PoolExecutorThreadFactory;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.LoggingService;
import com.hazelcast.nio.SocketInterceptor;
import com.hazelcast.security.Credentials;
import com.hazelcast.security.PasswordCredentials;
import com.hazelcast.security.TokenCredentials;
import com.hazelcast.spi.exception.TargetDisconnectedException;
import com.hazelcast.spi.properties.HazelcastProperties;
import com.hazelcast.spi.properties.HazelcastProperty;
import com.hazelcast.sql.impl.CoreQueryUtils;

import javax.annotation.Nonnull;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static com.hazelcast.client.config.ClientConnectionStrategyConfig.ReconnectMode.OFF;
import static com.hazelcast.client.config.ConnectionRetryConfig.DEFAULT_CLUSTER_CONNECT_TIMEOUT_MILLIS;
import static com.hazelcast.client.config.ConnectionRetryConfig.FAILOVER_CLIENT_DEFAULT_CLUSTER_CONNECT_TIMEOUT_MILLIS;
import static com.hazelcast.client.impl.connection.tcp.AuthenticationKeyValuePairConstants.ROUTING_MODE_NOT_SUPPORTED_MESSAGE;
import static com.hazelcast.client.impl.management.ManagementCenterService.MC_CLIENT_MODE_PROP;
import static com.hazelcast.client.impl.protocol.AuthenticationStatus.NOT_ALLOWED_IN_CLUSTER;
import static com.hazelcast.client.properties.ClientProperty.HEARTBEAT_TIMEOUT;
import static com.hazelcast.client.properties.ClientProperty.IO_BALANCER_INTERVAL_SECONDS;
import static com.hazelcast.client.properties.ClientProperty.IO_INPUT_THREAD_COUNT;
import static com.hazelcast.client.properties.ClientProperty.IO_OUTPUT_THREAD_COUNT;
import static com.hazelcast.client.properties.ClientProperty.IO_WRITE_THROUGH_ENABLED;
import static com.hazelcast.client.properties.ClientProperty.SHUFFLE_MEMBER_LIST;
import static com.hazelcast.core.LifecycleEvent.LifecycleState.CLIENT_CHANGED_CLUSTER;
import static com.hazelcast.internal.nio.IOUtil.closeResource;
import static com.hazelcast.internal.util.ExceptionUtil.rethrow;
import static com.hazelcast.internal.util.ThreadAffinity.newSystemThreadAffinity;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Implementation of {@link ClientConnectionManager}.
 */
@SuppressWarnings({"MethodLength", "NPathComplexity", "ClassDataAbstractionCoupling",
        "ClassFanOutComplexity", "MethodCount"})
public class TcpClientConnectionManager implements ClientConnectionManager, MembershipListener {

    /**
     * A private property to let users control the reconnection behavior of the client.
     * <p>
     * When enabled (true), the client will skip trying to connect to members in the last known
     * member list during reconnection attempts. Default is false.
     * <p>
     * This property might be handy for users who are using the client with SINGLE_MEMBER routing
     * mode and exposing their multi-member cluster via a single load balancer or node port
     * in Kubernetes. In that scenario, the client would normally try to reconnect to the
     * members in the last known member list first after disconnection, but that would fail
     * for sure, as the cluster members are not accessible from the client directly. In such
     * use cases, setting this to {@code true} might make the reconnections shorter, as the
     * client would directly try to connect to the configured load balancer/node port address
     * from the configuration.
     */
    public static final HazelcastProperty SKIP_MEMBER_LIST_DURING_RECONNECTION =
            new HazelcastProperty("hazelcast.client.internal.skip.member.list.during.reconnection", false);

    private static final int DEFAULT_IO_THREAD_COUNT = 3;
    private static final int EXECUTOR_CORE_POOL_SIZE = 10;
    private static final int SMALL_MACHINE_PROCESSOR_COUNT = 8;
    private static final int SQL_CONNECTION_RANDOM_ATTEMPTS = 10;

    protected final AtomicInteger connectionIdGen = new AtomicInteger();

    private final AtomicBoolean isAlive = new AtomicBoolean();
    private final ILogger logger;
    private final int connectionTimeoutMillis;
    private final HazelcastClientInstanceImpl client;
    private final Collection<ConnectionListener> connectionListeners = new CopyOnWriteArrayList<>();
    private final ClientConnectionProcessListenerRegistry connectionProcessListenerRunner;
    private final NioNetworking networking;

    private final long authenticationTimeout;
    private final String connectionType;
    private final UUID clientUuid = UuidUtil.newUnsecureUUID();
    // accessed only in synchronized block
    private final LinkedList<Integer> outboundPorts = new LinkedList<>();
    private final Set<String> labels;
    private final int outboundPortCount;
    private final boolean failoverConfigProvided;
    private final ScheduledExecutorService executor;
    private final boolean shuffleMemberList;
    private final WaitStrategy waitStrategy;
    private final ClusterDiscoveryService clusterDiscoveryService;

    private final boolean asyncStart;
    private final ReconnectMode reconnectMode;
    private final LoadBalancer loadBalancer;
    private final RoutingMode routingMode;
    private final boolean isTpcAwareClient;
    private final boolean skipMemberListDuringReconnection;
    private final ClientClusterService clientClusterService;
    private volatile Credentials currentCredentials;

    // following fields are updated inside synchronized(clientStateMutex)
    private final Object clientStateMutex = new Object();
    private final ConcurrentMap<UUID, TcpClientConnection> activeConnections = new ConcurrentHashMap<>();
    private volatile ClientState clientState = ClientState.INITIAL;
    private volatile boolean connectToClusterTaskSubmitted;
    private boolean establishedInitialClusterConnection;

    private enum ClientState {
        /**
         * Clients start with this state. Once a client connects to a cluster,
         * it directly switches to {@link #INITIALIZED_ON_CLUSTER} instead of
         * {@link #CONNECTED_TO_CLUSTER} because on startup a client has no
         * local state to send to the cluster.
         */
        INITIAL,

        /**
         * When a client switches to a new cluster, it moves to this state.
         * It means that the client has connected to a new cluster but not sent
         * its local state to the new cluster yet.
         */
        CONNECTED_TO_CLUSTER,

        /**
         * When a client sends its local state to the cluster it has connected,
         * it switches to this state.
         * <p>
         * Invocations are allowed in this state.
         */
        INITIALIZED_ON_CLUSTER,

        /**
         * When the client closes the last connection to the cluster it
         * currently connected to, it switches to this state.
         * <p>
         * In this state, ConnectToAllClusterMembersTask is not allowed to
         * attempt connecting to last known member list.
         */
        DISCONNECTED_FROM_CLUSTER,

        /**
         * We get into this state before we try to connect to next cluster. As
         * soon as the state is `SWITCHING_CLUSTER` any connection happened
         * without cluster switch intent are no longer allowed and will be
         * closed. Also, we will not allow ConnectToAllClusterMembersTask to
         * make any further connection attempts as long as the state is
         * `SWITCHING_CLUSTER`
         */
        SWITCHING_CLUSTER
    }

    @SuppressWarnings("ExecutableStatementCount")
    public TcpClientConnectionManager(HazelcastClientInstanceImpl client) {
        this.client = client;
        ClientConfig config = client.getClientConfig();
        HazelcastProperties properties = client.getProperties();
        this.loadBalancer = client.getLoadBalancer();
        this.labels = Collections.unmodifiableSet(config.getLabels());
        LoggingService loggingService = client.getLoggingService();
        this.logger = loggingService.getLogger(ClientConnectionManager.class);
        this.connectionType = properties.getBoolean(MC_CLIENT_MODE_PROP)
                ? ConnectionType.MC_JAVA_CLIENT : ConnectionType.JAVA_CLIENT;
        this.connectionTimeoutMillis = initConnectionTimeoutMillis();
        this.networking = initNetworking();
        this.outboundPorts.addAll(getOutboundPorts());
        this.outboundPortCount = outboundPorts.size();
        this.authenticationTimeout = properties.getPositiveMillisOrDefault(HEARTBEAT_TIMEOUT);
        this.failoverConfigProvided = client.getFailoverConfig() != null;
        this.executor = createExecutorService();
        this.clusterDiscoveryService = client.getClusterDiscoveryService();
        this.waitStrategy = initializeWaitStrategy(config);
        this.shuffleMemberList = properties.getBoolean(SHUFFLE_MEMBER_LIST);
        this.routingMode = decideRoutingMode(config);
        this.isTpcAwareClient = config.getTpcConfig().isEnabled();
        this.asyncStart = config.getConnectionStrategyConfig().isAsyncStart();
        this.reconnectMode = config.getConnectionStrategyConfig().getReconnectMode();
        this.connectionProcessListenerRunner = new ClientConnectionProcessListenerRegistry(client);
        this.skipMemberListDuringReconnection = properties.getBoolean(SKIP_MEMBER_LIST_DURING_RECONNECTION);
        this.clientClusterService = client.getClientClusterService();
    }

    private static RoutingMode decideRoutingMode(ClientConfig config) {
        ClientNetworkConfig networkConfig = config.getNetworkConfig();
        RoutingMode mode = networkConfig.getClusterRoutingConfig().getRoutingMode();

        if (config.getTpcConfig().isEnabled() && mode != RoutingMode.ALL_MEMBERS) {
            // This should be impossible due to validation in HazelcastClientInstanceImpl, but to catch just in case...
            throw new IllegalStateException("TPC is enabled but our RoutingMode is " + mode + " instead of ALL_MEMBERS!");
        }
        return mode;
    }

    private int initConnectionTimeoutMillis() {
        ClientNetworkConfig networkConfig = client.getClientConfig().getNetworkConfig();
        final int connTimeout = networkConfig.getConnectionTimeout();
        return connTimeout == 0 ? Integer.MAX_VALUE : connTimeout;
    }

    private ScheduledExecutorService createExecutorService() {
        ClassLoader classLoader = client.getClientConfig().getClassLoader();
        String name = client.getName();
        return new LoggingScheduledExecutor(logger, EXECUTOR_CORE_POOL_SIZE,
                new PoolExecutorThreadFactory(name + ".internal-", classLoader), (r, executor) -> {
            String message = "Internal executor rejected task: " + r + ", because client is shutting down...";
            logger.finest(message);
            throw new RejectedExecutionException(message);
        });
    }

    private Collection<Integer> getOutboundPorts() {
        ClientNetworkConfig networkConfig = client.getClientConfig().getNetworkConfig();
        Collection<Integer> outboundPorts = networkConfig.getOutboundPorts();
        Collection<String> outboundPortDefinitions = networkConfig.getOutboundPortDefinitions();
        return AddressUtil.getOutboundPorts(outboundPorts, outboundPortDefinitions);
    }

    public NioNetworking getNetworking() {
        return networking;
    }

    protected NioNetworking initNetworking() {
        HazelcastProperties properties = client.getProperties();

        int configuredInputThreads = properties.getInteger(IO_INPUT_THREAD_COUNT);
        int configuredOutputThreads = properties.getInteger(IO_OUTPUT_THREAD_COUNT);
        int inputThreads = findThreadCount(configuredInputThreads);
        int outputThreads = findThreadCount(configuredOutputThreads);

        return new NioNetworking(
                new NioNetworking.Context()
                        .loggingService(client.getLoggingService())
                        .metricsRegistry(client.getMetricsRegistry())
                        .threadNamePrefix(client.getName())
                        .errorHandler(new ClientChannelErrorHandler())
                        .inputThreadCount(inputThreads)
                        .inputThreadAffinity(newSystemThreadAffinity("hazelcast.client.io.input.thread.affinity"))
                        .outputThreadCount(outputThreads)
                        .outputThreadAffinity(newSystemThreadAffinity("hazelcast.client.io.output.thread.affinity"))
                        .balancerIntervalSeconds(properties.getInteger(IO_BALANCER_INTERVAL_SECONDS))
                        .writeThroughEnabled(properties.getBoolean(IO_WRITE_THROUGH_ENABLED))
                        .concurrencyDetection(client.getConcurrencyDetection())
        );
    }

    private int findThreadCount(int configuredThreadCount) {
        if (configuredThreadCount != -1) {
            return configuredThreadCount;
        }

        if (routingMode == RoutingMode.SINGLE_MEMBER) {
            return 1;
        }

        return (RuntimeAvailableProcessors.get() > SMALL_MACHINE_PROCESSOR_COUNT)
                ? DEFAULT_IO_THREAD_COUNT : 1;
    }

    private WaitStrategy initializeWaitStrategy(ClientConfig clientConfig) {
        ConnectionRetryConfig retryConfig = clientConfig
                .getConnectionStrategyConfig()
                .getConnectionRetryConfig();

        long clusterConnectTimeout = retryConfig.getClusterConnectTimeoutMillis();

        if (clusterConnectTimeout == DEFAULT_CLUSTER_CONNECT_TIMEOUT_MILLIS) {
            // If no value is provided, or set to -1 explicitly,
            // use a predefined timeout value for the failover client
            // and infinite for the normal client.
            if (failoverConfigProvided) {
                clusterConnectTimeout = FAILOVER_CLIENT_DEFAULT_CLUSTER_CONNECT_TIMEOUT_MILLIS;
            } else {
                clusterConnectTimeout = Long.MAX_VALUE;
            }
        }

        return new WaitStrategy(retryConfig.getInitialBackoffMillis(),
                retryConfig.getMaxBackoffMillis(),
                retryConfig.getMultiplier(),
                clusterConnectTimeout,
                retryConfig.getJitter(), logger);
    }

    public synchronized void start() {
        if (!isAlive.compareAndSet(false, true)) {
            return;
        }
        startNetworking();
    }

    public void tryConnectToAllClusterMembers(boolean sync) {
        if (routingMode == RoutingMode.SINGLE_MEMBER) {
            return;
        }

        if (sync) {
            for (Member member : client.getClientClusterService().getEffectiveMemberList()) {
                try {
                    getOrConnectToMember(member, false);
                } catch (Exception e) {
                    EmptyStatement.ignore(e);
                }
            }
        }

        executor.scheduleWithFixedDelay(new ConnectToAllClusterMembersTask(), 1, 1, TimeUnit.SECONDS);
    }

    protected void startNetworking() {
        networking.restart();
    }

    public synchronized void shutdown() {
        if (!isAlive.compareAndSet(true, false)) {
            return;
        }
        executor.shutdownNow();
        ClientExecutionServiceImpl.awaitExecutorTermination("cluster", executor, logger);
        for (ClientConnection connection : activeConnections.values()) {
            connection.close("Hazelcast client is shutting down", null);
        }

        stopNetworking();
        connectionListeners.clear();
        clusterDiscoveryService.current().destroy();
    }

    protected void stopNetworking() {
        networking.shutdown();
    }

    public void connectToCluster() {
        clusterDiscoveryService.current().start();

        if (asyncStart) {
            submitConnectToClusterTask();
        } else {
            doConnectToCluster();
        }
    }

    private void submitConnectToClusterTask() {
        // called in synchronized(clusterStateMutex)

        if (connectToClusterTaskSubmitted) {
            return;
        }

        executor.submit(() -> {
            try {
                doConnectToCluster();
                synchronized (clientStateMutex) {
                    connectToClusterTaskSubmitted = false;
                    if (activeConnections.isEmpty()) {
                        if (logger.isFineEnabled()) {
                            logger.warning("No connection to cluster: " + clientClusterService.getClusterId());
                        }

                        submitConnectToClusterTask();
                    }
                }
            } catch (Throwable e) {
                logger.warning("Could not connect to any cluster, shutting down the client", e);
                shutdownWithExternalThread();
            }
        });

        connectToClusterTaskSubmitted = true;
    }

    private void doConnectToCluster() {
        CandidateClusterContext currentContext = clusterDiscoveryService.current();

        logger.info("Trying to connect to cluster: " + currentContext.getClusterName());

        // try the current cluster
        if (doConnectToCandidateCluster(currentContext, false)) {
            connectionProcessListenerRunner.onClusterConnectionSucceeded(currentContext.getClusterName());
            return;
        }

        synchronized (clientStateMutex) {
            if (activeConnections.isEmpty()) {
                clientState = ClientState.SWITCHING_CLUSTER;
            } else {
                //ConnectToAllClusterMembersTask connected back to the same cluster
                //we don't need to switch cluster anymore.
                return;
            }
        }

        // try the next cluster
        if (clusterDiscoveryService.tryNextCluster(this::destroyCurrentClusterConnectionAndTryNextCluster)) {
            return;
        }

        // notify when no succeeded cluster connection is found
        String msg = client.getLifecycleService().isRunning()
                ? "Unable to connect to any cluster." : "Client is being shutdown.";
        throw new IllegalStateException(msg);
    }

    private Boolean destroyCurrentClusterConnectionAndTryNextCluster(CandidateClusterContext currentContext,
                                                                     CandidateClusterContext nextContext) {
        currentContext.destroy();

        client.onTryToConnectNextCluster();

        nextContext.start();

        ((ClientLoggingService) client.getLoggingService()).updateClusterName(nextContext.getClusterName());

        logger.info("Trying to connect to next cluster: " + nextContext.getClusterName());

        if (doConnectToCandidateCluster(nextContext, true)) {
            fireLifecycleEvent(CLIENT_CHANGED_CLUSTER);
            return true;
        }
        return false;
    }

    <A> ClientConnection connect(A target, Function<A, ClientConnection> getOrConnectFunction,
                                 Function<A, Address> addressTranslator) {
        try {
            logger.info("Trying to connect to " + target);
            return getOrConnectFunction.apply(target);
        } catch (InvalidConfigurationException | UnsupportedRoutingModeException
                 | UnsupportedClusterVersionException e) {
            logger.warning("Exception during initial connection to " + target + ": " + e);
            throw rethrow(e);
        } catch (ClientNotAllowedInClusterException e) {
            logger.warning("Exception during initial connection to " + target + ": " + e);
            throw e;
        } catch (TargetDisconnectedException e) {
            logger.warning("Exception during initial connection to " + target + ": " + e);
            connectionProcessListenerRunner.onRemoteClosedConnection(addressTranslator, target);
            return null;
        } catch (Exception e) {
            logger.warning("Exception during initial connection to " + target + ": " + e);
            connectionProcessListenerRunner.onConnectionAttemptFailed(addressTranslator, target);
            return null;
        }
    }

    private void fireLifecycleEvent(LifecycleState state) {
        LifecycleServiceImpl lifecycleService = (LifecycleServiceImpl) client.getLifecycleService();
        lifecycleService.fireLifecycleEvent(state);
    }

    private boolean doConnectToCandidateCluster(CandidateClusterContext context, boolean switchingToNextCluster) {
        Set<Address> triedAddresses = new HashSet<>();
        try {
            waitStrategy.reset();
            do {
                Set<Address> triedAddressesPerAttempt = new HashSet<>();

                // Try to connect to a member in the member list first
                if (tryConnectToMemberList(switchingToNextCluster, triedAddressesPerAttempt)) {
                    return true;
                }

                // Try to connect to a member given via config(explicit config/discovery mechanisms)
                for (Address address : getPossibleMemberAddresses(context.getAddressProvider())) {
                    checkClientActive();
                    if (!triedAddressesPerAttempt.add(address)) {
                        // If we can not add it means that it is already tried to be connected with the member list
                        continue;
                    }
                    connectionProcessListenerRunner.onAttemptingToConnectToTarget(this::translate, address);
                    ClientConnection connection = connect(address,
                            o -> getOrConnectToAddress(o, switchingToNextCluster),
                            this::translate);
                    if (connection != null) {
                        return true;
                    }
                }
                triedAddresses.addAll(triedAddressesPerAttempt);
                // If the address provider loads no addresses, then the above loop is not entered
                // and the lifecycle check is missing, hence we need to repeat the same check at this point.
                if (triedAddressesPerAttempt.isEmpty()) {
                    checkClientActive();
                }
            } while (waitStrategy.sleep());
        } catch (ClientNotAllowedInClusterException
                 | InvalidConfigurationException e) {
            logger.warning("Stopped trying on the cluster: " + context.getClusterName()
                    + " reason: " + e.getMessage());
        } catch (UnsupportedRoutingModeException e) {
            connectionProcessListenerRunner.onClusterConnectionFailed(context.getClusterName());
            logger.warning("Stopped trying on the cluster: " + context.getClusterName()
                    + " reason: " + e.getMessage());
            throw new InvalidConfigurationException(e.getMessage());
        } catch (UnsupportedClusterVersionException e) {
            connectionProcessListenerRunner.onClusterConnectionFailed(context.getClusterName());
            logger.warning("Stopped trying on the cluster: " + context.getClusterName()
                    + " reason: " + e.getMessage());
            throw new ClientNotAllowedInClusterException(e.getMessage());
        }

        connectionProcessListenerRunner.onClusterConnectionFailed(context.getClusterName());
        logger.info("Unable to connect to any address from the cluster with name: " + context.getClusterName()
                + ". The following addresses were tried: " + triedAddresses);
        return false;
    }

    /**
     * @return {@code true} if the connection attempt to member list succeeds,
     * {@code false} otherwise.
     */
    private boolean tryConnectToMemberList(boolean switchingToNextCluster, Set<Address> triedAddressesPerAttempt) {
        if (skipMemberListDuringReconnection) {
            // No need to try further if we want to skip connection
            // attempts to the last-known-member-list.
            return false;
        }

        List<Member> memberList = new ArrayList<>(client.getClientClusterService().getEffectiveMemberList());
        if (shuffleMemberList) {
            Collections.shuffle(memberList);
        }

        for (Member member : memberList) {
            checkClientActive();
            triedAddressesPerAttempt.add(member.getAddress());
            connectionProcessListenerRunner.onAttemptingToConnectToTarget(this::translate, member);
            ClientConnection connection = connect(member,
                    o -> getOrConnectToMember(o, switchingToNextCluster),
                    this::translate);
            if (connection != null) {
                return true;
            }
        }

        return false;
    }

    @Override
    public String getConnectionType() {
        return connectionType;
    }

    @Override
    public void checkInvocationAllowed() throws IOException {
        ClientState state = this.clientState;
        if (state == ClientState.INITIALIZED_ON_CLUSTER && !activeConnections.isEmpty()) {
            return;
        }

        if (state == ClientState.INITIAL) {
            if (asyncStart) {
                throw new HazelcastClientOfflineException();
            } else {
                throw new IOException("No connection found to cluster since the client is starting.");
            }
        } else if (ReconnectMode.ASYNC == reconnectMode) {
            throw new HazelcastClientOfflineException();
        } else {
            throw new IOException("No connection found to cluster.");
        }
    }

    @Override
    public boolean clientInitializedOnCluster() {
        return clientState == ClientState.INITIALIZED_ON_CLUSTER;
    }

    Collection<Address> getPossibleMemberAddresses(AddressProvider addressProvider) {
        Collection<Address> addresses = new LinkedHashSet<>();
        try {
            Addresses result = addressProvider.loadAddresses(connectionProcessListenerRunner);
            if (shuffleMemberList) {
                // The relative order between primary and secondary addresses should not be changed.
                // so we shuffle the lists separately and then add them to the final list so that
                // secondary addresses are not tried before all primary addresses have been tried.
                // Otherwise we can get startup delays.
                Collections.shuffle(result.primary());
                Collections.shuffle(result.secondary());
            }

            addresses.addAll(result.primary());
            addresses.addAll(result.secondary());
        } catch (NullPointerException e) {
            throw e;
        } catch (Exception e) {
            logger.warning("Exception from AddressProvider: " + addressProvider, e);
        }
        return addresses;
    }

    private void shutdownWithExternalThread() {
        new Thread(() -> {
            try {
                client.getLifecycleService().shutdown();
            } catch (Exception exception) {
                logger.severe("Exception during client shutdown", exception);
            }
        }, client.getName() + ".clientShutdown-").start();
    }

    @Override
    public Collection<ClientConnection> getActiveConnections() {
        return (Collection) activeConnections.values();
    }

    @Override
    public boolean isAlive() {
        return isAlive.get();
    }

    @Override
    public UUID getClientUuid() {
        return clientUuid;
    }

    @Override
    public ClientConnection getActiveConnection(@Nonnull UUID uuid) {
        return activeConnections.get(uuid);
    }

    TcpClientConnection getOrConnectToAddress(@Nonnull Address address, boolean switchingToNextCluster) {
        for (ClientConnection activeConnection : getActiveConnections()) {
            if (activeConnection.getRemoteAddress().equals(address)) {
                return (TcpClientConnection) activeConnection;
            }
        }

        address = translate(address);
        TcpClientConnection connection = createSocketConnection(address);
        AuthenticationResponse response = authenticateOnCluster(connection);
        return onAuthenticated(connection, response, switchingToNextCluster);
    }

    TcpClientConnection getOrConnectToMember(@Nonnull Member member, boolean switchingToNextCluster) {
        UUID uuid = member.getUuid();
        TcpClientConnection connection = activeConnections.get(uuid);
        if (connection != null) {
            return connection;
        }

        Address address = translate(member);
        connection = createSocketConnection(address);
        AuthenticationResponse response = authenticateOnCluster(connection);
        return onAuthenticated(connection, response, switchingToNextCluster);
    }

    private void fireConnectionEvent(TcpClientConnection connection, boolean isAdded) {
        if (!isAlive()) {
            return;
        }
        try {
            for (ConnectionListener listener : connectionListeners) {
                if (isAdded) {
                    executor.execute(() -> listener.connectionAdded(connection));
                } else {
                    executor.execute(() -> listener.connectionRemoved(connection));
                }
            }
        } catch (RejectedExecutionException e) {
            //RejectedExecutionException thrown when the client is shutting down
            EmptyStatement.ignore(e);
        }
    }

    private boolean useAnyOutboundPort() {
        return outboundPortCount == 0;
    }

    private int acquireOutboundPort() {
        if (outboundPortCount == 0) {
            return 0;
        }

        synchronized (outboundPorts) {
            Integer port = outboundPorts.removeFirst();
            outboundPorts.addLast(port);
            return port;
        }
    }

    private void bindSocketToPort(Socket socket) throws IOException {
        if (useAnyOutboundPort()) {
            SocketAddress socketAddress = new InetSocketAddress(0);
            socket.bind(socketAddress);
        } else {
            int retryCount = outboundPortCount * 2;
            IOException ex = null;
            for (int i = 0; i < retryCount; i++) {
                int port = acquireOutboundPort();
                if (port == 0) {
                    // fast-path for ephemeral range - no need to bind
                    return;
                }
                SocketAddress socketAddress = new InetSocketAddress(port);
                try {
                    socket.bind(socketAddress);
                    return;
                } catch (IOException e) {
                    ex = e;
                    logger.finest("Could not bind port[ " + port + "]: " + e.getMessage());
                }
            }

            if (ex != null) {
                throw ex;
            }
        }
    }


    @SuppressWarnings("unchecked")
    protected TcpClientConnection createSocketConnection(Address target) {
        CandidateClusterContext currentClusterContext = clusterDiscoveryService.current();
        SocketChannel socketChannel = null;
        try {
            socketChannel = SocketChannel.open();
            Socket socket = socketChannel.socket();

            bindSocketToPort(socket);
            ChannelInitializer channelInitializer = currentClusterContext.getChannelInitializer();
            Channel channel = networking.register(channelInitializer, socketChannel, true);
            channel.attributeMap().put(Address.class, target);

            InetSocketAddress inetSocketAddress = new InetSocketAddress(target.getInetAddress(), target.getPort());
            channel.connect(inetSocketAddress, connectionTimeoutMillis);

            TcpClientConnection connection = new TcpClientConnection(client, connectionIdGen.incrementAndGet(), channel);
            if (isTpcAwareClient) {
                connection.attributeMap().put(CandidateClusterContext.class, currentClusterContext);
            }

            socketChannel.configureBlocking(true);
            SocketInterceptor socketInterceptor = currentClusterContext.getSocketInterceptor();
            if (socketInterceptor != null) {
                socketInterceptor.onConnect(socket);
            }

            channel.start();
            return connection;
        } catch (Exception e) {
            closeResource(socketChannel);
            logger.finest(e);
            throw rethrow(e);
        }
    }

    private Channel createTpcChannel(Address address, TcpClientConnection connection) {
        SocketChannel socketChannel = null;
        try {
            socketChannel = SocketChannel.open();
            Socket socket = socketChannel.socket();

            // TODO: Outbound ports for TPC?
            bindSocketToPort(socket);

            ChannelInitializer channelInitializer = clusterDiscoveryService.current().getChannelInitializer();
            Channel channel = networking.register(channelInitializer, socketChannel, true);

            channel.addCloseListener(new TpcChannelCloseListener(client));

            ConcurrentMap attributeMap = channel.attributeMap();
            attributeMap.put(Address.class, address);
            attributeMap.put(TcpClientConnection.class, connection);
            attributeMap.put(TpcChannelClientConnectionAdapter.class, new TpcChannelClientConnectionAdapter(channel));

            InetSocketAddress socketAddress = new InetSocketAddress(address.getHost(), address.getPort());
            channel.connect(socketAddress, connectionTimeoutMillis);

            // TODO: Socket interceptor for TPC?
            channel.start();
            return channel;
        } catch (Exception e) {
            closeResource(socketChannel);
            logger.finest(e);
            throw rethrow(e);
        }
    }

    private Address translate(Member member) {
        return translate(member, AddressProvider::translate);
    }

    private Address translate(Address address) {
        return translate(address, AddressProvider::translate);
    }

    private <T> Address translate(T target, BiFunctionEx<AddressProvider, T, Address> translateFunction) {
        CandidateClusterContext currentContext = clusterDiscoveryService.current();
        AddressProvider addressProvider = currentContext.getAddressProvider();
        try {
            Address translatedAddress = translateFunction.apply(addressProvider, target);
            if (translatedAddress == null) {
                throw new HazelcastException("Address Provider " + addressProvider.getClass()
                        + " could not translate " + target);
            }
            return translatedAddress;
        } catch (Exception e) {
            logger.warning("Failed to translate " + target + " via address provider " + e.getMessage());
            throw rethrow(e);
        }
    }

    void onConnectionClose(TcpClientConnection connection) {
        client.getInvocationService().onConnectionClose(connection);
        Address endpoint = connection.getRemoteAddress();
        UUID memberUuid = connection.getRemoteUuid();
        if (endpoint == null) {
            logger.finest("Destroying %s, but it has end-point set to null -> not removing it from a connection map",
                    connection);
            return;
        }

        synchronized (clientStateMutex) {
            if (activeConnections.remove(memberUuid, connection)) {
                clientClusterService.getSubsetMembers().onConnectionRemoved(connection);
                logger.info("Removed connection to endpoint: " + endpoint + ":" + memberUuid + ", connection: " + connection);
                if (activeConnections.isEmpty()) {
                    if (clientState == ClientState.INITIALIZED_ON_CLUSTER) {
                        fireLifecycleEvent(LifecycleState.CLIENT_DISCONNECTED);
                    }

                    clientState = ClientState.DISCONNECTED_FROM_CLUSTER;
                    triggerClusterReconnection();
                }

                fireConnectionEvent(connection, false);
            } else {
                logger.finest("Destroying a connection, but there is no mapping %s:%s -> %s in the connection map.", endpoint,
                        memberUuid, connection);
            }
        }
    }

    private void triggerClusterReconnection() {
        if (reconnectMode == OFF) {
            logger.info("RECONNECT MODE is off. Shutting down the client.");
            shutdownWithExternalThread();
            return;
        }

        if (client.getLifecycleService().isRunning()) {
            try {
                submitConnectToClusterTask();
            } catch (RejectedExecutionException r) {
                shutdownWithExternalThread();
            }
        }
    }

    @Override
    public void addConnectionListener(ConnectionListener connectionListener) {
        connectionListeners.add(requireNonNull(connectionListener, "connectionListener cannot be null"));
    }

    @Override
    public void addClientConnectionProcessListener(ClientConnectionProcessListener listener) {
        connectionProcessListenerRunner.addListener(listener);
    }

    @Override
    public RoutingMode getRoutingMode() {
        return routingMode;
    }

    public Credentials getCurrentCredentials() {
        return currentCredentials;
    }

    public void reset() {
        for (TcpClientConnection activeConnection : activeConnections.values()) {
            activeConnection.close(null, new TargetDisconnectedException("Closing since client is switching cluster"));
        }
    }

    @Override
    public ClientConnection getRandomConnection() {
        // 1. Try getting the connection from the load balancer, if the client is not unisocket
        if (routingMode != RoutingMode.SINGLE_MEMBER) {
            Member member = loadBalancer.next();

            // Failed to get a member
            ClientConnection connection = member != null ? activeConnections.get(member.getUuid()) : null;
            if (connection != null) {
                return connection;
            }
        }

        // 2. Otherwise, iterate over connections and return
        // the first one. If there is no connection, return
        // null to indicate that no connection has been found
        Collection<ClientConnection> connections = getActiveConnections();
        return IterableUtil.getFirst(connections, null);
    }

    @Override
    public ClientConnection getConnectionForSql() {
        if (routingMode != RoutingMode.SINGLE_MEMBER) {
            // There might be a race - the chosen member might be just connected or disconnected - try a
            // couple of times, the memberOfLargerSameVersionGroup returns a random connection,
            // we might be lucky...
            for (int i = 0; i < SQL_CONNECTION_RANDOM_ATTEMPTS; i++) {
                Member member = CoreQueryUtils.memberOfLargerSameVersionGroup(
                        client.getClientClusterService().getEffectiveMemberList(), null);
                if (member == null) {
                    break;
                }
                ClientConnection connection = activeConnections.get(member.getUuid());
                if (connection != null) {
                    return connection;
                }
            }
        }

        // Otherwise iterate over connections and return the first one that's not to a lite member
        ClientConnection firstConnection = null;
        for (Map.Entry<UUID, TcpClientConnection> connectionEntry : activeConnections.entrySet()) {
            if (firstConnection == null) {
                firstConnection = connectionEntry.getValue();
            }
            UUID memberId = connectionEntry.getKey();
            Member member = client.getClientClusterService().getMember(memberId);
            if (member == null || member.isLiteMember()) {
                continue;
            }

            return connectionEntry.getValue();
        }

        // Failed to get a connection to a data member
        return firstConnection;
    }

    private AuthenticationResponse authenticateOnCluster(TcpClientConnection connection) {
        Address memberAddress = connection.getInitAddress();
        ClientMessage request = encodeAuthenticationRequest(memberAddress);
        ClientInvocationFuture future = new ClientInvocation(client, request, null, connection).invokeUrgent();
        try {
            return AuthenticationResponse.from(future.get(authenticationTimeout, MILLISECONDS));
        } catch (Exception e) {
            connection.close("Failed to authenticate connection", e);
            throw rethrow(e);
        }
    }


    /**
     * The returned connection could be different from the one passed to this method if there is already an existing
     * connection to the given member.
     */
    private TcpClientConnection onAuthenticated(TcpClientConnection connection,
                                                AuthenticationResponse response,
                                                boolean switchingToNextCluster) {
        synchronized (clientStateMutex) {
            checkAuthenticationResponse(connection, response);
            connection.setRemoteAddress(response.getAddress());
            connection.setRemoteUuid(response.getMemberUuid());
            connection.setClusterUuid(response.getClusterId());

            TcpClientConnection existingConnection = activeConnections.get(response.getMemberUuid());
            if (existingConnection != null) {
                connection.close("Duplicate connection to same member with uuid : " + response.getMemberUuid(), null);
                return existingConnection;
            }

            UUID newClusterId = response.getClusterId();
            UUID currentClusterId = clientClusterService.getClusterId();
            if (logger.isFineEnabled()) {
                logger.fine("Checking the cluster: " + newClusterId + ", current cluster: " + currentClusterId);
            }
            // `currentClusterId` is `null` only at the start of the client.
            // It is only set in this method below under `clientStateMutex`.
            // `currentClusterId` is set by master when a cluster is started.
            // `currentClusterId` is not preserved during HotRestart.
            // In split brain, both sides have the same `currentClusterId`
            boolean clusterIdChanged = currentClusterId != null && !newClusterId.equals(currentClusterId);
            if (clusterIdChanged) {
                checkClientStateOnClusterIdChange(connection, switchingToNextCluster);
                logger.warning("Switching from current cluster: " + currentClusterId + " to new cluster: " + newClusterId);
                client.onConnectionToNewCluster();
            }
            checkClientState(connection, switchingToNextCluster);

            List<Integer> tpcPorts = response.getTpcPorts();
            if (isTpcAwareClient && tpcPorts != null && !tpcPorts.isEmpty()) {
                connectTpcPorts(connection, tpcPorts, response.getTpcToken());
            }

            boolean connectionsEmpty = activeConnections.isEmpty();
            activeConnections.put(response.getMemberUuid(), connection);

            updateClusterViewMetaDataIfAvailable(connection, response);

            if (connectionsEmpty) {
                // The first connection that opens a connection to the new cluster should set `currentClusterId`.
                // This one will initiate `initializeClientOnCluster` if necessary.
                clientClusterService.onClusterConnect(newClusterId);

                if (establishedInitialClusterConnection) {
                    // In split brain, the client might connect to the one half
                    // of the cluster, and then later might reconnect to the
                    // other half, after the half it was connected to is
                    // completely dead. Since the cluster id is preserved in
                    // split brain scenarios, it is impossible to distinguish
                    // reconnection to the same cluster vs reconnection to the
                    // other half of the split brain. However, in the latter,
                    // we might need to send some state to the other half of
                    // the split brain (like Compact schemas or user code
                    // deployment classes). That forces us to send the client
                    // state to the cluster after the first cluster connection,
                    // regardless the cluster id is changed or not.
                    clientState = ClientState.CONNECTED_TO_CLUSTER;
                    executor.execute(() -> {
                        initializeClientOnCluster(newClusterId);
                        /*
                          We send statistics to the new cluster immediately to make clientVersion, isEnterprise and some other
                          fields available in Management Center as soon as possible. They are currently sent as part of client
                          statistics.

                          This method is called here instead of above on purpose because sending statistics require an active
                          connection to exist. Also, the client needs to be initialized on the new cluster in order for
                          invocations to be allowed.
                         */
                        client.collectAndSendStatsNow();
                    });
                } else {
                    establishedInitialClusterConnection = true;
                    if (!asyncStart) {
                        // For a sync start client, we will send the client state as the last statement of Client instance
                        // construction. Therefore, we can change client state and send the event here.
                        clientState = ClientState.INITIALIZED_ON_CLUSTER;
                        fireLifecycleEvent(LifecycleState.CLIENT_CONNECTED);
                    } else {
                        // For async clients, we return the client instance to the user without waiting for the client
                        // state to be sent. The state should be INITIALIZED_ON_CLUSTER after we send the client state.
                        // Also the CLIENT_CONNECTED event should be fired after the state is sent. initializeClientOnCluster
                        // will handle all of that.
                        executor.execute(() -> initializeClientOnCluster(clientClusterService.getClusterId()));
                    }
                }
            }

            logger.info("Authenticated with server " + response.getAddress() + ":" + response.getMemberUuid()
                    + ", server version: " + response.getServerHazelcastVersion()
                    + ", local address: " + connection.getLocalSocketAddress());

            fireConnectionEvent(connection, true);
        }

        // It could happen that this connection is already closed and
        // onConnectionClose() is called even before the synchronized block
        // above is executed. In this case, now we have a closed but registered
        // connection. We do a final check here to remove this connection
        // if needed.
        if (!connection.isAlive()) {
            onConnectionClose(connection);
        }
        return connection;
    }

    private void updateClusterViewMetaDataIfAvailable(TcpClientConnection connection, AuthenticationResponse response) {
        if (response.isMemberListVersionExists()) {
            client.getClientClusterService()
                    .handleMembersViewEvent(response.getMemberListVersion(), response.getMemberInfos(),
                            connection.getClusterUuid());
        }

        if (response.isPartitionListVersionExists()) {
            client.getClientPartitionService()
                    .handlePartitionsViewEvent(connection, response.getPartitions(), response.getPartitionListVersion());
        }

        if (response.isKeyValuePairsExists()) {
            Map<String, String> keyValuePairs = Collections.unmodifiableMap(response.getKeyValuePairs());
            // Pass along KV pairs for MULTI_MEMBER routing if required
            client.getClientClusterService()
                    .updateOnAuth(connection.getClusterUuid(), connection.getRemoteUuid(), keyValuePairs);

            // Pass CP leadership data to our tracking service
            client.getCPGroupViewService().initializeKnownLeaders(connection.getRemoteUuid(), connection.getRemoteAddress(),
                    keyValuePairs);
        } else {
            // If there are no key-value pairs, we have connected to a member that is older than 5_5
            // this is unsupported for clients operating with MULTI_MEMBER routing mode.
            if (routingMode == RoutingMode.MULTI_MEMBER) {
                throw new UnsupportedClusterVersionException(ROUTING_MODE_NOT_SUPPORTED_MESSAGE);
            }
        }
    }

    /**
     * Checks the client state against the intend of the callee(switchingToNextCluster)
     * closes the connection and throws exception if the authentication needs to be cancelled.
     */
    private void checkClientState(TcpClientConnection connection, boolean switchingToNextCluster) {
        if (clientState == ClientState.SWITCHING_CLUSTER && !switchingToNextCluster) {
            String reason = "There is a cluster switch in progress. "
                    + "This connection attempt initiated before the progress and not allowed to be authenticated.";
            connection.close(reason, null);
            throw new AuthenticationException(reason);
        }
        //Following state can not happen. There is only one path with `switchingToNextCluster` as true
        //and that path starts only when the old switch fails. There are no concurrent run of that path.
        if (clientState != ClientState.SWITCHING_CLUSTER && switchingToNextCluster) {
            String reason = "The cluster switch is already completed. "
                    + "This connection attempt is not allowed to be authenticated.";
            connection.close(reason, null);
            throw new AuthenticationException(reason);
        }
    }

    /**
     * Checks the response from the server to see if authentication needs to be continued,
     * closes the connection and throws exception if the authentication needs to be cancelled.
     */
    private void checkAuthenticationResponse(TcpClientConnection connection,
                                             AuthenticationResponse response) {
        AuthenticationStatus authenticationStatus = AuthenticationStatus.getById(response.getStatus());
        if (failoverConfigProvided && !response.isFailoverSupported()) {
            logger.warning("Cluster does not support failover. This feature is available in Hazelcast Enterprise");
            authenticationStatus = NOT_ALLOWED_IN_CLUSTER;
        }
        switch (authenticationStatus) {
            case AUTHENTICATED:
                connectionProcessListenerRunner.onAuthenticationSuccess(connection.getInitAddress());
                break;
            case CREDENTIALS_FAILED:
                AuthenticationException authException = new AuthenticationException("Authentication failed. The configured "
                        + "cluster name on the client (see ClientConfig.setClusterName()) does not match the one configured "
                        + "in the cluster or the credentials set in the Client security config could not be authenticated");
                connection.close("Failed to authenticate connection", authException);
                connectionProcessListenerRunner.onCredentialsFailed(connection.getInitAddress());
                throw authException;
            case NOT_ALLOWED_IN_CLUSTER:
                connectionProcessListenerRunner.onClientNotAllowedInCluster(connection.getInitAddress());
                ClientNotAllowedInClusterException notAllowedException =
                        new ClientNotAllowedInClusterException("Client is not allowed in the cluster");
                connection.close("Failed to authenticate connection", notAllowedException);
                throw notAllowedException;
            default:
                AuthenticationException exception =
                        new AuthenticationException("Authentication status code not supported. status: " + authenticationStatus);
                connection.close("Failed to authenticate connection", exception);
                throw exception;
        }
        ClientPartitionServiceImpl partitionService = (ClientPartitionServiceImpl) client.getClientPartitionService();
        if (!partitionService.checkAndSetPartitionCount(response.getPartitionCount())) {
            ClientNotAllowedInClusterException exception =
                    new ClientNotAllowedInClusterException("Client can not work with this cluster"
                            + " because it has a different partition count. "
                            + "Expected partition count: " + partitionService.getPartitionCount()
                            + ", Member partition count: " + response.getPartitionCount());
            connection.close("Failed to authenticate connection", exception);
            throw exception;
        }
    }

    private void checkClientStateOnClusterIdChange(TcpClientConnection connection, boolean switchingToNextCluster) {
        if (activeConnections.isEmpty()) {
            // We only have single connection established
            if (failoverConfigProvided) {
                // If failover is provided, and this single connection is established after failover logic kicks in
                // (checked via `switchingToNextCluster`), then it is OK to continue.
                // Otherwise, we force the failover logic to be used by throwing `ClientNotAllowedInClusterException`
                if (!switchingToNextCluster) {
                    String reason = "Force to hard cluster switch";
                    connection.close(reason, null);
                    throw new ClientNotAllowedInClusterException(reason);
                }
            }
        } else {
            // If there are other connections that means we have a connection to wrong cluster.
            // We should not stay connected to this new connection.
            // Note that in some racy scenarios we might close a connection that we can continue operating on.
            // In those cases, we rely on the fact that we will reopen the connections and continue. Here is one scenario:
            // 1. There were 2 members.
            // 2. The client is connected to the first one.
            // 3. While the client is trying to open the second connection, both members are restarted.
            // 4. In this case we will close the connection to the second member, thinking that it is not part of the
            // cluster we think we are in. We will reconnect to this member, and the connection is closed unnecessarily.
            // 5. The connection to the first cluster will be gone after that and we will initiate a reconnect to the cluster.
            String reason = "Connection does not belong to this cluster";
            connection.close(reason, null);
            throw new IllegalStateException(reason);
        }
    }

    private ClientMessage encodeAuthenticationRequest(Address toAddress) {
        InternalSerializationService ss = client.getSerializationService();
        String clientVersion = BuildInfoProvider.getBuildInfo().getVersion();

        CandidateClusterContext currentContext = clusterDiscoveryService.current();
        Credentials credentials = currentContext.getCredentialsFactory().newCredentials(toAddress);
        String clusterName = currentContext.getClusterName();
        currentCredentials = credentials;
        boolean cpDirectToLeader = client.getCPGroupViewService().isDirectToLeaderEnabled();
        byte routingModeByte = (byte) client.getConnectionManager().getRoutingMode().ordinal();
        if (credentials instanceof PasswordCredentials passwordCredentials) {
            return encodePasswordCredentialsRequest(clusterName, passwordCredentials,
                    ss.getVersion(), clientVersion, routingModeByte, cpDirectToLeader);
        } else {
            byte[] secretBytes;
            if (credentials instanceof TokenCredentials tokenCredentials) {
                secretBytes = tokenCredentials.getToken();
            } else {
                secretBytes = ss.toDataWithSchema(credentials).toByteArray();
            }

            return encodeCustomCredentialsRequest(clusterName, secretBytes, ss.getVersion(), clientVersion, routingModeByte,
                    cpDirectToLeader);
        }
    }

    private ClientMessage encodePasswordCredentialsRequest(String clusterName,
                                                           PasswordCredentials credentials,
                                                           byte serializationVersion,
                                                           String clientVersion, byte routingMode,
                                                           boolean cpDirectToLeader) {
        return ClientAuthenticationCodec.encodeRequest(clusterName, credentials.getName(),
                credentials.getPassword(), clientUuid, connectionType, serializationVersion,
                clientVersion, client.getName(), labels, routingMode, cpDirectToLeader);
    }

    private ClientMessage encodeCustomCredentialsRequest(String clusterName,
                                                         byte[] secretBytes,
                                                         byte serializationVersion,
                                                         String clientVersion,
                                                         byte routingMode,
                                                         boolean cpDirectToLeader) {
        return ClientAuthenticationCustomCodec.encodeRequest(clusterName, secretBytes, clientUuid,
                connectionType, serializationVersion, clientVersion, client.getName(), labels, routingMode, cpDirectToLeader);
    }

    protected void checkClientActive() {
        if (!client.getLifecycleService().isRunning()) {
            throw new HazelcastClientNotActiveException();
        }
    }

    private void initializeClientOnCluster(@Nonnull UUID targetClusterId) {
        // submitted inside synchronized(clientStateMutex)
        try {
            synchronized (clientStateMutex) {
                UUID clusterId = clientClusterService.getClusterId();
                if (!targetClusterId.equals(clusterId)) {
                    logger.warning("Won't send client state to cluster: " + targetClusterId
                            + " Because switched to a new cluster: " + clusterId);
                    return;
                }
            }

            client.sendStateToCluster();

            synchronized (clientStateMutex) {
                UUID clusterId = clientClusterService.getClusterId();
                if (targetClusterId.equals(clusterId)) {
                    if (logger.isFineEnabled()) {
                        logger.fine("Client state is sent to cluster: " + targetClusterId);
                    }

                    clientState = ClientState.INITIALIZED_ON_CLUSTER;
                    fireLifecycleEvent(LifecycleState.CLIENT_CONNECTED);
                } else if (logger.isFineEnabled()) {
                    logger.warning("Cannot set client state to " + ClientState.INITIALIZED_ON_CLUSTER
                            + " because current cluster id: " + clusterId + " is different than expected cluster id: "
                            + targetClusterId);
                }
            }
        } catch (Exception e) {
            String clusterName = clusterDiscoveryService.current().getClusterName();
            logger.warning("Failure during sending state to the cluster.", e);
            synchronized (clientStateMutex) {
                UUID clusterId = clientClusterService.getClusterId();
                if (targetClusterId.equals(clusterId)) {
                    if (logger.isFineEnabled()) {
                        logger.warning("Retrying sending state to the cluster: " + targetClusterId + ", name: " + clusterName);
                    }

                    executor.execute(() -> initializeClientOnCluster(targetClusterId));
                }
            }
        }
    }

    private void connectTpcPorts(TcpClientConnection connection, List<Integer> tpcPorts, byte[] tpcToken) {
        List<Integer> targetTpcPorts = getTargetTpcPorts(tpcPorts, client.getClientConfig().getTpcConfig());

        TpcChannelConnector connector = new TpcChannelConnector(
                client,
                authenticationTimeout,
                clientUuid,
                connection,
                targetTpcPorts,
                tpcToken,
                executor,
                this::createTpcChannel,
                client.getLoggingService());
        connector.initiate();
    }

    static List<Integer> getTargetTpcPorts(List<Integer> tpcPorts, ClientTpcConfig tpcConfig) {
        List<Integer> targetTpcPorts;
        int tpcConnectionCount = tpcConfig.getConnectionCount();
        if (tpcConnectionCount == 0 || tpcConnectionCount >= tpcPorts.size()) {
            // zero means connect to all.
            targetTpcPorts = tpcPorts;
        } else {
            // we make a copy of the tpc ports because items are removed.
            List<Integer> tpcPortsCopy = new LinkedList<>(tpcPorts);
            targetTpcPorts = new ArrayList<>(tpcConnectionCount);
            ThreadLocalRandom threadLocalRandom = ThreadLocalRandom.current();
            for (int k = 0; k < tpcConnectionCount; k++) {
                int index = threadLocalRandom.nextInt(tpcPortsCopy.size());
                targetTpcPorts.add(tpcPortsCopy.remove(index));
            }
        }
        return targetTpcPorts;
    }

    private class ClientChannelErrorHandler implements ChannelErrorHandler {
        @Override
        public void onError(Channel channel, Throwable cause) {
            if (channel == null) {
                logger.severe(cause);
            } else {
                if (cause instanceof OutOfMemoryError) {
                    logger.severe(cause);
                }

                ConcurrentMap attributeMap = channel.attributeMap();
                boolean isTpcChannel = attributeMap.containsKey(TpcChannelClientConnectionAdapter.class);
                ClientConnection connection = (ClientConnection) attributeMap.get(TcpClientConnection.class);
                if (isTpcChannel && connection.getTpcChannels() == null) {
                    // This means this is a TPC channel and the connection
                    // that owns this TPC channel is not operating on the
                    // TPC mode yet. However, we have faced with an issue
                    // on the channel, so it must be closed & any possible
                    // invocations made over it must be notified with an error.
                    // If we were to just close the connection below, this channel
                    // wouldn't be closed (because the 'tpcChannels' array
                    // is not assigned yet).
                    closeResource(channel);
                }

                if (cause instanceof EOFException) {
                    connection.close("Connection closed by the other side", cause);
                } else {
                    connection.close("Exception in " + connection + ", thread=" + Thread.currentThread().getName(), cause);
                }
            }
        }
    }

    /**
     * Schedules a task to open a connection if there is no connection for the member in the member list
     */
    private class ConnectToAllClusterMembersTask implements Runnable {

        private final Set<UUID> connectingAddresses = Collections.newSetFromMap(new ConcurrentHashMap<>());

        @Override
        public void run() {
            if (!client.getLifecycleService().isRunning()) {
                return;
            }

            for (Member member : client.getClientClusterService().getEffectiveMemberList()) {
                if (clientState == ClientState.SWITCHING_CLUSTER
                        || clientState == ClientState.DISCONNECTED_FROM_CLUSTER) {
                    // Best effort check to prevent this task from attempting to
                    // open a new connection when the client is either switching
                    // clusters or is not connected to any of the cluster members.
                    // In such occasions, only `doConnectToCandidateCluster`
                    // method should open new connections.
                    return;
                }

                UUID uuid = member.getUuid();
                if (activeConnections.get(uuid) != null) {
                    continue;
                }

                if (!connectingAddresses.add(uuid)) {
                    continue;
                }

                // submit a task for this address only if there is not
                // another connection attempt for it
                executor.submit(() -> {
                    try {
                        if (!client.getLifecycleService().isRunning()) {
                            return;
                        }
                        getOrConnectToMember(member, false);
                    } catch (Exception e) {
                        if (logger.isFineEnabled()) {
                            logger.warning("Could not connect to member " + uuid, e);
                        } else {
                            logger.warning("Could not connect to member " + uuid + ", reason " + e);
                        }
                    } finally {
                        connectingAddresses.remove(uuid);
                    }
                });
            }

            if (getRoutingMode() == RoutingMode.MULTI_MEMBER) {
                tryCloseConnectionsToMembersNotInSubset();
            }
        }
    }

    private void tryCloseConnectionsToMembersNotInSubset() {
        SubsetMembersView subsetMembersView = client.getClientClusterService().getSubsetMembers().getSubsetMembersView();
        Set<UUID> subsetMembers = subsetMembersView == null ? Collections.emptySet() : subsetMembersView.members();
        if (!haveAllSubsetMembersConnected(subsetMembers)) {
            return;
        }

        // remove connections to members not part of subset
        for (Member member : client.getClientClusterService().getMemberList()) {
            if (subsetMembers.contains(member.getUuid())) {
                // the member is part of the subset
                continue;
            }

            TcpClientConnection candidateForClosure = activeConnections.get(member.getUuid());
            if (candidateForClosure == null) {
                // no active connection to the member
                continue;
            }

            if (client.getInvocationService().isConnectionInUse(candidateForClosure)) {
                // connection is still in use despite the
                // member is not being part of the subset
                continue;
            }

            // connection can be closed
            candidateForClosure.close("Connection is closed because it is not relevant for the current MULTI_MEMBER "
                    + "configuration", null);
        }
    }

    private boolean haveAllSubsetMembersConnected(Collection<UUID> subsetMembers) {
        if (subsetMembers.isEmpty()) {
            return false;
        }

        for (UUID member : subsetMembers) {
            if (!activeConnections.containsKey(member)) {
                return false;
            }
        }

        return true;
    }

    @Override
    public void memberAdded(MembershipEvent membershipEvent) {

    }

    @Override
    public void memberRemoved(MembershipEvent membershipEvent) {
        Member member = membershipEvent.getMember();
        ClientConnection connection = getActiveConnection(member.getUuid());
        if (connection != null) {
            connection.close(null,
                    new TargetDisconnectedException("The client has closed the connection to this member,"
                            + " after receiving a member left event from the cluster. " + connection));
        }
    }
}
