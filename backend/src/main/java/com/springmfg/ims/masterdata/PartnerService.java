package com.springmfg.ims.masterdata;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.audit.AuditCommand;
import com.springmfg.ims.common.api.PageRequests;
import com.springmfg.ims.common.api.PageResponse;
import com.springmfg.ims.common.api.Specs;
import com.springmfg.ims.common.exception.ConflictException;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.common.exception.NotFoundException;

/**
 * Create, update, list and (de)activate for suppliers and customers, which differ only in their code column,
 * permissions and audit names. Partners are never deleted; deactivation only blocks new documents (DESIGN.md
 * section 5.2), so it needs no usage check. Every change is audited with old and new values.
 */
abstract class PartnerService<E extends BusinessPartner> {

    private static final Set<String> SORTABLE = Set.of("code", "name", "email", "gstNumber", "active", "createdAt");

    private final MasterDataMapper mapper;
    private final ApplicationEventPublisher events;
    private final String entityName;
    private final String codeProperty;

    PartnerService(MasterDataMapper mapper, ApplicationEventPublisher events, String entityName, String codeProperty) {
        this.mapper = mapper;
        this.events = events;
        this.entityName = entityName;
        this.codeProperty = codeProperty;
    }

    abstract JpaRepository<E, Long> repository();

    abstract JpaSpecificationExecutor<E> specifications();

    abstract boolean codeExists(String code);

    abstract E newEntity(String code);

    @Transactional(readOnly = true)
    public PageResponse<PartnerDtos.PartnerSummary> list(String q, Boolean active, Pageable pageable) {
        Pageable page = PageRequests.restrictSort(pageable, SORTABLE, Sort.by("code"));
        Sort translated = Sort.by(page.getSort().stream()
                .map(o -> o.getProperty().equals("code") ? o.withProperty(codeProperty) : o).toList());
        Specification<E> spec = Specs.<E>of().search(q, codeProperty, "name", "contactPerson", "email", "gstNumber")
                .eq("active", active).build();
        return PageResponse.from(specifications().findAll(spec,
                org.springframework.data.domain.PageRequest.of(page.getPageNumber(), page.getPageSize(), translated)),
                mapper::toSummary);
    }

    @Transactional(readOnly = true)
    public PartnerDtos.PartnerResponse get(long id) {
        return mapper.toResponse(find(id));
    }

    @Transactional
    public PartnerDtos.PartnerResponse create(PartnerDtos.CreatePartnerRequest request) {
        String code = request.code().trim();
        if (codeExists(code)) {
            throw new ConflictException(ErrorCode.DUPLICATE_KEY, entityName + " code '" + code + "' already exists.");
        }
        E partner = newEntity(code);
        partner.update(request.name().trim(), blank(request.contactPerson()), blank(request.phone()), email(request.email()),
                blank(request.address()), blank(request.gstNumber()), blank(request.paymentTerms()), request.leadTimeDays());
        E saved = repository().saveAndFlush(partner);
        audit("CREATED", saved, null, snapshot(saved));
        return mapper.toResponse(saved);
    }

    @Transactional
    public PartnerDtos.PartnerResponse update(long id, PartnerDtos.UpdatePartnerRequest request) {
        E partner = find(id);
        if (partner.getVersion() != request.version().longValue()) {
            throw new ConflictException(ErrorCode.VERSION_CONFLICT,
                    "The " + entityName.toLowerCase(Locale.ROOT) + " was modified by someone else. Reload and try again.");
        }
        Map<String, Object> before = snapshot(partner);
        partner.update(request.name().trim(), blank(request.contactPerson()), blank(request.phone()), email(request.email()),
                blank(request.address()), blank(request.gstNumber()), blank(request.paymentTerms()), request.leadTimeDays());
        repository().saveAndFlush(partner);
        audit("UPDATED", partner, before, snapshot(partner));
        return mapper.toResponse(partner);
    }

    @Transactional
    public void deactivate(long id, String reason) {
        E partner = find(id);
        if (!partner.isActive()) {
            return; // idempotent
        }
        Map<String, Object> before = snapshot(partner);
        partner.setActive(false);
        repository().saveAndFlush(partner);
        events.publishEvent(new AuditCommand(entityName.toUpperCase(Locale.ROOT) + "_DEACTIVATED", entityName,
                String.valueOf(id), before, snapshot(partner), blank(reason)));
    }

    @Transactional
    public PartnerDtos.PartnerResponse activate(long id) {
        E partner = find(id);
        if (!partner.isActive()) {
            Map<String, Object> before = snapshot(partner);
            partner.setActive(true);
            repository().saveAndFlush(partner);
            audit("ACTIVATED", partner, before, snapshot(partner));
        }
        return mapper.toResponse(partner);
    }

    final E find(long id) {
        return repository().findById(id).orElseThrow(() -> new NotFoundException(entityName, id));
    }

    private void audit(String what, E partner, Map<String, Object> before, Map<String, Object> after) {
        events.publishEvent(new AuditCommand(entityName.toUpperCase(Locale.ROOT) + "_" + what, entityName,
                String.valueOf(partner.getId()), before, after, null));
    }

    private Map<String, Object> snapshot(E partner) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("code", partner.getCode());
        map.put("name", partner.getName());
        map.put("contactPerson", partner.getContactPerson());
        map.put("phone", partner.getPhone());
        map.put("email", partner.getEmail());
        map.put("address", partner.getAddress());
        map.put("gstNumber", partner.getGstNumber());
        map.put("paymentTerms", partner.getPaymentTerms());
        map.put("leadTimeDays", partner.getLeadTimeDays());
        map.put("active", partner.isActive());
        return map;
    }

    static String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String email(String value) {
        String trimmed = blank(value);
        return trimmed == null ? null : trimmed.toLowerCase(Locale.ROOT);
    }
}
