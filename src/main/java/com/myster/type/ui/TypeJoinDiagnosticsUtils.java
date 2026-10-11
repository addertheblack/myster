package com.myster.type.ui;

import java.io.PrintWriter;
import java.io.StringWriter;

import com.myster.cid.ServerCid;
import com.myster.type.join.TypeJoinCoordinator.BootstrapLookupException;
import com.myster.type.join.TypeJoinCoordinator.JoinException;

/** Formats invitation failures without retaining the invitation URI, password or invitation ID. */
final class TypeJoinDiagnosticsUtils {
    private TypeJoinDiagnosticsUtils() {}

    static String message(Throwable failure) {
        if (failure instanceof JoinException joinFailure) {
            return joinFailure.getMessage();
        }
        return "Unexpected " + failure.getClass().getSimpleName()
                + " during the invitation operation. See Details for the cause.";
    }

    static String details(String operation, ServerCid bootstrap, Throwable failure) {
        StringWriter text = new StringWriter();
        PrintWriter writer = new PrintWriter(text);
        writer.println("Invitation operation: " + operation);
        writer.println("Bootstrap CID: " + bootstrap);
        if (failure instanceof BootstrapLookupException lookupFailure) {
            var result = lookupFailure.result();
            writer.println("3DNS outcome: " + result.status());
            writer.println("Closest verified peer: " + result.closestPeer()
                    .map(peer -> peer.cid() + " at " + peer.address()).orElse("none"));
            writer.print(result.diagnostics().describe());
        }
        writer.println();
        failure.printStackTrace(writer);
        writer.flush();
        return text.toString();
    }
}
