package com.mfg.ims.finance.tax.service;

import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.exception.ErrorCode;
import com.mfg.ims.enterprise.domain.Company;
import com.mfg.ims.enterprise.repository.CompanyRepository;
import com.mfg.ims.finance.config.domain.TaxCode;
import com.mfg.ims.finance.config.repository.TaxCodeRepository;
import com.mfg.ims.finance.tax.dto.TaxDtos.DeterminationDto;
import com.mfg.ims.finance.tax.dto.TaxDtos.TaxLineDto;
import com.mfg.ims.finance.tax.repository.GstStateRepository;
import com.mfg.ims.finance.tax.repository.TaxSlabRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * GST determination from data. The supply type follows the state of the company and the state of the partner (the first
 * two characters of its GSTIN): the same state is an intra-state supply (CGST + SGST, or CGST + UTGST in a union
 * territory), different states are inter-state (IGST). The slab then names the tax codes, and each tax code carries its
 * rate and its input and output GL accounts. Nothing here knows a rate.
 */
@Service
@Transactional(readOnly = true)
public class TaxDeterminationService {

    public enum Direction { PURCHASE, SALES }

    /** One component with the GL account the direction posts it to. */
    public record Component(Long taxCodeId, String taxCode, String taxType, BigDecimal ratePct,
                            BigDecimal taxableAmount, BigDecimal taxAmount, Long glAccountId) {}

    public record Determination(String slabCode, String supplyType, String companyStateCode, String partnerStateCode,
                                boolean partnerStateAssumed, BigDecimal taxableAmount, BigDecimal taxTotal,
                                List<Component> components) {

        public DeterminationDto toDto() {
            return new DeterminationDto(slabCode, supplyType, companyStateCode, partnerStateCode, partnerStateAssumed,
                    taxableAmount, taxTotal, components.stream()
                    .map(c -> new TaxLineDto(c.taxCode(), c.taxType(), c.ratePct(), c.taxableAmount(), c.taxAmount()))
                    .toList());
        }
    }

    private final TaxSlabRepository slabs;
    private final TaxCodeRepository taxCodes;
    private final GstStateRepository states;
    private final CompanyRepository companies;

    public TaxDeterminationService(TaxSlabRepository slabs, TaxCodeRepository taxCodes, GstStateRepository states,
                                   CompanyRepository companies) {
        this.slabs = slabs;
        this.taxCodes = taxCodes;
        this.states = states;
        this.companies = companies;
    }

    public Determination determine(Direction direction, String slabCode, String partnerGstNumber, BigDecimal taxable) {
        if (slabCode == null || slabCode.isBlank() || !slabs.existsBySlabCodeAndActiveTrue(slabCode.trim())) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "Unknown tax slab " + slabCode);
        }
        String slab = slabCode.trim();
        String companyState = companyState();
        String partnerState = stateOf(partnerGstNumber);
        boolean assumed = partnerState == null;
        if (assumed) {
            partnerState = companyState;
        }
        String supplyType;
        if (companyState.equals(partnerState)) {
            supplyType = states.findById(companyState).map(s -> s.isUnionTerritory() ? "UT" : "INTRA").orElse("INTRA");
        } else {
            supplyType = "INTER";
        }

        var rows = slabs.findBySlabCodeAndSupplyTypeAndActiveTrue(slab, supplyType);
        Map<Long, TaxCode> codes = taxCodes.findAllById(rows.stream().map(r -> r.getTaxCodeId()).toList()).stream()
                .collect(Collectors.toMap(TaxCode::getId, c -> c));
        List<Component> components = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (var row : rows) {
            TaxCode tc = codes.get(row.getTaxCodeId());
            BigDecimal amount = taxable.multiply(tc.getRatePct()).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
            Long gl = direction == Direction.PURCHASE ? tc.getInputGlAccountId() : tc.getOutputGlAccountId();
            if (amount.signum() > 0 && gl == null) {
                throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                        "Tax code " + tc.getTaxCode() + " has no " + (direction == Direction.PURCHASE ? "input" : "output")
                                + " GL account");
            }
            components.add(new Component(tc.getId(), tc.getTaxCode(), tc.getTaxType(), tc.getRatePct(), taxable, amount, gl));
            total = total.add(amount);
        }
        return new Determination(slab, supplyType, companyState, partnerState, assumed, taxable, total, components);
    }

    /** Several determinations of one document (one per slab) as a single one: same partner, so same supply type. */
    public Determination combine(List<Determination> parts) {
        Determination first = parts.get(0);
        List<Component> all = new ArrayList<>();
        BigDecimal taxable = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        for (Determination p : parts) {
            all.addAll(p.components());
            taxable = taxable.add(p.taxableAmount());
            tax = tax.add(p.taxTotal());
        }
        String slabs = parts.stream().map(Determination::slabCode).distinct().collect(Collectors.joining("+"));
        return new Determination(slabs, first.supplyType(), first.companyStateCode(), first.partnerStateCode(),
                first.partnerStateAssumed(), taxable, tax, all);
    }

    /** The GST state code of a GSTIN (its first two characters), or null for an unregistered partner. */
    public static String stateOf(String gstNumber) {
        if (gstNumber == null || gstNumber.length() < 2) {
            return null;
        }
        String code = gstNumber.substring(0, 2);
        return Character.isDigit(code.charAt(0)) && Character.isDigit(code.charAt(1)) ? code : null;
    }

    private String companyState() {
        String state = companies.findById(1L).map(Company::getGstin).map(TaxDeterminationService::stateOf).orElse(null);
        if (state == null) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "The company GSTIN is not configured, so the place of supply cannot be determined");
        }
        return state;
    }
}
