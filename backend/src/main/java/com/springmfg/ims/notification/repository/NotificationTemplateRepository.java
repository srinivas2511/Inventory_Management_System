package com.springmfg.ims.notification.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.springmfg.ims.notification.domain.NotificationTemplate;

public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, Long> {

    Optional<NotificationTemplate> findByTemplateCode(String templateCode);

    List<NotificationTemplate> findAllByOrderByTemplateCodeAsc();

    List<NotificationTemplate> findByActiveTrueOrderByTemplateCodeAsc();
}
