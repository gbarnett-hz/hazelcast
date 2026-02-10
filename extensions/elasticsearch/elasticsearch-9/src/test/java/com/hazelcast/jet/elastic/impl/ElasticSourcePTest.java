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

import co.elastic.clients.elasticsearch.core.ClearScrollResponse;
import co.elastic.clients.elasticsearch.core.ScrollRequest;
import co.elastic.clients.elasticsearch.core.ScrollResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.TotalHitsRelation;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.transport.TransportOptions;
import com.hazelcast.function.FunctionEx;
import com.hazelcast.jet.core.test.TestSupport;
import com.hazelcast.jet.elastic.impl.Shard.Prirep;
import com.hazelcast.test.HazelcastParallelClassRunner;
import com.hazelcast.test.annotation.ParallelJVMTest;
import com.hazelcast.test.annotation.QuickTest;
import org.elasticsearch.client.Node;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.util.Collection;
import java.util.List;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.util.Lists.newArrayList;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(HazelcastParallelClassRunner.class)
@Category({QuickTest.class, ParallelJVMTest.class})
public class ElasticSourcePTest {

    private static final String HIT_SOURCE = "{\"name\":\"Frantisek\"}";
    private static final String HIT_SOURCE2 = "{\"name\":\"Vladimir\"}";
    private static final String SCROLL_ID = "random-scroll-id";
    private static final String KEEP_ALIVE = "42m";

    private RestClient lowLevelClient;
    private RestClientBuilder clientBuilder;
    private ElasticSourceP.ElasticClientProxy mockProxy;
    private SearchResponse<JsonData> initialResponse;

    @Before
    public void setUp() throws Exception {
        lowLevelClient = mock(RestClient.class);
        clientBuilder = mock(RestClientBuilder.class);
        when(clientBuilder.build()).thenReturn(lowLevelClient);

        mockProxy = mock(ElasticSourceP.ElasticClientProxy.class, RETURNS_DEEP_STUBS);
        initialResponse = searchResponse(SCROLL_ID, emptyList(), 0);
        ClearScrollResponse clearScrollResponse = successfulClearScroll();
        when(mockProxy.search(any(), any())).thenReturn(initialResponse);
        when(mockProxy.clearScroll(any(), any())).thenReturn(clearScrollResponse);
    }

    private TestSupport runProcessor() throws Exception {
        return runProcessorWithInjectedProxy(request -> null, emptyList(), false, false);
    }

    private TestSupport runProcessor(FunctionEx<? super Object, TransportOptions> optionsFn) throws Exception {
        return runProcessorWithInjectedProxy(optionsFn, emptyList(), false, false);
    }

    private TestSupport runProcessorWithCoLocation(List<Shard> shards) throws Exception {
        return runProcessorWithInjectedProxy(request -> null, shards, false, true);
    }

    private TestSupport runProcessorWithInjectedProxy(FunctionEx<? super Object, TransportOptions> optionsFn,
                                                      List<Shard> shards,
                                                      boolean slicing,
                                                      boolean coLocatedReading) throws Exception {
        ElasticSourceConfiguration<String> configuration = new ElasticSourceConfiguration<>(
                () -> clientBuilder,
                () -> new SearchRequest.Builder().index("*"),
                optionsFn,
                hit -> hit.source().toJson().toString(),
                slicing,
                coLocatedReading,
                KEEP_ALIVE,
                5
        );

        return TestSupport.verifyProcessor(() -> new ElasticSourceP<>(configuration, shards, client -> mockProxy))
                .disableSnapshots();
    }

    @Test
    public void when_runProcessor_then_executeSearchRequestWithScroll() throws Exception {
        when(mockProxy.search(any(), any())).thenReturn(searchResponse(SCROLL_ID, emptyList(), 0));

        TestSupport support = runProcessor();

        support.expectOutput(emptyList());

        ArgumentCaptor<SearchRequest> captor = forClass(SearchRequest.class);
        verify(mockProxy).search(captor.capture(), any());

        SearchRequest request = captor.getValue();
        assertThat(request.scroll().time()).isEqualTo(KEEP_ALIVE);
    }

    @Test
    public void when_runProcessorWithOptionsFn_then_shouldUseOptionsFnForSearchRequest() throws Exception {
        TransportOptions searchOptions = mock(TransportOptions.class);

        TestSupport support = runProcessor(request -> request instanceof SearchRequest ? searchOptions : null);

        support.expectOutput(emptyList());

        verify(mockProxy).search(any(), same(searchOptions));
    }

    @Test
    public void given_singleHit_when_runProcessor_then_produceSingleHit() throws Exception {
        when(mockProxy.search(any(), any())).thenReturn(searchResponse(SCROLL_ID, newArrayList(hit(HIT_SOURCE)), 1));
        when(mockProxy.scroll(any(), any())).thenReturn(scrollResponse(SCROLL_ID, emptyList(), 1));

        TestSupport support = runProcessor();

        support.expectOutput(newArrayList(HIT_SOURCE));
    }

    @Test
    public void givenMultipleResults_when_runProcessor_then_useScrollIdInFollowupScrollRequest() throws Exception {
        when(mockProxy.search(any(), any())).thenReturn(searchResponse(SCROLL_ID, newArrayList(hit(HIT_SOURCE)), 3));
        when(mockProxy.scroll(any(), any())).thenReturn(
                scrollResponse(SCROLL_ID, newArrayList(hit(HIT_SOURCE2)), 3),
                scrollResponse(SCROLL_ID, emptyList(), 3)
        );

        TestSupport support = runProcessor();

        support.expectOutput(newArrayList(HIT_SOURCE, HIT_SOURCE2));

        ArgumentCaptor<ScrollRequest> captor = forClass(ScrollRequest.class);
        verify(mockProxy, times(2)).scroll(captor.capture(), any());

        ScrollRequest request = captor.getValue();
        assertThat(request.scrollId()).isEqualTo(SCROLL_ID);
        assertThat(request.scroll().time()).isEqualTo(KEEP_ALIVE);
    }

    @Test
    public void when_runProcessorWithOptionsFn_then_shouldUseOptionsFnForScrollRequest() throws Exception {
        TransportOptions scrollOptions = mock(TransportOptions.class);

        when(mockProxy.search(any(), any())).thenReturn(searchResponse(SCROLL_ID, newArrayList(hit(HIT_SOURCE)), 1));
        when(mockProxy.scroll(any(), any())).thenReturn(scrollResponse(SCROLL_ID, emptyList(), 1));

        TestSupport support = runProcessor(request -> request instanceof ScrollRequest ? scrollOptions : null);

        support.expectOutput(newArrayList(HIT_SOURCE));

        verify(mockProxy).scroll(any(), same(scrollOptions));
    }

    @Test
    public void when_runProcessorWithCoLocation_then_useLocalNodeOnly() throws Exception {
        when(mockProxy.search(any(), any())).thenReturn(searchResponse(SCROLL_ID, emptyList(), 0));

        TestSupport support = runProcessorWithCoLocation(newArrayList(
                new Shard("my-index", 0, Prirep.p, 42, "STARTED", "10.0.0.1", "10.0.0.1:9200", "es1")
        ));

        support.expectOutput(emptyList());

        ArgumentCaptor<Collection<Node>> nodesCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(lowLevelClient).setNodes(nodesCaptor.capture());

        Collection<Node> nodes = nodesCaptor.getValue();
        assertThat(nodes).hasSize(1);

        Node node = nodes.iterator().next();
        assertThat(node.getHost().toHostString()).isEqualTo("10.0.0.1:9200");
    }

    @Test
    public void when_runProcessorWithCoLocation_thenSearchShardsWithPreference() throws Exception {
        when(mockProxy.search(any(), any())).thenReturn(searchResponse(SCROLL_ID, emptyList(), 0));

        TestSupport support = runProcessorWithCoLocation(newArrayList(
                new Shard("my-index", 0, Prirep.p, 42, "STARTED", "10.0.0.1", "10.0.0.1:9200", "es1"),
                new Shard("my-index", 1, Prirep.p, 42, "STARTED", "10.0.0.1", "10.0.0.1:9200", "es1"),
                new Shard("my-index", 2, Prirep.p, 42, "STARTED", "10.0.0.1", "10.0.0.1:9200", "es1")
        ));

        support.expectOutput(emptyList());

        ArgumentCaptor<SearchRequest> captor = forClass(SearchRequest.class);
        verify(mockProxy).search(captor.capture(), any());

        SearchRequest request = captor.getValue();
        assertThat(request.preference()).isEqualTo("_shards:0,1,2|_only_local");
    }

    @Test
    public void when_runProcessorWithParallelism_thenUseSlicingBasedOnGlobalValues() throws Exception {
        when(mockProxy.search(any(), any())).thenReturn(searchResponse(SCROLL_ID, emptyList(), 0));

        TestSupport support = runProcessorWithInjectedProxy(request -> null, emptyList(), true, false);
        support.localProcessorIndex(1);
        support.localParallelism(2);
        support.globalProcessorIndex(4);
        support.totalParallelism(6);

        support.expectOutput(emptyList());

        ArgumentCaptor<SearchRequest> captor = forClass(SearchRequest.class);
        verify(mockProxy).search(captor.capture(), any());

        SearchRequest request = captor.getValue();
        assertThat(request.slice().id()).isEqualTo("4");
        assertThat(request.slice().max()).isEqualTo(6);
    }

    @Test
    public void when_runProcessorWithCoLocationAndSlicing_thenUseSlicingBasedOnLocalValues() throws Exception {
        when(mockProxy.search(any(), any())).thenReturn(searchResponse(SCROLL_ID, emptyList(), 0));

        TestSupport support = runProcessorWithInjectedProxy(request -> null,
                newArrayList(
                        new Shard("my-index", 0, Prirep.p, 42, "STARTED", "10.0.0.1", "10.0.0.1:9200", "es1"),
                        new Shard("my-index", 1, Prirep.p, 42, "STARTED", "10.0.0.1", "10.0.0.1:9200", "es1"),
                        new Shard("my-index", 2, Prirep.p, 42, "STARTED", "10.0.0.1", "10.0.0.1:9200", "es1")
                ),
                true, true);
        support.localProcessorIndex(1);
        support.localParallelism(2);
        support.globalProcessorIndex(4);
        support.totalParallelism(6);

        support.expectOutput(emptyList());

        ArgumentCaptor<SearchRequest> captor = forClass(SearchRequest.class);
        verify(mockProxy).search(captor.capture(), any());

        SearchRequest request = captor.getValue();
        assertThat(request.slice().id()).isEqualTo("1");
        assertThat(request.slice().max()).isEqualTo(2);
    }

    @Test
    public void given_emptyHitsWithNullScrollId_when_complete_then_noScrollCalls() throws Exception {
        when(mockProxy.search(any(), any())).thenReturn(searchResponse(null, emptyList(), 0));

        TestSupport support = runProcessor();

        support.expectOutput(emptyList());

        verify(mockProxy, never()).scroll(any(), any());
        verify(mockProxy, never()).clearScroll(any(), any());
    }

    private static Hit<JsonData> hit(String source) {
        return new Hit.Builder<JsonData>()
                .id("id-0")
                .index("my-index")
                .source(JsonData.fromJson(source))
                .build();
    }

    private static SearchResponse<JsonData> searchResponse(String scrollId, List<Hit<JsonData>> hits, long totalHits) {
        SearchResponse.Builder<JsonData> builder = new SearchResponse.Builder<JsonData>()
                .took(1)
                .timedOut(false)
                .shards(s -> s.total(1).successful(1).failed(0))
                .hits(h -> h
                        .hits(hits)
                        .total(t -> t.value(totalHits).relation(TotalHitsRelation.Eq))
                );
        if (scrollId != null) {
            builder.scrollId(scrollId);
        }
        return builder.build();
    }

    private static ScrollResponse<JsonData> scrollResponse(String scrollId, List<Hit<JsonData>> hits, long totalHits) {
        ScrollResponse.Builder<JsonData> builder = new ScrollResponse.Builder<JsonData>()
                .took(1)
                .timedOut(false)
                .shards(s -> s.total(1).successful(1).failed(0))
                .hits(h -> h
                        .hits(hits)
                        .total(t -> t.value(totalHits).relation(TotalHitsRelation.Eq))
                );
        if (scrollId != null) {
            builder.scrollId(scrollId);
        }
        return builder.build();
    }

    private static ClearScrollResponse successfulClearScroll() {
        return new ClearScrollResponse.Builder().succeeded(true).numFreed(1).build();
    }
}
