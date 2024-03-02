
package com.general.thread;

import java.util.concurrent.atomic.AtomicReference;

public class InvokerContainerImpl implements InvokerContainer {
    private final AtomicReference<Invoker> invoker = new AtomicReference<Invoker>();
    
    @Override
    public Invoker getInvoker() {
        return invoker.get();
    }

    @Override
    public void setInvoker(Invoker invoker) {
        this.invoker.set(invoker);
    }
}
