package com.springmfg.ims.document.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.springmfg.ims.document.domain.DocumentCategory;

public interface DocumentCategoryRepository extends JpaRepository<DocumentCategory, Long> {

    Optional<DocumentCategory> findByCategoryCode(String categoryCode);

    List<DocumentCategory> findAllByOrderByNameAsc();
}
