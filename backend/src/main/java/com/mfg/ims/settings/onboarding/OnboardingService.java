package com.mfg.ims.settings.onboarding;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mfg.ims.settings.onboarding.OnboardingDtos.CompleteStepRequest;
import com.mfg.ims.settings.onboarding.OnboardingDtos.OnboardingStatusDto;
import com.mfg.ims.settings.onboarding.OnboardingDtos.OnboardingStepDto;

@Service
public class OnboardingService {

    private final NamedParameterJdbcTemplate jdbc;

    public OnboardingService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public OnboardingStatusDto status(long companyId) {
        String sql = """
                SELECT id, step_key, step_label, completed, completed_by, completed_at, sort_order
                FROM ims.onboarding_steps
                WHERE company_id = :cid
                ORDER BY sort_order
                """;
        List<OnboardingStepDto> steps = jdbc.query(sql,
                new MapSqlParameterSource("cid", companyId),
                (rs, i) -> new OnboardingStepDto(
                        rs.getLong("id"),
                        rs.getString("step_key"),
                        rs.getString("step_label"),
                        rs.getBoolean("completed"),
                        rs.getLong("completed_by") == 0 ? null : rs.getLong("completed_by"),
                        rs.getTimestamp("completed_at") != null
                                ? rs.getTimestamp("completed_at").toInstant() : null,
                        rs.getInt("sort_order")
                ));
        int total = steps.size();
        int done = (int) steps.stream().filter(OnboardingStepDto::completed).count();
        int pct = total == 0 ? 0 : (done * 100 / total);
        return new OnboardingStatusDto(steps, total, done, pct);
    }

    @Transactional
    public OnboardingStatusDto completeStep(long companyId, CompleteStepRequest req, long userId) {
        jdbc.update("""
                UPDATE ims.onboarding_steps
                SET completed = true, completed_by = :uid, completed_at = :now
                WHERE company_id = :cid AND step_key = :key
                """,
                new MapSqlParameterSource()
                        .addValue("uid", userId)
                        .addValue("now", Timestamp.from(Instant.now()))
                        .addValue("cid", companyId)
                        .addValue("key", req.stepKey()));
        return status(companyId);
    }

    @Transactional
    public OnboardingStatusDto resetStep(long companyId, String stepKey) {
        jdbc.update("""
                UPDATE ims.onboarding_steps
                SET completed = false, completed_by = null, completed_at = null
                WHERE company_id = :cid AND step_key = :key
                """,
                new MapSqlParameterSource("cid", companyId).addValue("key", stepKey));
        return status(companyId);
    }
}
