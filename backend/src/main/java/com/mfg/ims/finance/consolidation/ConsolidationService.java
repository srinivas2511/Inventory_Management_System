package com.mfg.ims.finance.consolidation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.mfg.ims.common.exception.NotFoundException;
import com.mfg.ims.finance.consolidation.ConsolidationDtos.*;

@Service
public class ConsolidationService {

    private final NamedParameterJdbcTemplate jdbc;

    public ConsolidationService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public ConsolidationReportDto consolidatedReport(LocalDate from, LocalDate to) {
        var params = new MapSqlParameterSource("from", from).addValue("to", to);

        // Aggregate GL movements for the period — revenue positive, expense negative for income calc
        String glSql = """
            SELECT ga.account_code, ga.name AS account_name,
                   ga.account_type AS account_group,
                   COALESCE(SUM(jl.debit_amount - jl.credit_amount), 0) AS net_amount
            FROM ims.journal_lines jl
            JOIN ims.journal_headers jh ON jh.id = jl.journal_header_id
            JOIN ims.gl_accounts ga ON ga.id = jl.gl_account_id
            WHERE jh.status = 'POSTED'
              AND jh.posting_date >= :from AND jh.posting_date <= :to
            GROUP BY ga.id, ga.account_code, ga.name, ga.account_type
            ORDER BY ga.account_code
            """;
        List<ConsolidationLineDto> lines;
        try {
            lines = jdbc.query(glSql, params, (rs, rn) -> new ConsolidationLineDto(
                rs.getString("account_code"),
                rs.getString("account_name"),
                rs.getString("account_group"),
                safeBd(rs.getBigDecimal("net_amount"))
            ));
        } catch (Exception e) {
            lines = new ArrayList<>();
        }

        // Load manual eliminations for this date range
        String elimSql = """
            SELECT ie.id, ie.description, ie.amount,
                   ie.debit_account_code, ie.credit_account_code
            FROM ims.intercompany_eliminations ie
            JOIN ims.fiscal_periods fp ON fp.id = ie.period_id
            WHERE fp.start_date >= :from AND fp.end_date <= :to
            ORDER BY ie.id
            """;
        List<EliminationDto> eliminations;
        try {
            eliminations = jdbc.query(elimSql, params, (rs, rn) -> new EliminationDto(
                rs.getLong("id"),
                rs.getString("description"),
                safeBd(rs.getBigDecimal("amount")),
                rs.getString("debit_account_code"),
                rs.getString("credit_account_code")
            ));
        } catch (Exception e) {
            eliminations = new ArrayList<>();
        }

        // Revenue: normal balance CREDIT, so net (Dr-Cr) is negative; negate to get positive revenue
        BigDecimal totalRevenue = lines.stream()
            .filter(l -> "REVENUE".equalsIgnoreCase(l.accountGroup()))
            .map(ConsolidationLineDto::amount)
            .reduce(BigDecimal.ZERO, BigDecimal::add).negate();

        // Expense: normal balance DEBIT, so net (Dr-Cr) is positive
        BigDecimal totalExpense = lines.stream()
            .filter(l -> "EXPENSE".equalsIgnoreCase(l.accountGroup()))
            .map(ConsolidationLineDto::amount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal netIncome = totalRevenue.subtract(totalExpense);

        return new ConsolidationReportDto(from, to, lines, eliminations,
            totalRevenue, totalExpense, netIncome);
    }

    @Transactional
    public EliminationDto addElimination(AddEliminationRequest req) {
        Long userId = currentUserId();
        // Verify period exists first to return 404 rather than FK constraint error (500)
        Long cnt = jdbc.queryForObject("SELECT COUNT(*) FROM ims.fiscal_periods WHERE id = :id",
                new MapSqlParameterSource("id", req.periodId()), Long.class);
        if (cnt == null || cnt == 0) throw new NotFoundException("FiscalPeriod", req.periodId().toString());

        String sql = """
            INSERT INTO ims.intercompany_eliminations
                (period_id, description, amount, debit_account_code, credit_account_code, created_by)
            VALUES (:pid, :desc, :amount, :dr, :cr, :uid)
            RETURNING id, description, amount, debit_account_code, credit_account_code
            """;
        var params = new MapSqlParameterSource()
            .addValue("pid",    req.periodId())
            .addValue("desc",   req.description())
            .addValue("amount", req.amount())
            .addValue("dr",     req.debitAccountCode())
            .addValue("cr",     req.creditAccountCode())
            .addValue("uid",    userId);
        return jdbc.queryForObject(sql, params, (rs, rn) -> new EliminationDto(
            rs.getLong("id"),
            rs.getString("description"),
            safeBd(rs.getBigDecimal("amount")),
            rs.getString("debit_account_code"),
            rs.getString("credit_account_code")
        ));
    }

    private static BigDecimal safeBd(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }

    private Long currentUserId() {
        Object p = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return p instanceof CachedUser u ? u.id() : 1L;
    }
}
