package com.springmfg.ims.product;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttributeDefinitionRepository extends JpaRepository<AttributeDefinition, Long> {

    List<AttributeDefinition> findBySpringTypeOrderByDisplayOrderAscAttributeCodeAsc(SpringType springType);

    @Query("select d.springType, count(d) from AttributeDefinition d group by d.springType")
    List<Object[]> countByType();
}
