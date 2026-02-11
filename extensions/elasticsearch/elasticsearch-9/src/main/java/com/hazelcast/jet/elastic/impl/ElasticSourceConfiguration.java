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

import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.transport.TransportOptions;
import com.hazelcast.function.FunctionEx;
import com.hazelcast.function.SupplierEx;
import org.elasticsearch.client.RestClientBuilder;

import javax.annotation.Nonnull;
import java.io.Serial;
import java.io.Serializable;

/**
 * Serializable source configuration.
 */
public class ElasticSourceConfiguration<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final SupplierEx<RestClientBuilder> clientFn;
    private final SupplierEx<SearchRequest.Builder> searchRequestFn;
    private final FunctionEx<? super Object, TransportOptions> optionsFn;
    private final FunctionEx<? super Hit<JsonData>, T> mapToItemFn;
    private final boolean slicing;
    private final boolean coLocatedReading;
    private final String scrollKeepAlive;
    private final int retries;

    public ElasticSourceConfiguration(
            SupplierEx<RestClientBuilder> clientFn,
            SupplierEx<SearchRequest.Builder> searchRequestFn,
            FunctionEx<? super Object, TransportOptions> optionsFn,
            FunctionEx<? super Hit<JsonData>, T> mapToItemFn,
            boolean slicing,
            boolean coLocatedReading,
            String scrollKeepAlive,
            int retries
    ) {
        this.clientFn = clientFn;
        this.searchRequestFn = searchRequestFn;
        this.optionsFn = optionsFn;
        this.mapToItemFn = mapToItemFn;
        this.slicing = slicing;
        this.coLocatedReading = coLocatedReading;
        this.scrollKeepAlive = scrollKeepAlive;
        this.retries = retries;
    }

    @Nonnull
    public SupplierEx<RestClientBuilder> clientFn() {
        return clientFn;
    }

    @Nonnull
    public SupplierEx<SearchRequest.Builder> searchRequestFn() {
        return searchRequestFn;
    }

    @Nonnull
    public FunctionEx<? super Hit<JsonData>, T> mapToItemFn() {
        return mapToItemFn;
    }

    public FunctionEx<? super Object, TransportOptions> optionsFn() {
        return optionsFn;
    }

    public boolean isSlicingEnabled() {
        return slicing;
    }

    public boolean isCoLocatedReadingEnabled() {
        return coLocatedReading;
    }

    public String scrollKeepAlive() {
        return scrollKeepAlive;
    }

    public int retries() {
        return retries;
    }
}
