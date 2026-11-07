package com.flowpilot.shared.web;

import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Restricts client-supplied sorting to a whitelist. Sorting by an unknown or internal property
 * would otherwise fail deep inside the query (500) or expose columns that are not part of the API.
 */
public final class Pageables {

    private Pageables() {
    }

    public static Pageable restrictSort(Pageable requested, Set<String> allowed, Sort fallback) {
        Sort sort = Sort.by(requested.getSort().stream()
                .filter(order -> allowed.contains(order.getProperty()))
                .toList());
        return PageRequest.of(requested.getPageNumber(), requested.getPageSize(), sort.isSorted() ? sort : fallback);
    }
}
