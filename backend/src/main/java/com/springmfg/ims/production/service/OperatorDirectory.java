package com.springmfg.ims.production.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.common.search.SearchText;
import com.springmfg.ims.production.dto.AssignableOperatorDto;

/**
 * Who can be given production work: an active, unlocked user whose roles grant {@code PRODUCTION_EXECUTE}. The same
 * predicate answers the picker (search) and the check made when an operation is assigned, so they cannot disagree.
 */
@Component
@Transactional(readOnly = true)
public class OperatorDirectory {

    private static final String ASSIGNABLE = """
            u.active = TRUE AND (u.locked_until IS NULL OR u.locked_until <= now())
            AND EXISTS (SELECT 1 FROM user_roles ur
                          JOIN role_permissions rp ON rp.role_id = ur.role_id
                          JOIN permissions p ON p.id = rp.permission_id
                         WHERE ur.user_id = u.id AND p.code = 'PRODUCTION_EXECUTE')
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public OperatorDirectory(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<AssignableOperatorDto> assignable(String q) {
        return jdbc.query("""
                SELECT u.id, u.username, u.full_name FROM users u
                 WHERE %s AND (:q = '' OR LOWER(u.username) LIKE LOWER(CONCAT('%%', :q, '%%'))
                               OR LOWER(u.full_name) LIKE LOWER(CONCAT('%%', :q, '%%')))
                 ORDER BY u.full_name, u.id LIMIT 200
                """.formatted(ASSIGNABLE),
                new MapSqlParameterSource("q", SearchText.normalise(q)),
                (rs, i) -> new AssignableOperatorDto(rs.getLong("id"), rs.getString("username"),
                        rs.getString("full_name")));
    }

    public boolean isAssignable(Long userId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM users u WHERE u.id = :id AND " + ASSIGNABLE,
                new MapSqlParameterSource("id", userId), Integer.class);
        return n != null && n > 0;
    }

    /** Full names for the given user ids (any user, active or not, so history still reads). */
    public Map<Long, String> names(Collection<Long> ids) {
        Map<Long, String> names = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return names;
        }
        jdbc.query("SELECT id, full_name FROM users WHERE id IN (:ids)",
                new MapSqlParameterSource("ids", ids),
                rs -> {
                    names.put(rs.getLong("id"), rs.getString("full_name"));
                });
        return names;
    }
}
