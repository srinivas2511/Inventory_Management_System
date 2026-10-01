package com.springmfg.ims.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.springmfg.ims.common.api.PageRequests;
import com.springmfg.ims.common.exception.ValidationFailedException;

class PageRequestsTest {

    private static final Set<String> ALLOWED = Set.of("username", "createdAt");

    @Test
    void appliesTheDefaultSortWhenNoneIsGiven() {
        Pageable result = PageRequests.restrictSort(PageRequest.of(2, 10), ALLOWED, Sort.by("username"));
        assertThat(result.getSort()).isEqualTo(Sort.by("username"));
        assertThat(result.getPageNumber()).isEqualTo(2);
        assertThat(result.getPageSize()).isEqualTo(10);
    }

    @Test
    void keepsAnAllowedSort() {
        Pageable requested = PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "createdAt"));
        assertThat(PageRequests.restrictSort(requested, ALLOWED, Sort.by("username"))).isEqualTo(requested);
    }

    @Test
    void rejectsAnyPropertyOutsideTheWhitelist() {
        Pageable requested = PageRequest.of(0, 5, Sort.by("passwordHash"));
        assertThatThrownBy(() -> PageRequests.restrictSort(requested, ALLOWED, Sort.by("username")))
                .isInstanceOf(ValidationFailedException.class);
    }
}
