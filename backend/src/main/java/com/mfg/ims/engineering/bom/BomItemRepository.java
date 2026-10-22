package com.mfg.ims.engineering.bom;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BomItemRepository extends JpaRepository<BomItem, Long> {

    List<BomItem> findByBomIdOrderByLineNo(Long bomId);
}
