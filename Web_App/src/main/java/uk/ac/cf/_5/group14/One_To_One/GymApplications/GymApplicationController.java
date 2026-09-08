package uk.ac.cf._5.group14.One_To_One.GymApplications;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
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
    public String listApplications(Authentication authentication, Model model) {
        if (!isAdmin(authentication)) {
            return "redirect:/access-denied";
        }

        model.addAttribute("pageTitle", "Gym Applications");
        model.addAttribute("applications", gymApplicationService.getAllApplications());
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

        try {
            GymApplication application = gymApplicationService.getApplication(id);
            gymApplicationService.addAdminMessage(application, authHelper.getAuthenticatedUser(), subject, message);
            redirectAttributes.addFlashAttribute("gymApplicationSuccess", "Message sent to the gym applicant.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("gymApplicationError", ex.getMessage());
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

        try {
            gymApplicationService.requestMoreInfo(id, authHelper.getAuthenticatedUser(), subject, message);
            redirectAttributes.addFlashAttribute("gymApplicationSuccess", "Requested more information from the gym applicant.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("gymApplicationError", ex.getMessage());
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

        try {
            gymApplicationService.decline(id, authHelper.getAuthenticatedUser(), subject, message, reviewNotes);
            redirectAttributes.addFlashAttribute("gymApplicationSuccess", "Gym application declined.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("gymApplicationError", ex.getMessage());
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

        try {
            gymApplicationService.approve(id, authHelper.getAuthenticatedUser(), welcomeMessage);
            redirectAttributes.addFlashAttribute("gymApplicationSuccess", "Gym application approved and account created.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("gymApplicationError", ex.getMessage());
        }
        return "redirect:/admin/gym-applications/" + id;
    }

    @GetMapping("/signup/gym/application/{token}")
    public String viewApplicationPortal(@PathVariable("token") String token,
                                        Model model,
                                        RedirectAttributes redirectAttributes) {
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

    private boolean isAdmin(Authentication authentication) {
        return SecurityUtils.hasRole(authentication, "PLATFORM_ADMIN")
            || SecurityUtils.hasRole(authentication, "SUPER_ADMIN");
    }
}
