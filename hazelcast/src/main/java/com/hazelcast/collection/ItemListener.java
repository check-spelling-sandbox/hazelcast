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

package com.hazelcast.collection;

import com.hazelcast.spi.annotation.NamespacesSupported;

import java.util.EventListener;

/**
 * Item listener for {@link IQueue}, {@link ISet} and {@link IList}
 *
 * @param <E> item
 */
@NamespacesSupported
public interface ItemListener<E> extends EventListener {

    /**
     * Invoked when an item is added.
     *
     * @param item the added item
     */
    void itemAdded(ItemEvent<E> item);

    /**
     * Invoked when an item is removed.
     *
     * @param item the removed item.
     */
    void itemRemoved(ItemEvent<E> item);
}
