package com.springmfg.ims.common.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

import org.springframework.data.jpa.domain.Specification;

/**
 * Fluent builder for the list filters every master-data and document screen needs (ARCHITECTURE.md section 12.1).
 * Each method adds one condition <b>only if its argument is present</b>, so a controller can pass its optional
 * request parameters straight through:
 *
 * <pre>
 * Specification&lt;Supplier&gt; spec = Specs.&lt;Supplier&gt;of()
 *         .search(q, "supplierCode", "name", "email")
 *         .eq("active", active)
 *         .between("createdAt", from, to)
 *         .build();
 * </pre>
 *
 * Conditions combine with AND. Attribute names are entity property names and come from code, never from the
 * request: user-supplied sort and filter names must be whitelisted first (see {@link PageRequests}).
 */
public final class Specs<T> {

    private final List<Specification<T>> parts = new ArrayList<>();

    private Specs() {
    }

    public static <T> Specs<T> of() {
        return new Specs<>();
    }

    /** Case-insensitive "contains" on any of the (string) attributes; {@code %}, {@code _} and {@code \} in {@code text} are literal. */
    public Specs<T> search(String text, String... attributes) {
        if (text == null || text.isBlank() || attributes.length == 0) {
            return this;
        }
        String like = "%" + text.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        parts.add((root, query, cb) -> {
            List<Predicate> any = new ArrayList<>();
            for (String attribute : attributes) {
                any.add(cb.like(cb.lower(cb.coalesce(root.<String>get(attribute), "")), like, '\\'));
            }
            return cb.or(any.toArray(Predicate[]::new));
        });
        return this;
    }

    public Specs<T> eq(String attribute, Object value) {
        if (value != null && !(value instanceof CharSequence text && text.toString().isBlank())) {
            parts.add((root, query, cb) -> cb.equal(root.get(attribute), value));
        }
        return this;
    }

    public Specs<T> in(String attribute, Collection<?> values) {
        if (values != null && !values.isEmpty()) {
            parts.add((root, query, cb) -> root.get(attribute).in(values));
        }
        return this;
    }

    /** Inclusive range; either end may be null (open). */
    public <C extends Comparable<? super C>> Specs<T> between(String attribute, C from, C to) {
        if (from != null) {
            parts.add((root, query, cb) -> cb.greaterThanOrEqualTo(root.<C>get(attribute), from));
        }
        if (to != null) {
            parts.add((root, query, cb) -> cb.lessThanOrEqualTo(root.<C>get(attribute), to));
        }
        return this;
    }

    /**
     * Rows whose collection {@code collectionAttribute} has an element with {@code elementAttribute = value}, for
     * example users having a role with a given code. Uses an {@code EXISTS} subquery, so paging and counting are not
     * distorted by duplicate rows. The entity must have an {@code id} property.
     */
    public Specs<T> anyOf(String collectionAttribute, String elementAttribute, Object value) {
        if (value != null && !(value instanceof CharSequence text && text.toString().isBlank())) {
            parts.add((root, query, cb) -> {
                Subquery<Object> sub = query.subquery(Object.class);
                @SuppressWarnings("unchecked")
                Root<T> other = (Root<T>) sub.from(root.getJavaType());
                sub.select(other.get("id")).where(cb.equal(other.get("id"), root.get("id")),
                        cb.equal(other.join(collectionAttribute).get(elementAttribute), value));
                return cb.exists(sub);
            });
        }
        return this;
    }

    /** Adds a hand-written condition (ignored if null). */
    public Specs<T> and(Specification<T> specification) {
        if (specification != null) {
            parts.add(specification);
        }
        return this;
    }

    /** The conjunction of everything added; matches all rows if nothing was. */
    public Specification<T> build() {
        List<Specification<T>> snapshot = List.copyOf(parts);
        return (root, query, cb) -> cb.and(snapshot.stream().map(s -> s.toPredicate(root, query, cb)).toArray(Predicate[]::new));
    }
}
