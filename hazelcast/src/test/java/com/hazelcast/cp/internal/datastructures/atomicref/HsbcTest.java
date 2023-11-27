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

package com.hazelcast.cp.internal.datastructures.atomicref;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.cp.IAtomicReference;
import com.hazelcast.cp.internal.HazelcastRaftTestSupport;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HsbcTest extends HazelcastRaftTestSupport {
    private HazelcastInstance[] instances;

    @Before
    public void before() {
        instances = newInstances(3);
    }

    @Test
    public void testCasFinger() {
        IAtomicReference<String> atomicReference = instances[0].getCPSubsystem().getAtomicReference("hsbc");
        atomicReference.set("granville");
        assertTrue(atomicReference.compareAndSetFingerprint("granville", "barnett"));
        assertFalse(atomicReference.compareAndSetFingerprint("granville", "barnett"));
        assertEquals("barnett", atomicReference.get());
    }
}
