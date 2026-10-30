package com.mfg.ims.document.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mfg.ims.document.domain.DocumentMention;

public interface DocumentMentionRepository extends JpaRepository<DocumentMention, Long> {

    List<DocumentMention> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(String entityType, Long entityId);
}
