package com.general.util;

import com.sun.java.util.collections.Collection;

public interface Heap extends Collection {
    public Object extractTop();

    public Object top();
}