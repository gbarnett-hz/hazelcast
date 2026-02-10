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

import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import com.hazelcast.function.FunctionEx;
import com.hazelcast.function.SupplierEx;
import com.hazelcast.jet.pipeline.BatchSource;
import org.elasticsearch.client.RestClientBuilder;

import javax.annotation.Nonnull;
import java.util.Map;

/**
 * Provides factory methods for Elasticsearch sources.
 * Alternatively you can use {@link ElasticSourceBuilder}
 *
 * @since Jet 4.2
 */
public final class ElasticSources {

    private ElasticSources() {
    }

    @Nonnull
    public static BatchSource<String> elastic() {
        SupplierEx<RestClientBuilder> client = ElasticClients::client;
        return elastic(client);
    }

    @Nonnull
    public static BatchSource<String> elastic(@Nonnull SupplierEx<RestClientBuilder> clientFn) {
        return elastic(clientFn, hit -> hit.source().toJson().toString());
    }

    @Nonnull
    public static <T> BatchSource<T> elastic(@Nonnull FunctionEx<? super Hit<JsonData>, T> mapToItemFn) {
        return elastic(ElasticClients::client, mapToItemFn);
    }

    @Nonnull
    public static <T> BatchSource<T> elastic(
            @Nonnull SupplierEx<RestClientBuilder> clientFn,
            @Nonnull FunctionEx<? super Hit<JsonData>, T> mapToItemFn
    ) {
        return elastic(clientFn, SearchRequest.Builder::new, mapToItemFn);
    }

    @Nonnull
    public static <T> BatchSource<T> elastic(
            @Nonnull SupplierEx<RestClientBuilder> clientFn,
            @Nonnull SupplierEx<SearchRequest.Builder> searchRequestFn,
            @Nonnull FunctionEx<? super Hit<JsonData>, T> mapToItemFn
    ) {
        return ElasticSources.builder()
                .clientFn(clientFn)
                .searchRequestFn(searchRequestFn)
                .mapToItemFn(mapToItemFn)
                .build();
    }

    @Nonnull
    public static ElasticSourceBuilder<Void> builder() {
        return new ElasticSourceBuilder<>();
    }

    static String sourceAsString(Hit<JsonData> hit) {
        return hit.source().toJson().toString();
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> sourceAsMap(Hit<JsonData> hit) {
        return hit.source().to(Map.class);
    }
}
