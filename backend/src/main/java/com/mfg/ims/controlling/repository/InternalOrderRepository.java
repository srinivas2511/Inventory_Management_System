package com.mfg.ims.controlling.repository;

import com.mfg.ims.controlling.domain.InternalOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface InternalOrderRepository extends JpaRepository<InternalOrder, Long> {

    List<InternalOrder> findAllByOrderByOrderNumberAsc();

    boolean existsByOrderNumber(String orderNumber);

    // Collect costs: sum of journal lines tagged to this internal order
    @Query(value = """
            SELECT l.gl_account_id, ga.account_code, ga.name,
                   SUM(l.debit_amount), SUM(l.credit_amount)
            FROM ims.journal_lines l
            JOIN ims.journal_headers h ON h.id = l.journal_header_id
            JOIN ims.gl_accounts ga ON ga.id = l.gl_account_id
            WHERE h.status IN ('POSTED','REVERSED')
              AND l.internal_order_id = :orderId
            GROUP BY l.gl_account_id, ga.account_code, ga.name
            ORDER BY ga.account_code
            """, nativeQuery = true)
    List<Object[]> collectCosts(@Param("orderId") Long orderId);

    // Total net cost (debit - credit) for settlement
    @Query(value = """
            SELECT COALESCE(SUM(l.debit_amount - l.credit_amount), 0)
            FROM ims.journal_lines l
            JOIN ims.journal_headers h ON h.id = l.journal_header_id
            WHERE h.status IN ('POSTED','REVERSED')
              AND l.internal_order_id = :orderId
            """, nativeQuery = true)
    java.math.BigDecimal totalNetCost(@Param("orderId") Long orderId);
}
