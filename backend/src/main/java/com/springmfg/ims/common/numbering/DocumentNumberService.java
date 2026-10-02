package com.springmfg.ims.common.numbering;

import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues sequential document numbers in format PREFIX/YY/NNNNN (e.g. PO/26/00001).
 * Uses a pessimistic lock on number_sequences to prevent duplicates under concurrency.
 */
@Service
public class DocumentNumberService {

    private final NumberSequenceRepository repository;

    public DocumentNumberService(NumberSequenceRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String next(String prefix) {
        int year = LocalDate.now().getYear() % 100; // 2-digit year
        int fullYear = LocalDate.now().getYear();

        NumberSequence seq = repository.findForUpdate(prefix, fullYear)
            .orElseGet(() -> {
                NumberSequence s = new NumberSequence(prefix, fullYear);
                return repository.save(s);
            });

        long next = seq.getLastValue() + 1;
        seq.setLastValue(next);
        repository.save(seq);

        return String.format("%s/%02d/%05d", prefix, year, next);
    }
}
