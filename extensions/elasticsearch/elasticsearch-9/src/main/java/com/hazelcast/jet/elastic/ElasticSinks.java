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

import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import com.hazelcast.function.FunctionEx;
import com.hazelcast.function.SupplierEx;
import com.hazelcast.jet.pipeline.Sink;
import org.elasticsearch.client.RestClientBuilder;

import javax.annotation.Nonnull;

/**
 * Provides factory methods for Elasticsearch sinks.
 * Alternatively you can use {@link ElasticSinkBuilder}
 *
 * @since Jet 4.2
 */
public final class ElasticSinks {

    private ElasticSinks() {
    }

    @Nonnull
    public static <T> Sink<T> elastic(
            @Nonnull FunctionEx<? super T, ? extends BulkOperation> mapToRequestFn
    ) {
        return elastic(ElasticClients::client, mapToRequestFn);
    }

    @Nonnull
    public static <T> Sink<T> elastic(
            @Nonnull SupplierEx<RestClientBuilder> clientFn,
            @Nonnull FunctionEx<? super T, ? extends BulkOperation> mapToRequestFn
    ) {
        ElasticSinkBuilder<T> builder = new ElasticSinkBuilder<>()
                .clientFn(clientFn)
                .mapToRequestFn(mapToRequestFn);
        return builder.build();
    }

    @Nonnull
    public static ElasticSinkBuilder<Void> builder() {
        return new ElasticSinkBuilder<>();
    }
}
