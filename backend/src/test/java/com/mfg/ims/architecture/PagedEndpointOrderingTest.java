package com.mfg.ims.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.RestController;

/**
 * A paged list without an ORDER BY returns rows in whatever order PostgreSQL happens to produce, so a page can repeat
 * or miss rows and a new document can sit anywhere. Every controller method that takes a {@link Pageable} must declare
 * the default sort in {@link PageableDefault#sort()}; this test fails when a new paged endpoint forgets to.
 */
class PagedEndpointOrderingTest {

    /** Endpoints whose repository query carries its own explicit ORDER BY (reason in the value). */
    private static final Map<String, String> ORDERED_BY_THEIR_QUERY = Map.of(
            "AuditController#search", "AuditLogRepository orders by occurredAt DESC",
            "AlertController#search", "AlertRepository orders by firstSeenAt DESC");

    @Test
    @DisplayName("every paged controller method declares the default sort of its list")
    void everyPagedEndpointDeclaresItsDefaultSort() throws Exception {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        List<String> unsorted = new ArrayList<>();
        int paged = 0;
        for (BeanDefinition candidate : scanner.findCandidateComponents("com.mfg.ims")) {
            Class<?> controller = Class.forName(candidate.getBeanClassName());
            for (Method method : controller.getDeclaredMethods()) {
                for (Parameter parameter : method.getParameters()) {
                    if (!Pageable.class.equals(parameter.getType())) {
                        continue;
                    }
                    paged++;
                    String id = controller.getSimpleName() + "#" + method.getName();
                    PageableDefault declared = parameter.getAnnotation(PageableDefault.class);
                    boolean sorted = declared != null && (declared.sort().length > 0);
                    if (!sorted && !ORDERED_BY_THEIR_QUERY.containsKey(id)) {
                        unsorted.add(id);
                    }
                }
            }
        }

        assertThat(paged).as("the scan must find the paged endpoints, or this test proves nothing").isGreaterThan(40);
        assertThat(unsorted)
                .as("paged endpoints with no declared default sort: add sort = ... to @PageableDefault")
                .isEmpty();
    }
}
