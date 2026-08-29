package com.myster.tracker.ui;

import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import com.general.thread.Invoker;
import com.general.thread.PromiseFuture;
import com.general.thread.PromiseFutures;
import com.myster.net.MysterAddress;
import com.myster.tracker.MysterServer;

/**
 * Owns the cancellable delay, DNS, and server-stats stages for one typed address.
 *
 * <p>The exact sequence is {@link Stage#WAITING}, delay, {@link Stage#RESOLVING}, asynchronous
 * address resolution, {@link Stage#CONTACTING}, and pool resolution. Stage notifications are
 * delivered through the supplied invoker. Cancelling the returned future propagates through all
 * created stages and makes late results moot even when underlying work cannot stop immediately.
 */
final class ServerAddressLookup {
    static final Duration DEFAULT_DELAY = Duration.ofSeconds(1);

    enum Stage {
        WAITING,
        RESOLVING,
        CONTACTING
    }

    record StageUpdate(Stage stage, Optional<MysterAddress> resolvedAddress) {
        StageUpdate {
            Objects.requireNonNull(stage, "stage");
            Objects.requireNonNull(resolvedAddress, "resolvedAddress");
        }
    }

    @FunctionalInterface
    interface AddressResolver {
        MysterAddress resolve(String address) throws UnknownHostException;
    }

    private final KnownServerSource serverSource;
    private final Duration delay;
    private final Function<Duration, PromiseFuture<Void>> delayFactory;
    private final AddressResolver addressResolver;
    private final Invoker stageInvoker;

    ServerAddressLookup(KnownServerSource serverSource) {
        this(serverSource,
             DEFAULT_DELAY,
             PromiseFutures::delay,
             MysterAddress::createMysterAddress,
             Invoker.EDT);
    }

    ServerAddressLookup(KnownServerSource serverSource,
                        Duration delay,
                        Function<Duration, PromiseFuture<Void>> delayFactory,
                        AddressResolver addressResolver,
                        Invoker stageInvoker) {
        this.serverSource = Objects.requireNonNull(serverSource, "serverSource");
        this.delay = Objects.requireNonNull(delay, "delay");
        this.delayFactory = Objects.requireNonNull(delayFactory, "delayFactory");
        this.addressResolver = Objects.requireNonNull(addressResolver, "addressResolver");
        this.stageInvoker = Objects.requireNonNull(stageInvoker, "stageInvoker");
    }

    PromiseFuture<MysterServer> start(ServerAddressCandidate candidate,
                                      Consumer<StageUpdate> stageListener) {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(stageListener, "stageListener");

        notifyStage(stageListener, new StageUpdate(Stage.WAITING, Optional.empty()));
        return delayFactory.apply(delay)
                .mapAsyncInline(_ -> {
                    notifyStage(stageListener, new StageUpdate(Stage.RESOLVING, Optional.empty()));
                    return PromiseFutures.execute(() -> addressResolver.resolve(candidate.addressText()));
                })
                .mapAsyncInline(address -> {
                    notifyStage(stageListener,
                                new StageUpdate(Stage.CONTACTING, Optional.of(address)));
                    return serverSource.resolveServer(address);
                });
    }

    private void notifyStage(Consumer<StageUpdate> listener, StageUpdate update) {
        stageInvoker.invoke(() -> listener.accept(update));
    }
}
