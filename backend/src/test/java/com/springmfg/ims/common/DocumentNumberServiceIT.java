package com.springmfg.ims.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import com.springmfg.ims.common.numbering.DocumentNumberService;
import com.springmfg.ims.common.numbering.DocumentType;
import com.springmfg.ims.support.AbstractIntegrationTest;

/**
 * Task 1.7: gap-free, per-period, concurrency-safe document numbers. Each test uses its own far-future year so the
 * shared test database never produces collisions between tests.
 */
class DocumentNumberServiceIT extends AbstractIntegrationTest {

    private static final AtomicInteger YEAR = new AtomicInteger(3000);

    @Autowired
    DocumentNumberService numbers;
    @Autowired
    TransactionTemplate tx;

    private static LocalDate freshYear() {
        return LocalDate.of(YEAR.incrementAndGet(), 3, 15);
    }

    private String next(DocumentType type, LocalDate date) {
        return tx.execute(status -> numbers.next(type, date));
    }

    @Test
    void numbersCountUpFromOneInTheDocumentedFormat() {
        LocalDate date = freshYear();
        String year = String.valueOf(date.getYear());
        assertThat(next(DocumentType.PURCHASE_ORDER, date)).isEqualTo("PUR-" + year + "-00001");
        assertThat(next(DocumentType.PURCHASE_ORDER, date)).isEqualTo("PUR-" + year + "-00002");
        assertThat(next(DocumentType.RM_BATCH, date)).isEqualTo("RM-BATCH-" + year + "-001");
        assertThat(next(DocumentType.FG_BATCH, date)).isEqualTo("FG-" + year + "-0001");
        assertThat(next(DocumentType.PURCHASE_ORDER, date)).isEqualTo("PUR-" + year + "-00003"); // types are independent
    }

    @Test
    void everyTypeHasItsOwnSequence() {
        LocalDate date = freshYear();
        for (DocumentType type : DocumentType.values()) {
            assertThat(next(type, date)).as(type.name()).isEqualTo(type.format(date, 1));
            assertThat(next(type, date)).as(type.name()).isEqualTo(type.format(date, 2));
        }
    }

    @Test
    void sequencesRestartEachYear() {
        LocalDate first = freshYear();
        LocalDate second = first.plusYears(1);
        YEAR.incrementAndGet();
        assertThat(next(DocumentType.SALES_ORDER, first)).endsWith("-00001");
        assertThat(next(DocumentType.SALES_ORDER, first)).endsWith("-00002");
        assertThat(next(DocumentType.SALES_ORDER, second)).isEqualTo("SO-" + second.getYear() + "-00001");
        assertThat(next(DocumentType.SALES_ORDER, first)).endsWith("-00003"); // back-dated numbering continues its own year
    }

    @Test
    void stockTransactionsRestartEachMonthAndBomsNever() {
        LocalDate october = LocalDate.of(YEAR.incrementAndGet(), 10, 31);
        LocalDate november = october.plusDays(1);
        assertThat(next(DocumentType.STOCK_TRANSACTION, october)).isEqualTo("TXN-" + october.getYear() + "10-000001");
        assertThat(next(DocumentType.STOCK_TRANSACTION, october)).isEqualTo("TXN-" + october.getYear() + "10-000002");
        assertThat(next(DocumentType.STOCK_TRANSACTION, november)).isEqualTo("TXN-" + november.getYear() + "11-000001");

        // a BOM number is not tied to a year: it keeps counting across years (the global counter is shared by the whole suite)
        long a = Long.parseLong(next(DocumentType.BOM, october).substring(4));
        long b = Long.parseLong(next(DocumentType.BOM, october.plusYears(5)).substring(4));
        assertThat(b).isEqualTo(a + 1);
    }

    @Test
    void aRolledBackDocumentGivesItsNumberBack() {
        LocalDate date = freshYear();
        assertThat(next(DocumentType.GOODS_RECEIPT, date)).endsWith("-00001");

        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            assertThat(numbers.next(DocumentType.GOODS_RECEIPT, date)).endsWith("-00002");
            throw new IllegalStateException("document creation failed");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(next(DocumentType.GOODS_RECEIPT, date)).endsWith("-00002"); // no gap
    }

    @Test
    void aNumberCanOnlyBeTakenInsideTheTransactionThatCreatesTheDocument() {
        assertThatThrownBy(() -> numbers.next(DocumentType.DISPATCH, freshYear())).isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> numbers.next(DocumentType.DISPATCH)).isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void numberForTodayUsesTheCurrentYear() {
        String number = tx.execute(status -> numbers.next(DocumentType.PURCHASE_REQUISITION));
        assertThat(number).matches("PR-\\d{4}-\\d{5}");
    }

    @Test
    void concurrentCallersNeverGetTheSameNumberAndLeaveNoGaps() throws Exception {
        LocalDate date = freshYear();
        int threads = 12;
        int perThread = 6;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<List<String>>> futures = new ArrayList<>();
            CountDownLatch start = new CountDownLatch(1);
            for (int t = 0; t < threads; t++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    List<String> mine = new ArrayList<>();
                    for (int i = 0; i < perThread; i++) {
                        mine.add(tx.execute(status -> numbers.next(DocumentType.MATERIAL_ISSUE, date)));
                    }
                    return mine;
                }));
            }
            start.countDown();
            Set<String> all = new TreeSet<>();
            int total = 0;
            for (Future<List<String>> future : futures) {
                List<String> mine = future.get(60, TimeUnit.SECONDS);
                total += mine.size();
                all.addAll(mine);
            }
            assertThat(total).isEqualTo(threads * perThread);
            assertThat(all).as("all numbers unique").hasSize(total);
            for (int n = 1; n <= total; n++) {
                assertThat(all).contains(DocumentType.MATERIAL_ISSUE.format(date, n));
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void theNextCallerWaitsUntilTheFirstDocumentCommits() throws Exception {
        LocalDate date = freshYear();
        CountDownLatch firstHasNumber = new CountDownLatch(1);
        CountDownLatch commitFirst = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = pool.submit(() -> tx.execute(status -> {
                String number = numbers.next(DocumentType.INSPECTION, date);
                firstHasNumber.countDown();
                try {
                    commitFirst.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return number;
            }));
            assertThat(firstHasNumber.await(30, TimeUnit.SECONDS)).isTrue();
            Future<String> second = pool.submit(() -> tx.execute(status -> numbers.next(DocumentType.INSPECTION, date)));

            Thread.sleep(500);
            assertThat(second.isDone()).as("second caller is blocked behind the uncommitted first").isFalse();
            commitFirst.countDown();
            assertThat(first.get(30, TimeUnit.SECONDS)).endsWith("-00001");
            assertThat(second.get(30, TimeUnit.SECONDS)).endsWith("-00002");
        } finally {
            pool.shutdownNow();
        }
    }
}
