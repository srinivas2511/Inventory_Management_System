package com.springmfg.ims.iam.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;

import com.springmfg.ims.audit.service.AuditService;
import com.springmfg.ims.auth.service.AuthService;
import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.iam.domain.Role;
import com.springmfg.ims.iam.domain.User;
import com.springmfg.ims.iam.repository.RoleRepository;
import com.springmfg.ims.iam.repository.UserRepository;

@ExtendWith(OutputCaptureExtension.class)
class InitialAdminBootstrapTest {

    private final UserRepository userRepo = mock(UserRepository.class);
    private final RoleRepository roleRepo = mock(RoleRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final AuthService authService = mock(AuthService.class);
    private final AuditService auditService = mock(AuditService.class);
    private final PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);

    private InitialAdminBootstrap bootstrap(String username, String password) {
        return new InitialAdminBootstrap(userRepo, roleRepo, encoder, authService, auditService, txManager,
                username, password, "");
    }

    @Test
    void withoutTheVariablesNothingIsCreatedAndTheOperatorIsToldWhatToSet(CapturedOutput output) {
        when(userRepo.count()).thenReturn(0L);

        bootstrap("", "").onReady();

        verify(userRepo, never()).save(any(User.class));
        assertThat(output.getAll()).contains("IMS_BOOTSTRAP_ADMIN_USERNAME").contains("IMS_BOOTSTRAP_ADMIN_PASSWORD");
    }

    @Test
    void aPasswordThatFailsThePolicyCreatesNothing(CapturedOutput output) {
        when(userRepo.count()).thenReturn(0L);
        doThrow(new BusinessRuleException(ErrorCode.PASSWORD_POLICY, "Password must be at least 10 characters"))
                .when(authService).validateComplexity("weak", "boot-admin");

        bootstrap("boot-admin", "weak").onReady();

        verify(userRepo, never()).save(any(User.class));
        assertThat(output.getAll()).contains("Initial administrator was not created")
                .contains("Password must be at least 10 characters").doesNotContain("weak\"");
    }

    @Test
    void existingUsersMeanTheBootstrapDoesNothingAtAll() {
        when(userRepo.count()).thenReturn(3L);

        bootstrap("boot-admin", "Str0ng#Bootstrap-Pw").onReady();

        verifyNoInteractions(roleRepo, encoder, authService, auditService);
        verify(userRepo, never()).save(any(User.class));
    }

    @Test
    void aMissingAdminRoleIsReportedNotCreatedAround(CapturedOutput output) {
        when(userRepo.count()).thenReturn(0L);
        when(roleRepo.findByCode("ADMIN")).thenReturn(Optional.<Role>empty());

        bootstrap("boot-admin", "Str0ng#Bootstrap-Pw").onReady();

        verify(userRepo, never()).save(any(User.class));
        assertThat(output.getAll()).contains("Role ADMIN is missing");
    }
}
