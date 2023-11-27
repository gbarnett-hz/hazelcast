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

package com.hazelcast.cp.internal.datastructures.atomicref;

import com.hazelcast.cp.internal.datastructures.spi.atomic.RaftAtomicValue;
import com.hazelcast.internal.serialization.Data;
import com.hazelcast.cp.CPGroupId;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * State-machine implementation of the Raft-based atomic reference
 */
public class AtomicRef extends RaftAtomicValue<Data> {

    private Data value;

    AtomicRef(CPGroupId groupId, String name, Data value) {
        super(groupId, name);
        this.value = value;
    }

    @Override
    public Data get() {
        return value;
    }

    public void set(Data value) {
        this.value = value;
    }

    public boolean contains(Data expected) {
        return Objects.equals(value, expected);
    }

    static byte[] sha256Data(Data value) {
        return sha256Bytes(value.toByteArray());
    }

    static byte[] sha256Bytes(byte[] value) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        return digest.digest(value);
    }

    static String hex(byte[] sha256) {
        StringBuilder hexString = new StringBuilder(2 * sha256.length);
        for (byte b : sha256) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    public static String sha256Hex(Data data) {
        return hex(sha256Data(data));
    }

    public boolean hasFingerprint(String expected) {
        String actual = hex(sha256Data(value));
        return Objects.equals(actual, expected);
    }

    @Override
    public String toString() {
        return "AtomicRef{" + "groupId=" + groupId() + ", name='" + name() + '\'' + ", value=" + value + '}';
    }
}
