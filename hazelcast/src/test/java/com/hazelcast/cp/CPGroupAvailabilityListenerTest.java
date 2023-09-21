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

import com.hazelcast.config.Config;
import com.hazelcast.config.ListenerConfig;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.cp.event.CPGroupAvailabilityEvent;
import com.hazelcast.cp.event.CPGroupAvailabilityListener;
import com.hazelcast.cp.event.impl.CPGroupAvailabilityEventGracefulImpl;
import com.hazelcast.cp.internal.HazelcastRaftTestSupport;
import com.hazelcast.test.HazelcastParallelClassRunner;
import com.hazelcast.test.annotation.ParallelJVMTest;
import com.hazelcast.test.annotation.QuickTest;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(HazelcastParallelClassRunner.class)
@Category({QuickTest.class, ParallelJVMTest.class})
public class CPGroupAvailabilityListenerTest extends HazelcastRaftTestSupport {

    @Test
    public void whenRegisteredInConfig_thenReceiveEvents() {
        CountingDownCPGroupAvailabilityListener listener = new CountingDownCPGroupAvailabilityListener(1, 1);
        int cpCount = 3;
        int groupSize = 3;

        Config config = createConfig(cpCount, groupSize);
        config.addListenerConfig(new ListenerConfig().setImplementation(listener));

        HazelcastInstance[] instances = factory.newInstances(config, cpCount);
        waitUntilCPDiscoveryCompleted(instances);

        instances[2].getLifecycleService().terminate();
        assertOpenEventually(listener.availabilityLatch);
    }

    @Test
    public void whenMemberTerminated_thenReceiveEvents() throws Exception {
        CountingDownCPGroupAvailabilityListener listener = new CountingDownCPGroupAvailabilityListener(1, 1);

        HazelcastInstance[] instances = newInstances(5);
        instances[1].getCPSubsystem().addGroupAvailabilityListener(listener);

        instances[0].getLifecycleService().terminate();
        assertOpenEventually(listener.availabilityLatch);
        assertEquals(1, listener.availabilityEventCount.get());

        instances[4].getLifecycleService().terminate();
        assertFalse(listener.majorityLatch.await(1, TimeUnit.SECONDS));
        assertEquals(2, listener.availabilityEventCount.get());

        instances[2].getLifecycleService().terminate();
        assertOpenEventually(listener.majorityLatch);
        assertEquals(1, listener.majorityEventCount.get());
    }

    @Test
    public void whenListenerDeregistered_thenNoEventsReceived() {
        CountingDownCPGroupAvailabilityListener listener = new CountingDownCPGroupAvailabilityListener(1, 1);

        HazelcastInstance[] instances = newInstances(3);
        UUID id = instances[1].getCPSubsystem().addGroupAvailabilityListener(listener);

        instances[0].getLifecycleService().terminate();
        assertEqualsEventually(1, listener.availabilityEventCount);

        assertTrue(instances[1].getCPSubsystem().removeGroupAvailabilityListener(id));

        instances[2].getLifecycleService().terminate();
        assertTrueAllTheTime(() -> assertEquals(0, listener.majorityEventCount.get()), 3);
    }

    @Test
    public void whenMultipleListenersRegistered_thenAllReceiveEvents() {
        HazelcastInstance[] instances = newInstances(3);

        CountingDownCPGroupAvailabilityListener listener1 = new CountingDownCPGroupAvailabilityListener(1, 1);
        instances[1].getCPSubsystem().addGroupAvailabilityListener(listener1);
        CountingDownCPGroupAvailabilityListener listener2 = new CountingDownCPGroupAvailabilityListener(1, 1);
        instances[1].getCPSubsystem().addGroupAvailabilityListener(listener2);

        instances[0].getLifecycleService().terminate();
        assertOpenEventually(listener1.availabilityLatch);
        assertOpenEventually(listener2.availabilityLatch);
        assertEquals(1, listener1.availabilityEventCount.get());
        assertEquals(1, listener2.availabilityEventCount.get());
    }

    public static class CountingDownCPGroupAvailabilityListener implements CPGroupAvailabilityListener {
        public final CountDownLatch availabilityLatch;
        public final CountDownLatch majorityLatch;
        public final AtomicInteger availabilityEventCount = new AtomicInteger();
        public final AtomicInteger majorityEventCount = new AtomicInteger();

        public CountingDownCPGroupAvailabilityListener(int groupAvailability, int groupMajority) {
            this.availabilityLatch = new CountDownLatch(groupAvailability);
            this.majorityLatch = new CountDownLatch(groupMajority);
        }

        @Override
        public void availabilityDecreased(CPGroupAvailabilityEvent event) {
            assertTrue("Event: " + event, event.isMajorityAvailable());
            availabilityEventCount.incrementAndGet();
            availabilityLatch.countDown();
        }

        @Override
        public void majorityLost(CPGroupAvailabilityEvent event) {
            assertFalse("Event: " + event, event.isMajorityAvailable());
            majorityEventCount.incrementAndGet();
            majorityLatch.countDown();
        }
    }

    static class GracefulShutdownAvailabilityListener implements CPGroupAvailabilityListener {
        private final List<CPMember> memberShutdown;

        GracefulShutdownAvailabilityListener() {
            memberShutdown = Collections.synchronizedList(new ArrayList<>());
        }

        @Override
        public void availabilityDecreased(CPGroupAvailabilityEvent event) {
            if (event instanceof CPGroupAvailabilityEventGracefulImpl) {
                memberShutdown.addAll(event.getUnavailableMembers());
            }
        }

        @Override
        public void majorityLost(CPGroupAvailabilityEvent event) {
            if (event instanceof CPGroupAvailabilityEventGracefulImpl) {
                memberShutdown.addAll(event.getUnavailableMembers());
            }
        }

        void resetState() {
            memberShutdown.clear();
        }
    }

    @Test
    public void whenMemberShutdown_thenReceiveEventsGracefulEvents_5() {
        whenMemberShutdown_thenReceiveGracefulEvents(5);
    }

    @Test
    public void whenMemberShutdown_thenReceiveEventsGracefulEvents_3() {
        whenMemberShutdown_thenReceiveGracefulEvents(3);
    }

    public void whenMemberShutdown_thenReceiveGracefulEvents(int cpNodeCount) {
        GracefulShutdownAvailabilityListener listener = new GracefulShutdownAvailabilityListener();
        HazelcastInstance[] instances = newInstances(cpNodeCount);
        // (1) attach listener to each instance
        for (HazelcastInstance instance : instances) {
            instance.getCPSubsystem().addGroupAvailabilityListener(listener);
        }

        int instancesToShutdown = instances.length - 1;
        int actualInstancesShutdown = 0;
        // (2) cycle through each instance except the last, shutting each down in-turn
        AtomicInteger minimumEventsExpectedToBeHandled = new AtomicInteger();
        for (int i = 0, membersToDiscount = 1; i < instancesToShutdown; i++, membersToDiscount++) {
            listener.resetState();

            CPMember expectedCpMemberShutdown = instances[i].getCPSubsystem().getLocalCPMember();
            // min. expected number of handlers invoked is always (-membersToDiscount) because we have scenarios like the following
            // m1(shutting-down),m2,m3
            // here, we expect at least two members to handle the graceful event (m2, m3); m1 can still handle the event but there's
            // no certainty in this as it's in the process of shutting down
            minimumEventsExpectedToBeHandled.set(instances.length - membersToDiscount);
            instances[i].getLifecycleService().shutdown();
            assertTrueEventually(() -> assertTrue(listener.memberShutdown.size() >= minimumEventsExpectedToBeHandled.get()));
            sleepMillis(2_000); // time to settle to see if the shutdown member actually handled the event or not
            // there's always a (+1) because the instance shutdown may/may not have had time to handle the event when it was
            // shutting down
            assertTrue(listener.memberShutdown.size() <= (minimumEventsExpectedToBeHandled.get() + 1));

            // check that the member shutdown event carried the CP member we expected to be shutdown
            for (CPMember cpMemberShutdown : listener.memberShutdown) {
                assertEquals(expectedCpMemberShutdown, cpMemberShutdown);
            }

            actualInstancesShutdown++;
        }

        assertEquals(instancesToShutdown, actualInstancesShutdown);

        // we don't shut down the remaining member
    }
}
