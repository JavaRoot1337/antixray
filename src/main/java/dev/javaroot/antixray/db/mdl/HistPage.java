package dev.javaroot.antixray.db.mdl;

import java.util.List;

public record HistPage(
        List<DetLog> entries,
        int page,
        int maxPage,
        int totalCount
) {
    public static final HistPage EMPTY = new HistPage(List.of(), 1, 1, 0);
}
