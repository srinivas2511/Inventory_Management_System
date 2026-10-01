package com.springmfg.ims.common.api;

import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.springmfg.ims.common.exception.ValidationFailedException;

/** Guards list endpoints against sorting by arbitrary (or non-existent) properties. */
public final class PageRequests {

    private PageRequests() {
    }

    /**
     * Returns {@code pageable} if every requested sort property is allowed; otherwise HTTP 400 on field
     * {@code sort}. With no sort given, {@code defaultSort} applies.
     */
    public static Pageable restrictSort(Pageable pageable, Set<String> allowed, Sort defaultSort) {
        if (pageable.getSort().isUnsorted()) {
            return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), defaultSort);
        }
        for (Sort.Order order : pageable.getSort()) {
            if (!allowed.contains(order.getProperty())) {
                throw new ValidationFailedException("sort",
                        "Cannot sort by '" + order.getProperty() + "'. Allowed: " + String.join(", ", allowed.stream().sorted().toList()));
            }
        }
        return pageable;
    }
}
