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

package com.hazelcast.jet.elastic.pipeline;

import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import com.hazelcast.function.SupplierEx;
import com.hazelcast.jet.elastic.CommonElasticSinksTest.TestItem;
import com.hazelcast.jet.elastic.ElasticSinkBuilder;
import com.hazelcast.jet.elastic.ElasticSinks;
import com.hazelcast.jet.pipeline.Pipeline;
import com.hazelcast.jet.pipeline.Sink;
import com.hazelcast.jet.pipeline.test.TestSources;
import org.elasticsearch.client.RestClientBuilder;

public final class CommonElasticSinksPipeline {

    private CommonElasticSinksPipeline() {
    }

    public static Pipeline writeItemsToIndexPipeline(
            String index,
            SupplierEx<RestClientBuilder> elasticSupplier,
            TestItem... items) {
        Pipeline p = Pipeline.create();

        Sink<TestItem> elasticSink = new ElasticSinkBuilder<TestItem>()
                .clientFn(elasticSupplier)
                .bulkRequestFn(() -> new BulkRequest.Builder().refresh(Refresh.True))
                .mapToRequestFn((TestItem item) -> new BulkOperation.Builder()
                        .index(i -> i.index(index).id(item.getId()).document(item.asMap()))
                        .build())
                .build();

        p.readFrom(TestSources.items(items))
         .writeTo(elasticSink);

        return p;
    }

    public static Pipeline writeItemsToIndexUsingSourceFactoryMethodPipeline(
            String index,
            SupplierEx<RestClientBuilder> elasticSupplier,
            TestItem... items) {
        Pipeline p = Pipeline.create();

        Sink<TestItem> elasticSink = ElasticSinks.elastic(
                elasticSupplier,
                item -> new BulkOperation.Builder()
                        .index(i -> i.index(index).id(item.getId()).document(item.asMap()))
                        .build()
        );

        p.readFrom(TestSources.items(items))
         .writeTo(elasticSink);

        return p;
    }

    public static Pipeline updateItemsInIndexPipeline(
            String index,
            SupplierEx<RestClientBuilder> elasticSupplier,
            TestItem... items) {
        Pipeline p = Pipeline.create();

        Sink<TestItem> elasticSink = new ElasticSinkBuilder<TestItem>()
                .clientFn(elasticSupplier)
                .bulkRequestFn(() -> new BulkRequest.Builder().refresh(Refresh.True))
                .mapToRequestFn((TestItem item) -> new BulkOperation.Builder()
                        .update(u -> u.index(index).id(item.getId()).action(a -> a.doc(item.asMap())))
                        .build())
                .retries(0)
                .build();

        p.readFrom(TestSources.items(items))
         .writeTo(elasticSink);

        return p;
    }

    public static Pipeline deleteItemsFromIndexPipeline(
            String index,
            SupplierEx<RestClientBuilder> elasticSupplier,
            TestItem... items) {
        Pipeline p = Pipeline.create();

        Sink<TestItem> elasticSink = new ElasticSinkBuilder<TestItem>()
                .clientFn(elasticSupplier)
                .bulkRequestFn(() -> new BulkRequest.Builder().refresh(Refresh.True))
                .mapToRequestFn((TestItem item) -> new BulkOperation.Builder()
                        .delete(d -> d.index(index).id(item.getId()))
                        .build())
                .build();

        p.readFrom(TestSources.items(items))
         .writeTo(elasticSink);

        return p;
    }
}
