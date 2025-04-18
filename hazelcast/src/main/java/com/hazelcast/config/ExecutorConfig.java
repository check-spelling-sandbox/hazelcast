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

package com.hazelcast.config;

import com.hazelcast.internal.config.ConfigDataSerializerHook;
import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.IdentifiedDataSerializable;
import com.hazelcast.nio.serialization.impl.Versioned;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Objects;

import static com.hazelcast.internal.cluster.Versions.V5_4;

/**
 * Contains the configuration for an {@link com.hazelcast.core.IExecutorService}.
 */
public class ExecutorConfig implements IdentifiedDataSerializable, NamedConfig, Versioned,
                                       UserCodeNamespaceAwareConfig<ExecutorConfig> {

    /**
     * The number of executor threads per Member for the Executor based on this configuration.
     */
    public static final int DEFAULT_POOL_SIZE = 16;

    /**
     * Capacity of queue.
     */
    public static final int DEFAULT_QUEUE_CAPACITY = Integer.MAX_VALUE;

    private String name = "default";

    private int poolSize = DEFAULT_POOL_SIZE;

    private int queueCapacity = DEFAULT_QUEUE_CAPACITY;

    private boolean statisticsEnabled = true;

    private String splitBrainProtectionName;
    private @Nullable String userCodeNamespace = DEFAULT_NAMESPACE;

    public ExecutorConfig() {
    }

    public ExecutorConfig(String name) {
        this.name = name;
    }

    public ExecutorConfig(String name, int poolSize) {
        this.name = name;
        this.poolSize = poolSize;
    }

    public ExecutorConfig(ExecutorConfig config) {
        this.name = config.name;
        this.poolSize = config.poolSize;
        this.queueCapacity = config.queueCapacity;
        this.statisticsEnabled = config.statisticsEnabled;
        this.splitBrainProtectionName = config.splitBrainProtectionName;
        this.userCodeNamespace = config.userCodeNamespace;
    }

    /**
     * Gets the name of the executor task.
     *
     * @return the name of the executor task
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * Sets the name of the executor task.
     *
     * @param name the name of the executor task
     * @return this executor config instance
     */
    @Override
    public ExecutorConfig setName(String name) {
        this.name = name;
        return this;
    }

    /**
     * Gets the number of executor threads per member for the executor.
     *
     * @return the number of executor threads per member for the executor
     */
    public int getPoolSize() {
        return poolSize;
    }

    /**
     * Sets the number of executor threads per member for the executor.
     *
     * @param poolSize the number of executor threads per member for the executor
     * @return this executor config instance
     */
    public ExecutorConfig setPoolSize(final int poolSize) {
        if (poolSize <= 0) {
            throw new IllegalArgumentException("poolSize must be positive");
        }
        this.poolSize = poolSize;
        return this;
    }

    /**
     * Gets the queue capacity of the executor task. 0 means {@code Integer.MAX_VALUE}.
     *
     * @return Queue capacity of the executor task. 0 means {@code Integer.MAX_VALUE}
     */
    public int getQueueCapacity() {
        return queueCapacity;
    }

    /**
     * Sets the queue capacity of the executor task. 0 means {@code Integer.MAX_VALUE}.
     *
     * @param queueCapacity Queue capacity of the executor task. 0 means {@code Integer.MAX_VALUE}
     * @return this executor config instance
     */
    public ExecutorConfig setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
        return this;
    }

    /**
     * @return {@code true} if statistics gathering is enabled
     * on the executor task (default), {@code false} otherwise
     */
    public boolean isStatisticsEnabled() {
        return statisticsEnabled;
    }

    /**
     * Enables or disables statistics gathering on the executor task.
     *
     * @param statisticsEnabled {@code true} if statistics gathering is enabled on the executor task, {@code false} otherwise
     * @return this executor config instance
     */
    public ExecutorConfig setStatisticsEnabled(boolean statisticsEnabled) {
        this.statisticsEnabled = statisticsEnabled;
        return this;
    }

    /**
     * Returns the split brain protection name for operations.
     *
     * @return the split brain protection name
     */
    public String getSplitBrainProtectionName() {
        return splitBrainProtectionName;
    }

    /**
     * Sets the split brain protection name for operations.
     *
     * @param splitBrainProtectionName the split brain protection name
     * @return the updated configuration
     */
    public ExecutorConfig setSplitBrainProtectionName(String splitBrainProtectionName) {
        this.splitBrainProtectionName = splitBrainProtectionName;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Nullable
    public String getUserCodeNamespace() {
        return userCodeNamespace;
    }

    /**
     * Associates the provided Namespace Name with this structure for {@link ClassLoader} awareness.
     * <p>
     * The behaviour of setting this to {@code null} is outlined in the documentation for
     * {@link UserCodeNamespaceAwareConfig#DEFAULT_NAMESPACE}.
     *
     * @param userCodeNamespace The ID of the Namespace to associate with this structure.
     * @return the updated {@link ExecutorConfig} instance
     * @since 5.4
     */
    @Override
    public ExecutorConfig setUserCodeNamespace(@Nullable String userCodeNamespace) {
        this.userCodeNamespace = userCodeNamespace;
        return this;
    }

    @Override
    public String toString() {
        return "ExecutorConfig{"
                + "name='" + name + '\''
                + ", poolSize=" + poolSize
                + ", queueCapacity=" + queueCapacity
                + ", splitBrainProtectionName=" + splitBrainProtectionName
                + ", userCodeNamespace=" + userCodeNamespace
                + '}';
    }

    @Override
    public int getFactoryId() {
        return ConfigDataSerializerHook.F_ID;
    }

    @Override
    public int getClassId() {
        return ConfigDataSerializerHook.EXECUTOR_CONFIG;
    }

    @Override
    public void writeData(ObjectDataOutput out) throws IOException {
        out.writeString(name);
        out.writeInt(poolSize);
        out.writeInt(queueCapacity);
        out.writeBoolean(statisticsEnabled);
        out.writeString(splitBrainProtectionName);

        // RU_COMPAT_5_3
        if (out.getVersion().isGreaterOrEqual(V5_4)) {
            out.writeString(userCodeNamespace);
        }
    }

    @Override
    public void readData(ObjectDataInput in) throws IOException {
        name = in.readString();
        poolSize = in.readInt();
        queueCapacity = in.readInt();
        statisticsEnabled = in.readBoolean();
        splitBrainProtectionName = in.readString();

        // RU_COMPAT_5_3
        if (in.getVersion().isGreaterOrEqual(V5_4)) {
            userCodeNamespace = in.readString();
        }
    }

    @Override
    @SuppressWarnings("checkstyle:npathcomplexity")
    public final boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ExecutorConfig that)) {
            return false;
        }

        if (poolSize != that.poolSize) {
            return false;
        }
        if (queueCapacity != that.queueCapacity) {
            return false;
        }
        if (statisticsEnabled != that.statisticsEnabled) {
            return false;
        }
        if (!Objects.equals(splitBrainProtectionName, that.splitBrainProtectionName)) {
            return false;
        }
        if (!Objects.equals(userCodeNamespace, that.userCodeNamespace)) {
            return false;
        }
        return name.equals(that.name);
    }

    @Override
    public final int hashCode() {
        int result = name.hashCode();
        result = 31 * result + poolSize;
        result = 31 * result + queueCapacity;
        result = 31 * result + (statisticsEnabled ? 1 : 0);
        result = 31 * result + (splitBrainProtectionName != null ? splitBrainProtectionName.hashCode() : 0);
        result = 31 * result + (userCodeNamespace != null ? userCodeNamespace.hashCode() : 0);
        return result;
    }
}
