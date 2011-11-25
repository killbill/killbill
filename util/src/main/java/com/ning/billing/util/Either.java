/*
 * Copyright 2010-2011 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.ning.billing.util;

public abstract class Either<T, V> {
    public static <T, V> Either<T, V> left(T value) {
        return new Left<T, V>(value);
    }
    public static <T, V> Either<T, V> right(V value) {
        return new Right<T, V>(value);
    }

    private Either() {
    }

    public boolean isLeft() {
        return false;
    }
    public boolean isRight() {
        return false;
    }
    public T getLeft() {
        throw new UnsupportedOperationException();
    }
    public V getRight() {
        throw new UnsupportedOperationException();
    }

    public static class Left<T, V> extends Either<T, V> {
        private final T value;

        public Left(T value) {
            this.value = value;
        }
        @Override
        public boolean isLeft() {
            return true;
        }
        @Override
        public T getLeft() {
            return value;
        }
    }

    public static class Right<T, V> extends Either<T, V> {
        private final V value;

        public Right(V value) {
            this.value = value;
        }
        @Override
        public boolean isRight() {
            return true;
        }

        @Override
        public V getRight() {
            return value;
        }
    }
}
