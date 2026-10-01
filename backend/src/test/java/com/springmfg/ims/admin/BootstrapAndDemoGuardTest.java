package com.springmfg.ims.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.env.MockEnvironment;

import com.springmfg.ims.config.BootstrapAdminProperties;
import com.springmfg.ims.iam.UserRepository;

class BootstrapAndDemoGuardTest {

    private final UserRepository users = mock(UserRepository.class);
    private final UserAdminService userAdmin = mock(UserAdminService.class);

    @Test
    void bootstrapDoesNothingUnlessConfigured() {
        new BootstrapAdminInitializer(new BootstrapAdminProperties("admin", "Administrator", null, null), users, userAdmin).run(null);
        new BootstrapAdminInitializer(new BootstrapAdminProperties("admin", "Administrator", "a@example.com", ""), users, userAdmin).run(null);
        verify(userAdmin, never()).create(any());
    }

    @Test
    void bootstrapNeverTouchesAnInstallationThatAlreadyHasUsers() {
        when(users.count()).thenReturn(3L);
        new BootstrapAdminInitializer(new BootstrapAdminProperties("admin", "Administrator", "a@example.com", "Strong-Pass-123!"),
                users, userAdmin).run(null);
        verify(userAdmin, never()).create(any());
    }

    @Test
    void bootstrapCreatesTheFirstAdminWithTheGivenTemporaryPassword() {
        when(users.count()).thenReturn(0L);
        new BootstrapAdminInitializer(new BootstrapAdminProperties("root", "The Admin", "root@example.com", "Strong-Pass-123!"),
                users, userAdmin).run(null);

        ArgumentCaptor<UserDtos.CreateUserRequest> request = ArgumentCaptor.forClass(UserDtos.CreateUserRequest.class);
        verify(userAdmin).create(request.capture());
        assertThat(request.getValue().username()).isEqualTo("root");
        assertThat(request.getValue().roles()).isEqualTo(Set.of("ADMIN"));
        assertThat(request.getValue().temporaryPassword()).isEqualTo("Strong-Pass-123!");
    }

    @Test
    void demoUsersRefuseToLoadUnderTheProdProfile() {
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");
        assertThatThrownBy(() -> new DemoUserLoader(users, userAdmin, prod))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("prod");
        new DemoUserLoader(users, userAdmin, new MockEnvironment()); // fine elsewhere
    }

    @Test
    void demoLoaderSkipsExistingUsersAndCreatesTheRest() {
        when(users.existsByUsernameIgnoreCase(any())).thenReturn(false);
        when(users.existsByUsernameIgnoreCase("admin")).thenReturn(true);
        new DemoUserLoader(users, userAdmin, new MockEnvironment()).run(null);
        verify(userAdmin, org.mockito.Mockito.times(13)).create(any());
    }
}
