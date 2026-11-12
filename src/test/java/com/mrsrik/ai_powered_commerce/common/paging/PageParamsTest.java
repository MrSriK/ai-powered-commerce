package com.mrsrik.ai_powered_commerce.common.paging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mrsrik.ai_powered_commerce.common.error.ErrorCode;
import com.mrsrik.ai_powered_commerce.common.error.InvalidPaginationException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

class PageParamsTest {

    @Test
    void nullsFallBackToDefaults() {
        PageParams params = PageParams.of(null, null);

        assertThat(params.page()).isEqualTo(0);
        assertThat(params.size()).isEqualTo(20);
    }

    @ParameterizedTest
    @CsvSource({"0, 1", "0, 100", "5, 20", "1000, 50"})
    void acceptsValuesInRange(int page, int size) {
        PageParams params = PageParams.of(page, size);

        assertThat(params.page()).isEqualTo(page);
        assertThat(params.size()).isEqualTo(size);
    }

    @ParameterizedTest
    @CsvSource({"0, 0", "0, 101", "0, -1", "-1, 20", "-1, 0"})
    void rejectsValuesOutOfRange(int page, int size) {
        assertThatThrownBy(() -> PageParams.of(page, size))
                .isInstanceOfSatisfying(InvalidPaginationException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getCode()).isEqualTo(ErrorCode.INVALID_PAGINATION);
                });
    }

    @Test
    void rejectionMessageNamesTheParameter() {
        assertThatThrownBy(() -> PageParams.of(0, 101)).hasMessageContaining("size").hasMessageContaining("100");
        assertThatThrownBy(() -> PageParams.of(-1, 10)).hasMessageContaining("page");
    }

    @Test
    void toPageableAppendsIdAscendingTieBreaker() {
        Pageable pageable = PageParams.of(2, 10).toPageable(Sort.by("name"));

        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(10);
        assertThat(pageable.getSort().toList())
                .containsExactly(Sort.Order.asc("name"), Sort.Order.asc("id"));
    }

    @Test
    void toPageableKeepsCallerIdOrder() {
        Sort sort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

        Pageable pageable = PageParams.of(0, 20).toPageable(sort);

        assertThat(pageable.getSort().toList())
                .containsExactly(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
    }

    @Test
    void toPageableWithUnsortedUsesIdOnly() {
        Pageable pageable = PageParams.of(null, null).toPageable(Sort.unsorted());

        assertThat(pageable.getSort().toList()).containsExactly(Sort.Order.asc("id"));
    }

    @Test
    void pageResponseCopiesPageMetadata() {
        PageImpl<String> page = new PageImpl<>(List.of("a", "b"), PageRequest.of(1, 2), 5);

        PageResponse<String> response = PageResponse.from(page);

        assertThat(response.content()).containsExactly("a", "b");
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.totalElements()).isEqualTo(5);
        assertThat(response.totalPages()).isEqualTo(3);
    }
}
