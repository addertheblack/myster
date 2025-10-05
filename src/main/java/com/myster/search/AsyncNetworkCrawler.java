package com.myster.search;

import java.util.Arrays;
import java.util.function.Consumer;
import java.util.logging.Logger;

import com.general.thread.AsyncTaskTracker;
import com.general.thread.PromiseFuture;
import com.general.thread.PromiseFutures;
import com.myster.client.net.MysterDatagram;
import com.myster.client.net.MysterProtocol;
import com.myster.client.net.ParamBuilder;
import com.myster.net.MysterAddress;
import com.myster.type.MysterType;

public class AsyncNetworkCrawler {
    public interface SearchIp  {
        PromiseFuture<?> search(MysterAddress address, MysterType type);
    }

    public static void startWork(Logger logger,
                                 MysterProtocol protocol,
                                 SearchIp searcher,
                                 MysterType type,
                                 IPQueue primedIpList,
                                 Consumer<MysterAddress> addIp,
                                 AsyncTaskTracker tracker) {
        startMoreWork(new Context(logger, protocol, searcher, type, primedIpList, addIp, tracker));
    }

    private static record Context(Logger logger,
                                  MysterProtocol protocol,
                                  SearchIp searcher,
                                  MysterType type,
                                  IPQueue ipQueue,
                                  Consumer<MysterAddress> addIp,
                                  AsyncTaskTracker tracker) {}
    

    private static void startMoreWork(Context c) {
        for (;;) {
            MysterAddress address = c.ipQueue.getNextIP();
            if (address == null) {
                return;
            }

            MysterDatagram datagram = c.protocol.getDatagram();
            c.tracker.doAsync(() -> {
                PromiseFuture<String[]> ff = datagram.getTopServers(new ParamBuilder(address), c.type).clearInvoker();
                return ff.addResultListener(ips -> addIps(c, ips))
                        .addExceptionListener(ex -> c.logger
                                .fine("Exception while doing UDP hash search crawler getTopServers("
                                        + address + ", " + c.type + ") " + ex))
                        .addFinallyListener(() -> startMoreWork(c));
            });

            c.tracker.doAsync(() -> c.searcher.search(address, c.type))
                    .addStandardExceptionHandler().addFinallyListener(() -> startMoreWork(c));
        }
    }


    private static void addIps(Context c, String[] ips) {
        Arrays.asList(ips).forEach(ip -> {
            c.tracker.doAsync(() -> PromiseFutures.execute(() -> MysterAddress.createMysterAddress(ip)))
                    .addResultListener(c.ipQueue::addIP).addResultListener(c.addIp)
                    .addStandardExceptionHandler();
        });
    }
}