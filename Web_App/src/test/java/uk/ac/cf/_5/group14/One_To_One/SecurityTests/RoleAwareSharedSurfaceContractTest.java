package uk.ac.cf._5.group14.One_To_One.SecurityTests;

import org.junit.jupiter.api.Test;
import uk.ac.cf._5.group14.One_To_One.Config.RoleSurfaceContext;
import uk.ac.cf._5.group14.One_To_One.Users.Role;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RoleAwareSharedSurfaceContractTest {

    @Test
    void sharedContextProvidesRoleCorrectDestinationsAndToolIntent() {
        RoleSurfaceContext client = RoleSurfaceContext.forRole(Role.CLIENT);
        RoleSurfaceContext trainer = RoleSurfaceContext.forRole(Role.TRAINER);
        RoleSurfaceContext gym = RoleSurfaceContext.forRole(Role.GYM_ADMIN);
        RoleSurfaceContext admin = RoleSurfaceContext.forRole(Role.PLATFORM_ADMIN);

        assertThat(client.dashboardPath()).isEqualTo("/dashboard");
        assertThat(client.inboxEmptyActionPath()).isEqualTo("/explore");
        assertThat(client.coachingToolsPrimary()).isTrue();

        assertThat(trainer.dashboardPath()).isEqualTo("/trainer/dashboard");
        assertThat(trainer.inboxEmptyActionPath()).isEqualTo("/trainer/clients");
        assertThat(trainer.workoutHeading()).contains("Client");

        assertThat(gym.dashboardPath()).isEqualTo("/gym/dashboard");
        assertThat(gym.inboxEmptyActionPath()).isEqualTo("/support");
        assertThat(gym.coachingToolsPrimary()).isFalse();

        assertThat(admin.dashboardPath()).isEqualTo("/admin/dashboard");
        assertThat(admin.inboxEmptyActionPath()).isEqualTo("/admin/feedback");
        assertThat(admin.coachingToolsPrimary()).isFalse();
    }

    @Test
    void inboxAndSupportUseSharedRoleCopyAndValidRecoveryActions() throws IOException {
        String inbox = read("src/main/resources/templates/shared-views/inbox/index.html");
        String thread = read("src/main/resources/templates/shared-views/inbox/thread.html");
        String support = read("src/main/resources/templates/shared-views/support/index.html");

        assertThat(inbox)
                .contains("${roleSurface.inboxHeading}")
                .contains("${roleSurface.inboxEmptyActionPath}")
                .contains("/js/messaging/inbox.js(v=${uiCssVersion})")
                .doesNotContain("Messages between you and your trainer/client")
                .doesNotContain("href=\"/profile\"");
        assertThat(thread)
                .contains("${roleSurface.threadEmptyCopy}")
                .contains("${roleSurface.messagePlaceholder}")
                .contains("${roleSurface.dashboardPath}")
                .contains("/js/messaging/inbox.js(v=${uiCssVersion})");
        assertThat(support)
                .contains("${roleSurface.supportHeading}")
                .contains("${roleSurface.supportSubmitLabel}")
                .contains("th:action=\"@{/support/feedback}\"");
    }

    @Test
    void sharedToolPanelExcludesOperationallyWrongRoleActions() throws IOException {
        String panel = read("src/main/resources/static/js/core/platform-panel.js");

        assertThat(panel)
                .contains("oneToOne.platformPanel.v2.${role}")
                .contains("label: \"Gym support\", href: \"/support\"")
                .contains("label: \"Support inbox\", href: \"/admin/feedback\"")
                .contains("label: \"Client inbox\", href: \"/inbox\"")
                .contains("label: \"My workouts\", href: \"/workout-management\"")
                .doesNotContain("label: \"Workouts\", href: \"/workouts\"");
    }

    @Test
    void workoutDialogAndRoleSurfaceCssMeetInteractionContracts() throws IOException {
        String workout = read("src/main/resources/templates/trainer-views/workouts/index.html");
        String script = read("src/main/resources/static/js/workouts/workout-studio.js");
        String css = read("src/main/resources/static/css/components/core/role-surfaces.css");

        assertThat(workout)
                .contains("role=\"dialog\"")
                .contains("aria-hidden=\"true\" inert")
                .contains("/js/workouts/workout-studio.js(v=${assetVersion})")
                .contains("role=\"tablist\" aria-label=\"Workout studio views\"")
                .contains("role=\"tab\" aria-selected=\"true\"")
                .contains("role=\"tabpanel\" aria-labelledby=\"studio-tab-my-workouts\"")
                .contains("data-filter=\"chest\" aria-pressed=\"false\"")
                .contains("for=\"exercise-library-search\"")
                .doesNotContain("<main class=\"workout-studio\"");
        assertThat(script)
                .contains("panel?.removeAttribute('inert')")
                .contains("panel?.setAttribute('inert', '')")
                .contains("event.key === 'Escape'")
                .contains("event.key === 'Tab'")
                .contains("event.key === 'ArrowRight'")
                .contains("tab.setAttribute('aria-selected', active ? 'true' : 'false')")
                .contains("tag.setAttribute('aria-pressed', 'true')")
                .contains("custom-exercise-btn-cancel")
                .contains("toast.setAttribute('role', type === 'error' ? 'alert' : 'status')");
        assertThat(workout)
                .contains("for=\"custom-exercise-name\"")
                .contains("for=\"custom-exercise-category\"")
                .contains("for=\"custom-exercise-difficulty\"");
        assertThat(css)
                .contains("min-height: 44px")
                .contains("prefers-reduced-motion")
                .contains(".inbox-thread-surface #inboxSendForm > button[type=\"submit\"]")
                .doesNotContain("transition: all");
    }

    @Test
    void sharedOperationalTouchTargetsKeepA44PixelReleaseQaBaseline() throws IOException {
        String workoutCss = read("src/main/resources/static/css/components/training/workout-studio.css");
        String calendarCss = read("src/main/resources/static/css/components/calendar/calendar-redesign.css");
        String dashboardCss = read("src/main/resources/static/css/components/dashboard/client-dashboard-refresh.css");
        String navbarCss = read("src/main/resources/static/css/components/core/navbar.css");
        String inboxScript = read("src/main/resources/static/js/messaging/inbox.js");

        assertThat(workoutCss).contains("min-height: 44px");
        assertThat(calendarCss)
                .contains("min-height: 2.75rem")
                .contains("button[aria-label=\"Toggle workout heatmap legend\"]")
                .contains("a.calendar-day-number");
        assertThat(dashboardCss)
                .contains("min-height: 45px")
                .contains("min-height: 2.75rem");
        assertThat(navbarCss)
                .contains(".nav-brand-link")
                .contains("min-width: 44px")
                .contains("min-height: 44px");
        assertThat(inboxScript)
                .contains("inline-flex min-h-11 items-center")
                .contains("inline-flex min-h-11 w-fit items-center");
    }

    @Test
    void populatedPlatformOperationsKeepNamed44PixelControls() throws IOException {
        String applications = read("src/main/resources/templates/admin-views/admin/gym-applications.html");
        String feedback = read("src/main/resources/templates/admin-views/admin/feedback.html");

        assertThat(applications)
                .contains("inline-flex min-h-11 items-center")
                .contains("Open application");
        assertThat(feedback)
                .contains("th:for=\"${'feedback-status-' + item.id}\"")
                .contains("th:id=\"${'feedback-status-' + item.id}\"")
                .contains("th:for=\"${'feedback-response-' + item.id}\"")
                .contains("th:id=\"${'feedback-response-' + item.id}\"")
                .contains("class=\"min-h-11 w-full");
    }

    @Test
    void calendarDrawerAndGoalViewsExposeCompleteKeyboardPatterns() throws IOException {
        String month = read("src/main/resources/templates/shared-views/calendar/month.html");
        String week = read("src/main/resources/templates/shared-views/calendar/week.html");
        String drawer = read("src/main/resources/templates/shared-views/calendar/fragments/schedule-drawer.html");
        String calendarScript = read("src/main/resources/static/js/calendar/calendar-ux.js");
        String goals = read("src/main/resources/templates/client-views/goals/index.html");
        String goalsScript = read("src/main/resources/static/js/goals/goal-pages.js");

        assertThat(month)
                .contains("<h1 class=\"sr-only\" th:text=\"#{ui.01790}\">Calendar month view</h1>")
                .contains("class=\"calendar-singular-list\"")
                .contains("tabindex=\"0\"")
                .contains("id=\"sticker-tooltip\"")
                .contains("aria-hidden=\"true\" hidden")
                .contains("/js/calendar/month.js(v=${assetVersion})");
        assertThat(week)
                .contains("<h1 class=\"sr-only\" th:text=\"#{ui.01854}\">Calendar week view</h1>")
                .contains("class=\"calendar-singular-list\"")
                .contains("tabindex=\"0\"")
                .contains("/js/calendar/week.js(v=${assetVersion})")
                .contains("/js/calendar/calendar-ux.js(v=${assetVersion})");
        assertThat(drawer)
                .contains("role=\"dialog\" aria-modal=\"true\"")
                .contains("aria-labelledby=\"scheduleDrawerTitle\"")
                .contains("data-is-premium=\"true\" inert");
        assertThat(calendarScript)
                .contains("scheduleDrawer.removeAttribute(\"inert\")")
                .contains("scheduleDrawer.setAttribute(\"inert\", \"\")")
                .contains("e.key === \"Tab\"")
                .contains("focusDrawerWhenVisible")
                .contains("getComputedStyle(scheduleDrawer).visibility")
                .contains("[data-drawer-close]")
                .contains("lastFocusedElement.focus()");

        assertThat(goals)
                .contains("role=\"tab\" aria-selected=\"true\"")
                .contains("role=\"tabpanel\" aria-labelledby=\"goal-tab-week\"")
                .contains("aria-labelledby=\"goal-tab-month\" hidden")
                .contains("/js/goals/goal-pages.js(v=${assetVersion})");
        assertThat(goalsScript)
                .contains("event.key === \"ArrowRight\"")
                .contains("event.key === \"Home\"")
                .contains("view.hidden = !active")
                .contains("tab.tabIndex = active ? 0 : -1");
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(Path.of(relativePath));
    }
}
