package com.mfg.ims.finance.fx.service;

import com.mfg.ims.common.exception.NotFoundException;
import com.mfg.ims.finance.ap.domain.ApInvoice;
import com.mfg.ims.finance.ap.repository.ApInvoiceRepository;
import com.mfg.ims.finance.ar.domain.ArInvoice;
import com.mfg.ims.finance.ar.repository.ArInvoiceRepository;
import com.mfg.ims.finance.config.domain.ExchangeRate;
import com.mfg.ims.finance.config.repository.ExchangeRateRepository;
import com.mfg.ims.finance.fx.domain.FxRevaluation;
import com.mfg.ims.finance.fx.dto.FxRevaluationDtos.FxRevaluationDto;
import com.mfg.ims.finance.fx.dto.FxRevaluationDtos.FxRevaluationRunResult;
import com.mfg.ims.finance.fx.repository.FxRevaluationRepository;
import com.mfg.ims.finance.gl.domain.FiscalPeriod;
import com.mfg.ims.finance.gl.dto.AutoJournalRequest;
import com.mfg.ims.finance.gl.dto.JournalHeaderDto;
import com.mfg.ims.finance.gl.dto.JournalLineRequest;
import com.mfg.ims.finance.gl.repository.FiscalPeriodRepository;
import com.mfg.ims.finance.gl.service.GlAccountResolver;
import com.mfg.ims.finance.gl.service.JournalService;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Unrealised FX revaluation: open AP and AR invoices in a foreign currency are restated at the
 * period closing rate. The difference is an unrealised FX gain (4902) or loss (5209).
 *
 * <p>The table {@code fx_revaluations} tracks the cumulative unrealised position per
 * (document, period). Re-running for the same period posts only the incremental adjustment,
 * so the process is safe to repeat as rates are updated.
 */
@Service
@Transactional
public class FxRevaluationService {

    private static final String AP_INVOICE = "AP_INVOICE";
    private static final String AR_INVOICE = "AR_INVOICE";
    private static final String AP_CONTROL = "2402";
    private static final String AR_CONTROL = "1101";
    private static final String UNREALISED_GAIN = "4902";
    private static final String UNREALISED_LOSS = "5209";
    private static final BigDecimal THRESHOLD = new BigDecimal("0.01");
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final FxRevaluationRepository revalRepo;
    private final ApInvoiceRepository apInvoiceRepo;
    private final ArInvoiceRepository arInvoiceRepo;
    private final ExchangeRateRepository exchangeRateRepo;
    private final FiscalPeriodRepository periodRepo;
    private final JournalService journalService;
    private final GlAccountResolver glAccounts;

    public FxRevaluationService(FxRevaluationRepository revalRepo,
                                 ApInvoiceRepository apInvoiceRepo,
                                 ArInvoiceRepository arInvoiceRepo,
                                 ExchangeRateRepository exchangeRateRepo,
                                 FiscalPeriodRepository periodRepo,
                                 JournalService journalService,
                                 GlAccountResolver glAccounts) {
        this.revalRepo = revalRepo;
        this.apInvoiceRepo = apInvoiceRepo;
        this.arInvoiceRepo = arInvoiceRepo;
        this.exchangeRateRepo = exchangeRateRepo;
        this.periodRepo = periodRepo;
        this.journalService = journalService;
        this.glAccounts = glAccounts;
    }

    /**
     * Runs revaluation for all open foreign-currency AP and AR invoices using the closing rate of
     * {@code periodId}. Invoices whose currency has no rate for this period are skipped silently.
     * Re-running posts only the marginal adjustment since the previous run.
     */
    public FxRevaluationRunResult run(Long periodId) {
        FiscalPeriod period = periodRepo.findById(periodId)
                .orElseThrow(() -> new NotFoundException("FiscalPeriod", periodId));
        Long actor = currentUserId();

        int apCount = 0, arCount = 0;
        BigDecimal apGain = ZERO, apLoss = ZERO, arGain = ZERO, arLoss = ZERO;

        Map<String, BigDecimal> rateCache = buildRateCache(periodId);

        for (ApInvoice inv : apInvoiceRepo.findByStatusIn(List.of("OPEN", "PARTIALLY_PAID"))) {
            if ("INR".equals(inv.getCurrencyCode())) continue;
            BigDecimal closingRate = rateCache.get(inv.getCurrencyCode());
            if (closingRate == null) continue;

            BigDecimal remainingForeign = inv.getTotalAmount()
                    .subtract(inv.getPaidAmount())
                    .subtract(inv.getDebitedAmount());
            if (remainingForeign.signum() <= 0) continue;

            // Carrying value of the remaining open portion at the booking rate.
            // Using remainingForeign * bookingRate avoids depending on payment/debit-note
            // carrying records and correctly prorates for partial payments.
            BigDecimal baseCarrying = remainingForeign.multiply(inv.getExchangeRate())
                    .setScale(4, RoundingMode.HALF_UP);

            Optional<FxRevaluation> prior = revalRepo.findByDocumentTypeAndDocumentIdAndPeriodId(
                    AP_INVOICE, inv.getId(), periodId);
            BigDecimal priorDiff = prior.map(FxRevaluation::getDifferenceInr).orElse(ZERO);
            BigDecimal netCarrying = baseCarrying.add(priorDiff);

            BigDecimal revalued = remainingForeign.multiply(closingRate).setScale(4, RoundingMode.HALF_UP);
            BigDecimal increment = revalued.subtract(netCarrying);
            if (increment.abs().compareTo(THRESHOLD) < 0) continue;

            JournalHeaderDto jnl = journalService.postSourceJournal(new AutoJournalRequest(
                    "FX_REVALUATION", "FINANCE", AP_INVOICE, inv.getId(),
                    period.getEndDate(),
                    "Unrealised FX revaluation " + inv.getInvoiceNumber()
                            + " (" + inv.getCurrencyCode() + " @" + closingRate.toPlainString() + ")",
                    actor, apLines(increment)));

            upsert(prior, AP_INVOICE, inv.getId(), periodId, inv.getCurrencyCode(),
                    remainingForeign, inv.getExchangeRate(), closingRate,
                    priorDiff.add(increment), jnl.id());

            apCount++;
            if (increment.signum() > 0) apLoss = apLoss.add(increment);
            else apGain = apGain.add(increment.negate());
        }

        for (ArInvoice inv : arInvoiceRepo.findByStatusIn(List.of("OPEN", "PARTIALLY_PAID"))) {
            if ("INR".equals(inv.getCurrencyCode())) continue;
            BigDecimal closingRate = rateCache.get(inv.getCurrencyCode());
            if (closingRate == null) continue;

            BigDecimal remainingForeign = inv.getTotalAmount()
                    .subtract(inv.getReceivedAmount())
                    .subtract(inv.getCreditedAmount());
            if (remainingForeign.signum() <= 0) continue;

            BigDecimal baseCarrying = remainingForeign.multiply(inv.getExchangeRate())
                    .setScale(4, RoundingMode.HALF_UP);

            Optional<FxRevaluation> prior = revalRepo.findByDocumentTypeAndDocumentIdAndPeriodId(
                    AR_INVOICE, inv.getId(), periodId);
            BigDecimal priorDiff = prior.map(FxRevaluation::getDifferenceInr).orElse(ZERO);
            BigDecimal netCarrying = baseCarrying.add(priorDiff);

            BigDecimal revalued = remainingForeign.multiply(closingRate).setScale(4, RoundingMode.HALF_UP);
            BigDecimal increment = revalued.subtract(netCarrying);
            if (increment.abs().compareTo(THRESHOLD) < 0) continue;

            JournalHeaderDto jnl = journalService.postSourceJournal(new AutoJournalRequest(
                    "FX_REVALUATION", "FINANCE", AR_INVOICE, inv.getId(),
                    period.getEndDate(),
                    "Unrealised FX revaluation " + inv.getInvoiceNumber()
                            + " (" + inv.getCurrencyCode() + " @" + closingRate.toPlainString() + ")",
                    actor, arLines(increment)));

            upsert(prior, AR_INVOICE, inv.getId(), periodId, inv.getCurrencyCode(),
                    remainingForeign, inv.getExchangeRate(), closingRate,
                    priorDiff.add(increment), jnl.id());

            arCount++;
            if (increment.signum() > 0) arGain = arGain.add(increment);
            else arLoss = arLoss.add(increment.negate());
        }

        return new FxRevaluationRunResult(apCount, arCount, apGain, apLoss, arGain, arLoss);
    }

    @Transactional(readOnly = true)
    public List<FxRevaluationDto> list(Long periodId) {
        Map<Long, String> periodNames = periodRepo.findAll().stream()
                .collect(Collectors.toMap(FiscalPeriod::getId, FiscalPeriod::getPeriodName));

        // Document numbers fetched lazily; build lookup maps for AP and AR invoice numbers
        Map<Long, String> apNumbers = apInvoiceRepo.findAll().stream()
                .collect(Collectors.toMap(ApInvoice::getId, ApInvoice::getInvoiceNumber));
        Map<Long, String> arNumbers = arInvoiceRepo.findAll().stream()
                .collect(Collectors.toMap(ArInvoice::getId, ArInvoice::getInvoiceNumber));

        List<FxRevaluation> rows = periodId != null
                ? revalRepo.findByPeriodIdOrderByDocumentTypeAscDocumentIdAsc(periodId)
                : revalRepo.findAll();

        return rows.stream().map(r -> {
            String num = AP_INVOICE.equals(r.getDocumentType())
                    ? apNumbers.getOrDefault(r.getDocumentId(), String.valueOf(r.getDocumentId()))
                    : arNumbers.getOrDefault(r.getDocumentId(), String.valueOf(r.getDocumentId()));
            return new FxRevaluationDto(r.getId(), r.getPeriodId(),
                    periodNames.get(r.getPeriodId()), r.getDocumentType(), r.getDocumentId(),
                    num, r.getCurrencyCode(), r.getRemainingForeign(), r.getBookingRate(),
                    r.getClosingRate(), r.getDifferenceInr(), r.getJournalHeaderId());
        }).toList();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Map<String, BigDecimal> buildRateCache(Long periodId) {
        return exchangeRateRepo.findAll().stream()
                .filter(r -> r.getPeriodId().equals(periodId) && "INR".equals(r.getToCurrency()))
                .collect(Collectors.toMap(
                        ExchangeRate::getFromCurrency,
                        ExchangeRate::getRate,
                        (a, b) -> a));
    }

    private List<JournalLineRequest> apLines(BigDecimal increment) {
        BigDecimal abs = increment.abs();
        if (increment.signum() > 0) {
            // AP liability grew → unrealised loss: Dr 5209 / Cr 2402
            return List.of(
                    new JournalLineRequest(glAccounts.idOf(UNREALISED_LOSS), abs, ZERO, "Unrealised FX loss AP"),
                    new JournalLineRequest(glAccounts.idOf(AP_CONTROL), ZERO, abs, "AP revaluation Cr"));
        } else {
            // AP liability shrank → unrealised gain: Dr 2402 / Cr 4902
            return List.of(
                    new JournalLineRequest(glAccounts.idOf(AP_CONTROL), abs, ZERO, "AP revaluation Dr"),
                    new JournalLineRequest(glAccounts.idOf(UNREALISED_GAIN), ZERO, abs, "Unrealised FX gain AP"));
        }
    }

    private List<JournalLineRequest> arLines(BigDecimal increment) {
        BigDecimal abs = increment.abs();
        if (increment.signum() > 0) {
            // AR receivable grew → unrealised gain: Dr 1101 / Cr 4902
            return List.of(
                    new JournalLineRequest(glAccounts.idOf(AR_CONTROL), abs, ZERO, "AR revaluation Dr"),
                    new JournalLineRequest(glAccounts.idOf(UNREALISED_GAIN), ZERO, abs, "Unrealised FX gain AR"));
        } else {
            // AR receivable shrank → unrealised loss: Dr 5209 / Cr 1101
            return List.of(
                    new JournalLineRequest(glAccounts.idOf(UNREALISED_LOSS), abs, ZERO, "Unrealised FX loss AR"),
                    new JournalLineRequest(glAccounts.idOf(AR_CONTROL), ZERO, abs, "AR revaluation Cr"));
        }
    }

    private void upsert(Optional<FxRevaluation> prior, String docType, Long docId, Long periodId,
                        String currency, BigDecimal remainingForeign, BigDecimal bookingRate,
                        BigDecimal closingRate, BigDecimal totalDiff, Long journalHeaderId) {
        FxRevaluation r = prior.orElseGet(FxRevaluation::new);
        r.setPeriodId(periodId);
        r.setDocumentType(docType);
        r.setDocumentId(docId);
        r.setCurrencyCode(currency);
        r.setRemainingForeign(remainingForeign);
        r.setBookingRate(bookingRate);
        r.setClosingRate(closingRate);
        r.setDifferenceInr(totalDiff);
        r.setJournalHeaderId(journalHeaderId);
        revalRepo.save(r);
    }

    private Long currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof com.mfg.ims.auth.service.UserPermissionCache.CachedUser u) {
            return u.id();
        }
        return -1L;
    }
}
