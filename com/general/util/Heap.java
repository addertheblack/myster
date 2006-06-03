package com.general.util;

import java.util.Collection;

public interface Heap extends Collection {
    public Object extractTop();

    public Object top();
}