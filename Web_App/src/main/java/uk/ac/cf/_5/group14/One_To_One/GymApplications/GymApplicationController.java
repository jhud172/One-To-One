package uk.ac.cf._5.group14.One_To_One.GymApplications;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Locale;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import uk.ac.cf._5.group14.One_To_One.Security.SecurityUtils;
import uk.ac.cf._5.group14.One_To_One.Users.AuthHelper;
import uk.ac.cf._5.group14.One_To_One.Users.User;

@Controller
public class GymApplicationController {

    private final GymApplicationService gymApplicationService;
    private final AuthHelper authHelper;

    public GymApplicationController(GymApplicationService gymApplicationService, AuthHelper authHelper) {
        this.gymApplicationService = gymApplicationService;
        this.authHelper = authHelper;
    }

    @GetMapping("/admin/gym-applications")
    public String listApplications(Authentication authentication, Model model,
                                   @RequestParam(defaultValue = "") String search,
                                   @RequestParam(defaultValue = "") String status, HttpServletResponse response) {
        if (!isAdmin(authentication)) {
            return "redirect:/access-denied";
        }

        model.addAttribute("pageTitle", "Gym Applications");
        var applications = gymApplicationService.getAllApplications();
        String query = search.trim().toLowerCase(Locale.ROOT);
        model.addAttribute("applications", applications.stream().filter(item -> status.isEmpty() || item.getStatus().name().equals(status))
            .filter(item -> query.isEmpty() || item.getGymName().toLowerCase(Locale.ROOT).contains(query)
                || item.getAdminEmail().toLowerCase(Locale.ROOT).contains(query)
                || item.getCity().toLowerCase(Locale.ROOT).contains(query)).toList());
        model.addAttribute("statuses", GymApplicationStatus.values()); model.addAttribute("totalApplications", applications.size());
        model.addAttribute("search", search); model.addAttribute("selectedStatus", status);
        if (search.length() > 120 || (!status.isEmpty() && java.util.Arrays.stream(GymApplicationStatus.values()).noneMatch(value -> value.name().equals(status)))) {
            response.setStatus(400); model.addAttribute("gymApplicationError", "Use a search of up to 120 characters and a valid application status.");
        }
        return "admin-views/admin/gym-applications";
    }

    @GetMapping("/admin/gym-applications/{id}")
    public String applicationDetail(@PathVariable("id") Long id,
                                    Authentication authentication,
                                    Model model,
                                    RedirectAttributes redirectAttributes) {
        if (!isAdmin(authentication)) {
            return "redirect:/access-denied";
        }

        try {
            GymApplication application = gymApplicationService.getApplication(id);
            model.addAttribute("pageTitle", "Gym Application");
            model.addAttribute("gymApplication", application);
            model.addAttribute("applicationClosed", application.getStatus() == GymApplicationStatus.APPROVED || application.getStatus() == GymApplicationStatus.DECLINED);
            model.addAttribute("messages", gymApplicationService.getMessages(id));
            model.addAttribute("openApplicationCount", gymApplicationService.countOpenApplications());
            return "admin-views/admin/gym-application-detail";
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("gymApplicationError", ex.getMessage());
            return "redirect:/admin/gym-applications";
        }
    }

    @PostMapping("/admin/gym-applications/{id}/message")
    public String sendAdminMessage(@PathVariable("id") Long id,
                                   @RequestParam("subject") String subject,
                                   @RequestParam("message") String message,
                                   Authentication authentication,
                                   RedirectAttributes redirectAttributes) {
        if (!isAdmin(authentication)) {
            return "redirect:/access-denied";
        }

        retainDraft(redirectAttributes, "message", subject, message, null, null);
        try {
            GymApplication application = gymApplicationService.getApplication(id);
            gymApplicationService.addAdminMessage(application, authHelper.getAuthenticatedUser(), subject, message);
            redirectAttributes.addFlashAttribute("gymApplicationSuccess", "The email provider accepted the message for the gym applicant.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("gymApplicationError", "Unable to complete this action. Check your details and email configuration. Your draft is retained.");
        }
        return "redirect:/admin/gym-applications/" + id;
    }

    @PostMapping("/admin/gym-applications/{id}/request-info")
    public String requestInfo(@PathVariable("id") Long id,
                              @RequestParam("subject") String subject,
                              @RequestParam("message") String message,
                              Authentication authentication,
                              RedirectAttributes redirectAttributes) {
        if (!isAdmin(authentication)) {
            return "redirect:/access-denied";
        }

        retainDraft(redirectAttributes, "request-info", subject, message, null, null);
        try {
            gymApplicationService.requestMoreInfo(id, authHelper.getAuthenticatedUser(), subject, message);
            redirectAttributes.addFlashAttribute("gymApplicationSuccess", "Requested more information from the gym applicant.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("gymApplicationError", "Unable to complete this action. Check your details and email configuration. Your draft is retained.");
        }
        return "redirect:/admin/gym-applications/" + id;
    }

    @PostMapping("/admin/gym-applications/{id}/decline")
    public String decline(@PathVariable("id") Long id,
                          @RequestParam("subject") String subject,
                          @RequestParam("message") String message,
                          @RequestParam(value = "reviewNotes", required = false) String reviewNotes,
                          Authentication authentication,
                          RedirectAttributes redirectAttributes) {
        if (!isAdmin(authentication)) {
            return "redirect:/access-denied";
        }

        retainDraft(redirectAttributes, "decline", subject, message, reviewNotes, null);
        try {
            gymApplicationService.decline(id, authHelper.getAuthenticatedUser(), subject, message, reviewNotes);
            redirectAttributes.addFlashAttribute("gymApplicationSuccess", "Gym application declined.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("gymApplicationError", "Unable to complete this action. Check your details and email configuration. Your draft is retained.");
        }
        return "redirect:/admin/gym-applications/" + id;
    }

    @PostMapping("/admin/gym-applications/{id}/approve")
    public String approve(@PathVariable("id") Long id,
                          @RequestParam(value = "welcomeMessage", required = false) String welcomeMessage,
                          Authentication authentication,
                          RedirectAttributes redirectAttributes) {
        if (!isAdmin(authentication)) {
            return "redirect:/access-denied";
        }

        retainDraft(redirectAttributes, "approve", null, null, null, welcomeMessage);
        try {
            gymApplicationService.approve(id, authHelper.getAuthenticatedUser(), welcomeMessage);
            redirectAttributes.addFlashAttribute("gymApplicationSuccess", "Gym application approved and account created.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("gymApplicationError", "Unable to complete this action. Check your details and email configuration. Your draft is retained.");
        }
        return "redirect:/admin/gym-applications/" + id;
    }

    @GetMapping("/signup/gym/application/{token}")
    public String viewApplicationPortal(@PathVariable("token") String token,
                                        Model model, HttpServletResponse response,
                                        RedirectAttributes redirectAttributes) {
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("Cache-Control", "no-store");
        try {
            GymApplication application = gymApplicationService.getApplicationByAccessToken(token);
            model.addAttribute("authPageLayout", true);
            model.addAttribute("compactTopContent", true);
            model.addAttribute("disableGlobalChatbot", true);
            model.addAttribute("gymApplication", application);
            model.addAttribute("messages", gymApplicationService.getMessages(application.getId()));
            return "public-views/auth/signup-gym-application";
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("gymApplicationError", ex.getMessage());
            return "redirect:/signup/gym";
        }
    }

    @PostMapping("/signup/gym/application/{token}/reply")
    public String replyToApplication(@PathVariable("token") String token,
                                     @RequestParam("message") String message,
                                     RedirectAttributes redirectAttributes) {
        try {
            gymApplicationService.addApplicantReply(token, message);
            redirectAttributes.addFlashAttribute("gymApplicationSuccess", "Your reply was added to the application.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("gymApplicationError", ex.getMessage());
        }
        return "redirect:/signup/gym/application/" + token;
    }

    private void retainDraft(RedirectAttributes flash, String action, String subject, String message, String notes, String welcome) {
        flash.addFlashAttribute("applicationDraftAction", action);
        flash.addFlashAttribute("applicationSubject", subject == null ? "" : subject);
        flash.addFlashAttribute("applicationMessage", message == null ? "" : message);
        flash.addFlashAttribute("applicationNotes", notes == null ? "" : notes);
        flash.addFlashAttribute("applicationWelcome", welcome == null ? "" : welcome);
    }

    private boolean isAdmin(Authentication authentication) {
        return SecurityUtils.hasRole(authentication, "PLATFORM_ADMIN")
            || SecurityUtils.hasRole(authentication, "SUPER_ADMIN");
    }
}
