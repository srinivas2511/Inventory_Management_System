package com.springmfg.ims.crm.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.audit.service.AuditService;
import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ConflictException;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.common.exception.NotFoundException;
import com.springmfg.ims.crm.domain.CustomerTag;
import com.springmfg.ims.crm.domain.CustomerTagAssignment;
import com.springmfg.ims.crm.dto.CrmCustomerDto;
import com.springmfg.ims.crm.dto.SaveTagRequest;
import com.springmfg.ims.crm.dto.TagDto;
import com.springmfg.ims.crm.repository.CrmCustomerRepository;
import com.springmfg.ims.crm.repository.CustomerTagAssignmentRepository;
import com.springmfg.ims.crm.repository.CustomerTagRepository;
import com.springmfg.ims.partner.domain.Customer;

@Service
public class CustomerTagService {

    private final CustomerTagRepository tagRepo;
    private final CustomerTagAssignmentRepository assignmentRepo;
    private final CrmCustomerRepository customerRepo;
    private final CrmLookups lookups;
    private final AuditService auditService;

    public CustomerTagService(CustomerTagRepository tagRepo, CustomerTagAssignmentRepository assignmentRepo,
                              CrmCustomerRepository customerRepo, CrmLookups lookups, AuditService auditService) {
        this.tagRepo = tagRepo;
        this.assignmentRepo = assignmentRepo;
        this.customerRepo = customerRepo;
        this.lookups = lookups;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<TagDto> listTags() {
        return tagRepo.findAll().stream()
                .sorted((a, b) -> a.getTagGroup().equals(b.getTagGroup())
                        ? a.getName().compareToIgnoreCase(b.getName()) : a.getTagGroup().compareTo(b.getTagGroup()))
                .map(CustomerTagService::toDto).toList();
    }

    @Transactional
    public TagDto createTag(SaveTagRequest req) {
        if (tagRepo.existsByTagGroupAndName(req.tagGroup(), req.name())) {
            throw new ConflictException(ErrorCode.DUPLICATE_KEY, "Tag already exists: " + req.tagGroup() + " / " + req.name());
        }
        CustomerTag t = new CustomerTag();
        t.setTagGroup(req.tagGroup());
        t.setName(req.name());
        t.setActive(req.active() == null || req.active());
        t = tagRepo.save(t);
        auditService.record("TAG_CREATE", "CUSTOMER_TAG", String.valueOf(t.getId()), null, t.getTagGroup() + "/" + t.getName(), null);
        return toDto(t);
    }

    @Transactional
    public TagDto updateTag(Long id, SaveTagRequest req) {
        CustomerTag t = requireTag(id);
        boolean keyChanged = !t.getTagGroup().equals(req.tagGroup()) || !t.getName().equals(req.name());
        if (keyChanged && tagRepo.existsByTagGroupAndName(req.tagGroup(), req.name())) {
            throw new ConflictException(ErrorCode.DUPLICATE_KEY, "Tag already exists: " + req.tagGroup() + " / " + req.name());
        }
        String old = t.getTagGroup() + "/" + t.getName();
        t.setTagGroup(req.tagGroup());
        t.setName(req.name());
        if (req.active() != null) t.setActive(req.active());
        t = tagRepo.save(t);
        auditService.record("TAG_UPDATE", "CUSTOMER_TAG", String.valueOf(id), old, t.getTagGroup() + "/" + t.getName(), null);
        return toDto(t);
    }

    @Transactional
    public void deleteTag(Long id) {
        CustomerTag t = requireTag(id);
        if (assignmentRepo.existsByTagId(id)) {
            throw new BusinessRuleException(ErrorCode.DEACTIVATION_BLOCKED,
                    "Tag is assigned to customers; remove it from them or deactivate it instead");
        }
        tagRepo.delete(t);
        auditService.record("TAG_DELETE", "CUSTOMER_TAG", String.valueOf(id), t.getTagGroup() + "/" + t.getName(), null, null);
    }

    @Transactional(readOnly = true)
    public Page<CrmCustomerDto> searchCustomers(String q, Long tagId, Pageable pageable) {
        Page<Customer> page = customerRepo.searchWithTag(q == null ? "" : q.trim(), tagId == null ? 0L : tagId, pageable);
        Map<Long, List<TagDto>> tags = tagsFor(page.getContent().stream().map(Customer::getId).toList());
        return page.map(c -> toCustomerDto(c, tags.getOrDefault(c.getId(), List.of())));
    }

    @Transactional(readOnly = true)
    public CrmCustomerDto getCustomer(Long customerId) {
        Customer c = requireCustomer(customerId);
        return toCustomerDto(c, tagsFor(List.of(customerId)).getOrDefault(customerId, List.of()));
    }

    /** Replaces the customer's tag set, adding and removing only the difference. */
    @Transactional
    public CrmCustomerDto setCustomerTags(Long customerId, List<Long> tagIds) {
        Customer customer = requireCustomer(customerId);
        Set<Long> desired = new HashSet<>(tagIds);
        Map<Long, CustomerTag> found = tagRepo.findAllById(desired).stream()
                .collect(Collectors.toMap(CustomerTag::getId, t -> t));
        for (Long id : desired) {
            CustomerTag t = found.get(id);
            if (t == null) throw new NotFoundException("Tag", id);
            if (!t.isActive()) {
                throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "Tag is inactive: " + t.getName());
            }
        }
        List<CustomerTagAssignment> existing = assignmentRepo.findByCustomerId(customerId);
        Set<Long> before = existing.stream().map(CustomerTagAssignment::getTagId).collect(Collectors.toSet());
        existing.stream().filter(a -> !desired.contains(a.getTagId())).forEach(assignmentRepo::delete);
        desired.stream().filter(id -> !before.contains(id))
                .forEach(id -> assignmentRepo.save(new CustomerTagAssignment(customerId, id)));
        auditService.record("CUSTOMER_TAGS_SET", "CUSTOMER", String.valueOf(customerId), before.toString(), desired.toString(), null);
        return toCustomerDto(customer, desired.stream().map(found::get).map(CustomerTagService::toDto).toList());
    }

    private Map<Long, List<TagDto>> tagsFor(Collection<Long> customerIds) {
        Map<Long, List<TagDto>> result = new HashMap<>();
        if (customerIds.isEmpty()) return result;
        List<CustomerTagAssignment> assignments = assignmentRepo.findByCustomerIdIn(customerIds);
        Map<Long, CustomerTag> tags = tagRepo.findAllById(
                assignments.stream().map(CustomerTagAssignment::getTagId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(CustomerTag::getId, t -> t));
        for (CustomerTagAssignment a : assignments) {
            CustomerTag t = tags.get(a.getTagId());
            if (t != null) result.computeIfAbsent(a.getCustomerId(), k -> new java.util.ArrayList<>()).add(toDto(t));
        }
        return result;
    }

    private Customer requireCustomer(Long id) {
        return lookups.requireCustomer(id);
    }

    private CustomerTag requireTag(Long id) {
        return tagRepo.findById(id).orElseThrow(() -> new NotFoundException("Tag", id));
    }

    private static CrmCustomerDto toCustomerDto(Customer c, List<TagDto> tags) {
        return new CrmCustomerDto(c.getId(), c.getCustomerCode(), c.getName(), c.getStatus(), tags);
    }

    private static TagDto toDto(CustomerTag t) {
        return new TagDto(t.getId(), t.getTagGroup(), t.getName(), t.isActive());
    }
}
