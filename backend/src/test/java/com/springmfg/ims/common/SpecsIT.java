package com.springmfg.ims.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;

import com.springmfg.ims.common.api.Specs;
import com.springmfg.ims.iam.User;
import com.springmfg.ims.iam.UserRepository;
import com.springmfg.ims.support.AbstractIntegrationTest;
import com.springmfg.ims.support.TestUsers;

/** Task 1.7: the reusable Specification builder, against real JPA and PostgreSQL. */
class SpecsIT extends AbstractIntegrationTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    UserRepository users;
    @Autowired
    TestUsers testUsers;
    @Autowired
    JdbcTemplate jdbc;

    /** Users whose name carries a unique tag, so each test sees only its own rows. */
    private String tag() {
        return "spc" + SEQ.incrementAndGet() + "q";
    }

    private User user(String tag, String suffix, String role) {
        User user = testUsers.create(role);
        jdbc.update("UPDATE users SET username = ?, full_name = ?, email = ? WHERE id = ?", tag + suffix, "Name " + suffix,
                tag + suffix + "@example.com", user.getId());
        return user;
    }

    private List<String> names(org.springframework.data.jpa.domain.Specification<User> spec) {
        return users.findAll(spec, Sort.by("username")).stream().map(User::getUsername).toList();
    }

    @Test
    void anEmptyBuilderMatchesEverythingAndNullOrBlankArgumentsAreIgnored() {
        String tag = tag();
        user(tag, "a", "SALES");
        user(tag, "b", "SALES");
        long all = users.count();
        assertThat(users.count(Specs.<User>of().build())).isEqualTo(all);
        assertThat(users.count(Specs.<User>of().search(null, "username").search("  ", "username").eq("active", null)
                .eq("username", " ").in("id", null).in("id", Set.of()).between("createdAt", null, null)
                .anyOf("roles", "code", null).anyOf("roles", "code", "").and(null).build())).isEqualTo(all);
    }

    @Test
    void searchIsCaseInsensitiveAcrossAllNamedAttributes() {
        String tag = tag();
        user(tag, "alpha", "SALES");
        user(tag, "beta", "SALES");
        assertThat(names(Specs.<User>of().search(tag.toUpperCase() + "ALPHA", "username").build())).containsExactly(tag + "alpha");
        // matches in any of the listed attributes
        assertThat(names(Specs.<User>of().search(tag, "username", "fullName", "email").build())).containsExactly(tag + "alpha", tag + "beta");
        assertThat(names(Specs.<User>of().search("Name BETA", "fullName").and((root, q, cb) -> cb.like(root.get("username"), tag + "%")).build()))
                .containsExactly(tag + "beta");
    }

    @Test
    void searchTreatsWildcardCharactersAsPlainText() {
        String tag = tag();
        user(tag, "x_1", "SALES");
        user(tag, "xy1", "SALES");
        assertThat(names(Specs.<User>of().search(tag + "x_1", "username").build())).containsExactly(tag + "x_1"); // '_' is not "any char"
        assertThat(names(Specs.<User>of().search(tag + "%", "username").build())).isEmpty();
        assertThat(names(Specs.<User>of().search("\\", "username").build())).isEmpty();
    }

    @Test
    void equalsAndInFilterOnProperties() {
        String tag = tag();
        User active = user(tag, "on", "SALES");
        User inactive = user(tag, "off", "SALES");
        jdbc.update("UPDATE users SET active = FALSE WHERE id = ?", inactive.getId());
        assertThat(names(Specs.<User>of().search(tag, "username").eq("active", false).build())).containsExactly(tag + "off");
        assertThat(names(Specs.<User>of().search(tag, "username").eq("active", true).build())).containsExactly(tag + "on");
        assertThat(names(Specs.<User>of().in("id", List.of(active.getId(), inactive.getId())).build())).containsExactly(tag + "off", tag + "on");
        assertThat(names(Specs.<User>of().eq("username", tag + "on").build())).containsExactly(tag + "on");
    }

    @Test
    void betweenIsInclusiveAndEitherEndMayBeOpen() {
        String tag = tag();
        User early = user(tag, "1", "SALES");
        User middle = user(tag, "2", "SALES");
        User late = user(tag, "3", "SALES");
        Instant base = Instant.parse("2020-05-10T00:00:00Z");
        jdbc.update("UPDATE users SET created_at = ? WHERE id = ?", Timestamp.from(base), early.getId());
        jdbc.update("UPDATE users SET created_at = ? WHERE id = ?", Timestamp.from(base.plusSeconds(3600)), middle.getId());
        jdbc.update("UPDATE users SET created_at = ? WHERE id = ?", Timestamp.from(base.plusSeconds(7200)), late.getId());

        var scope = Specs.<User>of().search(tag, "username");
        assertThat(names(Specs.<User>of().search(tag, "username").between("createdAt", base, base.plusSeconds(3600)).build()))
                .containsExactly(tag + "1", tag + "2"); // both ends inclusive
        assertThat(names(Specs.<User>of().search(tag, "username").between("createdAt", base.plusSeconds(3600), null).build()))
                .containsExactly(tag + "2", tag + "3");
        assertThat(names(scope.between("createdAt", null, base.plusSeconds(3599)).build())).containsExactly(tag + "1");
    }

    @Test
    void anyOfFindsRowsByCollectionElementWithoutDuplicatingThem() {
        String tag = tag();
        User both = user(tag, "both", "SALES");
        jdbc.update("INSERT INTO user_roles (user_id, role_id) SELECT ?, id FROM roles WHERE code = 'DISPATCH'", both.getId());
        user(tag, "only", "SALES");

        assertThat(names(Specs.<User>of().search(tag, "username").anyOf("roles", "code", "SALES").build())).containsExactly(tag + "both", tag + "only");
        assertThat(names(Specs.<User>of().search(tag, "username").anyOf("roles", "code", "DISPATCH").build())).containsExactly(tag + "both");
        assertThat(names(Specs.<User>of().search(tag, "username").anyOf("roles", "code", "ADMIN").build())).isEmpty();

        // a user with two matching elements is still one row, and the page counts agree
        var page = users.findAll(Specs.<User>of().search(tag, "username").anyOf("roles", "name", "Role does not exist").build(), PageRequest.of(0, 10));
        assertThat(page.getTotalElements()).isZero();
        var pageAll = users.findAll(Specs.<User>of().search(tag, "username").build(), PageRequest.of(0, 1, Sort.by("username")));
        assertThat(pageAll.getTotalElements()).isEqualTo(2);
        assertThat(pageAll.getTotalPages()).isEqualTo(2);
    }

    @Test
    void conditionsCombineWithAnd() {
        String tag = tag();
        User sales = user(tag, "sales", "SALES");
        user(tag, "dispatch", "DISPATCH");
        jdbc.update("UPDATE users SET active = FALSE WHERE id = ?", sales.getId());
        assertThat(names(Specs.<User>of().search(tag, "username").eq("active", true).anyOf("roles", "code", "SALES").build())).isEmpty();
        assertThat(names(Specs.<User>of().search(tag, "username").eq("active", false).anyOf("roles", "code", "SALES").build())).containsExactly(tag + "sales");
    }
}
