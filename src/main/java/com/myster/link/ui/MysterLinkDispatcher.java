package com.myster.link.ui;

import java.awt.Window;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

import com.general.thread.Invoker;
import com.general.util.AnswerDialog;
import com.general.util.Util;
import com.myster.client.ui.ClientWindow;
import com.myster.client.ui.ClientWindowProvider;
import com.myster.link.MysterLinkService;
import com.myster.link.MysterLinkUri;

/** Serializes resource preparation and hands destinations to initial-data navigation; successful activation never starts a transfer. */
public final class MysterLinkDispatcher {
    private final MysterLinkService service;
    private final Interaction interaction;
    private final Deque<MysterLinkUri> waiting = new ArrayDeque<>();
    private final Set<String> queued = new HashSet<>();
    private String active;

    /** UI boundary permits deterministic tests without creating native windows. */
    interface Interaction {
        default void begin() {}
        boolean confirm(MysterLinkUri.TargetKind kind);
        boolean alive();
        void navigate(MysterLinkService.Target target);
        void error(String message);
    }

    public MysterLinkDispatcher(MysterLinkService service, Supplier<Window> owner, ClientWindowProvider windows) {
        this(service, new Interaction() {
            private Window requestOwner;

            public void begin() { requestOwner = owner.get(); }

            public boolean confirm(MysterLinkUri.TargetKind kind) {
                String what = switch (kind) {
                    case SERVER -> "this server";
                    case TYPE -> "this type on this server";
                    case FILE -> "the file";
                };
                return "Yes".equals(AnswerDialog.simpleAlert(requestOwner,
                                                             "This link is for this server. Would you like to show " + what + "?",
                                                             new String[]{ "Yes", "No", "Cancel" }));
            }

            public boolean alive() { return requestOwner == null || requestOwner.isDisplayable(); }

            public void navigate(MysterLinkService.Target target) {
                ClientWindow window = windows.getOrCreateWindow(new ClientWindow.ClientWindowData(target.address(),
                                                                                                  target.identity(),
                                                                                                  target.type(),
                                                                                                  target.name()));
                window.setVisible(true);
                window.toFrontAndUnminimize();
            }

            public void error(String message) { AnswerDialog.simpleAlert(requestOwner, message); }
        });
    }

    /** Package protected for unit tests */
    MysterLinkDispatcher(MysterLinkService service, Interaction interaction) {
        this.service = service;
        this.interaction = interaction;
    }

    public void dispatch(MysterLinkUri uri) {
        Util.invokeNowOrLater(() -> {
            String canonical = uri.toString();
            if (canonical.equals(active) || waiting.size() >= 8 || !queued.add(canonical)) return;
            waiting.addLast(uri);
            next();
        });
    }

    private void next() {
        if (active != null || waiting.isEmpty()) { return; }

        MysterLinkUri uri = waiting.removeFirst();
        active = uri.toString();
        queued.remove(active);
        interaction.begin();

        if (!interaction.alive() || service.isLocal(uri) && !interaction.confirm(uri.kind())) {
            finish(null);
            return;
        }

        service.prepare(uri).withInvoker(Invoker.EDT).addResultListener(target -> {
            if (!interaction.alive()) { finish(null); } else {
                interaction.navigate(target);
                finish(null);
            }
        }).addExceptionListener(error -> finish(error));
    }

    private void finish(Throwable error) {
        if (error != null && interaction.alive()) interaction.error(error.getMessage());
        active = null;
        javax.swing.SwingUtilities.invokeLater(this::next);
    }
}
