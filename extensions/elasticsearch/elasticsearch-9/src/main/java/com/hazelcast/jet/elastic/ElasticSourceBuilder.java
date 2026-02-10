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
import co.elastic.clients.transport.TransportOptions;
import com.hazelcast.function.FunctionEx;
import com.hazelcast.function.SupplierEx;
import com.hazelcast.jet.elastic.impl.ElasticSourceConfiguration;
import com.hazelcast.jet.elastic.impl.ElasticSourcePMetaSupplier;
import com.hazelcast.jet.pipeline.BatchSource;
import com.hazelcast.jet.pipeline.Sources;
import org.elasticsearch.client.RestClientBuilder;

import javax.annotation.Nonnull;

import static com.hazelcast.jet.impl.util.Util.checkNonNullAndSerializable;
import static com.hazelcast.jet.impl.util.Util.checkSerializable;
import static java.util.Objects.requireNonNull;

/**
 * Builder for Elasticsearch source.
 *
 * @param <T> type of the output of the mapping function
 * @since Jet 4.2
 */
public final class ElasticSourceBuilder<T> {

    private static final String DEFAULT_NAME = "elasticSource";
    private static final int DEFAULT_RETRIES = 5;

    private SupplierEx<RestClientBuilder> clientFn;
    private SupplierEx<SearchRequest.Builder> searchRequestFn;
    private FunctionEx<? super Object, TransportOptions> optionsFn = request -> null;
    private FunctionEx<? super Hit<JsonData>, T> mapToItemFn;
    private boolean slicing;
    private boolean coLocatedReading;
    private String scrollKeepAlive = "1m";
    private int retries = DEFAULT_RETRIES;

    @Nonnull
    public BatchSource<T> build() {
        requireNonNull(clientFn, "clientFn must be set");
        requireNonNull(searchRequestFn, "searchRequestFn must be set");
        requireNonNull(mapToItemFn, "mapToItemFn must be set");

        ElasticSourceConfiguration<T> configuration = new ElasticSourceConfiguration<>(
                clientFn, searchRequestFn, optionsFn, mapToItemFn, slicing, coLocatedReading, scrollKeepAlive, retries
        );
        ElasticSourcePMetaSupplier<T> metaSupplier = new ElasticSourcePMetaSupplier<>(configuration);
        return Sources.batchFromProcessor(DEFAULT_NAME, metaSupplier);
    }

    @Nonnull
    public ElasticSourceBuilder<T> clientFn(@Nonnull SupplierEx<RestClientBuilder> clientFn) {
        this.clientFn = checkNonNullAndSerializable(clientFn, "clientFn");
        return this;
    }

    @Nonnull
    public ElasticSourceBuilder<T> searchRequestFn(@Nonnull SupplierEx<SearchRequest.Builder> searchRequestFn) {
        this.searchRequestFn = checkSerializable(searchRequestFn, "searchRequestFn");
        return this;
    }

    @Nonnull
    @SuppressWarnings("unchecked")
    public <T_NEW> ElasticSourceBuilder<T_NEW> mapToItemFn(@Nonnull FunctionEx<? super Hit<JsonData>, T_NEW> mapToItemFn) {
        ElasticSourceBuilder<T_NEW> newThis = (ElasticSourceBuilder<T_NEW>) this;
        newThis.mapToItemFn = checkSerializable(mapToItemFn, "mapToItemFn");
        return newThis;
    }

    @Nonnull
    public ElasticSourceBuilder<T> optionsFn(@Nonnull FunctionEx<? super Object, TransportOptions> optionsFn) {
        this.optionsFn = checkSerializable(optionsFn, "optionsFn");
        return this;
    }

    @Nonnull
    public ElasticSourceBuilder<T> enableSlicing() {
        this.slicing = true;
        return this;
    }

    @Nonnull
    public ElasticSourceBuilder<T> enableCoLocatedReading() {
        this.coLocatedReading = true;
        return this;
    }

    @Nonnull
    public ElasticSourceBuilder<T> scrollKeepAlive(@Nonnull String scrollKeepAlive) {
        this.scrollKeepAlive = requireNonNull(scrollKeepAlive, scrollKeepAlive);
        return this;
    }

    @Nonnull
    public ElasticSourceBuilder<T> retries(int retries) {
        if (retries < 0) {
            throw new IllegalArgumentException("retries must be positive");
        }
        this.retries = retries;
        return this;
    }
}
