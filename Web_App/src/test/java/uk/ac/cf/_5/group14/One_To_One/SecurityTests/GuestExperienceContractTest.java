package uk.ac.cf._5.group14.One_To_One.SecurityTests;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GuestExperienceContractTest {

    private static final List<String> REDESIGNED_TEMPLATES = List.of(
            "client-views/explore/index.html",
            "shared-views/support/index.html",
            // The shop uses its own server-rendered commerce workspace, covered by MerchJourneyIntegrationTest.
            "public-views/public/about.html",
            "public-views/public/faq.html",
            "public-views/public/profile.html",
            "public-views/payments/pricing.html",
            "public-views/policies/privacy.html",
            "public-views/policies/payments.html",
            "public-views/policies/terms.html",
            "public-views/policies/subscription-terms.html",
            "public-views/dashboard/client-dashboard-public.html",
            "public-views/auth/confirm-logout.html",
            "public-views/auth/forgot-password.html",
            "public-views/auth/login.html",
            "public-views/auth/reset-password.html",
            "public-views/auth/signup-choice.html",
            "public-views/auth/signup-client.html",
            "public-views/auth/signup-trainer.html",
            "public-views/auth/signup-trainer-success.html",
            "public-views/auth/signup-gym.html",
            "public-views/auth/signup-gym-application.html",
            "public-views/verify/email-confirm.html",
            "public-views/verify/email-code.html",
            "public-views/verify/phone-code.html",
            "system-views/dev-mode/hub.html",
            "system-views/dev-mode/unauthorized.html",
            "system-views/dev-mode/restricted.html",
            "system-views/error/403.html",
            "system-views/error/404.html",
            "system-views/error/500.html",
            "system-views/error/error.html"
    );

    @Test
    void everyLiveGuestViewUsesTheSharedSceneContract() throws IOException {
        for (String template : REDESIGNED_TEMPLATES) {
            String source = readTemplate(template);

            assertThat(source)
                    .as(template)
                    .contains("guest-experience")
                    .contains("data-guest-scene");
        }
    }

    @Test
    void guestSystemRemainsRouteScopedAwayFromTheHomepage() throws IOException {
        String guestEntry = read("src/main/resources/static/css/entries/guest.css");
        String publicHome = readTemplate("public-views/home/public.html");

        assertThat(guestEntry)
                .contains("guest-foundation.css")
                .contains("guest-marketing.css")
                .contains("guest-auth.css")
                .contains("guest-utility.css")
                .doesNotContain("home-public.css");
        assertThat(publicHome)
                .doesNotContain("guest-experience")
                .doesNotContain("data-guest-scene");
    }

    @Test
    void guestMotionUsesViewportPointerCoordinatesAndAnAnimatedReset() throws IOException {
        String guestExperience = read("src/main/resources/static/js/public/guest-experience.js");
        String guestFoundation = read("src/main/resources/static/css/components/public/guest-foundation.css");

        assertThat(guestExperience)
                .contains("event.clientX / window.innerWidth")
                .contains("event.clientY / window.innerHeight")
                .contains("requestAnimationFrame(renderPointerLight)")
                .contains("root.addEventListener('pointerleave'")
                .contains("root[revealObserverKey] = observer")
                .contains("delete root[revealObserverKey]")
                .doesNotContain("scrollX")
                .doesNotContain("scrollY");
        assertThat(guestFoundation)
                .contains("visibility: hidden;")
                .contains("visibility: visible;")
                .doesNotContain("clip-path: inset(0 0 100% 0)");
    }

    @Test
    void publicWorkspaceFeatureGridOwnsItsListItemsDirectly() throws IOException {
        String workspace = readTemplate("public-views/dashboard/client-dashboard-public.html");
        long articleListItems = workspace.lines()
                .filter(line -> line.contains("<article") && line.contains("role=\"listitem\""))
                .count();
        long nestedDivListItems = workspace.lines()
                .filter(line -> line.contains("<div") && line.contains("role=\"listitem\""))
                .count();

        assertThat(workspace)
                .contains("class=\"mt-4 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4\" role=\"list\"");
        assertThat(articleListItems).isEqualTo(8);
        assertThat(nestedDivListItems).isZero();
    }

    @Test
    void responsiveTrainerFiltersKeepAnExplicitAccessibleName() throws IOException {
        String explore = readTemplate("client-views/explore/index.html");
        long localisedFilterLabels = explore.lines()
                .filter(line -> line.contains("th:attr=\"aria-label=#{ui.00554}\""))
                .count();

        assertThat(explore).contains("aria-label=\"Apply filters\"");
        assertThat(localisedFilterLabels).isEqualTo(2);
    }

    @Test
    void guestMicrocopyAndSupportConsentMeetTheReadabilityFloor() throws IOException {
        String foundation = read("src/main/resources/static/css/components/public/guest-foundation.css");
        String support = readTemplate("shared-views/support/index.html");

        assertThat(foundation)
                .contains("body.public-guest.guest-route :is(")
                .contains(".language-selector__code")
                .contains(".footer-legal-link")
                .contains(".devhub-route-nav__link > strong")
                .contains("font-size: 0.75rem;");
        assertThat(support)
                .contains("name=\"allowEmailReply\" class=\"mt-1 h-6 w-6 shrink-0\"")
                .doesNotContain("name=\"allowEmailReply\" class=\"mt-1 h-5 w-5 shrink-0\"");
        assertThat(readTemplate("public-views/dashboard/client-dashboard-public.html"))
                .contains("class=\"text-xs font-extrabold uppercase")
                .doesNotContain("class=\"text-[11px] font-extrabold uppercase");
    }

    @Test
    void guestStoreTrustTilesUseTheDarkGuestSurface() throws IOException {
        String utility = read("src/main/resources/static/css/components/public/guest-utility.css");

        assertThat(utility)
                .contains(".guest-store.store-page .guest-store__trust > .card-neon")
                .contains("linear-gradient(145deg, rgba(11, 38, 31, 0.94), rgba(4, 22, 18, 0.96))")
                .doesNotContain(".guest-store__trust > div {\n    border: 0 !important;");
    }

    @Test
    void unpublishedLegalTermsCannotBeAcceptedAsFinalCopy() throws IOException {
        for (String template : List.of(
                "public-views/policies/terms.html",
                "public-views/policies/subscription-terms.html")) {
            assertThat(readTemplate(template))
                    .as(template)
                    .contains("Awaiting review")
                    .doesNotContain("legal-confirmation.js")
                    .doesNotContain("localStorage")
                    .doesNotContain("data-legal-accept")
                    .doesNotContain("I accept");
        }
    }

    @Test
    void publicProfileLinksUseControllerNormalisedUrls() throws IOException {
        String profile = readTemplate("public-views/public/profile.html");

        assertThat(profile)
                .contains("${safeInstagramUrl}")
                .contains("${safeTiktokUrl}")
                .contains("${safeYoutubeUrl}")
                .contains("${safeLinkedInUrl}")
                .contains("${safeWebsiteUrl}")
                .doesNotContain("th:href=\"${trainerProfile.instagramUrl}\"")
                .doesNotContain("th:href=\"${trainerProfile.tiktokUrl}\"")
                .doesNotContain("th:href=\"${trainerProfile.youtubeUrl}\"")
                .doesNotContain("th:href=\"${trainerProfile.linkedInUrl}\"")
                .doesNotContain("th:href=\"${trainerProfile.websiteUrl}\"");
    }

    @Test
    void publicTrainerViewsNeverRenderInternalGymIdentifiers() throws IOException {
        String profileController = read(
                "src/main/java/uk/ac/cf/_5/group14/One_To_One/PublicProfile/PublicProfileController.java");
        String exploreController = read(
                "src/main/java/uk/ac/cf/_5/group14/One_To_One/Explore/ExploreController.java");
        String profile = readTemplate("public-views/public/profile.html");
        String explore = readTemplate("client-views/explore/index.html");

        assertThat(profileController)
                .contains("trainerProfile.getPrimaryGym()")
                .doesNotContain("mav.addObject(\"gymAffiliation\", user.getGymId())");
        assertThat(exploreController)
                .contains("public String getDisplayGym()")
                .doesNotContain("\"Gym #\"")
                .doesNotContain("return \"Independent\"");
        assertThat(profile)
                .contains("th:text=\"${gymAffiliation}\"")
                .doesNotContain("trainer.gymId");
        assertThat(explore)
                .contains("th:text=\"#{ui.00150}\"")
                .doesNotContain("trainer.gymId");
    }

    @Test
    void dynamicGuestLabelsUseLocalisedStructureAndAccessibleRatings() throws IOException {
        String profile = readTemplate("public-views/public/profile.html");
        String explore = readTemplate("client-views/explore/index.html");
        String shop = readTemplate("shared-views/merch/shop.html");
        String support = readTemplate("shared-views/support/index.html");
        String genericError = readTemplate("system-views/error/error.html");
        String forbiddenError = readTemplate("system-views/error/403.html");
        String errorController = read(
                "src/main/java/uk/ac/cf/_5/group14/One_To_One/ErrorHandling/CustomErrorController.java");

        assertThat(profile)
                .contains("th:text=\"#{ui.00558}\"")
                .contains("th:text=\"#{ui.01572}\"")
                .contains("class=\"flex items-center gap-1\" aria-hidden=\"true\"")
                .doesNotContain("reviewCount + ' '");
        assertThat(explore)
                .contains("th:text=\"${resultCount}\"")
                .contains("th:text=\"#{ui.01528}\"")
                .contains("th:text=\"#{ui.00558}\"")
                .contains("th:text=\"#{ui.01572}\"")
                .doesNotContain("resultCount + ' verified trainers'")
                .doesNotContain("'(' + card.reviewCount + ')'");
        assertThat(shop)
                .contains("th:text=\"#{ui.00133}\"")
                .contains("sec:authorize=\"!isAuthenticated()\"")
                .contains("sec:authorize=\"isAuthenticated()\"")
                .doesNotContain("product.name + ' back'")
                .doesNotContain("product.stockQuantity} + ' left'");
        assertThat(support)
                .contains("th:text=\"#{ui.00950}\"")
                .contains("th:text=\"#{ui.00542}\"")
                .contains("th:text=\"#{ui.00311}\"")
                .doesNotContain("roleLabel + ' workspace'")
                .doesNotContain("? 'Back to home' : 'Back to dashboard'");
        assertThat(genericError)
                .contains("th:text=\"#{ui.02384}\"")
                .contains("th:text=\"#{ui.02385}\"")
                .doesNotContain("errorMessage")
                .doesNotContain("?: 'Error'");
        assertThat(forbiddenError)
                .contains("th:text=\"#{ui.02372}\"")
                .doesNotContain("errorMessage")
                .doesNotContain("You do not have permission");
        assertThat(errorController)
                .doesNotContain("model.addAttribute(\"errorMessage\"")
                .doesNotContain("You do not have permission to access this page.");
    }

    @Test
    void guestPageChromeCannotRestyleSignedInSharedRoutes() throws IOException {
        String foundation = read("src/main/resources/static/css/components/public/guest-foundation.css");

        assertThat(foundation)
                .contains("body.public-guest.guest-route,")
                .contains("html body.public-guest.guest-route #main-content")
                .contains("body.public-guest.guest-route .footer-redesign")
                .doesNotContain("body.guest-route,")
                .doesNotContain("body.guest-route .footer-redesign");
    }

    private static String readTemplate(String relativePath) throws IOException {
        return read("src/main/resources/templates/" + relativePath);
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(Path.of(relativePath));
    }
}
