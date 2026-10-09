package uk.ac.cf._5.group14.One_To_One.Support;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import uk.ac.cf._5.group14.One_To_One.DevMode.DevModePageAccessMode;
import uk.ac.cf._5.group14.One_To_One.DevMode.DevModePageAccessService;
import uk.ac.cf._5.group14.One_To_One.GymApplications.GymApplicationService;
import uk.ac.cf._5.group14.One_To_One.Membership.EmailService;
import uk.ac.cf._5.group14.One_To_One.Security.SecurityUtils;
import uk.ac.cf._5.group14.One_To_One.Users.AuthHelper;
import uk.ac.cf._5.group14.One_To_One.Users.User;
import uk.ac.cf._5.group14.One_To_One.Users.UserRepository;
import uk.ac.cf._5.group14.One_To_One.Waitlist.WaitlistEmail;
import uk.ac.cf._5.group14.One_To_One.Waitlist.WaitlistEmailRepository;

import java.time.Instant;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import java.util.Locale;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Controller
public class AdminSupportController {

    private final SupportRequestRepository supportRequestRepository;
    private final WaitlistEmailRepository waitlistEmailRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final AuthHelper authHelper;
    private final DevModePageAccessService devModePageAccessService;
    private final GymApplicationService gymApplicationService;

    public AdminSupportController(SupportRequestRepository supportRequestRepository,
                                  WaitlistEmailRepository waitlistEmailRepository,
                                  UserRepository userRepository,
                                  EmailService emailService,
                                  AuthHelper authHelper,
                                  DevModePageAccessService devModePageAccessService,
                                  GymApplicationService gymApplicationService) {
        this.supportRequestRepository = supportRequestRepository;
        this.waitlistEmailRepository = waitlistEmailRepository;
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.authHelper = authHelper;
        this.devModePageAccessService = devModePageAccessService;
        this.gymApplicationService = gymApplicationService;
    }

    @GetMapping("/admin/dashboard")
    public String adminDashboard(Authentication authentication, Model model, HttpSession session) {
        if (!isAdmin(authentication)) {
            return "redirect:/access-denied";
        }

        List<SupportRequest> latestFeedback = supportRequestRepository.findAllByOrderBySubmittedAtDesc();
        List<WaitlistEmail> waitlist = waitlistEmailRepository.findAll();

        model.addAttribute("pageTitle", "Admin Dashboard");
        model.addAttribute("outreachPreview", session.getAttribute("adminOutreachPreview"));
        model.addAttribute("feedbackUnreadCount", supportRequestRepository.countByViewedFalse());
        model.addAttribute("feedbackOngoingCount", supportRequestRepository.countByStatus(SupportRequestStatus.ONGOING));
        model.addAttribute("feedbackNewCount", supportRequestRepository.countByStatus(SupportRequestStatus.NEW));
        model.addAttribute("latestFeedback", latestFeedback.stream().limit(8).toList());
        model.addAttribute("waitlistCount", waitlist.size());
        model.addAttribute("waitlistEntries", waitlist.stream().sorted((a, b) -> b.getSignedUpAt().compareTo(a.getSignedUpAt())).limit(25).toList());
        model.addAttribute("userCount", userRepository.count());
        model.addAttribute("gymApplicationCount", gymApplicationService.countOpenApplications());
        model.addAttribute("latestGymApplications", gymApplicationService.getAllApplications().stream().limit(6).toList());
        model.addAttribute("devPageSummary", devModePageAccessService.buildAdminSummary());
        model.addAttribute("devPageRows", devModePageAccessService.buildAdminRows());
        model.addAttribute("devPageModes", DevModePageAccessMode.values());
        return "admin-views/dashboard/admin-dashboard";
    }

    @PostMapping("/admin/dev-pages/{pageKey}")
    public String updateDevPageAccess(@PathVariable("pageKey") String pageKey,
                                      @RequestParam("mode") String mode,
                                      RedirectAttributes redirectAttributes,
                                      Authentication authentication) {
        if (!isAdmin(authentication)) {
            return "redirect:/access-denied";
        }

        if (!devModePageAccessService.hasPage(pageKey)) {
            redirectAttributes.addFlashAttribute("devPageAccessError", "Unknown Dev Hub page.");
            return "redirect:/admin/dashboard";
        }

        try {
            DevModePageAccessMode accessMode = DevModePageAccessMode.valueOf(mode);
            devModePageAccessService.updateMode(pageKey, accessMode);
            redirectAttributes.addFlashAttribute("devPageAccessSuccess", "Updated " + pageKey + " to " + accessMode.name().toLowerCase() + ".");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("devPageAccessError", "Invalid access mode selected.");
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("devPageAccessError", ex.getMessage());
        }

        return "redirect:/admin/dashboard";
    }

    @GetMapping("/admin/feedback")
    public String feedbackInbox(Authentication authentication, Model model,
                                @RequestParam(defaultValue = "") String search,
                                @RequestParam(defaultValue = "") String status, HttpServletResponse response) {
        if (!isAdmin(authentication)) {
            return "redirect:/access-denied";
        }

        model.addAttribute("pageTitle", "Admin Feedback Inbox");
        var feedback = supportRequestRepository.findAllByOrderBySubmittedAtDesc();
        String query = search.trim().toLowerCase(Locale.ROOT);
        model.addAttribute("feedbackItems", feedback.stream().filter(item -> status.isEmpty() || item.getStatus().name().equals(status))
            .filter(item -> query.isEmpty() || item.getSubject().toLowerCase(Locale.ROOT).contains(query)
                || (item.getSubmitterEmail() != null && item.getSubmitterEmail().toLowerCase(Locale.ROOT).contains(query))).toList());
        model.addAttribute("totalFeedback", feedback.size());
        model.addAttribute("search", search); model.addAttribute("selectedStatus", status);
        if (search.length() > 120 || (!status.isEmpty() && java.util.Arrays.stream(SupportRequestStatus.values()).noneMatch(value -> value.name().equals(status)))) {
            response.setStatus(400); model.addAttribute("adminFeedbackError", "Use a search of up to 120 characters and a valid case status.");
        }
        model.addAttribute("statuses", SupportRequestStatus.values());
        return "admin-views/admin/feedback";
    }

    @PostMapping("/admin/feedback/{id}/viewed")
    public String toggleViewed(@PathVariable("id") Long id,
                               RedirectAttributes redirectAttributes,
                               Authentication authentication) {
        if (!isAdmin(authentication)) {
            return "redirect:/access-denied";
        }

        SupportRequest req = supportRequestRepository.findById(id).orElse(null);
        if (req == null) {
            redirectAttributes.addFlashAttribute("adminFeedbackError", "Feedback item not found.");
            return "redirect:/admin/feedback";
        }

        req.setViewed(!req.isViewed());
        if (req.isViewed() && req.getStatus() == SupportRequestStatus.NEW) {
            req.setStatus(SupportRequestStatus.VIEWED);
        }
        supportRequestRepository.save(req);
        return "redirect:/admin/feedback";
    }

    @PostMapping("/admin/feedback/{id}/status")
    public String updateStatus(@PathVariable("id") Long id,
                               @RequestParam("status") String status,
                               RedirectAttributes redirectAttributes,
                               Authentication authentication) {
        if (!isAdmin(authentication)) {
            return "redirect:/access-denied";
        }

        SupportRequest req = supportRequestRepository.findById(id).orElse(null);
        if (req == null) {
            redirectAttributes.addFlashAttribute("adminFeedbackError", "Feedback item not found.");
            return "redirect:/admin/feedback";
        }

        try {
            req.setStatus(SupportRequestStatus.valueOf(status));
            req.setViewed(true);
            supportRequestRepository.save(req);
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("adminFeedbackError", "Invalid status selected.");
        }
        return "redirect:/admin/feedback";
    }

    @PostMapping("/admin/feedback/{id}/respond")
    public String respond(@PathVariable("id") Long id,
                          @RequestParam("response") String response,
                          RedirectAttributes redirectAttributes,
                          Authentication authentication) {
        if (!isAdmin(authentication)) {
            return "redirect:/access-denied";
        }

        SupportRequest req = supportRequestRepository.findById(id).orElse(null);
        if (req == null) {
            redirectAttributes.addFlashAttribute("adminFeedbackError", "Feedback item not found.");
            return "redirect:/admin/feedback";
        }

        String cleanResponse = response == null ? "" : response.trim();
        redirectAttributes.addFlashAttribute("responseDraftId", id);
        redirectAttributes.addFlashAttribute("responseDraft", response);
        if (cleanResponse.isBlank() || cleanResponse.length() > 5000) {
            redirectAttributes.addFlashAttribute("adminFeedbackError", "Enter a response between 1 and 5000 characters.");
            return "redirect:/admin/feedback";
        }

        if (!req.isAllowEmailReply() || req.getSubmitterEmail() == null || req.getSubmitterEmail().isBlank()) {
            redirectAttributes.addFlashAttribute("adminFeedbackError", "This requester did not opt in for email responses.");
            return "redirect:/admin/feedback";
        }

        String subject = "Update on your support request: " + req.getSubject();
        if (cleanResponse.equals(req.getAdminResponse()) && req.getRespondedAt() != null) {
            redirectAttributes.addFlashAttribute("adminFeedbackSuccess", "This response was already accepted by the email provider.");
            return "redirect:/admin/feedback";
        }
        try {
            emailService.sendAdminMessage(req.getSubmitterEmail(), subject, cleanResponse);
        } catch (RuntimeException deliveryFailure) {
            redirectAttributes.addFlashAttribute("adminFeedbackError", "The email provider did not confirm acceptance. Your draft is retained; the case remains unchanged.");
            return "redirect:/admin/feedback";
        }

        User admin = authHelper.getAuthenticatedUser();
        req.setAdminResponse(cleanResponse);
        req.setRespondedAt(Instant.now());
        req.setRespondedBy(admin);
        req.setViewed(true);
        req.setStatus(SupportRequestStatus.RESOLVED);
        supportRequestRepository.save(req);

        redirectAttributes.addFlashAttribute("adminFeedbackSuccess", "The email provider accepted the response for " + req.getSubmitterEmail());
        return "redirect:/admin/feedback";
    }

    @PostMapping("/admin/outreach/send")
    public String sendOutreach(@RequestParam(defaultValue = "SPECIFIC") String audience,
                               @RequestParam(required = false) String specificEmail,
                               @RequestParam(defaultValue = "") String subject,
                               @RequestParam(defaultValue = "") String message,
                               @RequestParam(required = false) String previewToken,
                               HttpSession session, RedirectAttributes flash, Authentication authentication) {
        if (!isAdmin(authentication)) return "redirect:/access-denied";
        if (previewToken != null) {
            Object pending = session.getAttribute("adminOutreachPreview");
            if (!(pending instanceof OutreachPreview draft) || !draft.token().equals(previewToken)
                || !draft.author().equals(authentication.getName()) || draft.createdAt().isBefore(Instant.now().minusSeconds(900))) {
                flash.addFlashAttribute("adminOutreachError", "This preview expired or was already submitted. Prepare a fresh preview.");
                return "redirect:/admin/dashboard";
            }
            // Consume before attempting delivery: refresh/replay cannot silently send the batch again.
            synchronized (session) {
                if (session.getAttribute("adminOutreachPreview") != draft) {
                    flash.addFlashAttribute("adminOutreachError", "This preview was already submitted.");
                    return "redirect:/admin/dashboard";
                }
                session.removeAttribute("adminOutreachPreview");
            }
            int accepted = 0;
            for (String recipient : draft.recipients()) {
                try { emailService.sendAdminMessage(recipient, draft.subject(), draft.message()); accepted++; }
                catch (RuntimeException failure) { /* Count failures without exposing provider internals. */ }
            }
            String outcome = "Email provider accepted " + accepted + " of " + draft.recipients().size() + " messages. Inbox delivery is not confirmed.";
            flash.addFlashAttribute(accepted == draft.recipients().size() ? "adminOutreachSuccess" : "adminOutreachError", outcome);
            return "redirect:/admin/dashboard";
        }
        String cleanSubject = subject.trim(), cleanMessage = message.trim();
        flash.addFlashAttribute("outreachAudience", audience); flash.addFlashAttribute("outreachEmail", specificEmail);
        flash.addFlashAttribute("outreachSubject", subject); flash.addFlashAttribute("outreachMessage", message);
        if (cleanSubject.isBlank() || cleanSubject.length() > 180 || cleanSubject.contains("\r") || cleanSubject.contains("\n")
            || cleanMessage.isBlank() || cleanMessage.length() > 5000) {
            flash.addFlashAttribute("adminOutreachError", "Use a subject of 1–180 characters and a message of 1–5000 characters.");
            return "redirect:/admin/dashboard";
        }
        Set<String> recipients = new LinkedHashSet<>();
        switch (audience) {
            case "ALL_USERS" -> userRepository.findAll().forEach(account -> addRecipient(recipients, account.getEmail()));
            case "WAITLIST" -> waitlistEmailRepository.findAll().stream().filter(WaitlistEmail::isConfirmed)
                .forEach(entry -> addRecipient(recipients, entry.getEmail()));
            case "SPECIFIC" -> addRecipient(recipients, specificEmail);
            default -> { flash.addFlashAttribute("adminOutreachError", "Invalid audience selected."); return "redirect:/admin/dashboard"; }
        }
        if (recipients.isEmpty() || recipients.size() > 500) {
            flash.addFlashAttribute("adminOutreachError", "Select 1–500 valid recipients. Waitlist outreach uses confirmed signups.");
            return "redirect:/admin/dashboard";
        }
        session.setAttribute("adminOutreachPreview", new OutreachPreview(UUID.randomUUID().toString(), authentication.getName(),
            audience, cleanSubject, cleanMessage, List.copyOf(recipients), Instant.now()));
        return "redirect:/admin/dashboard";
    }

    private void addRecipient(Set<String> recipients, String email) {
        String clean = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (clean.length() <= 255 && clean.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$")) recipients.add(clean);
    }

    public record OutreachPreview(String token, String author, String audience, String subject, String message,
                                  List<String> recipients, Instant createdAt) implements java.io.Serializable { }

    private boolean isAdmin(Authentication authentication) {
        return SecurityUtils.hasRole(authentication, "PLATFORM_ADMIN")
                || SecurityUtils.hasRole(authentication, "SUPER_ADMIN");
    }
}
