package com.mfg.ims.crm.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.mfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.mfg.ims.common.exception.ConflictException;
import com.mfg.ims.common.exception.ErrorCode;
import com.mfg.ims.common.exception.NotFoundException;
import com.mfg.ims.crm.domain.CrmContact;
import com.mfg.ims.crm.repository.CrmContactRepository;
import com.mfg.ims.iam.repository.UserRepository;
import com.mfg.ims.partner.domain.Customer;
import com.mfg.ims.partner.repository.CustomerRepository;
import com.mfg.ims.product.domain.Product;
import com.mfg.ims.product.repository.ProductRepository;

/** Shared reference lookups for the CRM services: owner resolution, display names, existence checks, version checks. */
@Component
class CrmLookups {

    private final UserRepository users;
    private final CustomerRepository customers;
    private final CrmContactRepository contacts;
    private final ProductRepository products;

    CrmLookups(UserRepository users, CustomerRepository customers, CrmContactRepository contacts,
               ProductRepository products) {
        this.users = users;
        this.customers = customers;
        this.contacts = contacts;
        this.products = products;
    }

    /** The authenticated user's id, or null when there is none (background jobs). */
    static Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CachedUser u) {
            return u.id();
        }
        return null;
    }

    /** The requested owner, else the current user; the user must exist because owner_id is a foreign key. */
    Long ownerOrCurrent(Long requested) {
        Long id = requested != null ? requested : currentUserId();
        if (id == null) {
            throw new NotFoundException("Owner not specified and no authenticated user");
        }
        if (!users.existsById(id)) {
            throw new NotFoundException("User", id);
        }
        return id;
    }

    String userName(Long id) {
        return id == null ? null : users.findById(id).map(u -> u.getFullName()).orElse(null);
    }

    Customer requireCustomer(Long id) {
        return customers.findById(id).orElseThrow(() -> new NotFoundException("Customer", id));
    }

    String customerName(Long id) {
        return id == null ? null : customers.findById(id).map(Customer::getName).orElse(null);
    }

    CrmContact requireContact(Long id) {
        return contacts.findById(id).orElseThrow(() -> new NotFoundException("Contact", id));
    }

    String contactName(Long id) {
        return id == null ? null : contacts.findById(id).map(CrmContact::fullName).orElse(null);
    }

    Product requireProduct(Long id) {
        return products.findById(id).orElseThrow(() -> new NotFoundException("Product", id));
    }

    static void checkVersion(Long expected, Long actual, String what) {
        if (expected != null && !expected.equals(actual)) {
            throw new ConflictException(ErrorCode.VERSION_CONFLICT,
                    what + " was changed by someone else; reload and retry");
        }
    }
}
