/*
 * Copyright 2022-2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.cedarpolicy.value;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.stream.Collectors;

/** Lists in the Cedar language. */
public final class CedarList extends Value implements List<Value> {
    /** Internal list of Value objects. */
    public final java.util.List<Value> list;

    /**
     * Create a Cedar list by copy.
     *
     * @param source list to copy from
     */
    public CedarList(java.util.List<Value> source) {
        this.list = new ArrayList<>(source);
    }

    /** Create an empty Cedar list. */
    public CedarList() {
        this.list = new ArrayList<Value>();
    }

    /** Equals. */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        CedarList list1 = (CedarList) o;
        return this.list.equals(list1.list);
    }

    /** Hash. */
    @Override
    public int hashCode() {
        return Objects.hash(list);
    }

    /** toString. */
    @Override
    public String toString() {
        return list.toString();
    }

    /** To Cedar expr that can be used in a Cedar policy. */
    @Override
    public String toCedarExpr() {
        return "[" + list.stream().map(Value::toCedarExpr).collect(Collectors.joining(", ")) + "]";
    }

    // Overrides to ArrayList.

    @Override
    public boolean add(Value e) throws NullPointerException {
        if (e == null) {
            throw new NullPointerException("Attempt to put null element in CedarList");
        }
        return list.add(e);
    }

    @Override
    public void add(int index, Value e) throws NullPointerException {
        if (e == null) {
            throw new NullPointerException("Attempt to put null element in CedarList");
        }
        list.add(index, e);
    }

    @Override
    public boolean addAll(Collection<? extends Value> c) throws NullPointerException {
        for (Value v : c) {
            if (v == null) {
                throw new NullPointerException("Attempt to put null element in CedarList");
            }
        }
        return list.addAll(c);
    }

    @Override
    public boolean addAll(int index, Collection<? extends Value> c) throws NullPointerException {
        for (Value v : c) {
            if (v == null) {
                throw new NullPointerException("Attempt to put null element in CedarList");
            }
        }
        return list.addAll(c);
    }

    @Override
    public void clear() {
        list.clear();
    }

    @Override
    public boolean contains(Object o) {
        return list.contains(o);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return list.containsAll(c);
    }
    // @Override
    // public boolean equals(Object o) { return list.equals(o);}
    @Override
    public Value get(int index) {
        return list.get(index);
    }
    // @Override
    // public int hashcode() { return list.hashcode();}
    @Override
    public int indexOf(Object o) {
        return list.indexOf(o);
    }

    @Override
    public boolean isEmpty() {
        return list.isEmpty();
    }

    @Override
    public Iterator<Value> iterator() {
        return list.iterator();
    }

    @Override
    public int lastIndexOf(Object o) {
        return list.lastIndexOf(o);
    }

    @Override
    public ListIterator<Value> listIterator() {
        return list.listIterator();
    }

    @Override
    public ListIterator<Value> listIterator(int index) {
        return list.listIterator(index);
    }

    @Override
    public Value remove(int index) {
        return list.remove(index);
    }

    @Override
    public boolean remove(Object o) {
        return list.remove(o);
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return list.removeAll(c);
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return list.retainAll(c);
    }

    @Override
    public Value set(int index, Value element) throws NullPointerException {
        if (element == null) {
            throw new NullPointerException("Attempt to put null element in CedarList");
        }
        return list.set(index, element);
    }

    @Override
    public List<Value> subList(int fromIndex, int toIndex) {
        return list.subList(fromIndex, toIndex);
    }

    @Override
    public int size() {
        return list.size();
    }

    @Override
    public Object[] toArray() {
        return list.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return list.toArray(a);
    }
}
