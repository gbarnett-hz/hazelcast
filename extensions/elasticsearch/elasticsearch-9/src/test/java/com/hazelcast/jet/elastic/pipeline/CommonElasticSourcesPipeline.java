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

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import com.hazelcast.collection.IList;
import com.hazelcast.function.SupplierEx;
import com.hazelcast.jet.elastic.ElasticSourceBuilder;
import com.hazelcast.jet.elastic.ElasticSources;
import com.hazelcast.jet.pipeline.BatchSource;
import com.hazelcast.jet.pipeline.Pipeline;
import com.hazelcast.jet.pipeline.Sinks;
import org.elasticsearch.client.RestClientBuilder;

import java.util.Map;

public final class CommonElasticSourcesPipeline {

    private CommonElasticSourcesPipeline() {
    }

    public static Pipeline readFromIndexAsStringPipeline(
            String index,
            SupplierEx<RestClientBuilder> elasticSupplier,
            IList<String> resultsList) {
        Pipeline p = Pipeline.create();

        BatchSource<String> source = new ElasticSourceBuilder<>()
                .clientFn(elasticSupplier)
                .searchRequestFn(() -> new SearchRequest.Builder().index(index))
                .mapToItemFn(hit -> hit.source().toJson().toString())
                .build();

        p.readFrom(source)
                .writeTo(Sinks.list(resultsList));

        return p;
    }

    public static Pipeline readFromIndexExtractNamePipeline(
            String index,
            SupplierEx<RestClientBuilder> elasticSupplier,
            IList<String> resultsList) {
        Pipeline p = Pipeline.create();

        BatchSource<String> source = new ElasticSourceBuilder<>()
                .clientFn(elasticSupplier)
                .searchRequestFn(() -> new SearchRequest.Builder().index(index))
                .mapToItemFn(hit -> (String) hit.source().to(Map.class).get("name"))
                .build();

        p.readFrom(source)
                .writeTo(Sinks.list(resultsList));

        return p;
    }

    public static Pipeline readFromIndexUsingSourceFactoryMethod1ExtractNamePipeline(
            SupplierEx<RestClientBuilder> elasticSupplier,
            IList<String> resultsList) {
        Pipeline p = Pipeline.create();

        BatchSource<String> source = ElasticSources.elastic(
                elasticSupplier,
                hit -> (String) hit.source().to(Map.class).get("name")
        );

        p.readFrom(source)
                .writeTo(Sinks.list(resultsList));

        return p;
    }

    public static Pipeline readFromIndexUsingSourceFactoryMethod2ExtractNamePipeline(
            String index,
            SupplierEx<RestClientBuilder> elasticSupplier,
            IList<String> resultsList) {
        Pipeline p = Pipeline.create();

        BatchSource<String> source = ElasticSources.elastic(
                elasticSupplier,
                () -> new SearchRequest.Builder().index(index),
                hit -> (String) hit.source().to(Map.class).get("name")
        );

        p.readFrom(source)
                .writeTo(Sinks.list(resultsList));

        return p;
    }

    public static Pipeline readFromIndexUsingScrollAsStringPipeline(
            String index,
            SupplierEx<RestClientBuilder> elasticSupplier,
            IList<String> resultsList) {
        Pipeline p = Pipeline.create();

        BatchSource<String> source = new ElasticSourceBuilder<>()
                .clientFn(elasticSupplier)
                .searchRequestFn(() -> new SearchRequest.Builder().index(index).size(10).query(Query.of(q -> q.matchAll(m -> m))))
                .mapToItemFn(hit -> hit.source().toJson().toString())
                .build();

        p.readFrom(source)
                .writeTo(Sinks.list(resultsList));

        return p;
    }

    public static Pipeline readFromIndexWithQueryExtractNamePipeline(
            String index,
            SupplierEx<RestClientBuilder> elasticSupplier,
            IList<String> resultsList) {
        Pipeline p = Pipeline.create();

        BatchSource<String> source = new ElasticSourceBuilder<>()
                .clientFn(elasticSupplier)
                .searchRequestFn(() -> new SearchRequest.Builder()
                        .index(index)
                        .query(Query.of(q -> q.match(m -> m.field("name").query("Frantisek")))))
                .mapToItemFn(hit -> (String) hit.source().to(Map.class).get("name"))
                .build();

        p.readFrom(source)
                .writeTo(Sinks.list(resultsList));

        return p;
    }

    public static Pipeline readFromIndexAsStringEnableSlicingPipeline(
            String index,
            SupplierEx<RestClientBuilder> elasticSupplier,
            IList<String> resultsList) {
        Pipeline p = Pipeline.create();

        BatchSource<String> source = new ElasticSourceBuilder<>()
                .clientFn(elasticSupplier)
                .searchRequestFn(() -> new SearchRequest.Builder().index(index))
                .mapToItemFn(hit -> hit.source().toJson().toString())
                .enableSlicing()
                .build();

        p.readFrom(source)
                .writeTo(Sinks.list(resultsList));

        return p;
    }

    public static Pipeline readFromIndexAsStringZeroRetriesPipeline(
            String index,
            SupplierEx<RestClientBuilder> elasticSupplier,
            IList<String> resultsList) {
        Pipeline p = Pipeline.create();

        BatchSource<String> source = new ElasticSourceBuilder<>()
                .clientFn(elasticSupplier)
                .searchRequestFn(() -> new SearchRequest.Builder().index(index))
                .mapToItemFn(hit -> hit.source().toJson().toString())
                .retries(0)
                .build();

        p.readFrom(source)
                .writeTo(Sinks.list(resultsList));

        return p;
    }
}
