package org.gridsuite.directory.server.elasticsearch;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;

import java.util.List;

public final class ESUtils {
    private ESUtils() {
        throw new java.lang.UnsupportedOperationException("BddUtils Utility class and cannot be instantiated");
    }

    public static <T> Page<T> searchHitsToPage(SearchHits<T> searchHits, Pageable pageable) {
        List<T> content = searchHits.stream().map(SearchHit::getContent).toList();
        return new PageImpl<>(content, pageable, searchHits.getTotalHits());
    }
}
