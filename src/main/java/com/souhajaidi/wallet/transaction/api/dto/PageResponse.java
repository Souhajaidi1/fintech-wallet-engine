package com.souhajaidi.wallet.transaction.api.dto;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * A page of results.
 *
 * <p>Spring's own {@code Page} is not returned directly because its JSON shape is an
 * implementation detail that has changed between versions; pinning the contract here
 * keeps the API stable across upgrades.
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages, boolean last) {

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast());
    }
}
