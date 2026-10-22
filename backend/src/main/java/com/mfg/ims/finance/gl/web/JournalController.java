package com.mfg.ims.finance.gl.web;

import com.mfg.ims.finance.gl.dto.JournalCreateRequest;
import com.mfg.ims.finance.gl.dto.JournalHeaderDto;
import com.mfg.ims.finance.gl.service.JournalService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/journals")
public class JournalController {

    private final JournalService journalService;

    public JournalController(JournalService journalService) {
        this.journalService = journalService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('GL_POST')")
    public JournalHeaderDto create(@Valid @RequestBody JournalCreateRequest req) {
        return journalService.createDraft(req);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('GL_VIEW')")
    public Page<JournalHeaderDto> list(@PageableDefault(size = 20, sort = "id", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
        return journalService.findAll(pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('GL_VIEW')")
    public JournalHeaderDto getById(@PathVariable Long id) {
        return journalService.findById(id);
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('GL_POST')")
    public JournalHeaderDto submit(@PathVariable Long id) {
        return journalService.submit(id);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('JOURNAL_APPROVE')")
    public JournalHeaderDto approve(@PathVariable Long id) {
        return journalService.approve(id);
    }

    @PostMapping("/{id}/post")
    @PreAuthorize("hasAuthority('GL_POST')")
    public JournalHeaderDto post(@PathVariable Long id) {
        return journalService.post(id);
    }

    @PostMapping("/{id}/reverse")
    @PreAuthorize("hasAuthority('GL_POST')")
    public JournalHeaderDto reverse(@PathVariable Long id,
                                    @RequestParam(defaultValue = "Reversal") String reason) {
        return journalService.reverse(id, reason);
    }
}
