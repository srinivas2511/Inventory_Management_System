package com.springmfg.ims.masterdata;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface MaterialRepository extends JpaRepository<Material, Long>, JpaSpecificationExecutor<Material> {

    boolean existsByMaterialCode(String materialCode);

    /** Lists load the preferred supplier in the same query (a page of 100 must not cost 101 queries). */
    @Override
    @EntityGraph(attributePaths = "preferredSupplier")
    Page<Material> findAll(Specification<Material> spec, Pageable pageable);
}
