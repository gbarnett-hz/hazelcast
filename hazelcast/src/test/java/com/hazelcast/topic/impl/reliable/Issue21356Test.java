package com.hazelcast.topic.impl.reliable;

import com.hazelcast.config.Config;
import com.hazelcast.config.ReliableTopicConfig;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.test.HazelcastParallelClassRunner;
import com.hazelcast.test.HazelcastTestSupport;
import com.hazelcast.topic.TopicOverloadPolicy;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.LongSupplier;

import static org.junit.Assert.assertEquals;

// https://github.com/hazelcast/hazelcast/issues/21356
@RunWith(HazelcastParallelClassRunner.class)
public class Issue21356Test extends HazelcastTestSupport {
    private static final int SECOND_LOOKS = 1_000;
    private HazelcastInstance instance;
    private String topicName;

    @Before
    public void before() {
        Config config = new Config();
        String topicConfigName = "default";
        ReliableTopicConfig topicConfig = new ReliableTopicConfig();
        topicConfig.setName(topicConfigName);
        topicConfig.setStatisticsEnabled(true);
        topicConfig.setReadBatchSize(10);
        topicConfig.setTopicOverloadPolicy(TopicOverloadPolicy.BLOCK);
        Map<String, ReliableTopicConfig> topicConfigs = new HashMap<>();
        topicConfigs.put(topicConfigName, topicConfig);
        config.setReliableTopicConfigs(topicConfigs);
        instance = createHazelcastInstance(config);
        topicName = randomName();
    }

    @Test
    public void testTopicPublish() {
        instance.getTopic(topicName).publish(randomName());
        long published = instance.getTopic(topicName).getLocalTopicStats().getPublishOperationCount();
        long received = instance.getTopic(topicName).getLocalTopicStats().getReceiveOperationCount();
        assertEquals(1, published);
        assertEquals(0, received); // we haven't consumed anything so this should remain 0
    }

    @Test
    public void testTopicPublishConsume() throws ExecutionException, InterruptedException {
        final String actualPayload = randomName();
        final CompletableFuture<String> expectedPayload = new CompletableFuture<>();
        instance.getTopic(topicName).addMessageListener(message -> expectedPayload.complete((String) message.getMessageObject()));
        instance.getTopic(topicName).publish(actualPayload);
        final long published = instance.getTopic(topicName).getLocalTopicStats().getPublishOperationCount();
        assertEquals(1, published);

        assertEquals(actualPayload, expectedPayload.get());
        final long received = instance.getTopic(topicName).getLocalTopicStats().getReceiveOperationCount();
        assertEquals(1, received);
    }

    // there doesn't seem to be a programmatic way to deterministically read the updated counts like there does via the non-reliable
    // topic, hence the nastiness -- for example, those tests would fail here given the same testing strategy. The local stats for
    // reliable topic are being incremented asynchronously via AbstractRingBufferOperation#reportReliableTopic{Publish,Received}
    private static void waitUntilObserved(final long value, final LongSupplier supplier) {
        while (supplier.getAsLong() != value) {
        }
    }

    // second look in case funny things are happening and it's not actually working as expected (in my eyes)
    private static void secondLook(final int looks, long expectedPublished, long expectedReceived,
                                   final LongSupplier publishedOpCount, final LongSupplier receivedOpCount) {
        for (int look = 0; look < looks; look++) {
            assertEquals(expectedPublished, publishedOpCount.getAsLong());
            if (receivedOpCount != null) {
                assertEquals(expectedReceived, receivedOpCount.getAsLong());
            }
        }
    }

    @Test
    public void testReliableTopicPublish() {
        instance.getReliableTopic(topicName).publish(randomName());
        final long expectedPublished = 1;
        final LongSupplier publishedOpCount = () -> instance.getReliableTopic(topicName).getLocalTopicStats().getPublishOperationCount();
        waitUntilObserved(expectedPublished, publishedOpCount);
        secondLook(SECOND_LOOKS, expectedPublished, 0, publishedOpCount, null);
    }


    @Test
    public void testReliableTopicPublishConsume() throws ExecutionException, InterruptedException {
        final String actualPayload = randomName();
        final CompletableFuture<String> expectedPayload = new CompletableFuture<>();
        instance.getReliableTopic(topicName).addMessageListener(message -> expectedPayload.complete((String) message.getMessageObject()));
        instance.getReliableTopic(topicName).publish(actualPayload);

        final long expectedPublished = 1;
        final long expectedReceived = 1;

        final LongSupplier publishedOpCount = () -> instance.getReliableTopic(topicName).getLocalTopicStats().getPublishOperationCount();
        final LongSupplier receivedOpCount = () -> instance.getReliableTopic(topicName).getLocalTopicStats().getReceiveOperationCount();
        waitUntilObserved(expectedPublished, publishedOpCount);
        waitUntilObserved(expectedReceived, receivedOpCount);
        secondLook(SECOND_LOOKS, expectedPublished, expectedReceived, publishedOpCount, receivedOpCount);
        assertEquals(actualPayload, expectedPayload.get());
    }
}
