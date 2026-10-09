package uk.ac.cf._5.group14.One_To_One.Web;

import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import org.springframework.web.server.ResponseStatusException;
import uk.ac.cf._5.group14.One_To_One.Config.DevModeProperties;
import uk.ac.cf._5.group14.One_To_One.Config.LocalisationAdvice;
import uk.ac.cf._5.group14.One_To_One.ErrorHandling.CustomErrorController;
import uk.ac.cf._5.group14.One_To_One.Waitlist.WaitlistEmail;
import uk.ac.cf._5.group14.One_To_One.Waitlist.WaitlistEmailRepository;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SharedRecoveryFlowTest {
    private final DevModeProperties properties = new DevModeProperties();
    private final WaitlistEmailRepository waitlist = mock(WaitlistEmailRepository.class);
    private final DevModeController controller = new DevModeController();

    @BeforeEach
    void setup() {
        properties.setDevMode(true);
        ReflectionTestUtils.setField(controller, "devModeProperties", properties);
        ReflectionTestUtils.setField(controller, "waitlistEmailRepository", waitlist);
    }

    @Test
    void dispatchKeepsErrorStatusAndDoesNotExposePrivateFailureDetails() {
        var request = new MockHttpServletRequest();
        request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 429);
        request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/private?token=secret");
        request.setAttribute(RequestDispatcher.ERROR_MESSAGE, "private database/password failure");
        var response = new MockHttpServletResponse();
        var model = new ExtendedModelMap();
        assertThat(new CustomErrorController().handleError(request, response, model)).isEqualTo("system-views/error/error");
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(response.getHeader("Referrer-Policy")).isEqualTo("no-referrer");
        assertThat(model).doesNotContainKey("errorMessage");
        assertThat(model.get("requestPath")).isEqualTo("/private");
    }

    @Test
    void malformedOrNonErrorDispatchStatusStillReturnsServerFailure() {
        for (Object status : new Object[]{"invalid", 200}) {
            var request = new MockHttpServletRequest();
            request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, status);
            var response = new MockHttpServletResponse();
            assertThat(new CustomErrorController().handleError(request, response, new ExtendedModelMap())).isEqualTo("system-views/error/500");
            assertThat(response.getStatus()).isEqualTo(500);
        }
    }

    @Test
    void nativeLanguageFormKeepsFiltersButDoesNotCopyPrivateOrPostFields() {
        var request = new MockHttpServletRequest("GET", "/merch");
        request.addParameter("search", "Training top");
        request.addParameter("status", "ACTIVE");
        request.addParameter("page", "2");
        request.addParameter("lang", "en");
        request.addParameter("password", "never copy");
        request.addParameter("token", "never copy");
        var advice = new LocalisationAdvice();
        assertThat(advice.languageQueryParameters(request)).extracting(LocalisationAdvice.LanguageQueryParameter::name)
                .containsExactly("page", "search", "status");
        request.setMethod("POST");
        assertThat(advice.languageQueryParameters(request)).isEmpty();
    }

    @Test
    void hubSearchWorksOnTheServerAndKeepsRestrictedRoutesRestricted() {
        var access = new uk.ac.cf._5.group14.One_To_One.DevMode.DevModePageAccessService(
                mock(uk.ac.cf._5.group14.One_To_One.DevMode.DevModePageSettingRepository.class));
        ReflectionTestUtils.setField(controller, "devModePageAccessService", access);
        var model = new ExtendedModelMap();
        assertThat(controller.devModeHub(null, "vault", new MockHttpServletResponse(), model)).isEqualTo("system-views/dev-mode/hub");
        var hub = (uk.ac.cf._5.group14.One_To_One.DevMode.DevModePageAccessService.DevModeHubView) model.get("devHubView");
        assertThat(hub.publicPages()).isEmpty();
        assertThat(hub.loginRequiredPages()).isEmpty();
        assertThat(hub.restrictedPages()).hasSize(1);
        assertThat(hub.restrictedPages().getFirst().href()).startsWith("/dev-mode/restricted");
        var response = new MockHttpServletResponse();
        controller.devModeHub(null, "x".repeat(101), response, new ExtendedModelMap());
        assertThat(response.getStatus()).isEqualTo(400);
    }

    @Test
    void waitlistDoesNotAcceptProductionOrOversizedSubmissions() {
        properties.setDevMode(false);
        assertThatThrownBy(() -> controller.joinWaitlist("test@example.invalid", new RedirectAttributesModelMap()))
                .isInstanceOf(ResponseStatusException.class);
        properties.setDevMode(true);
        var flash = new RedirectAttributesModelMap();
        controller.joinWaitlist("x".repeat(256) + "@example.invalid", flash);
        assertThat(flash.getFlashAttributes()).containsKey("waitlistError");
        verifyNoInteractions(waitlist);
    }

    @Test
    void signupNormalisesAddressAndDoesNotClaimInboxConfirmation() {
        var flash = new RedirectAttributesModelMap();
        controller.joinWaitlist("  Test@Example.Invalid  ", flash);
        var capture = org.mockito.ArgumentCaptor.forClass(WaitlistEmail.class);
        verify(waitlist).saveAndFlush(capture.capture());
        assertThat(capture.getValue().getEmail()).isEqualTo("test@example.invalid");
        assertThat(capture.getValue().isConfirmed()).isFalse();
        assertThat(flash.getFlashAttributes().get("waitlistSuccess").toString()).contains("No email has been sent").doesNotContain("test@example.invalid");
    }

    @Test
    void duplicateSignupRaceReturnsSameGenericOutcome() {
        when(waitlist.existsByEmailIgnoreCase("test@example.invalid")).thenReturn(false, true);
        when(waitlist.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));
        var flash = new RedirectAttributesModelMap();
        assertThat(controller.joinWaitlist("TEST@example.invalid", flash)).isEqualTo("redirect:/login");
        assertThat(flash.getFlashAttributes()).containsKey("waitlistSuccess").doesNotContainKey("waitlistError");
    }
}
