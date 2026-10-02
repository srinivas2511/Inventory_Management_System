package com.springmfg.ims.product.repository;

import java.util.List;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.springmfg.ims.product.domain.SpringAttributeDefinition;

public interface SpringAttributeDefinitionRepository
        extends JpaRepository<SpringAttributeDefinition, Long> {

    @Cacheable("springAttributes")
    List<SpringAttributeDefinition> findBySpringTypeOrderBySortOrderAsc(String springType);
}
