package uk.ac.cf._5.group14.One_To_One.DevMode;

import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.InvalidDataAccessResourceUsageException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DevModePageAccessServiceTest {

    @Mock
    private DevModePageSettingRepository settingRepository;

    private DevModePageAccessService service;

    @BeforeEach
    void setUp() {
        service = new DevModePageAccessService(settingRepository);
    }

    @Test
    void fallsBackToDefaultModesWhenSettingsTableIsMissing() {
        when(settingRepository.findAll())
            .thenThrow(new InvalidDataAccessResourceUsageException("relation does not exist"));

        DevModePageAccessService.DevModeAdminSummary summary = service.buildAdminSummary();

        assertThat(summary.enabledCount()).isEqualTo(15);
        assertThat(summary.disabledCount()).isZero();
        assertThat(summary.restrictedCount()).isEqualTo(2);
    }

    @Test
    void clientTrainersPageIsEnabledByDefault() {
        when(settingRepository.findAll()).thenReturn(Collections.emptyList());

        DevModePageAccessService.DevModePageAdminRow clientTrainersRow = service.buildAdminRows()
                .stream()
                .filter(row -> "client-trainers".equals(row.key()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("client-trainers page not found"));

        assertThat(clientTrainersRow.currentMode()).isEqualTo("ENABLED");
    }

    @Test
    void signedInWrongRoleDoesNotGetAClientOnlyRouteAsAnAvailableDestination() {
        when(settingRepository.findAll()).thenReturn(Collections.emptyList());
        var admin = new org.springframework.security.authentication.TestingAuthenticationToken("demo_admin", "unused", "ROLE_PLATFORM_ADMIN");
        var card = service.buildHubView(admin).loginRequiredPages().stream()
                .filter(page -> page.key().equals("client-trainers")).findFirst().orElseThrow();
        assertThat(card.href()).isEqualTo("/dashboard");
        assertThat(card.availabilityCopy()).isEqualTo("Client account required");
        var client = new org.springframework.security.authentication.TestingAuthenticationToken("demo_client", "unused", "ROLE_CLIENT");
        assertThat(service.buildHubView(client).loginRequiredPages().stream()
                .filter(page -> page.key().equals("client-trainers")).findFirst().orElseThrow().href()).isEqualTo("/client/trainers");
        assertThat(service.buildHubView(null).loginRequiredPages().stream()
                .filter(page -> page.key().equals("client-trainers")).findFirst().orElseThrow().href()).startsWith("/login?next=");
    }

    @Test
    void surfacesFriendlyErrorWhenUpdatingModeWithoutBackingTable() {
        when(settingRepository.findByPageKey("home"))
            .thenThrow(new InvalidDataAccessResourceUsageException("relation does not exist"));

        assertThatThrownBy(() -> service.updateMode("home", DevModePageAccessMode.DISABLED))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Dev mode page settings are temporarily unavailable.");
    }
}
