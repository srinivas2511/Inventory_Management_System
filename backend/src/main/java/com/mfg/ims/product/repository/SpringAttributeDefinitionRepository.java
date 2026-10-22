package com.mfg.ims.product.repository;

import java.util.List;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.mfg.ims.product.domain.SpringAttributeDefinition;

public interface SpringAttributeDefinitionRepository
        extends JpaRepository<SpringAttributeDefinition, Long> {

    @Cacheable("springAttributes")
    List<SpringAttributeDefinition> findBySpringTypeAndActiveTrueOrderByDisplayOrderAsc(String springType);
}
