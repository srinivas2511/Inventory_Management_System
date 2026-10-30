package com.mfg.ims.finance.tds;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "tds_sections", schema = "ims")
public class TdsSection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId = 1L;

    @Column(name = "section", nullable = false, length = 10)
    private String section;

    @Column(name = "description", length = 150)
    private String description;

    @Column(name = "rate", nullable = false, precision = 9, scale = 4)
    private BigDecimal rate;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    public Long getId() { return id; }
    public Long getCompanyId() { return companyId; }
    public String getSection() { return section; }
    public String getDescription() { return description; }
    public BigDecimal getRate() { return rate; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
}
