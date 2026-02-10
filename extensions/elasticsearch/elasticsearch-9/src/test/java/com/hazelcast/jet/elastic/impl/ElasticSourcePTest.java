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
import com.hazelcast.jet.elastic.ElasticSourceBuilder;
import com.hazelcast.test.HazelcastParallelClassRunner;
import com.hazelcast.test.annotation.ParallelJVMTest;
import com.hazelcast.test.annotation.QuickTest;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(HazelcastParallelClassRunner.class)
@Category({QuickTest.class, ParallelJVMTest.class})
public class ElasticSourcePTest {

    @Test
    public void when_sourceBuilt_then_itIsNonNull() {
        ElasticSourceBuilder<String> builder = new ElasticSourceBuilder<String>()
                .clientFn(() -> RestClient.builder(new HttpHost("localhost")))
                .searchRequestFn(() -> new SearchRequest.Builder().index("*"))
                .mapToItemFn(hit -> hit.source().toJson().toString());

        assertThat(builder.build()).isNotNull();
    }
}
