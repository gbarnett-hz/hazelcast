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

package com.hazelcast.cp.internal.datastructures.map.proxy;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.cp.ICPMap;
import com.hazelcast.cp.internal.HazelcastRaftTestSupport;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ICPMapServiceTest extends HazelcastRaftTestSupport {
    private static final String MAP1 = "map1";
    private static final String KEY = "k";
    private static final String VALUE = "v";
    private HazelcastInstance[] members;

    private static ICPMap<String, String> getMap(HazelcastInstance instance, String map) {
        return instance.getCPSubsystem().getMap(map);
    }

    private ICPMap<String, String> getTestMap() {
        return getMap(members[0], MAP1);
    }

    @Before
    public void before() {
        members = newInstances(3);
    }

    @Test
    public void testPutAndGet() {
        ICPMap<String, String> map = getTestMap();
        assertNotNull(map);
        assertTrue(map instanceof CPMapProxy);
        assertEquals(MAP1, map.getName());
        assertEquals(CPMapService.SERVICE_NAME, map.getServiceName());
        String firstValue = "v1";
        String previousValue = map.put(KEY, firstValue);
        assertNull(previousValue);
        String secondValue = "v2";
        previousValue = map.put(KEY, secondValue);
        assertEquals(firstValue, previousValue);
        assertEquals(secondValue, map.get(KEY));
    }

    @Test
    public void testPutAllAndGetAll() {
        ICPMap<String, String> map = getTestMap();
        Map<String, String> kvs = getKvs(100);
        Map<String, String> puts = map.putAll(kvs);
        assertEquals(kvs.size(), puts.size());
        for (Map.Entry<String, String> e : puts.entrySet()) {
            assertNull(e.getValue());
        }
        Set<Object> keys = new HashSet<>(kvs.keySet());
        Map<Object, String> gets = map.getAll(keys);
        assertEquals(new HashMap<>(kvs), gets);
    }

    @Test
    public void testSet() {
        ICPMap<String, String> map = getTestMap();
        map.set(KEY, VALUE);
        assertEquals(VALUE, map.get(KEY));
    }

    @Test
    public void testContainsKey() {
        ICPMap<String, String> map = getTestMap();
        map.set(KEY, VALUE);
        assertTrue(map.containsKey(KEY));
    }

    @Test
    public void testRemove() {
        ICPMap<String, String> map = getTestMap();
        map.set(KEY, VALUE);
        assertTrue(map.containsKey(KEY));
        assertEquals(VALUE, map.remove(KEY));
        assertFalse(map.containsKey(KEY));
    }

    @Test
    public void testDelete() {
        ICPMap<String, String> map = getTestMap();
        map.set(KEY, VALUE);
        assertTrue(map.containsKey(KEY));
        map.delete(KEY);
        assertFalse(map.containsKey(KEY));
    }

    @Test
    public void testRemoveAll() {
        ICPMap<String, String> map = getTestMap();
        int kvs = 1_000;
        Map<String, String> put = map.putAll(getKvs(kvs));
        assertEquals(kvs, put.size());
        assertEquals(kvs, map.size());
        Map<Object, String> removed = map.removeAll(new HashSet<>(put.keySet()));
        assertEquals(kvs, removed.size());
        assertTrue(map.isEmpty());
    }

    @Test
    public void testDeleteAll() {
        ICPMap<String, String> map = getTestMap();
        int kvs = 1_000;
        Map<String, String> put = map.putAll(getKvs(kvs));
        assertEquals(kvs, put.size());
        assertEquals(kvs, map.size());
        map.deleteAll(new HashSet<>(put.keySet()));
        assertTrue(map.isEmpty());
    }

    @Test
    public void testSize() {
        ICPMap<String, String> map = getTestMap();
        assertEquals(0, map.size());
        int expectedMapSize = 100;
        Map<String, String> kvs = getKvs(expectedMapSize);
        map.putAll(kvs);
        assertEquals(kvs.size(), map.size());
    }

    @Test
    public void testIsEmpty() {
        ICPMap<String, String> map = getTestMap();
        assertTrue(map.isEmpty());
        map.put(KEY, VALUE);
        assertFalse(map.isEmpty());
        map.delete(KEY);
        assertTrue(map.isEmpty());
    }

    private static Map<String, String> getKvs(int size) {
        Map<String, String> m = new HashMap<>(size);
        for (int i = 0; i < size; i++) {
            m.put("k" + i, "v" + i);
        }
        return m;
    }
}