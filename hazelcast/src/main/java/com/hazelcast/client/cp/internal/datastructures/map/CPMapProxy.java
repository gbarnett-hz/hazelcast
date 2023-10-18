/*
 * Copyright (c) 2008-2023, Hazelcast, Inc. All Rights Reserved.
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

package com.hazelcast.client.cp.internal.datastructures.map;

import com.hazelcast.cp.CPMap;
import com.hazelcast.query.Predicate;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.TimeUnit;

// client proxy
public class CPMapProxy<K, V> implements CPMap<K, V> {
    @Override
    public String getPartitionKey() {
        return null;
    }

    @Override
    public String getName() {
        return null;
    }

    @Override
    public String getServiceName() {
        return null;
    }

    @Override
    public void destroy() {

    }

    @Override
    public boolean containsKey(Object key) {
        return false;
    }

    @Override
    public V get(Object key) {
        return null;
    }

    @Override
    public V put(K key, V value) {
        return null;
    }

    @Override
    public V put(K key, V value, long ttl, TimeUnit timeunit) {
        return null;
    }

    @Override
    public void set(K key, V value) {

    }

    @Override
    public V putIfAbsent(K key, V value) {
        return null;
    }

    @Override
    public V replace(K key, V value) {
        return null;
    }

    @Override
    public boolean replace(K key, V oldValue, V newValue) {
        return false;
    }

    @Override
    public V remove(Object key) {
        return null;
    }

    @Override
    public void delete(Object key) {

    }

    @Override
    public boolean remove(Object key, Object value) {
        return false;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public int size() {
        return 0;
    }

    @Override
    public Set<K> keySet() {
        return null;
    }

    @Override
    public Set<K> keySet(Predicate<K, V> predicate) {
        return null;
    }

    @Override
    public Collection<V> values() {
        return null;
    }

    @Override
    public Collection<V> values(Predicate<K, V> predicate) {
        return null;
    }
}
