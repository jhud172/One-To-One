package uk.ac.cf._5.group14.One_To_One.SecurityTests;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardConsistencyContractTest {

    private static final Path DASHBOARD_CSS = Path.of(
            "src/main/resources/static/css/components/dashboard/dashboard-consistency.css");

    @Test
    void sharedDashboardTextTokensMeetWcagAaContrast() {
        assertThat(contrast("#0f172a", "#ffffff")).isGreaterThanOrEqualTo(4.5);
        assertThat(contrast("#1e293b", "#ffffff")).isGreaterThanOrEqualTo(4.5);
        assertThat(contrast("#475569", "#ffffff")).isGreaterThanOrEqualTo(4.5);
        assertThat(contrast("#64748b", "#ffffff")).isGreaterThanOrEqualTo(4.5);
        assertThat(contrast("#047857", "#ffffff")).isGreaterThanOrEqualTo(4.5);

        assertThat(contrast("#f8fafc", "#111827")).isGreaterThanOrEqualTo(4.5);
        assertThat(contrast("#e2e8f0", "#111827")).isGreaterThanOrEqualTo(4.5);
        assertThat(contrast("#cbd5e1", "#111827")).isGreaterThanOrEqualTo(4.5);
        assertThat(contrast("#94a3b8", "#111827")).isGreaterThanOrEqualTo(4.5);
        assertThat(contrast("#6ee7b7", "#111827")).isGreaterThanOrEqualTo(4.5);
    }

    @Test
    void everyRoleDashboardUsesTheSharedHierarchyAndActionContract() throws IOException {
        assertDashboard("client-views/dashboard/client-dashboard.html", "app-dashboard--client");
        assertDashboard("trainer-views/dashboard/trainer-dashboard.html", "app-dashboard--trainer");
        assertDashboard("gym-views/dashboard/gym-dashboard.html", "app-dashboard--gym");
        assertDashboard("admin-views/dashboard/admin-dashboard.html", "app-dashboard--admin");

        String stylesheet = Files.readString(DASHBOARD_CSS);
        assertThat(stylesheet)
                .contains(".dashboard-page-title")
                .contains(".dashboard-action--primary")
                .contains(".dashboard-action--secondary")
                .contains("min-height: 44px")
                .contains("@media (prefers-reduced-motion: reduce)")
                .doesNotContain("transition: all");

        String entry = read("src/main/resources/static/css/entries/dashboard.css");
        assertThat(entry.indexOf("client-dashboard-refresh.css"))
                .isLessThan(entry.indexOf("dashboard-consistency.css"));
    }

    @Test
    void operationalEmptyStatesExplainWhatHappensNext() throws IOException {
        String trainer = read("src/main/resources/templates/trainer-views/dashboard/trainer-dashboard.html");
        assertThat(trainer)
                .contains("Nothing scheduled for today")
                .contains("Plan in calendar")
                .contains("Open clients");

        String gym = read("src/main/resources/templates/gym-views/dashboard/gym-dashboard.html");
        assertThat(gym)
                .contains("No operational items scheduled today")
                .contains("Plan in calendar")
                .contains("Manage trainers");

        String admin = read("src/main/resources/templates/admin-views/dashboard/admin-dashboard.html");
        assertThat(admin)
                .contains("No support requests waiting")
                .contains("Compose an update")
                .contains("No waitlist signups yet")
                .contains("Prepare an outreach message");

        String client = read("src/main/resources/templates/client-views/dashboard/fragments/client-dashboard-shell.html");
        assertThat(client)
                .contains("cd-empty-state__title")
                .contains("cd-empty-state__copy")
                .contains("Explore trainers")
                .contains("Open goals");
    }

    @Test
    void clientDashboardRevealKeepsContentAvailableToScrollFocusAndReducedMotion() throws IOException {
        String script = read("src/main/resources/static/js/dashboard/client-dashboard-page.js");
        String stylesheet = read("src/main/resources/static/css/components/dashboard/client-dashboard-refresh.css");

        assertThat(script)
                .contains("window.addEventListener(\"scroll\", queueRevealCheck")
                .contains("page.addEventListener(\"focusin\", revealFocusedCard)")
                .contains("card.scrollIntoView({ block: \"nearest\", behavior: \"auto\" })")
                .contains("revealReachedCards();");
        assertThat(stylesheet)
                .contains("@media (prefers-reduced-motion: reduce)")
                .contains(".cd-inview-reveal")
                .contains("opacity: 1 !important")
                .contains("transform: none !important");
    }

    @Test
    void clientDashboardStartsWithAReleaseQaHeadingAndReflowsAtHighZoom() throws IOException {
        String page = read("src/main/resources/templates/client-views/dashboard/client-dashboard.html");
        String template = read("src/main/resources/templates/client-views/dashboard/fragments/client-dashboard-shell.html");
        String stylesheet = read("src/main/resources/static/css/components/dashboard/client-dashboard-refresh.css");

        assertThat(page.indexOf("class=\"cd-dashboard-main\""))
                .isLessThan(page.indexOf("id=\"dashboardLeftRail\""));
        assertThat(template)
                .contains("<h1 class=\"sr-only\" th:text=\"#{ui.00480}\">Client dashboard</h1>");
        assertThat(stylesheet)
                .contains("@media (max-width: 360px)")
                .contains(".cd-primary-actions,")
                .contains(".cd-coach-spotlight__metrics,")
                .contains("grid-template-columns: minmax(0, 1fr)")
                .contains(".cd-inline-link")
                .contains("min-width: 45px")
                .contains("min-height: 45px")
                .contains("overflow-wrap: anywhere");
    }

    @Test
    void dashboardTourKeepsAVisibleFocusIndicatorAndTouchSizedSkipAction() throws IOException {
        String stylesheet = read("src/main/resources/static/css/components/tutorial/site-tour.css");

        assertThat(stylesheet)
                .contains(".site-tour__button:focus,")
                .contains(".site-tour__skip:focus,")
                .contains("min-width: 2.75rem")
                .contains("min-height: 2.75rem")
                .contains("outline: 3px solid var(--tour-accent-soft)")
                .contains("outline-offset: 2px");
    }

    @Test
    void clientDashboardKeepsHiddenPanelsOutOfTheFocusOrderAndRequestsLocationOnDemand() throws IOException {
        String page = read("src/main/resources/templates/client-views/dashboard/client-dashboard.html");
        String fragment = read("src/main/resources/templates/client-views/dashboard/fragments/client-dashboard-shell.html");
        String script = read("src/main/resources/static/js/dashboard/client-dashboard-page.js");

        assertThat(page)
                .contains("aria-label=\"Explore platform\" aria-hidden=\"true\" inert")
                .contains("aria-label=\"Trainer overview\" aria-hidden=\"true\" inert")
                .contains("aria-label=\"Goals overview\" aria-hidden=\"true\" inert")
                .contains("aria-label=\"Help and trust\" aria-hidden=\"true\" inert")
                .contains("aria-label=\"Profile overview\" aria-hidden=\"true\" inert");
        assertThat(fragment)
                .contains("dashboard-action-panel-all")
                .contains("data-ambience-location-request");
        assertThat(script)
                .contains("tab.setAttribute(\"role\", \"tab\")")
                .contains("view.setAttribute(\"role\", \"tabpanel\")")
                .contains("panel?.toggleAttribute(\"inert\", !isOpen)")
                .contains("view.toggleAttribute(\"inert\", !active)")
                .contains("tab.tabIndex = active ? 0 : -1")
                .contains("locationRequestEl?.addEventListener(\"click\", () => loadForecast(true))")
                .contains("loadForecast(false)")
                .doesNotContain("flyout.hidden = !isMobile");
    }

    private static void assertDashboard(String relativePath, String roleClass) throws IOException {
        assertThat(read("src/main/resources/templates/" + relativePath))
                .contains("app-dashboard")
                .contains(roleClass);
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(Path.of(relativePath));
    }

    private static double contrast(String foreground, String background) {
        double lighter = Math.max(luminance(foreground), luminance(background));
        double darker = Math.min(luminance(foreground), luminance(background));
        return (lighter + 0.05) / (darker + 0.05);
    }

    private static double luminance(String colour) {
        int red = Integer.parseInt(colour.substring(1, 3), 16);
        int green = Integer.parseInt(colour.substring(3, 5), 16);
        int blue = Integer.parseInt(colour.substring(5, 7), 16);
        return 0.2126 * linear(red) + 0.7152 * linear(green) + 0.0722 * linear(blue);
    }

    private static double linear(int channel) {
        double value = channel / 255.0;
        return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }
}
