package com.mfg.ims.finance.fx.repository;

import com.mfg.ims.finance.fx.domain.FxRevaluation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FxRevaluationRepository extends JpaRepository<FxRevaluation, Long> {

    Optional<FxRevaluation> findByDocumentTypeAndDocumentIdAndPeriodId(
            String documentType, Long documentId, Long periodId);

    List<FxRevaluation> findByPeriodIdOrderByDocumentTypeAscDocumentIdAsc(Long periodId);
}
