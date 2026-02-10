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
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.TransportOptions;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.hazelcast.function.FunctionEx;
import com.hazelcast.function.SupplierEx;
import com.hazelcast.jet.JetException;
import com.hazelcast.jet.pipeline.Sink;
import com.hazelcast.jet.pipeline.SinkBuilder;
import com.hazelcast.logging.ILogger;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import static com.hazelcast.jet.elastic.impl.RetryUtils.withRetry;
import static com.hazelcast.jet.impl.util.Util.checkNonNullAndSerializable;
import static java.util.Objects.requireNonNull;

/**
 * Builder for Elasticsearch Sink.
 *
 * @param <T> item type
 * @since Jet 4.2
 */
public final class ElasticSinkBuilder<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String DEFAULT_NAME = "elasticSink";
    private static final int DEFAULT_LOCAL_PARALLELISM = 2;
    private static final int DEFAULT_RETRIES = 5;

    private SupplierEx<RestClientBuilder> clientFn;
    private SupplierEx<BulkRequest.Builder> bulkRequestFn = BulkRequest.Builder::new;
    private FunctionEx<? super T, ? extends BulkOperation> mapToRequestFn;
    private FunctionEx<? super Object, TransportOptions> optionsFn = request -> null;
    private int retries = DEFAULT_RETRIES;

    @Nonnull
    public ElasticSinkBuilder<T> clientFn(@Nonnull SupplierEx<RestClientBuilder> clientFn) {
        this.clientFn = checkNonNullAndSerializable(clientFn, "clientFn");
        return this;
    }

    @Nonnull
    public ElasticSinkBuilder<T> bulkRequestFn(@Nonnull SupplierEx<BulkRequest.Builder> bulkRequestFn) {
        this.bulkRequestFn = checkNonNullAndSerializable(bulkRequestFn, "bulkRequestFn");
        return this;
    }

    @Nonnull
    @SuppressWarnings("unchecked")
    public <T_NEW> ElasticSinkBuilder<T_NEW> mapToRequestFn(
            @Nonnull FunctionEx<? super T_NEW, ? extends BulkOperation> mapToRequestFn
    ) {
        ElasticSinkBuilder<T_NEW> newThis = (ElasticSinkBuilder<T_NEW>) this;
        newThis.mapToRequestFn = checkNonNullAndSerializable(mapToRequestFn, "mapToRequestFn");
        return newThis;
    }

    @Nonnull
    public ElasticSinkBuilder<T> optionsFn(@Nonnull FunctionEx<? super Object, TransportOptions> optionsFn) {
        this.optionsFn = checkNonNullAndSerializable(optionsFn, "optionsFn");
        return this;
    }

    @Nonnull
    public ElasticSinkBuilder<T> retries(int retries) {
        if (retries < 0) {
            throw new IllegalArgumentException("retries must be positive");
        }
        this.retries = retries;
        return this;
    }

    @Nonnull
    public Sink<T> build() {
        requireNonNull(clientFn, "clientFn is not set");
        requireNonNull(mapToRequestFn, "mapToRequestFn is not set");

        return SinkBuilder
                .sinkBuilder(
                        DEFAULT_NAME,
                        ctx -> new BulkContext(clientFn.get(), bulkRequestFn, optionsFn, retries, ctx.logger())
                )
                .<T>receiveFn((bulkContext, item) -> bulkContext.add(mapToRequestFn.apply(item)))
                .flushFn(BulkContext::flush)
                .destroyFn(BulkContext::close)
                .preferredLocalParallelism(DEFAULT_LOCAL_PARALLELISM)
                .build();
    }

    static final class BulkContext {

        private final RestClient lowLevelClient;
        private final RestClientTransport transport;
        private final ElasticsearchClient client;
        private final SupplierEx<BulkRequest.Builder> bulkRequestSupplier;
        private final FunctionEx<? super Object, TransportOptions> optionsFn;
        private final int retries;
        private final ILogger logger;
        private final List<BulkOperation> operations = new ArrayList<>();

        BulkContext(
                RestClientBuilder builder,
                SupplierEx<BulkRequest.Builder> bulkRequestSupplier,
                FunctionEx<? super Object, TransportOptions> optionsFn,
                int retries,
                ILogger logger
        ) {
            this.lowLevelClient = builder.build();
            this.transport = new RestClientTransport(lowLevelClient, new JacksonJsonpMapper());
            this.client = new ElasticsearchClient(transport);
            this.bulkRequestSupplier = bulkRequestSupplier;
            this.optionsFn = optionsFn;
            this.retries = retries;
            this.logger = logger;
        }

        void add(BulkOperation request) {
            operations.add(request);
        }

        void flush() {
            if (operations.isEmpty()) {
                return;
            }

            BulkRequest.Builder requestBuilder = bulkRequestSupplier.get();
            for (BulkOperation operation : operations) {
                requestBuilder.operations(operation);
            }
            BulkRequest request = requestBuilder.build();

            withRetry(
                    () -> {
                        BulkResponse response = withOptions(request).bulk(request);
                        if (response.errors()) {
                            StringBuilder failure = new StringBuilder("Bulk request failed: ");
                            for (BulkResponseItem item : response.items()) {
                                if (item.error() != null) {
                                    failure.append('[').append(item.error().reason()).append("] ");
                                }
                            }
                            throw new JetException(failure.toString());
                        }
                        if (logger.isFineEnabled()) {
                            logger.fine("BulkRequest with %s requests succeeded", operations.size());
                        }
                        operations.clear();
                        return response;
                    },
                    retries,
                    IOException.class,
                    JetException.class
            );
        }

        private ElasticsearchClient withOptions(Object request) {
            TransportOptions options = optionsFn.apply(request);
            return options == null ? client : client.withTransportOptions(options);
        }

        void close() throws IOException {
            logger.fine("Closing BulkContext");
            try {
                flush();
            } finally {
                transport.close();
            }
        }
    }
}
