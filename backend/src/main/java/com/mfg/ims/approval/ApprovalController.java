package com.mfg.ims.approval;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.mfg.ims.approval.dto.ApprovalRequestDto;
import com.mfg.ims.approval.service.ApprovalService;

@RestController
@RequestMapping("/api/approvals")
public class ApprovalController {

    private final ApprovalService service;

    public ApprovalController(ApprovalService service) {
        this.service = service;
    }

    /** Pending requests the current user can decide. */
    @GetMapping("/pending")
    @PreAuthorize("hasAuthority('APPROVAL_DECIDE')")
    public List<ApprovalRequestDto> pending() {
        return service.findPendingForCurrentUser();
    }

    /** Requests the current user has already decided. */
    @GetMapping("/history")
    @PreAuthorize("hasAuthority('APPROVAL_DECIDE')")
    public List<ApprovalRequestDto> history() {
        return service.findHistoryForCurrentUser();
    }

    /** Approve a pending request. Enforces SoD (requester cannot approve own request). */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('APPROVAL_DECIDE')")
    public ApprovalRequestDto approve(@PathVariable Long id,
                                      @RequestBody(required = false) DecideRequest req) {
        return service.approve(id, req != null ? req.comment() : null);
    }

    /** Reject a pending request. */
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('APPROVAL_DECIDE')")
    public ApprovalRequestDto reject(@PathVariable Long id,
                                     @RequestBody(required = false) DecideRequest req) {
        return service.reject(id, req != null ? req.comment() : null);
    }

    /** Withdraw a pending request. Only the original requester may withdraw. */
    @PostMapping("/{id}/withdraw")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("isAuthenticated()")
    public void withdraw(@PathVariable Long id) {
        service.withdraw(id);
    }

    public record DecideRequest(String comment) {}
}
