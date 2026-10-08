package com.springmfg.ims.enterprise.scope;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads and writes the plants a user is assigned to (user_plant_access). No assignment means no restriction. */
@Service
public class PlantAccessService {

    private final NamedParameterJdbcTemplate jdbc;

    public PlantAccessService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Set<Long> plantIdsOf(Long userId) {
        List<Long> ids = jdbc.queryForList(
                "SELECT plant_id FROM user_plant_access WHERE user_id = :u ORDER BY plant_id",
                new MapSqlParameterSource("u", userId), Long.class);
        return new LinkedHashSet<>(ids);
    }

    /** Replaces the user's assignments. An empty collection removes every restriction. */
    @Transactional
    public void replace(Long userId, Collection<Long> plantIds, Long changedBy) {
        jdbc.update("DELETE FROM user_plant_access WHERE user_id = :u", new MapSqlParameterSource("u", userId));
        for (Long plantId : new LinkedHashSet<>(plantIds)) {
            jdbc.update("INSERT INTO user_plant_access (user_id, plant_id, created_by) VALUES (:u, :p, :by)",
                    new MapSqlParameterSource("u", userId).addValue("p", plantId).addValue("by", changedBy));
        }
    }
}
