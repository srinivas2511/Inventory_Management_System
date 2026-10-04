package com.springmfg.ims.notification.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.springmfg.ims.notification.domain.Notification;
import com.springmfg.ims.notification.domain.NotificationChannel;
import com.springmfg.ims.notification.domain.NotificationStatus;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    boolean existsByDedupKey(String dedupKey);

    Page<Notification> findByUserIdAndChannel(Long userId, NotificationChannel channel, Pageable pageable);

    Page<Notification> findByUserIdAndChannelAndReadAtIsNull(Long userId, NotificationChannel channel,
                                                              Pageable pageable);

    long countByUserIdAndChannelAndReadAtIsNull(Long userId, NotificationChannel channel);

    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    @Modifying
    @Query("""
            UPDATE Notification n SET n.readAt = :now, n.status = :read, n.updatedAt = :now
            WHERE n.userId = :userId AND n.channel = :channel AND n.readAt IS NULL
            """)
    int markAllRead(@Param("userId") Long userId, @Param("channel") NotificationChannel channel,
                    @Param("read") NotificationStatus read, @Param("now") Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT n FROM Notification n WHERE n.id = :id")
    Optional<Notification> findByIdForUpdate(@Param("id") Long id);

    /** Ids of e-mails that are due: PENDING and either never tried or past their retry time. */
    @Query("""
            SELECT n.id FROM Notification n
            WHERE n.channel = :channel AND n.status = :pending
              AND (n.nextAttemptAt IS NULL OR n.nextAttemptAt <= :now)
            ORDER BY n.createdAt
            """)
    List<Long> findDueIds(@Param("channel") NotificationChannel channel,
                          @Param("pending") NotificationStatus pending, @Param("now") Instant now,
                          Pageable pageable);

    @Query("""
            SELECT n FROM Notification n
            WHERE n.status IN :statuses AND n.channel IN :channels
            """)
    Page<Notification> searchLog(@Param("statuses") Collection<NotificationStatus> statuses,
                                 @Param("channels") Collection<NotificationChannel> channels, Pageable pageable);

    default Page<Notification> searchLog(NotificationStatus status, NotificationChannel channel, Pageable pageable) {
        return searchLog(status == null ? List.of(NotificationStatus.values()) : List.of(status),
                channel == null ? List.of(NotificationChannel.values()) : List.of(channel), pageable);
    }
}
