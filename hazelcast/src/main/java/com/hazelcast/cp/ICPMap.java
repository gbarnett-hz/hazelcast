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

package com.hazelcast.cp;

import com.hazelcast.map.BaseMap;
import com.hazelcast.query.Predicate;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public interface ICPMap<K, V> extends BaseMap<K, V> {
    @Override
    default boolean containsKey(Object key) {
        return get(key) != null;
    }

    default void set(K key, V value) {
        put(key, value);
    }

    @Override
    default V put(K key, V value) {
        Map<K, V> result = putAll(Map.of(key, value));
        return result.get(key);
    }

    Map<K, V> putAll(Map<K, V> keyValues);

    @Override
    default V get(Object key) {
        Map<Object, V> result = getAll(Set.of(key));
        return result.get(key);
    }

    Map<Object, V> getAll(Set<Object> keys);

    @Override
    default V putIfAbsent(K key, V value) {
        Map<K, V> result = putIfAbsentAll(Map.of(key, value));
        return result.get(key);
    }

    Map<K, V> putIfAbsentAll(Map<K, V> keyValues);

    @Override
    default V replace(K key, V value) {
        Map<K, V> result = replaceAll(Map.of(key, value));
        return result.get(key);
    }

    Map<K, V> replaceAll(Map<K, V> keyValues);

    @Override
    default V remove(Object key) {
        Map<Object, V> result = removeAll(Set.of(key));
        return result.get(key);
    }
    Map<Object, V> removeAll(Set<Object> keys);

    @Override
    default void delete(Object key) {
        remove(key);
    }

    default void deleteAll(Set<Object> keys) {
        removeAll(keys);
    }

    @Override
    default boolean isEmpty() {
        return 0 == size();
    }

    @Override
    default V put(K key, V value, long ttl, TimeUnit timeunit) {
        throw new UnsupportedOperationException();
    }

    // test-{replace,remove} --- TODO
    @Override
    default boolean replace(K key, V oldValue, V newValue) {
        throw new UnsupportedOperationException();
    }

    @Override
    default boolean remove(Object key, Object value) {
        throw new UnsupportedOperationException();
    }

    // the following not done because they seem very expensive; simple to implement
    // but they can lead to heavy reads
    @Override
    default Set<K> keySet() {
        throw new UnsupportedOperationException();
    }

    @Override
    default Set<K> keySet(Predicate<K, V> predicate) {
        throw new UnsupportedOperationException();
    }

    @Override
    default Collection<V> values() {
        throw new UnsupportedOperationException();
    }

    @Override
    default Collection<V> values(Predicate<K, V> predicate) {
        throw new UnsupportedOperationException();
    }
}
