package uk.ac.cf._5.group14.One_To_One.Support;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import uk.ac.cf._5.group14.One_To_One.Users.AuthHelper;
import uk.ac.cf._5.group14.One_To_One.Users.User;

@Controller
public class PublicSupportController {

    private final SupportRequestRepository supportRequestRepository;
    private final AuthHelper authHelper;

    public PublicSupportController(SupportRequestRepository supportRequestRepository,
                                   AuthHelper authHelper) {
        this.supportRequestRepository = supportRequestRepository;
        this.authHelper = authHelper;
    }

    @GetMapping("/support")
    public String support(Model model, @RequestParam(value = "subject", required = false) String subject) {
        if (!model.containsAttribute("feedbackSubject") && subject != null && !subject.isBlank()) {
            model.addAttribute("feedbackSubject", subject.trim().substring(0, Math.min(subject.trim().length(), 180)));
        }
        model.addAttribute("authUser", authHelper.getAuthenticatedUser());
        model.addAttribute("pageTitle", "Support");
        model.addAttribute("pageDescription", "Get practical help with One To One accounts, coaching, payments, access and technical questions.");
        return "shared-views/support/index";
    }

    @PostMapping("/support/feedback")
    public String submitFeedback(@RequestParam("requestType") String requestType,
                                 @RequestParam("subject") String subject,
                                 @RequestParam("message") String message,
                                 @RequestParam(value = "name", required = false) String name,
                                 @RequestParam(value = "email", required = false) String email,
                                 @RequestParam(value = "allowEmailReply", required = false) String allowEmailReply,
                                 RedirectAttributes redirectAttributes) {

        User user = authHelper.getAuthenticatedUser();
        String cleanSubject = subject == null ? "" : subject.trim();
        String cleanMessage = message == null ? "" : message.trim();
        String cleanName = name == null ? "" : name.trim();
        String cleanEmail = user != null ? user.getEmail() : email == null ? "" : email.trim();
        boolean canReply = allowEmailReply != null;

        // Keep failed form input in the redirect session, never in the URL.
        redirectAttributes.addFlashAttribute("feedbackSubject", cleanSubject.substring(0, Math.min(cleanSubject.length(), 180)));
        redirectAttributes.addFlashAttribute("feedbackMessage", cleanMessage.substring(0, Math.min(cleanMessage.length(), 5000)));
        redirectAttributes.addFlashAttribute("feedbackName", cleanName.substring(0, Math.min(cleanName.length(), 120)));
        redirectAttributes.addFlashAttribute("feedbackEmail", email == null ? "" : email.trim().substring(0, Math.min(email.trim().length(), 255)));
        redirectAttributes.addFlashAttribute("feedbackReply", canReply);
        redirectAttributes.addFlashAttribute("feedbackType", requestType);

        SupportRequestType type;
        try {
            type = SupportRequestType.valueOf(requestType.trim().toUpperCase());
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("feedbackError", "Choose a valid support type.");
            return "redirect:/support";
        }

        if (cleanSubject.isBlank() || cleanSubject.length() > 180) {
            redirectAttributes.addFlashAttribute("feedbackError", "Subject is required and must be under 180 characters.");
            return "redirect:/support";
        }
        if (cleanMessage.isBlank() || cleanMessage.length() > 5000) {
            redirectAttributes.addFlashAttribute("feedbackError", "Message is required and must be under 5000 characters.");
            return "redirect:/support";
        }

        if (cleanName.length() > 120 || (cleanEmail != null && cleanEmail.length() > 255)) {
            redirectAttributes.addFlashAttribute("feedbackError", "Name or email is too long.");
            return "redirect:/support";
        }
        if (canReply && (cleanEmail == null || cleanEmail.isBlank() || !cleanEmail.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$"))) {
            redirectAttributes.addFlashAttribute("feedbackError", "Add a valid email if you'd like a response.");
            return "redirect:/support";
        }

        if (type == SupportRequestType.QUERY && !canReply) {
            redirectAttributes.addFlashAttribute("feedbackError", "For queries, tick the box to allow an email response.");
            return "redirect:/support";
        }

        SupportRequest row = new SupportRequest();
        row.setRequestType(type);
        row.setSubject(cleanSubject);
        row.setMessage(cleanMessage);
        row.setAllowEmailReply(canReply);

        if (user != null) {
            row.setUser(user);
            row.setSubmitterName((user.getFirstName() + " " + user.getLastName()).trim());
            row.setSubmitterEmail(user.getEmail());
        } else {
            row.setSubmitterName(cleanName.isBlank() ? null : cleanName);
            row.setSubmitterEmail(cleanEmail.isBlank() ? null : cleanEmail);
        }

        supportRequestRepository.save(row);
        redirectAttributes.getFlashAttributes().clear();
        redirectAttributes.addFlashAttribute("feedbackSuccess", "Thanks. Your support request was submitted.");
        return "redirect:/support";
    }
}
