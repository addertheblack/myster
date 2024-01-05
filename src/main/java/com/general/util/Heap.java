package com.general.util;

import java.util.Collection;

public interface Heap<E> extends Collection<E> {
    public E extractTop();

    public E top();
}