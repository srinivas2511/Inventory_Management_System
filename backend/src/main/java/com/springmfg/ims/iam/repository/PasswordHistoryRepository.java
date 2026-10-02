package com.springmfg.ims.iam.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.springmfg.ims.iam.domain.PasswordHistory;

public interface PasswordHistoryRepository extends JpaRepository<PasswordHistory, Long> {

    List<PasswordHistory> findByUserIdOrderByChangedAtDesc(Long userId, Pageable pageable);
}
