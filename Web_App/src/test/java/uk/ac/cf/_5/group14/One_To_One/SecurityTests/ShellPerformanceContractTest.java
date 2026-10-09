package uk.ac.cf._5.group14.One_To_One.SecurityTests;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import uk.ac.cf._5.group14.One_To_One.Config.UiStyleBundleAdvice;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ShellPerformanceContractTest {

    private final UiStyleBundleAdvice styleBundleAdvice = new UiStyleBundleAdvice();

    @Test
    void compiledCoreCssStaysBelowThePayloadBudget() throws IOException {
        Path coreCss = Path.of("src/main/resources/static/css/app.css");

        assertThat(Files.size(coreCss))
                .as("compiled core CSS must stay below 256 KiB")
                .isLessThan(256L * 1024L);
    }

    @Test
    void sharedNavigationAndDemoAvatarImagesStayWithinColdLoadBudgets() throws IOException {
        assertThat(Files.size(Path.of("src/main/resources/static/img/brand/logo.png")))
                .as("navigation logo must stay below 64 KiB")
                .isLessThan(64L * 1024L);
        assertThat(Files.size(Path.of(
                "src/main/resources/static/img/Products/Short_Sleeve_Top/Short_Sleeve_Front.jpg")))
                .as("trainer demo avatar source must stay below 256 KiB")
                .isLessThan(256L * 1024L);
        assertThat(Files.size(Path.of(
                "src/main/resources/static/img/Products/Long_Sleeve_Top/Long_Sleeve_Front.jpg")))
                .as("gym demo avatar source must stay below 256 KiB")
                .isLessThan(256L * 1024L);
    }

    @Test
    void featureStylesAreSplitOutOfTheCoreEntryPoint() throws IOException {
        String coreImports = read("src/main/resources/static/css/components/core/index.css");

        assertThat(coreImports)
                .doesNotContain("../account/auth.css")
                .doesNotContain("../account/profile.css")
                .doesNotContain("../calendar/")
                .doesNotContain("../training/")
                .doesNotContain("../dashboard/")
                .doesNotContain("../chat/chat-widget.css")
                .doesNotContain("../chat/quick-actions-widget.css");

        for (String bundle : List.of(
                "assistant.css", "authenticated-shell.css", "auth.css", "profile.css",
                "calendar.css", "dashboard.css", "training.css", "content.css", "guest.css")) {
            assertThat(Path.of("src/main/resources/static/css/bundles", bundle)).exists();
        }
    }

    @Test
    void baseLoadsShellAndRouteBundlesConditionally() throws IOException {
        String base = read("src/main/resources/templates/base.html");

        assertThat(base)
                .contains("/css/bundles/assistant.css")
                .contains("/css/bundles/authenticated-shell.css")
                .contains("th:each=\"styleBundle : ${uiStyleBundles}\"")
                .contains("/js/public/guest-experience.js")
                .contains("has-quick-actions has-platform-panel");
    }

    @Test
    void navigationLanguageControlKeepsStableHoverGeometry() throws IOException {
        String languageSelector = read("src/main/resources/static/css/components/core/language-selector.css");

        assertThat(languageSelector)
                .contains("background-color 0.24s var(--motion-ease-enter)")
                .contains(".language-selector__code {")
                .contains("line-height: 1;")
                .contains("transform: rotate(45deg);")
                .contains("transform: rotate(225deg);")
                .doesNotContain("transform: scale(1.025)")
                .doesNotContain("translateY(-0.1rem) rotate(45deg)")
                .doesNotContain("translateY(0.1rem) rotate(225deg)");
    }

    @Test
    void routeFamiliesReceiveOnlyTheirFeatureBundle() {
        assertThat(bundlesFor("/"))
                .isEmpty();
        assertThat(bundlesFor("/calendar/day/2026-07-13"))
                .containsExactly("/css/bundles/calendar.css");
        assertThat(bundlesFor("/client/dashboard"))
                .containsExactly("/css/bundles/dashboard.css");
        assertThat(bundlesFor("/workouts/studio/4"))
                .containsExactly("/css/bundles/training.css");
        assertThat(bundlesFor("/profile/orders"))
                .containsExactly("/css/bundles/profile.css");
        assertThat(bundlesFor("/pricing/checkout"))
                .containsExactly("/css/bundles/content.css");
        assertThat(bundlesFor("/pricing"))
                .containsExactly("/css/bundles/content.css", "/css/bundles/guest.css");
        assertThat(bundlesFor("/login"))
                .containsExactly("/css/bundles/auth.css", "/css/bundles/guest.css");
        assertThat(bundlesFor("/signup/trainer"))
                .containsExactly("/css/bundles/auth.css", "/css/bundles/guest.css");
        assertThat(bundlesFor("/explore"))
                .containsExactly("/css/bundles/guest.css");
        assertThat(bundlesFor("/dashboard/public"))
                .containsExactly("/css/bundles/dashboard.css", "/css/bundles/guest.css");
        assertThat(bundlesFor("/access-denied"))
                .containsExactly("/css/bundles/guest.css");
    }

    @Test
    void fixedSurfacesUseTheSharedBottomReservation() throws IOException {
        String shell = read("src/main/resources/static/css/components/core/shell-layout.css");
        String chat = read("src/main/resources/static/css/components/chat/chat-widget.css");
        String quickActions = read("src/main/resources/static/css/components/chat/quick-actions-widget.css");
        String dashboard = read("src/main/resources/static/css/components/dashboard/client-dashboard-refresh.css");

        assertThat(shell)
                .contains("--shell-platform-panel-height")
                .contains("--shell-local-dock-height")
                .contains("--shell-floating-control-gap")
                .contains("--shell-floating-control-clearance")
                .contains("padding-bottom: calc(var(--shell-platform-panel-height) + var(--shell-local-dock-height) + var(--shell-floating-control-clearance))");
        assertThat(chat).contains("bottom: calc(var(--shell-platform-panel-height");
        assertThat(quickActions).contains("bottom: calc(var(--shell-platform-panel-height");
        assertThat(dashboard)
                .contains("bottom: calc(var(--shell-platform-panel-height) + var(--shell-local-dock-height) + 0.35rem)")
                .contains("padding-bottom: calc(var(--shell-platform-panel-height) + var(--shell-local-dock-height))");
    }

    @Test
    void textCompressionAndStaticCachingAreConfigured() throws IOException {
        String properties = read("src/main/resources/application.properties");

        assertThat(properties)
                .contains("server.compression.enabled=true")
                .contains("server.compression.min-response-size=1024")
                .contains("spring.web.resources.cache.cachecontrol.max-age=1d")
                .contains("spring.web.resources.cache.cachecontrol.cache-public=true");
    }

    private List<String> bundlesFor(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setRequestURI(path);
        return styleBundleAdvice.uiStyleBundles(request);
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(Path.of(relativePath));
    }
}
