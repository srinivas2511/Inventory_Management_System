package com.mfg.ims.integration.gst;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GstEInvoiceRepository extends JpaRepository<GstEInvoice, Long> {

    Optional<GstEInvoice> findByDispatchId(Long dispatchId);
}
