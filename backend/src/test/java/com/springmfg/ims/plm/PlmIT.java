package com.springmfg.ims.plm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.springmfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.engineering.bom.Bom;
import com.springmfg.ims.engineering.bom.BomDto;
import com.springmfg.ims.engineering.bom.BomItemRequest;
import com.springmfg.ims.engineering.bom.BomRepository;
import com.springmfg.ims.engineering.bom.BomService;
import com.springmfg.ims.engineering.bom.CreateBomRequest;
import com.springmfg.ims.material.domain.Material;
import com.springmfg.ims.plm.domain.EcoChangeCategory;
import com.springmfg.ims.plm.domain.EcoItemType;
import com.springmfg.ims.plm.domain.EcoStatus;
import com.springmfg.ims.plm.domain.ProductLifecycleStatus;
import com.springmfg.ims.plm.dto.EcoDtos.EcoDto;
import com.springmfg.ims.plm.dto.EcoDtos.SaveEcoItemRequest;
import com.springmfg.ims.plm.dto.EcoDtos.SaveEcoRequest;
import com.springmfg.ims.plm.dto.LifecycleDtos.ProductLifecycleDto;
import com.springmfg.ims.plm.dto.LifecycleDtos.TransitionRequest;
import com.springmfg.ims.plm.repository.ProductLifecycleEventRepository;
import com.springmfg.ims.plm.service.EcoService;
import com.springmfg.ims.plm.service.ProductLifecycleService;
import com.springmfg.ims.product.domain.Product;
import com.springmfg.ims.product.repository.ProductRepository;
import com.springmfg.ims.support.AbstractIntegrationTest;
import com.springmfg.ims.support.TestData;

/**
 * Integration tests for R15 PLM against real PostgreSQL. Every test builds its own products, so none depends on
 * another and committed rows cannot collide. Implementation must commit or roll back as one unit, so these tests do
 * not run inside a wrapping transaction.
 */
class PlmIT extends AbstractIntegrationTest {

    @Autowired EcoService ecoService;
    @Autowired ProductLifecycleService lifecycleService;
    @Autowired BomService bomService;
    @Autowired BomRepository bomRepo;
    @Autowired ProductRepository productRepo;
    @Autowired ProductLifecycleEventRepository eventRepo;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestData td;

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    // ---- helpers ------------------------------------------------------------------------------------------

    private Long loginEngineer() {
        Long id = jdbc.queryForObject("""
                INSERT INTO ims.users (username, full_name, email, password_hash)
                VALUES (?, 'PLM Tester', ?, 'x') RETURNING id
                """, Long.class, td.code("plm"), td.code("plm") + "@example.com");
        String[] authorities = { "ECO_CREATE", "ECO_APPROVE", "PLM_VIEW", "LIFECYCLE_MANAGE" };
        var token = new UsernamePasswordAuthenticationToken(
                new CachedUser(id, "u" + id, true, 1, Set.of(), Set.copyOf(Arrays.asList(authorities))), null,
                Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
        SecurityContextHolder.getContext().setAuthentication(token);
        return id;
    }

    /** A product with an ACTIVE BOM, reached through the real BOM workflow. */
    private Product productWithActiveBom() {
        Product p = td.product();
        Material m = td.material();
        BomDto draft = bomService.create(new CreateBomRequest(p.getId(), BigDecimal.ONE, "initial",
                List.of(new BomItemRequest(1, "MATERIAL", m.getId(), null, new BigDecimal("0.045"), "KG",
                        BigDecimal.ZERO, null))));
        bomService.submitForReview(draft.id());
        bomService.approve(draft.id());
        bomService.activate(draft.id());
        return p;
    }

    private List<Bom> boms(Long productId) {
        return bomRepo.findByProductId(productId, Pageable.unpaged()).getContent();
    }

    private SaveEcoRequest ecoRequest(String title, SaveEcoItemRequest... items) {
        return new SaveEcoRequest(title, "desc", "Supplier change", EcoChangeCategory.BOM_CHANGE, null, null,
                "Low impact", Arrays.asList(items));
    }

    private SaveEcoItemRequest bomItem(Product p) {
        return new SaveEcoItemRequest(EcoItemType.BOM, p.getId(), "Switch to new wire grade");
    }

    private EcoDto approvedEco(SaveEcoItemRequest... items) {
        EcoDto eco = ecoService.create(ecoRequest("ECO " + td.code("t"), items));
        ecoService.submit(eco.id());
        return ecoService.approve(eco.id());
    }

    private int auditCount(String action, String entity, Object entityId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM ims.audit_logs WHERE action = ? AND entity = ? AND entity_id = ?",
                Integer.class, action, entity, String.valueOf(entityId));
    }

    // ---- ECO workflow and BOM revisions --------------------------------------------------------------------

    @Test
    @DisplayName("PLM-003: approval creates nothing; implementation creates a DRAFT revision; activation supersedes the old BOM")
    void approvalDoesNotCreateRevisionButImplementationDoesAndActivationSupersedes() {
        Long userId = loginEngineer();
        Product p = productWithActiveBom();
        Bom original = bomRepo.findByProductIdAndStatus(p.getId(), "ACTIVE").orElseThrow();

        EcoDto eco = ecoService.create(ecoRequest("Wire grade change", bomItem(p)));
        assertThat(eco.status()).isEqualTo(EcoStatus.DRAFT);
        assertThat(eco.ecoNumber()).matches("ECO/\\d{2}/\\d{5}");
        assertThat(eco.initiatedBy()).isEqualTo(userId);

        assertThat(ecoService.submit(eco.id()).status()).isEqualTo(EcoStatus.REVIEW);
        EcoDto approved = ecoService.approve(eco.id());
        assertThat(approved.status()).isEqualTo(EcoStatus.APPROVED);
        assertThat(approved.approvedBy()).isEqualTo(userId);
        assertThat(boms(p.getId())).as("approval alone must not create a BOM revision").hasSize(1);

        EcoDto implemented = ecoService.implement(eco.id());
        assertThat(implemented.status()).isEqualTo(EcoStatus.IMPLEMENTED);
        assertThat(implemented.implementedAt()).isNotNull();

        List<Bom> all = boms(p.getId());
        assertThat(all).hasSize(2);
        Bom draft = all.stream().filter(b -> !b.getId().equals(original.getId())).findFirst().orElseThrow();
        assertThat(draft.getStatus()).isEqualTo("DRAFT");
        assertThat(draft.getRevision()).isEqualTo("B");
        assertThat(draft.getBomNumber()).isEqualTo(original.getBomNumber());
        assertThat(draft.getChangeReason()).contains(eco.ecoNumber());
        BomDto draftDto = bomService.findById(draft.getId());
        assertThat(draftDto.items()).hasSize(1);
        assertThat(draftDto.items().get(0).requiredQty()).isEqualByComparingTo("0.045");

        Bom stillActive = bomRepo.findById(original.getId()).orElseThrow();
        assertThat(stillActive.getStatus()).as("the old BOM stays ACTIVE until the new revision is activated")
                .isEqualTo("ACTIVE");
        assertThat(stillActive.getEffectiveTo()).isNull();

        var item = implemented.items().get(0);
        assertThat(item.oldBomId()).isEqualTo(original.getId());
        assertThat(item.newBomId()).isEqualTo(draft.getId());
        assertThat(item.newBomLabel()).endsWith("rev B");

        assertThat(eventRepo.findByEcoId(eco.id())).hasSize(1);
        assertThat(eventRepo.findByEcoId(eco.id()).get(0).getReason()).contains(eco.ecoNumber());
        assertThat(productRepo.findById(p.getId()).orElseThrow().getLifecycleStatus()).isEqualTo("ACTIVE");
        assertThat(auditCount("ECO_IMPLEMENT", "Eco", eco.id())).isEqualTo(1);

        bomService.submitForReview(draft.getId());
        bomService.approve(draft.getId());
        bomService.activate(draft.getId());
        assertThat(bomRepo.findById(original.getId()).orElseThrow().getStatus()).isEqualTo("SUPERSEDED");
        assertThat(bomRepo.findById(draft.getId()).orElseThrow().getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("Implementation needs an APPROVED ECO and can only happen once")
    void implementNeedsApprovalAndCannotRepeat() {
        loginEngineer();
        Product p = productWithActiveBom();
        EcoDto draft = ecoService.create(ecoRequest("Not yet approved", bomItem(p)));

        assertThatThrownBy(() -> ecoService.implement(draft.id())).isInstanceOfSatisfying(BusinessRuleException.class,
                e -> assertThat(e.getCode()).isEqualTo(ErrorCode.ILLEGAL_STATE_TRANSITION));
        ecoService.submit(draft.id());
        assertThatThrownBy(() -> ecoService.implement(draft.id())).isInstanceOf(BusinessRuleException.class);
        assertThat(boms(p.getId())).hasSize(1);

        ecoService.approve(draft.id());
        ecoService.implement(draft.id());
        assertThatThrownBy(() -> ecoService.implement(draft.id())).isInstanceOf(BusinessRuleException.class);
        assertThat(boms(p.getId())).as("a second implement must not create another revision").hasSize(2);
    }

    @Test
    @DisplayName("Implementation is all-or-nothing: one product without an active BOM rolls back every revision")
    void implementIsAllOrNothing() {
        loginEngineer();
        Product withBom = productWithActiveBom();
        Product withoutBom = td.product();
        EcoDto eco = approvedEco(bomItem(withBom), bomItem(withoutBom));

        assertThatThrownBy(() -> ecoService.implement(eco.id())).isInstanceOfSatisfying(BusinessRuleException.class,
                e -> assertThat(e.getCode()).isEqualTo(ErrorCode.BOM_NOT_ACTIVE));

        assertThat(ecoService.get(eco.id()).status()).isEqualTo(EcoStatus.APPROVED);
        assertThat(boms(withBom.getId())).as("no draft revision may leak from the failed run").hasSize(1);
        assertThat(eventRepo.findByEcoId(eco.id())).isEmpty();
        assertThat(auditCount("ECO_IMPLEMENT", "Eco", eco.id())).isZero();
    }

    @Test
    @DisplayName("An open BOM revision blocks implementation instead of colliding on the revision letter")
    void openRevisionBlocksImplementation() {
        loginEngineer();
        Product p = productWithActiveBom();
        Bom active = bomRepo.findByProductIdAndStatus(p.getId(), "ACTIVE").orElseThrow();
        bomService.copyRevision(active.getId());
        EcoDto eco = approvedEco(bomItem(p));

        assertThatThrownBy(() -> ecoService.implement(eco.id())).isInstanceOfSatisfying(BusinessRuleException.class,
                e -> {
                    assertThat(e.getCode()).isEqualTo(ErrorCode.ILLEGAL_STATE_TRANSITION);
                    assertThat(e.getMessage()).contains("open BOM revision");
                });
        assertThat(ecoService.get(eco.id()).status()).isEqualTo(EcoStatus.APPROVED);
        assertThat(boms(p.getId())).hasSize(2);
    }

    @Test
    @DisplayName("Routing and product items are recorded on implementation without creating revisions")
    void nonBomItemsCreateNoRevisions() {
        loginEngineer();
        Product p = td.product();
        EcoDto eco = approvedEco(new SaveEcoItemRequest(EcoItemType.ROUTING, p.getId(), "Add stress-relief step"));

        EcoDto done = ecoService.implement(eco.id());

        assertThat(done.status()).isEqualTo(EcoStatus.IMPLEMENTED);
        assertThat(done.items().get(0).newBomId()).isNull();
        assertThat(boms(p.getId())).isEmpty();
        assertThat(eventRepo.findByEcoId(eco.id())).hasSize(1);
    }

    @Test
    @DisplayName("Return and cancel need a reason; editing is DRAFT-only; submitting needs affected items")
    void workflowGuards() {
        loginEngineer();
        Product p = td.product();
        EcoDto empty = ecoService.create(ecoRequest("No items yet"));
        assertThatThrownBy(() -> ecoService.submit(empty.id())).isInstanceOfSatisfying(BusinessRuleException.class,
                e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));

        EcoDto eco = ecoService.create(ecoRequest("Guards", bomItem(p)));
        ecoService.submit(eco.id());
        assertThatThrownBy(() -> ecoService.update(eco.id(), ecoRequest("Edit in review", bomItem(p))))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> ecoService.returnToDraft(eco.id(), " ")).isInstanceOfSatisfying(
                BusinessRuleException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));

        EcoDto returned = ecoService.returnToDraft(eco.id(), "Impact assessment missing");
        assertThat(returned.status()).isEqualTo(EcoStatus.DRAFT);
        assertThat(returned.statusReason()).isEqualTo("Impact assessment missing");

        ecoService.submit(eco.id());
        ecoService.approve(eco.id());
        assertThatThrownBy(() -> ecoService.cancel(eco.id(), "")).isInstanceOf(BusinessRuleException.class);
        EcoDto cancelled = ecoService.cancel(eco.id(), "Superseded by another change");
        assertThat(cancelled.status()).isEqualTo(EcoStatus.CANCELLED);
        assertThatThrownBy(() -> ecoService.implement(eco.id())).isInstanceOf(BusinessRuleException.class);
        assertThat(auditCount("ECO_CANCEL", "Eco", eco.id())).isEqualTo(1);
        assertThat(auditCount("ECO_RETURN", "Eco", eco.id())).isEqualTo(1);
    }

    @Test
    @DisplayName("Items are edited in place: unchanged items survive, removed ones go, duplicates are rejected")
    void itemsAreEditedInPlace() {
        loginEngineer();
        Product a = td.product();
        Product b = td.product();
        EcoDto eco = ecoService.create(ecoRequest("Items", bomItem(a), bomItem(b)));
        assertThat(eco.items()).hasSize(2);

        EcoDto edited = ecoService.update(eco.id(), ecoRequest("Items edited",
                new SaveEcoItemRequest(EcoItemType.BOM, a.getId(), "New wording")));
        assertThat(edited.items()).hasSize(1);
        assertThat(edited.items().get(0).changeDescription()).isEqualTo("New wording");
        assertThat(edited.items().get(0).id()).isEqualTo(eco.items().get(0).id());

        assertThatThrownBy(() -> ecoService.update(eco.id(), ecoRequest("Dupes", bomItem(a), bomItem(a))))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    @DisplayName("ECO search works with no query, a matching query and a status filter")
    void searchHandlesNullAndFilters() {
        loginEngineer();
        Product p = td.product();
        String title = "Searchable " + td.code("s");
        EcoDto eco = ecoService.create(ecoRequest(title, bomItem(p)));
        Pageable page = PageRequest.of(0, 500);

        assertThat(ecoService.search(null, null, page).getContent()).extracting("id").contains(eco.id());
        assertThat(ecoService.search("", EcoStatus.DRAFT, page).getContent()).extracting("id").contains(eco.id());
        assertThat(ecoService.search(title.toLowerCase(), null, page).getContent()).extracting("id")
                .containsExactly(eco.id());
        assertThat(ecoService.search(title, EcoStatus.APPROVED, page).getContent()).isEmpty();
    }

    // ---- product lifecycle ---------------------------------------------------------------------------------

    @Test
    @DisplayName("PLM-005: lifecycle walks DEVELOPMENT to OBSOLETE one audited step at a time")
    void lifecycleLadderIsAuditedStepByStep() {
        Long userId = loginEngineer();
        Product p = td.product();
        p.setLifecycleStatus("DEVELOPMENT");
        productRepo.save(p);

        ProductLifecycleStatus[] ladder = { ProductLifecycleStatus.PROTOTYPE, ProductLifecycleStatus.APPROVED,
                ProductLifecycleStatus.ACTIVE, ProductLifecycleStatus.PHASE_OUT, ProductLifecycleStatus.OBSOLETE };
        ProductLifecycleDto last = null;
        for (ProductLifecycleStatus target : ladder) {
            last = lifecycleService.transition(p.getId(), new TransitionRequest(target, "moving to " + target));
        }

        assertThat(last.lifecycleStatus()).isEqualTo(ProductLifecycleStatus.OBSOLETE);
        assertThat(last.nextStatus()).isNull();
        assertThat(last.history()).hasSize(5);
        assertThat(last.history().get(0).fromStatus()).isEqualTo("PHASE_OUT");
        assertThat(last.history().get(0).toStatus()).isEqualTo("OBSOLETE");
        assertThat(last.history()).allSatisfy(e -> {
            assertThat(e.changedBy()).isEqualTo(userId);
            assertThat(e.reason()).startsWith("moving to ");
        });
        assertThat(auditCount("PRODUCT_LIFECYCLE_CHANGE", "Product", p.getId())).isEqualTo(5);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM ims.audit_logs WHERE action = 'PRODUCT_LIFECYCLE_CHANGE' AND entity_id = ?
                AND new_value::text LIKE '%OBSOLETE%' AND reason = 'moving to OBSOLETE'
                """, Integer.class, String.valueOf(p.getId()))).isEqualTo(1);

        assertThatThrownBy(() -> lifecycleService.transition(p.getId(),
                new TransitionRequest(ProductLifecycleStatus.ACTIVE, "reactivate")))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("Lifecycle changes cannot skip a step or omit the reason, and a refused change leaves no trace")
    void lifecycleRejectsSkipsAndMissingReasons() {
        loginEngineer();
        Product p = td.product();

        assertThatThrownBy(() -> lifecycleService.transition(p.getId(),
                new TransitionRequest(ProductLifecycleStatus.OBSOLETE, "skip ahead")))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.ILLEGAL_STATE_TRANSITION));
        assertThatThrownBy(() -> lifecycleService.transition(p.getId(),
                new TransitionRequest(ProductLifecycleStatus.PHASE_OUT, "  ")))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));

        assertThat(lifecycleService.get(p.getId()).lifecycleStatus()).isEqualTo(ProductLifecycleStatus.ACTIVE);
        assertThat(lifecycleService.get(p.getId()).history()).isEmpty();
        assertThat(auditCount("PRODUCT_LIFECYCLE_CHANGE", "Product", p.getId())).isZero();
    }

    @Test
    @DisplayName("Lifecycle list filters by status and tolerates a null search text")
    void lifecycleListFilters() {
        loginEngineer();
        Product p = td.product();
        lifecycleService.transition(p.getId(), new TransitionRequest(ProductLifecycleStatus.PHASE_OUT, "end of line"));
        Pageable page = PageRequest.of(0, 1000);

        assertThat(lifecycleService.list(null, ProductLifecycleStatus.PHASE_OUT, page).getContent())
                .extracting("productId").contains(p.getId());
        assertThat(lifecycleService.list(p.getProductCode(), null, page).getContent())
                .extracting("productId").containsExactly(p.getId());
        assertThat(lifecycleService.list(p.getProductCode(), ProductLifecycleStatus.ACTIVE, page).getContent())
                .isEmpty();
    }
}
