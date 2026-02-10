/*
 * Copyright 2026 Hazelcast Inc.
 *
 * Licensed under the Hazelcast Community License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://hazelcast.com/hazelcast-community-license
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.jet.elastic;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.hazelcast.collection.IList;
import com.hazelcast.config.Config;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.function.SupplierEx;
import com.hazelcast.jet.Job;
import com.hazelcast.jet.config.JobConfig;
import com.hazelcast.jet.pipeline.Pipeline;
import com.hazelcast.jet.test.IgnoreInJenkinsOnWindows;
import com.hazelcast.jet.test.SerialTest;
import com.hazelcast.test.HazelcastSerialClassRunner;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.hazelcast.test.DockerTestUtil.assumeDockerEnabled;
import static com.hazelcast.test.HazelcastTestSupport.smallInstanceConfig;
import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base class for running Elasticsearch connector tests.
 */
@RunWith(HazelcastSerialClassRunner.class)
@Category({SerialTest.class, IgnoreInJenkinsOnWindows.class})
public abstract class BaseElasticTest {

    protected static final int BATCH_SIZE = 42;

    protected RestClient lowLevelClient;
    protected RestClientTransport transport;
    protected ElasticsearchClient elasticClient;
    protected HazelcastInstance hz;
    protected IList<String> results;

    @BeforeClass
    public static void beforeClassCheckDocker() {
        assumeDockerEnabled();
    }

    @Before
    public void setUpBase() throws Exception {
        if (elasticClient == null) {
            lowLevelClient = elasticClientSupplier().get().build();
            transport = new RestClientTransport(lowLevelClient, new JacksonJsonpMapper());
            elasticClient = new ElasticsearchClient(transport);
        }
        cleanElasticData();

        if (hz == null) {
            hz = createHazelcastInstance();
        }
        results = hz.getList("results");
        results.clear();
    }

    @After
    public void tearDown() throws Exception {
        if (transport != null) {
            try {
                transport.close();
            } finally {
                transport = null;
                elasticClient = null;
                lowLevelClient = null;
            }
        }
    }

    protected SupplierEx<RestClientBuilder> elasticClientSupplier() {
        return ElasticSupport.elasticClientSupplier();
    }

    protected SupplierEx<RestClientBuilder> elasticPipelineClientSupplier() {
        return ElasticSupport.elasticClientSupplier();
    }

    protected abstract HazelcastInstance createHazelcastInstance();

    protected void initShardedIndex(String index) throws IOException {
        createShardedIndex(index, 3, 0);
        indexBatchOfDocuments(index);
    }

    protected void createShardedIndex(String index, int shards, int replicas) throws IOException {
        elasticClient.indices().create(c -> c
                .index(index)
                .settings(s -> s
                        .numberOfShards(String.valueOf(shards))
                        .numberOfReplicas(String.valueOf(replicas))
                )
        );
    }

    protected void cleanElasticData() {
        try {
            elasticClient.indices().delete(d -> d.index("*"));
        } catch (Exception ignored) {
            // ignore missing indices
        }
    }

    protected void deleteDocuments() throws IOException {
        Query query = Query.of(q -> q.matchAll(m -> m));
        elasticClient.deleteByQuery(d -> d.index("*").query(query).refresh(true));
    }

    protected List<String> indexBatchOfDocuments(String index) {
        return indexBatchOfDocuments(index, CommonElasticSourcesTest.BATCH_SIZE);
    }

    protected List<String> indexBatchOfDocuments(String index, int batchSize) {
        List<Map<String, Object>> docs = new ArrayList<>();
        for (int i = 0; i < batchSize; i++) {
            docs.add(Map.of("title", "document " + i));
        }
        return indexDocuments(index, docs);
    }

    protected String indexDocument(String index, Map<String, Object> document) {
        return indexDocuments(index, List.of(document)).get(0);
    }

    protected List<String> indexDocuments(String index, List<Map<String, Object>> documents) {
        try {
            BulkResponse response = elasticClient.bulk(b -> {
                b.refresh(Refresh.True);
                for (Map<String, Object> document : documents) {
                    b.operations(op -> op.index(i -> i.index(index).document(document)));
                }
                return b;
            });
            return response.items().stream().map(item -> item.id()).collect(Collectors.toList());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected void refreshIndex() throws IOException {
        elasticClient.indices().refresh(r -> r.index("my-index"));
    }

    protected void assertSingleDocument() throws IOException {
        assertSingleDocument("id", "Frantisek");
    }

    protected void assertSingleDocument(String id, String name) throws IOException {
        List<Hit<JsonData>> hits = elasticClient.search(s -> s.index("my-index"), JsonData.class).hits().hits();
        assertThat(hits).hasSize(1);
        Map<String, Object> document = hits.get(0).source().to(Map.class);
        assertThat(document).contains(
                entry("id", id),
                entry("name", name)
        );
    }

    protected void assertNoDocuments(String index) throws IOException {
        List<Hit<JsonData>> hits = elasticClient.search(s -> s.index(index), JsonData.class).hits().hits();
        assertThat(hits).hasSize(0);
    }

    protected void submitJob(Pipeline p) {
        Job job = submitJobNoWait(p);
        job.join();
    }

    protected Job submitJobNoWait(Pipeline p) {
        JobConfig config = new JobConfig();

        Class<?> clazz = this.getClass();
        while (clazz.getSuperclass() != null) {
            config.addClass(clazz);
            clazz = clazz.getSuperclass();
        }

        return hz.getJet().newJob(p, config);
    }

    protected static Config config() {
        Config config = smallInstanceConfig();
        config.getJetConfig().setResourceUploadEnabled(true);
        return config;
    }
}
