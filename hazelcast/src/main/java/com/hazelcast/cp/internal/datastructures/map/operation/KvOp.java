/*
 * Copyright (c) 2008-2023, Hazelcast, Inc. All Rights Reserved.
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

package com.hazelcast.cp.internal.datastructures.map.operation;

public enum KvOp {
    PUT_ALWAYS(0),
    PUT_IF_ABSENT(1),
    PUT_REPLACE(2);

    private final int id;

    KvOp(int id) {
        this.id = id;
    }

    int getId() {
        return id;
    }

    static KvOp fromId(int id) {
        switch (id) {
            case 0:
                return PUT_ALWAYS;
            case 1:
                return PUT_IF_ABSENT;
            case 2:
                return PUT_REPLACE;
            default:
                throw new IllegalArgumentException("Unknown ID: " + id);
        }
    }
}
