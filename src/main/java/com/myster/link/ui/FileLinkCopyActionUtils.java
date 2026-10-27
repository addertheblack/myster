package com.myster.link.ui;

import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.StringSelection;
import java.security.PublicKey;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.JMenuItem;

import com.general.thread.Invoker;
import com.general.thread.PromiseFuture;
import com.general.thread.PromiseFutures;
import com.general.util.AnswerDialog;
import com.myster.link.MysterLinkService;
import com.myster.link.MysterLinkUri;
import com.myster.search.MysterFileStub;
import com.myster.type.MysterType;

/** Explicit copy commands: selecting a row alone never copies or resolves a link. */
public final class FileLinkCopyActionUtils {
    public record RemoteFile(MysterFileStub stub, Optional<PublicKey> expectedKey) {}
    public record LocalFile(MysterType type, String name) {}

    private FileLinkCopyActionUtils() {}

    public static JMenuItem remoteItem(Supplier<Window> owner,
                                       MysterLinkService service,
                                       Supplier<Optional<RemoteFile>> selected) {
        return item(owner,
                    selected,
                    file -> service.createRemote(file.stub(), file.expectedKey()),
                    FileLinkCopyActionUtils::writeClipboard,
                    message -> AnswerDialog.simpleAlert(owner.get(), message));
    }

    public static JMenuItem localItem(Supplier<Window> owner,
                                      MysterLinkService service,
                                      Supplier<Optional<LocalFile>> selected) {
        return item(owner,
                    selected,
                    file -> PromiseFutures.execute(() -> service.createLocal(file.type(), file.name())),
                    FileLinkCopyActionUtils::writeClipboard,
                    message -> AnswerDialog.simpleAlert(owner.get(), message));
    }

    private static void writeClipboard(String text) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
    }

    /** Package protected for unit tests: injected clipboard avoids native desktop dependencies. */
    static <S> JMenuItem item(Supplier<Window> owner,
                              Supplier<Optional<S>> selected,
                              Function<S, PromiseFuture<MysterLinkUri>> copy, //TODO make copy a function that is not async
                              Consumer<String> clipboard,
                              Consumer<String> errors) {
        JMenuItem item = new JMenuItem("Copy File Link");
        long[] request = { 0 };

        item.addActionListener(_ -> {
            Optional<S> snapshot = selected.get();

            if (snapshot.isEmpty()) { return; }

            long token = ++request[0];
            Window window = owner.get();

            Supplier<Boolean> current = () -> token == request[0] && (window == null || window.isDisplayable()) && snapshot.equals(
                    selected.get());

            copy.apply(snapshot.get()).withInvoker(Invoker.EDT).addResultListener(uri -> {
                if (!current.get()) { return; }
                try {
                    clipboard.accept(uri.toString());
                } catch (IllegalStateException exception) {
                    errors.accept("The clipboard is currently unavailable.");
                }
            }).addExceptionListener(error -> {
                if (current.get()) { errors.accept("Could not copy file link: " + error.getMessage()); }
            });
        });

        return item;
    }
}
