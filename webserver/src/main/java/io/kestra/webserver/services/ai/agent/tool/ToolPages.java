package io.kestra.webserver.services.ai.agent.tool;

import java.util.List;

final class ToolPages {
    private ToolPages() {
    }

    static int pageNumber(final Integer page) {
        if (page != null && page < 1) {
            throw new IllegalArgumentException("Page must be 1 or greater, got %d.".formatted(page));
        }
        return page == null ? 1 : page;
    }

    static <T> List<T> slice(final List<T> items, final int page, final int size) {
        return items.stream().skip((long) (page - 1) * size).limit(size).toList();
    }
}
