package com.mfg.ims.doccore.transporter;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mfg.ims.common.exception.NotFoundException;

@Service
public class TransporterService {

    private final TransporterRepository repo;

    public TransporterService(TransporterRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public List<TransporterDto> list() {
        return repo.findByCompanyIdAndActiveTrueOrderByName(1L)
                .stream().map(TransporterDto::from).toList();
    }

    @Transactional
    public TransporterDto create(SaveTransporterRequest req) {
        Transporter t = new Transporter();
        apply(t, req);
        return TransporterDto.from(repo.save(t));
    }

    @Transactional
    public TransporterDto update(Long id, SaveTransporterRequest req) {
        Transporter t = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Transporter not found: " + id));
        apply(t, req);
        return TransporterDto.from(repo.save(t));
    }

    @Transactional
    public void deactivate(Long id) {
        Transporter t = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Transporter not found: " + id));
        t.setActive(false);
        repo.save(t);
    }

    private void apply(Transporter t, SaveTransporterRequest req) {
        t.setTransporterCode(req.transporterCode());
        t.setName(req.name().trim());
        t.setGstTransporterId(req.gstTransporterId());
        t.setContact(req.contact());
    }
}
