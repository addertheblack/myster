package com.myster.tracker;

import java.lang.ref.Cleaner;

import com.general.thread.Invoker;

public class TrackerUtils {
    static final Cleaner CLEANER = Cleaner.create();
    static final Invoker INVOKER = Invoker.newVThreadInvoker();
}
