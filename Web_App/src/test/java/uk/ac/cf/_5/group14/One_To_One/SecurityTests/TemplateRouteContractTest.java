package uk.ac.cf._5.group14.One_To_One.SecurityTests;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateRouteContractTest {

    @Test
    void publicHomeUsesPublicExploreRouteForTrainerDiscovery() throws IOException {
        String template = read("src/main/resources/templates/public-views/home/public.html");

        assertThat(template).contains("th:href=\"@{/explore}\"");
        assertThat(template).doesNotContain("th:href=\"@{/trainers}\"");
    }

    @Test
    void signedInHomeUsesInboxAsCanonicalMessagingEntryPoint() throws IOException {
        String template = read("src/main/resources/templates/public-views/home/user.html");

        assertThat(template).contains("th:href=\"@{/inbox}\"");
        assertThat(template).contains("th:href=\"@{/chat}\"");
        assertThat(template).contains("Open Coach");
        assertThat(template).doesNotContain("th:href=\"@{/client/messages}\"");
    }

    @Test
    void gymDashboardUsesGymSpecificOperationalRoutes() throws IOException {
        String template = read("src/main/resources/templates/gym-views/dashboard/gym-dashboard.html");

        assertThat(template).contains("href=\"/gym/dashboard\"");
        assertThat(template).contains("href=\"/gym/admin/trainers\"");
        assertThat(template).contains("href=\"/gym/admin/memberships\"");
        assertThat(template).contains("href=\"/support\"");
        assertThat(template).contains("Submit a support request");
        assertThat(template).contains("Inbox");
        assertThat(template).doesNotContain("th:href=\"@{/admin/feedback}\"");
        assertThat(template).doesNotContain("href=\"/admin/trainers\"");
        assertThat(template).doesNotContain("href=\"/admin/settings\"");
        assertThat(template).doesNotContain("â†’");
        assertThat(template).doesNotContain("ðŸ");
    }

    @Test
    void trainerDashboardUsesInboxAsPrimaryMessagingSurface() throws IOException {
        String template = read("src/main/resources/templates/trainer-views/dashboard/trainer-dashboard.html");

        assertThat(template).contains("th:href=\"@{/inbox}\"");
        assertThat(template).contains("th:href=\"@{/trainer/clients}\"");
        assertThat(template).contains("th:href=\"@{/trainer/library/programmes}\"");
        assertThat(template).contains("th:href=\"@{/trainer/library/exercises}\"");
        assertThat(template).contains("th:href=\"@{/trainer/templates}\"");
        assertThat(template).contains("Inbox");
        assertThat(template).doesNotContain("/client/messages");
        assertThat(template).doesNotContain("/trainer/active-clients");
        assertThat(template).doesNotContain("/trainer/programmes/list");
        assertThat(template).doesNotContain("/trainer/exercises/list");
        assertThat(template).doesNotContain("/trainer/templates/index");
        assertThat(template).doesNotContain("â†’");
        assertThat(template).doesNotContain("ðŸ");
    }

    @Test
    void canonicalClientMessageActionsUseInboxAndUnusedAlternatesAreRetired() throws IOException {
        String clients = read("src/main/resources/templates/trainer-views/trainer/clients.html");

        assertThat(clients)
                .contains("th:action=\"@{/inbox/start/{id}(id=${link.clientUserId})}\"")
                .contains("method=\"post\"")
                .doesNotContain("th:href=\"@{/inbox/start/{id}(id=${link.clientUserId})}\"");
        assertThat(java.nio.file.Path.of("src/main/resources/templates/trainer-views/trainer/active-clients.html")).doesNotExist();
        assertThat(java.nio.file.Path.of("src/main/resources/templates/trainer-views/trainer/client-requests.html")).doesNotExist();
        assertThat(clients).contains("/decline(id=${req.clientUserId})").doesNotContain("/trainer/requests/{id}");
    }

    @Test
    void adminDashboardUsesCleanOperationalMetadata() throws IOException {
        String template = read("src/main/resources/templates/admin-views/dashboard/admin-dashboard.html");

        assertThat(template).contains("Platform Operations");
        assertThat(template).contains("th:href=\"@{/admin/feedback}\"");
        assertThat(template).contains("th:href=\"@{/admin/merch}\"");
        assertThat(template).contains("href=\"#admin-outreach-form\"");
        assertThat(template).contains("href=\"#dev-page-controls\"");
        assertThat(template).contains("aria-label=${'Access mode for ' + page.title}");
        assertThat(template).contains("aria-label=${'Save access mode for ' + page.title}");
        assertThat(template).contains("<caption class=\"sr-only\" th:text=\"#{ui.00104}\">Dev Hub page access controls</caption>");
        assertThat(template).doesNotContain("â€¢");
    }

    @Test
    void adminControllersReturnExistingAdminViewTemplates() throws IOException {
        String gymApplicationController = read("src/main/java/uk/ac/cf/_5/group14/One_To_One/GymApplications/GymApplicationController.java");
        String adminSupportController = read("src/main/java/uk/ac/cf/_5/group14/One_To_One/Support/AdminSupportController.java");
        String paymentController = read("src/main/java/uk/ac/cf/_5/group14/One_To_One/Messaging/AdminOffPlatformPaymentController.java");

        assertThat(gymApplicationController).contains("return \"admin-views/admin/gym-applications\"");
        assertThat(gymApplicationController).contains("return \"admin-views/admin/gym-application-detail\"");
        assertThat(adminSupportController).contains("return \"admin-views/admin/feedback\"");
        assertThat(paymentController).contains("new ModelAndView(\"admin-views/admin/off-platform-payments\")");
        assertThat(gymApplicationController).doesNotContain("return \"admin/gym-applications\"");
        assertThat(gymApplicationController).doesNotContain("return \"admin/gym-application-detail\"");
        assertThat(adminSupportController).doesNotContain("return \"admin/feedback\"");
        assertThat(paymentController).doesNotContain("new ModelAndView(\"admin/off-platform-payments\")");
    }

    @Test
    void scheduleListUsesPostFormsForDeletionAndAppliedWindowSettings() throws IOException {
        String template = read("src/main/resources/templates/trainer-views/schedule/list.html");

        assertThat(template).contains("method=\"post\"");
        assertThat(template).contains("th:action=\"@{/schedules/applied/{id}/settings(id=${applied.id})}\"");
        assertThat(template).contains("th:action=\"@{/schedules/{id}/delete(id=${schedule.id})}\"");
        assertThat(template).doesNotContain("th:href=\"@{/schedules/applied/{id}/settings", "th:href=\"@{/schedules/{id}/delete");
    }

    @Test
    void clientTrainerRoutesAreClientOnlyInSecurityConfig() throws IOException {
        String securityConfig = read("src/main/java/uk/ac/cf/_5/group14/One_To_One/Security/SecurityConfig.java");

        assertThat(securityConfig).contains(".requestMatchers(\"/client/trainers\", \"/client/trainers/**\").hasRole(\"CLIENT\")");
        assertThat(securityConfig).contains(".requestMatchers(\"/client/**\").hasRole(\"CLIENT\")");
        assertThat(securityConfig).contains(".requestMatchers(\"/trainers/**\").hasRole(\"CLIENT\")");
        assertThat(securityConfig).doesNotContain(".requestMatchers(\"/client/trainers\", \"/client/trainers/**\").hasAnyRole(\"CLIENT\", \"USER\")");
        assertThat(securityConfig).doesNotContain(".requestMatchers(\"/client/**\").hasAnyRole(\"CLIENT\", \"USER\")");
        assertThat(securityConfig).doesNotContain(".requestMatchers(\"/trainers/**\").hasAnyRole(\"CLIENT\", \"USER\")");
    }

    @Test
    void navbarUsesCanonicalCharlieAndRoleSpecificOperationalRoutes() throws IOException {
        String template = read("src/main/resources/templates/universal-fragments/layout/navbar.html");

        assertThat(template).contains("th:href=\"@{/chat}\"");
        assertThat(template).contains("th:href=\"@{/inbox}\"");
        assertThat(template).contains("th:href=\"@{/gym/admin/trainers}\"");
        assertThat(template).contains("th:href=\"@{/gym/admin/memberships}\"");
        assertThat(template).contains("th:href=\"@{/super-admin/verification/queue}\"");
        assertThat(template).contains("th:href=\"@{/admin/feedback}\"");
        assertThat(template).contains("aria-label=\"Charlie\"");
        assertThat(template).doesNotContain("th:href=\"@{/trainer/messages}\"");
        assertThat(template).doesNotContain("id=\"mobileChatToggle\"");
    }

    @Test
    void calendarDayUsesCoachWordingAndCleanAsciiFallbacks() throws IOException {
        String template = read("src/main/resources/templates/shared-views/calendar/day.html");

        assertThat(template).contains("Open Coach");
        assertThat(template).contains("Generate coach reflection");
        assertThat(template).doesNotContain("Open Chat");
        assertThat(template).doesNotContain("Ã‚Â·");
        assertThat(template).doesNotContain("Ã¢â‚¬Â¦");
        assertThat(template).doesNotContain("Ã°Å¸");
        assertThat(template).doesNotContain("weÃ¢â‚¬â„¢ll");
    }

    @Test
    void clientDashboardShellExposesCoachAsCanonicalAiAction() throws IOException {
        String template = read("src/main/resources/templates/client-views/dashboard/fragments/client-dashboard-shell.html");

        assertThat(template).contains("th:href=\"@{/chat}\"");
        assertThat(template).contains("Open coach");
    }

    @Test
    void legacyMessageTemplatesPointAtInboxThreads() throws IOException {
        String trainerInbox = read("src/main/resources/templates/shared-views/messages/trainer-inbox.html");
        String clientInbox = read("src/main/resources/templates/shared-views/messages/client-inbox.html");

        assertThat(trainerInbox).contains("th:href=\"@{/inbox/{id}(id=${t.id})}\"");
        assertThat(clientInbox).contains("th:href=\"@{/inbox/{id}(id=${thread.id})}\"");
        assertThat(trainerInbox).doesNotContain("th:href=\"@{/messages/{id}(id=${t.id})}\"");
        assertThat(clientInbox).doesNotContain("th:href=\"@{/messages/{id}(id=${thread.id})}\"");
        assertThat(trainerInbox).doesNotContain("Ã‚Â·");
    }

    @Test
    void globalChatWidgetUsesCoachWordingAndCleanAsciiText() throws IOException {
        String template = read("src/main/resources/templates/universal-fragments/chat/chat-widget.html");

        assertThat(template).contains("aria-label=\"Open Charlie\"");
        assertThat(template).contains("<label class=\"sr-only\" for=\"chatInput\" th:text=\"#{ui.02962}\">Message Charlie</label>");
        assertThat(template).contains("<textarea id=\"chatInput\"");
        assertThat(template).contains("Log in to use coach");
        assertThat(template).contains("Mark all read");
        assertThat(template).doesNotContain("aria-label=\"Open chat\"");
        assertThat(template).doesNotContain("MessageÃ¢â‚¬Â¦");
        assertThat(template).doesNotContain("Ã°Å¸");
        assertThat(template).doesNotContain("Ã¢Å“");
        assertThat(template).doesNotContain("Ã¢â€ ");
    }

    @Test
    void globalChatWidgetSeparatesGuestAndPremiumNavigation() throws IOException {
        String template = read("src/main/resources/templates/universal-fragments/chat/chat-widget.html");
        String css = read("src/main/resources/static/css/components/chat/chat-widget.css");
        String script = read("src/main/resources/static/js/chat/chat.js");

        assertThat(template)
                .contains("chat-tabs-premium--guest")
                .contains("<button sec:authorize=\"isAuthenticated()\"\n                        type=\"button\"\n                        id=\"chatProChatBtn\"")
                .contains("chat-tab-btn--premium")
                .contains("chat-tab-btn--locked")
                .contains("id=\"chatPlusAccessNotice\"")
                .contains("Chat+ is a Premium workspace")
                .contains("View Premium");
        assertThat(css)
                .contains(".chat-tabs-premium--guest")
                .contains(".chat-tabs-premium--member")
                .contains(".chat-plus-access-notice.is-open")
                .contains("grid-template-areas: \"camera message send\"");
        assertThat(script)
                .contains("setChatPlusAccessOpen")
                .contains("chatPlusAccessNotice")
                .contains("proChatBtn.getAttribute(\"aria-expanded\") === \"true\"");
    }

    @Test
    void trainerDirectoryUsesFormattedPresentationPrice() throws IOException {
        String template = read("src/main/resources/templates/client-views/explore/index.html");

        assertThat(template).contains("th:text=\"${card.formattedPricePerSession}\"");
        assertThat(template).doesNotContain("th:text=\"'£' + card.pricePerSession");
    }

    @Test
    void publicEntryRoutesUseCentralAuthStylesAndKeepCriticalScriptsVersioned() throws IOException {
        String base = read("src/main/resources/templates/base.html");
        String about = read("src/main/resources/templates/public-views/public/about.html");
        String styleAdvice = read("src/main/java/uk/ac/cf/_5/group14/One_To_One/Config/UiStyleBundleAdvice.java");
        String login = read("src/main/resources/templates/public-views/auth/login.html");
        String signupChoice = read("src/main/resources/templates/public-views/auth/signup-choice.html");
        String signupClient = read("src/main/resources/templates/public-views/auth/signup-client.html");
        String signupTrainer = read("src/main/resources/templates/public-views/auth/signup-trainer.html");
        String signupGym = read("src/main/resources/templates/public-views/auth/signup-gym.html");

        assertThat(base).contains("assetVersion=${uiCssVersion != null");
        assertThat(styleAdvice)
                .contains("\"/login\", \"/signup\", \"/forgot-password\", \"/reset-password\"")
                .contains("addWhenMatched(bundles, path, AUTH_PATHS, \"/css/bundles/auth.css\")")
                .contains("\"/about\", \"/faq\", \"/pricing\"")
                .contains("addWhenMatched(bundles, path, CONTENT_PATHS, \"/css/bundles/content.css\")");
        assertThat(about).doesNotContain("/css/bundles/content.css");

        assertThat(login)
                .doesNotContain("/css/bundles/auth.css")
                .contains("/js/auth/login-page.js(v=${assetVersion})");
        assertThat(signupChoice).doesNotContain("/css/bundles/auth.css");

        assertThat(signupClient)
                .doesNotContain("/css/bundles/auth.css")
                .contains("/js/auth/signup-client-page.js(v=${assetVersion})");
        assertThat(signupTrainer)
                .doesNotContain("/css/bundles/auth.css")
                .contains("/js/auth/signup-trainer-page.js(v=${assetVersion})");
        assertThat(signupGym)
                .doesNotContain("/css/bundles/auth.css")
                .contains("/js/auth/signup-gym-page.js(v=${assetVersion})");
    }

    @Test
    void publicFooterKeepsNavigationCompactAndActionFocused() throws IOException {
        String footer = read("src/main/resources/templates/universal-fragments/layout/footer.html");

        assertThat(footer)
                .contains("Train with purpose. Coach with clarity.")
                .contains("aria-label=\"Footer navigation\"")
                .contains("th:href=\"@{/signup}\"")
                .contains("aria-label=\"Legal\"")
                .doesNotContain("footer-role-card")
                .doesNotContain("footer-spotlight")
                .doesNotContain("footer-brand-description");
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(Path.of(relativePath));
    }
}
