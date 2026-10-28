package com.mfg.ims.production.barcode;

import com.mfg.ims.common.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class BarcodeService {

    private final BarcodeLabelRepository repo;

    public BarcodeService(BarcodeLabelRepository repo) {
        this.repo = repo;
    }

    public BarcodeLabelDto generate(GenerateBarcodeRequest request, Long actorId) {
        BarcodeLabel label = new BarcodeLabel();
        label.setLabelType(request.labelType());
        label.setRefType(request.refType());
        label.setRefId(request.refId());
        label.setBarcodeValue(buildBarcodeValue(request));
        label.setQrData(request.qrData());
        label = repo.save(label);
        return toDto(label);
    }

    @Transactional(readOnly = true)
    public List<BarcodeLabelDto> list() {
        return repo.findByCompanyIdOrderByIdDesc(1L).stream()
                .map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public BarcodeLabelDto get(Long id) {
        BarcodeLabel label = repo.findByIdAndCompanyId(id, 1L)
                .orElseThrow(() -> new NotFoundException("BarcodeLabel", id));
        return toDto(label);
    }

    public BarcodeLabelDto recordPrint(Long id, Long actorId) {
        BarcodeLabel label = repo.findByIdAndCompanyId(id, 1L)
                .orElseThrow(() -> new NotFoundException("BarcodeLabel", id));
        label.setPrintCount(label.getPrintCount() + 1);
        label.setPrintedAt(Instant.now());
        label.setPrintedBy(actorId);
        return toDto(repo.save(label));
    }

    private String buildBarcodeValue(GenerateBarcodeRequest req) {
        return req.refType() + "-" + req.refId() + "-" + System.currentTimeMillis();
    }

    private BarcodeLabelDto toDto(BarcodeLabel b) {
        return new BarcodeLabelDto(b.getId(), b.getLabelType(), b.getRefType(), b.getRefId(),
                b.getBarcodeValue(), b.getQrData(), b.getPrintedAt(),
                b.getPrintCount(), b.getCreatedAt());
    }
}
