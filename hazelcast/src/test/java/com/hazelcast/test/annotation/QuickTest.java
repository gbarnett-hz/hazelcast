/*
 * Copyright (c) 2008-2024, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.test.annotation;

import org.junit.runners.model.FrameworkMethod;

import java.text.MessageFormat;
import java.time.Duration;

/**
 * Annotates quick tests which are fast enough (i.e. execution sub-{@link #EXPECTED_RUNTIME_THRESHOLD} per test) for the PR builder.
 * <p>
 * Will be executed in the PR builder and for code coverage measurements.
 *
 * @see {@link SlowTest}
 */
public final class QuickTest {
    public static final Duration EXPECTED_RUNTIME_THRESHOLD = Duration.ofMinutes(1);

    public static void logMessageIfTestOverran(FrameworkMethod method, float tookSeconds) {
        if (tookSeconds > QuickTest.EXPECTED_RUNTIME_THRESHOLD.getSeconds()) {
            System.err.println(MessageFormat.format(
                    "{0} is annotated as a {1}, expected to complete within {2} seconds - but took {3} seconds",
                    method.getName(), QuickTest.class.getSimpleName(), EXPECTED_RUNTIME_THRESHOLD.getSeconds(), tookSeconds));
        }
    }
}
