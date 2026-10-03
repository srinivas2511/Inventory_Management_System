package com.springmfg.ims.hrm.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.audit.service.AuditService;
import com.springmfg.ims.common.exception.ConflictException;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.common.exception.NotFoundException;
import com.springmfg.ims.hrm.domain.Shift;
import com.springmfg.ims.hrm.dto.AttendanceDtos.SaveShiftRequest;
import com.springmfg.ims.hrm.dto.AttendanceDtos.ShiftDto;
import com.springmfg.ims.hrm.repository.ShiftRepository;

@Service
public class ShiftService {

    private final ShiftRepository repo;
    private final AuditService auditService;

    public ShiftService(ShiftRepository repo, AuditService auditService) {
        this.repo = repo;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<ShiftDto> list() {
        return repo.findAllByOrderByShiftCodeAsc().stream().map(ShiftService::toDto).toList();
    }

    @Transactional
    public ShiftDto create(SaveShiftRequest req) {
        String code = req.shiftCode().trim().toUpperCase();
        if (repo.findByShiftCode(code).isPresent()) {
            throw new ConflictException(ErrorCode.DUPLICATE_KEY, "Shift " + code + " already exists");
        }
        Shift s = new Shift();
        s.setShiftCode(code);
        apply(s, req);
        s = repo.save(s);
        auditService.record("SHIFT_CREATE", "SHIFT", String.valueOf(s.getId()), null, code, null);
        return toDto(s);
    }

    /** The shift code is the key attendance and employees refer to, so it never changes. */
    @Transactional
    public ShiftDto update(Long id, SaveShiftRequest req) {
        Shift s = repo.findById(id).orElseThrow(() -> new NotFoundException("Shift", id));
        apply(s, req);
        s = repo.save(s);
        auditService.record("SHIFT_UPDATE", "SHIFT", String.valueOf(id), null, s.getShiftCode(), null);
        return toDto(s);
    }

    private static void apply(Shift s, SaveShiftRequest req) {
        s.setName(req.name().trim());
        s.setStartTime(req.startTime());
        s.setEndTime(req.endTime());
        s.setGraceMinutes(req.graceMinutes() == null ? 0 : req.graceMinutes().shortValue());
        s.setActive(req.active() == null || req.active());
    }

    static ShiftDto toDto(Shift s) {
        return new ShiftDto(s.getId(), s.getShiftCode(), s.getName(), s.getStartTime(), s.getEndTime(),
                s.getGraceMinutes(), s.isActive());
    }
}
