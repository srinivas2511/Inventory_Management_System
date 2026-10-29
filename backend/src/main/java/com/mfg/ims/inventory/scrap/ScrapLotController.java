package com.mfg.ims.inventory.scrap;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.exception.ErrorCode;
import com.mfg.ims.common.exception.NotFoundException;
import com.mfg.ims.inventory.scrap.ScrapLotDtos.CreateScrapLotRequest;
import com.mfg.ims.inventory.scrap.ScrapLotDtos.DisposeScrapRequest;
import com.mfg.ims.inventory.scrap.ScrapLotDtos.ScrapLotDto;
import com.mfg.ims.inventory.scrap.ScrapLotDtos.SellScrapRequest;

@RestController
@RequestMapping("/api/inventory/scrap-lots")
public class ScrapLotController {

    private final NamedParameterJdbcTemplate jdbc;

    public ScrapLotController(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SCRAP_VIEW')")
    public List<ScrapLotDto> list() {
        return jdbc.query(
                "SELECT * FROM ims.scrap_lots ORDER BY created_at DESC LIMIT 500",
                new MapSqlParameterSource(),
                this::mapRow);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SCRAP_VIEW')")
    public ScrapLotDto get(@PathVariable Long id) {
        var rows = jdbc.query(
                "SELECT * FROM ims.scrap_lots WHERE id = :id",
                new MapSqlParameterSource("id", id),
                this::mapRow);
        if (rows.isEmpty()) throw new NotFoundException("Scrap lot not found: " + id);
        return rows.get(0);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SCRAP_MANAGE')")
    public ScrapLotDto create(@RequestBody CreateScrapLotRequest req,
                               @AuthenticationPrincipal CachedUser user) {
        String lotNumber = "SCRAP-" + UUID.randomUUID().toString().toUpperCase().substring(0, 8);
        Long id = jdbc.queryForObject("""
                INSERT INTO ims.scrap_lots
                    (lot_number, source_type, source_id, item_name, quantity, uom,
                     scrap_reason, notes, created_by)
                VALUES
                    (:lot, :sType, :sId, :iName, :qty, :uom, :reason, :notes, :uid)
                RETURNING id
                """,
                new MapSqlParameterSource()
                        .addValue("lot", lotNumber)
                        .addValue("sType", req.sourceType())
                        .addValue("sId", req.sourceId())
                        .addValue("iName", req.itemName())
                        .addValue("qty", req.quantity())
                        .addValue("uom", req.uom())
                        .addValue("reason", req.scrapReason())
                        .addValue("notes", req.notes())
                        .addValue("uid", user.id()),
                Long.class);
        return get(id);
    }

    @PostMapping("/{id}/list-for-sale")
    @PreAuthorize("hasAuthority('SCRAP_MANAGE')")
    public ScrapLotDto listForSale(@PathVariable Long id,
                                    @RequestBody(required = false) SellScrapRequest req,
                                    @AuthenticationPrincipal CachedUser user) {
        if (req == null) req = new SellScrapRequest(null, null, null);
        ScrapLotDto lot = get(id);
        if (!"PENDING".equals(lot.status())) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Scrap lot must be PENDING to list for sale");
        }
        jdbc.update("""
                UPDATE ims.scrap_lots
                SET status = 'LISTED_FOR_SALE', sale_price = :price, buyer_name = :buyer,
                    notes = COALESCE(:notes, notes)
                WHERE id = :id
                """,
                new MapSqlParameterSource()
                        .addValue("price", req.salePrice())
                        .addValue("buyer", req.buyerName())
                        .addValue("notes", req.notes())
                        .addValue("id", id));
        return get(id);
    }

    @PostMapping("/{id}/sell")
    @PreAuthorize("hasAuthority('SCRAP_MANAGE')")
    public ScrapLotDto sell(@PathVariable Long id,
                             @RequestBody(required = false) SellScrapRequest req,
                             @AuthenticationPrincipal CachedUser user) {
        if (req == null) req = new SellScrapRequest(null, null, null);
        ScrapLotDto lot = get(id);
        if (!"LISTED_FOR_SALE".equals(lot.status()) && !"PENDING".equals(lot.status())) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Scrap lot must be PENDING or LISTED_FOR_SALE to mark sold");
        }
        jdbc.update("""
                UPDATE ims.scrap_lots
                SET status = 'SOLD', sale_price = :price, buyer_name = :buyer,
                    disposed_at = :now, disposed_by = :uid
                WHERE id = :id
                """,
                new MapSqlParameterSource()
                        .addValue("price", req.salePrice())
                        .addValue("buyer", req.buyerName())
                        .addValue("now", Instant.now())
                        .addValue("uid", user.id())
                        .addValue("id", id));
        return get(id);
    }

    @PostMapping("/{id}/dispose")
    @PreAuthorize("hasAuthority('SCRAP_MANAGE')")
    public ScrapLotDto dispose(@PathVariable Long id,
                                @RequestBody(required = false) DisposeScrapRequest req,
                                @AuthenticationPrincipal CachedUser user) {
        if (req == null) req = new DisposeScrapRequest(null);
        ScrapLotDto lot = get(id);
        if ("SOLD".equals(lot.status()) || "DISPOSED".equals(lot.status())) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Scrap lot is already " + lot.status());
        }
        jdbc.update("""
                UPDATE ims.scrap_lots
                SET status = 'DISPOSED', disposed_at = :now, disposed_by = :uid,
                    notes = COALESCE(:notes, notes)
                WHERE id = :id
                """,
                new MapSqlParameterSource()
                        .addValue("now", Instant.now())
                        .addValue("uid", user.id())
                        .addValue("notes", req.notes())
                        .addValue("id", id));
        return get(id);
    }

    private ScrapLotDto mapRow(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        return new ScrapLotDto(
                rs.getLong("id"),
                rs.getString("lot_number"),
                rs.getString("source_type"),
                rs.getLong("source_id") == 0 ? null : rs.getLong("source_id"),
                rs.getString("item_name"),
                rs.getBigDecimal("quantity"),
                rs.getString("uom"),
                rs.getString("scrap_reason"),
                rs.getString("status"),
                rs.getBigDecimal("sale_price"),
                rs.getString("buyer_name"),
                rs.getTimestamp("disposed_at") != null ? rs.getTimestamp("disposed_at").toInstant() : null,
                rs.getString("notes"),
                rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : null
        );
    }
}
