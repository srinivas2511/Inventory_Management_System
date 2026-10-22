package com.mfg.ims.production.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.exception.ErrorCode;
import com.mfg.ims.production.repository.ProductionOrderRepository;

@Service
public class AccessScopeService {

    private final ProductionOrderRepository orderRepo;

    public AccessScopeService(ProductionOrderRepository orderRepo) {
        this.orderRepo = orderRepo;
    }

    @Transactional(readOnly = true)
    public boolean canAccessOrder(Long orderId) {
        CachedUser user = currentUser();
        if (user == null) return false;

        // Users with broad view/create access see all orders
        if (user.permissions().contains("PRODUCTION_VIEW") || user.permissions().contains("PRODUCTION_CREATE")) {
            return true;
        }

        return orderRepo.isAccessibleByUser(orderId, user.id());
    }

    public void assertCanAccessOrder(Long orderId) {
        if (!canAccessOrder(orderId)) {
            throw new BusinessRuleException(ErrorCode.OUT_OF_SCOPE, "Order not in your scope");
        }
    }

    private CachedUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CachedUser u) return u;
        return null;
    }
}
