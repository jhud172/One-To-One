package uk.ac.cf._5.group14.One_To_One.SecurityTests;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LoginPageAccessibilityContractTest {

    @Test
    void roleTabsMoveSelectionAndKeyboardFocusTogether() throws IOException {
        String script = Files.readString(Path.of("src/main/resources/static/js/auth/login-page.js"));

        assertThat(script)
                .contains("event.key === \"ArrowRight\"")
                .contains("event.key === \"ArrowLeft\"")
                .contains("event.key === \"Home\"")
                .contains("event.key === \"End\"")
                .contains("Array.from(roleOptions).find((roleOption) => roleOption.dataset.role === nextRole)?.focus()");
    }

    @Test
    void authenticationControlsKeepA44PixelTouchTarget() throws IOException {
        String stylesheet = Files.readString(Path.of("src/main/resources/static/css/components/account/auth.css"));

        assertThat(stylesheet)
                .contains(".role-slider__option")
                .contains("min-height: 44px")
                .contains(".password-toggle")
                .contains("width: 44px")
                .contains("height: 44px")
                .contains("inline-flex min-h-11 items-center");
    }

    @Test
    void sharedGuestNavigationKeepsA44PixelTouchTargetOnMobile() throws IOException {
        String navbar = Files.readString(Path.of("src/main/resources/static/css/components/core/navbar.css"));
        String devMode = Files.readString(Path.of("src/main/resources/static/css/components/core/dev-mode.css"));
        String footer = Files.readString(Path.of("src/main/resources/static/css/components/core/footer.css"));

        assertThat(navbar)
                .contains(".nav-login-cta")
                .contains("min-height: 2.75rem");
        assertThat(devMode)
                .contains("@media (max-width: 450px)")
                .contains("min-height: 2.75rem");
        assertThat(footer)
                .contains("@media (max-width: 420px)")
                .contains(".footer-legal-link")
                .contains("min-width: 2.75rem")
                .contains("min-height: 2.75rem");
    }

    @Test
    void guestLoginActionUsesStableScaleAndHeldHoverProgress() throws IOException {
        String navbar = Files.readString(Path.of("src/main/resources/static/css/components/core/navbar.css"));

        assertThat(navbar)
                .contains(".nav-login-cta:hover")
                .contains("transform: scale(1.025)")
                .doesNotContain("transform: translateY(-2px) scale(1.015)")
                .contains(".nav-login-cta__icon::before")
                .contains("transform-origin: left center")
                .contains(".nav-login-cta:hover .nav-login-cta__icon::before")
                .contains("transition-duration: 1s")
                .contains("transition-timing-function: linear");
    }

    @Test
    void developmentNavbarActionMirrorsStableHeldHoverMotion() throws IOException {
        String devMode = Files.readString(Path.of("src/main/resources/static/css/components/core/dev-mode.css"));

        assertThat(devMode)
                .contains(".dev-mode-pill--navbar")
                .contains("border-radius: 0.95rem")
                .contains("background: linear-gradient(145deg, #fcd34d 0%, #f59e0b 58%, #d97706 100%)")
                .contains(".dev-mode-pill--navbar::before")
                .contains(".dev-mode-pill--navbar:hover")
                .contains("transform: scale(1.025)")
                .doesNotContain(".dev-mode-pill--navbar:hover {\n        border-color: rgba(180, 83, 9, 0.42)")
                .contains(".dev-mode-pill--navbar:hover .dev-mode-pill__icon::before")
                .contains("transition-duration: 1s")
                .contains("transition-timing-function: linear");
    }

    @Test
    void navigationHoverIndicatorRisesFromBelowAndExitsDownward() throws IOException {
        String navbar = Files.readString(Path.of("src/main/resources/static/css/components/core/navbar.css"));

        assertThat(navbar)
                .contains("right: var(--nav-link-padding-x)")
                .contains("left: var(--nav-link-padding-x)")
                .contains("transform: translateY(0.52rem) scaleX(0.28)")
                .contains("transform-origin: center")
                .contains("filter: blur(1px)")
                .contains(".navlink:hover::after")
                .contains("transform: translateY(0) scaleX(1)")
                .contains("filter: blur(0)")
                .doesNotContain("transform: translateX(-72%) translateY(0.12rem) scaleX(0.18)")
                .doesNotContain("transform-origin: left center;\n        box-shadow: 0 0.2rem 0.6rem");
    }

    @Test
    void loginValidationUsesOneFocusLinkedCrossBrowserMessage() throws IOException {
        String template = Files.readString(Path.of("src/main/resources/templates/public-views/auth/login.html"));
        String script = Files.readString(Path.of("src/main/resources/static/js/auth/login-page.js"));

        assertThat(template)
                .contains("id=\"loginForm\" novalidate")
                .contains("id=\"loginValidationError\"")
                .doesNotContain("placeholder=\"Enter your email or username\"")
                .doesNotContain("placeholder=\"Enter your gym username\"");
        assertThat(script)
                .contains("showLoginValidation(missingField.input, missingField.message)")
                .contains("input.setAttribute(\"aria-invalid\", \"true\")")
                .contains("descriptionIds.add(\"loginValidationError\")")
                .doesNotContain("reportValidity()")
                .doesNotContain("setCustomValidity(");
    }

    @Test
    void authenticationJourneysStayProgressiveAccessibleAndMotionAware() throws IOException {
        String base = Files.readString(Path.of("src/main/resources/templates/base.html"));
        String client = Files.readString(Path.of("src/main/resources/templates/public-views/auth/signup-client.html"));
        String trainer = Files.readString(Path.of("src/main/resources/templates/public-views/auth/signup-trainer.html"));
        String gym = Files.readString(Path.of("src/main/resources/templates/public-views/auth/signup-gym.html"));
        String trainerSuccess = Files.readString(Path.of("src/main/resources/templates/public-views/auth/signup-trainer-success.html"));
        String gymApplication = Files.readString(Path.of("src/main/resources/templates/public-views/auth/signup-gym-application.html"));
        String emailVerification = Files.readString(Path.of("src/main/resources/templates/public-views/verify/email-code.html"));
        String script = Files.readString(Path.of("src/main/resources/static/js/auth/auth-experience.js"));
        String stylesheet = Files.readString(Path.of("src/main/resources/static/css/components/public/auth-journey.css"));

        assertThat(base)
                .contains("authPageLayout == true")
                .contains("/js/auth/auth-experience.js(v=${assetVersion})");
        assertThat(client)
                .contains("data-auth-wizard")
                .contains("auth-wizard__nav--two")
                .contains("data-password-strength=\"password\"")
                .doesNotContain("style=");
        assertThat(trainer)
                .contains("data-auth-wizard")
                .contains("auth-wizard__nav--three")
                .doesNotContain("style=");
        assertThat(gym)
                .contains("data-auth-wizard")
                .contains("auth-wizard__nav--three")
                .doesNotContain("style=");
        assertThat(trainerSuccess)
                .contains("auth-credential-card")
                .contains("auth-completion-actions");
        assertThat(gymApplication)
                .contains("auth-application-card")
                .contains("auth-application-message");
        assertThat(emailVerification)
                .contains("guest-auth--verify")
                .contains("autocomplete=\"one-time-code\"");
        assertThat(script)
                .contains("(prefers-reduced-motion: reduce)")
                .contains("aria-valuenow")
                .contains("auth:rolechange")
                .contains("setRegionAvailable");
        assertThat(stylesheet)
                .contains(".auth-flow-ready .auth-wizard__step[hidden]")
                .contains("@media (prefers-reduced-motion: reduce)")
                .contains(".auth-wizard__nav--three");
    }
}
