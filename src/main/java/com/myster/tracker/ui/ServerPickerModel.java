package com.myster.tracker.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

import com.myster.tracker.MysterServer;

final class ServerPickerModel {
    record Row(MysterServer server, String displayName, String address, String status) {}

    private ServerPickerModel() {}

    static List<Row> snapshot(KnownServerSource source, Predicate<MysterServer> eligibility) {
        List<Row> rows = new ArrayList<>();
        source.forEachServer(server -> {
            if (eligibility.test(server)) {
                rows.add(row(server));
            }
        });
        return List.copyOf(rows);
    }

    static List<Row> filter(List<Row> rows, String searchText) {
        String term = searchText.trim().toLowerCase(Locale.ROOT);
        if (term.isEmpty()) {
            return rows;
        }
        return rows.stream()
                .filter(row -> row.displayName().toLowerCase(Locale.ROOT).contains(term)
                        || row.address().toLowerCase(Locale.ROOT).contains(term))
                .toList();
    }

    static List<Row> filterIncluding(List<Row> rows, String searchText, MysterServer server) {
        List<Row> filtered = new ArrayList<>();
        filtered.add(row(server));
        filter(rows, searchText).stream()
                .filter(row -> !row.server().getIdentity().equals(server.getIdentity()))
                .forEach(filtered::add);
        return List.copyOf(filtered);
    }

    static List<Row> upsert(List<Row> rows, MysterServer server) {
        List<Row> updated = new ArrayList<>(rows.size() + 1);
        rows.stream()
                .filter(row -> !row.server().getIdentity().equals(server.getIdentity()))
                .forEach(updated::add);
        updated.add(row(server));
        return List.copyOf(updated);
    }

    static Row row(MysterServer server) {
        String address = server.getBestAddress().map(Object::toString).orElse("—");
        String name = server.getServerName();
        String displayName = name == null || name.isBlank()
                ? (address.equals("—") ? "Unnamed Server" : address)
                : name.trim();
        String status;
        if (server.isUntried()) {
            status = "Untried";
        } else {
            status = server.getPingTime() == MysterServer.DOWN ? "Down" : "Up";
        }
        return new Row(server, displayName, address, status);
    }
}
