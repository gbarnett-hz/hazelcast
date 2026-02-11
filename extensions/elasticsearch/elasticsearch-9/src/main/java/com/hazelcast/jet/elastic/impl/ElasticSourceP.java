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

package com.hazelcast.jet.elastic.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SlicedScroll;
import co.elastic.clients.elasticsearch.core.ClearScrollRequest;
import co.elastic.clients.elasticsearch.core.ClearScrollResponse;
import co.elastic.clients.elasticsearch.core.ScrollRequest;
import co.elastic.clients.elasticsearch.core.ScrollResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.TransportOptions;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.hazelcast.function.FunctionEx;
import com.hazelcast.jet.JetException;
import com.hazelcast.jet.Traverser;
import com.hazelcast.jet.Traversers;
import com.hazelcast.jet.core.AbstractProcessor;
import com.hazelcast.logging.ILogger;
import org.apache.http.HttpHost;
import org.elasticsearch.client.Node;
import org.elasticsearch.client.RestClient;

import javax.annotation.Nonnull;
import java.util.List;

import static com.hazelcast.jet.elastic.impl.RetryUtils.withRetry;
import static java.util.Collections.singleton;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

final class ElasticSourceP<T> extends AbstractProcessor {

    private final ElasticSourceConfiguration<T> configuration;
    private final List<Shard> shards;
    private final ElasticClientProxyFactory clientProxyFactory;
    private RestClient lowLevelClient;
    private RestClientTransport transport;
    private ElasticsearchClient client;
    private ILogger logger;
    private Traverser<T> traverser;

    private ElasticScrollTraverser scrollTraverser;

    @FunctionalInterface
    interface ElasticClientProxyFactory {
        ElasticClientProxy create(ElasticsearchClient client);
    }

    interface ElasticClientProxy {
        SearchResponse<JsonData> search(SearchRequest request, TransportOptions options) throws Exception;

        ScrollResponse<JsonData> scroll(ScrollRequest request, TransportOptions options) throws Exception;

        ClearScrollResponse clearScroll(ClearScrollRequest request, TransportOptions options) throws Exception;
    }

    static final class DefaultElasticClientProxy implements ElasticClientProxy {
        private final ElasticsearchClient client;

        DefaultElasticClientProxy(ElasticsearchClient client) {
            this.client = client;
        }

        @Override
        public SearchResponse<JsonData> search(SearchRequest request, TransportOptions options) throws Exception {
            return withOptions(options).search(request, JsonData.class);
        }

        @Override
        public ScrollResponse<JsonData> scroll(ScrollRequest request, TransportOptions options) throws Exception {
            return withOptions(options).scroll(request, JsonData.class);
        }

        @Override
        public ClearScrollResponse clearScroll(ClearScrollRequest request, TransportOptions options) throws Exception {
            return withOptions(options).clearScroll(request);
        }

        private ElasticsearchClient withOptions(TransportOptions options) {
            return options == null ? client : client.withTransportOptions(options);
        }
    }

    ElasticSourceP(ElasticSourceConfiguration<T> configuration, List<Shard> shards) {
        this(configuration, shards, DefaultElasticClientProxy::new);
    }

    ElasticSourceP(
            ElasticSourceConfiguration<T> configuration,
            List<Shard> shards,
            ElasticClientProxyFactory clientProxyFactory
    ) {
        this.configuration = configuration;
        this.shards = shards;
        this.clientProxyFactory = clientProxyFactory;
    }

    @Override
    protected void init(@Nonnull Context context) throws Exception {
        super.init(context);

        logger = context.logger();
        logger.fine("init");

        lowLevelClient = configuration.clientFn().get().build();
        transport = new RestClientTransport(lowLevelClient, new JacksonJsonpMapper());
        client = new ElasticsearchClient(transport);

        SearchRequest.Builder requestBuilder = configuration.searchRequestFn().get()
                .scroll(s -> s.time(configuration.scrollKeepAlive()));

        if (configuration.isSlicingEnabled()) {
            if (configuration.isCoLocatedReadingEnabled()) {
                int sliceId = context.localProcessorIndex();
                int totalSlices = context.localParallelism();
                if (totalSlices > 1) {
                    logger.fine("Slice id=%s, max=%s", sliceId, totalSlices);
                    requestBuilder.slice(new SlicedScroll.Builder().id(String.valueOf(sliceId)).max(totalSlices).build());
                }
            } else {
                int sliceId = context.globalProcessorIndex();
                int totalSlices = context.totalParallelism();
                if (totalSlices > 1) {
                    logger.fine("Slice id=%s, max=%s", sliceId, totalSlices);
                    requestBuilder.slice(new SlicedScroll.Builder().id(String.valueOf(sliceId)).max(totalSlices).build());
                }
            }
        }

        if (configuration.isCoLocatedReadingEnabled()) {
            logger.fine("Assigned shards: %s", shards);
            if (shards.isEmpty()) {
                traverser = Traversers.empty();
                return;
            }

            Node node = createLocalElasticNode();
            lowLevelClient.setNodes(singleton(node));
            String preference = "_shards:" + shards.stream().map(shard -> String.valueOf(shard.getShard())).collect(joining(","))
                    + "|_only_local";
            requestBuilder.preference(preference);
        }

        SearchRequest searchRequest = requestBuilder.build();
        scrollTraverser = new ElasticScrollTraverser(
                configuration,
                this.clientProxyFactory.create(client),
                searchRequest,
                logger
        );
        traverser = scrollTraverser.map(configuration.mapToItemFn());
    }

    private Node createLocalElasticNode() {
        List<String> ips = shards.stream().map(Shard::getHttpAddress).distinct().collect(toList());
        if (ips.size() != 1) {
            throw new JetException("Should receive shards from single local node, got: " + ips);
        }
        String localIp = ips.get(0);
        return new Node(HttpHost.create(localIp));
    }

    @Override
    public boolean isCooperative() {
        return false;
    }

    @Override
    public boolean complete() {
        return emitFromTraverser(traverser);
    }

    @Override
    public void close() {
        if (scrollTraverser != null) {
            scrollTraverser.close();
        }

        try {
            if (transport != null) {
                transport.close();
            }
        } catch (Exception e) {
            logger.fine("Could not close client", e);
        }
    }

    static class ElasticScrollTraverser implements Traverser<Hit<JsonData>> {

        private final ILogger logger;
        private final ElasticClientProxy clientProxy;
        private final FunctionEx<? super Object, TransportOptions> optionsFn;
        private final String scrollKeepAlive;
        private final int retries;

        private List<Hit<JsonData>> hits;
        private int nextHit;
        private String scrollId;

        ElasticScrollTraverser(
                ElasticSourceConfiguration<?> configuration,
                ElasticClientProxy clientProxy,
                SearchRequest searchRequest,
                ILogger logger
        ) {
            this.clientProxy = clientProxy;
            this.optionsFn = configuration.optionsFn();
            this.scrollKeepAlive = configuration.scrollKeepAlive();
            this.retries = configuration.retries();
            this.logger = logger;

            try {
                SearchResponse<JsonData> response = withRetry(
                        () -> clientProxy.search(searchRequest, optionsFn.apply(searchRequest)),
                        retries
                );
                hits = response.hits().hits();
                scrollId = response.scrollId();
                if (scrollId == null && !hits.isEmpty()) {
                    throw new IllegalStateException("Unexpected response: returned scrollId is null, but hits are not empty.");
                }

                if (response.hits().total() != null) {
                    logger.fine("Initialized scroll with scrollId %s, total results %s, %s",
                            scrollId,
                            response.hits().total().relation(),
                            response.hits().total().value());
                }
            } catch (Exception e) {
                throw new JetException("Could not execute SearchRequest to Elastic", e);
            }
        }

        @Override
        public Hit<JsonData> next() {
            if (hits.isEmpty()) {
                scrollId = null;
                return null;
            }

            if (nextHit >= hits.size()) {
                try {
                    ScrollRequest scrollRequest = new ScrollRequest.Builder()
                            .scrollId(scrollId)
                            .scroll(s -> s.time(scrollKeepAlive))
                            .build();

                    ScrollResponse<JsonData> searchResponse = withRetry(
                            () -> clientProxy.scroll(scrollRequest, optionsFn.apply(scrollRequest)),
                            retries
                    );
                    hits = searchResponse.hits().hits();
                    if (hits.isEmpty()) {
                        return null;
                    }
                    nextHit = 0;
                } catch (Exception e) {
                    throw new JetException("Could not execute ScrollRequest to Elastic", e);
                }
            }

            return hits.get(nextHit++);
        }

        public void close() {
            if (scrollId != null) {
                clearScroll(scrollId);
                scrollId = null;
            }
        }

        private void clearScroll(String scrollId) {
            ClearScrollRequest clearScrollRequest = new ClearScrollRequest.Builder().scrollId(scrollId).build();
            try {
                ClearScrollResponse response = withRetry(
                        () -> clientProxy.clearScroll(clearScrollRequest, optionsFn.apply(clearScrollRequest)),
                        retries
                );

                if (Boolean.TRUE.equals(response.succeeded())) {
                    logger.fine("Succeeded clearing %s scrolls", response.numFreed());
                } else {
                    logger.warning("Clearing scroll " + scrollId + " failed");
                }
            } catch (Exception e) {
                logger.fine("Could not clear scroll with scrollId=" + scrollId, e);
            }
        }
    }
}
