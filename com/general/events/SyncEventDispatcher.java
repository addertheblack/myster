package com.general.events;

import java.util.Vector;

import com.general.util.LinkedList;

/**
 * Dispatches event synchronously on the same thread that fired them.
 */
public class SyncEventDispatcher extends AbstractEventDispatcher {
    private LinkedList queue = new LinkedList();

    private boolean isDispatching = false;

    /**
     * This command cannot be called inside a monitor used to forward the addListener/removeListener
     * commands because there is a serious risk of deadlock with the current implementation.
     */
    public void fireEvent(GenericEvent e) {
        doCommandBacklog();

        synchronized (this) {
            isDispatching = true;
        }

        Vector listeners = this.listeners;
        //synchronized (listeners) { //should not be necessary since no one
        // should be adding while dispatching.
        for (int i = 0; i < listeners.size(); i++) {
            try {
                ((EventListener) (listeners.elementAt(i))).fireEvent(e);
            } catch (Exception ex) {
                ex.printStackTrace();
                //we don't want all events to not be delivered just because one
                // handler died
            }
        }
        //}

        synchronized (this) {
            isDispatching = false;
        }
        doCommandBacklog();
    }

    private void doCommandBacklog() {
        while (queue.getSize() > 0) {
            ((ModifyListCommand) (queue.removeFromHead())).execute();
        }
    }

    public synchronized void addListener(EventListener listener) {
        if (isDispatching) {
            queue.addToTail(new ModifyListCommand(listener, true));
            return;
        }

        listeners.addElement(listener);
    }

    /*
     *  (non-Javadoc)
     * @see com.general.events.EventDispatcher#removeListener(com.general.events.EventListener)
     */
    public synchronized void removeListener(EventListener listener) {
        if (isDispatching) {
            queue.addToTail(new ModifyListCommand(listener, false));
            return;
        }

        listeners.removeElement(listener);
    }

    public synchronized int getListenerCount() {
        return listeners.size();
    }

    private class ModifyListCommand {
        private final EventListener listener;

        private final boolean isAdd;

        public ModifyListCommand(EventListener listener, boolean isAdd) {
            this.listener = listener;
            this.isAdd = isAdd;
        }

        public void execute() {
            if (isAdd) {
                addListener(listener);
            } else {
                removeListener(listener);
            }
        }
    }
}