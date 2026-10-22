package com.mfg.ims.notification.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mfg.ims.notification.domain.NotificationChannel;
import com.mfg.ims.notification.domain.NotificationSubscription;

public interface NotificationSubscriptionRepository extends JpaRepository<NotificationSubscription, Long> {

    List<NotificationSubscription> findByUserId(Long userId);

    List<NotificationSubscription> findByUserIdAndTemplateCode(Long userId, String templateCode);

    Optional<NotificationSubscription> findByUserIdAndTemplateCodeAndChannel(Long userId, String templateCode,
                                                                             NotificationChannel channel);
}
