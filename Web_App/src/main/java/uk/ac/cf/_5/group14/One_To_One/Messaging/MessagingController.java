package uk.ac.cf._5.group14.One_To_One.Messaging;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import uk.ac.cf._5.group14.One_To_One.Users.AuthHelper;
import uk.ac.cf._5.group14.One_To_One.Users.User;
import uk.ac.cf._5.group14.One_To_One.Users.UserService;

@Controller
public class MessagingController {

    private final AuthHelper authHelper;
    private final UserService userService;
    private final MessagingService messagingService;
    private final uk.ac.cf._5.group14.One_To_One.Security.AccessGuard accessGuard;

    public MessagingController(AuthHelper authHelper,
                               UserService userService,
                               MessagingService messagingService,
                               uk.ac.cf._5.group14.One_To_One.Security.AccessGuard accessGuard) {
        this.authHelper = authHelper;
        this.userService = userService;
        this.messagingService = messagingService;
        this.accessGuard = accessGuard;
    }

    private User currentUserOrThrow() {
        User sessionUser = authHelper.getAuthenticatedUser();
        if (sessionUser != null) {
            return sessionUser;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AccessDeniedException("Not authenticated");
        }
        User user = userService.findByUsername(auth.getName());
        if (user == null) {
            throw new AccessDeniedException("User not found");
        }
        return user;
    }

    @GetMapping("/trainer/messages")
    public String trainerInbox() {
        currentUserOrThrow();
        return "redirect:/inbox";
    }

    @GetMapping("/client/messages")
    public String clientInbox() {
        currentUserOrThrow();
        return "redirect:/inbox";
    }

    @GetMapping("/messages/{threadId}")
    public String thread(@PathVariable Long threadId) {
        User user = currentUserOrThrow();

        try {
            messagingService.getThreadForUser(threadId, user.getId());
            return "redirect:/inbox/" + threadId;
        } catch (AccessDeniedException ex) {
            return "redirect:/access-denied";
        } catch (IllegalArgumentException missingThread) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
        }
    }

    @PostMapping("/messages/{threadId}/send")
    public String send(@PathVariable Long threadId,
                       @RequestParam("type") MessageType type,
                       @RequestParam(value = "bodyText", required = false) String bodyText,
                       @RequestParam(value = "checkinMood", required = false) String checkinMood,
                       @RequestParam(value = "checkinEnergy", required = false) String checkinEnergy,
                       @RequestParam(value = "checkinNotes", required = false) String checkinNotes,
                       RedirectAttributes redirectAttributes) {
        User sender = currentUserOrThrow();

        try {
            MessageThread thread = messagingService.getThreadForUser(threadId, sender.getId());
            if (sender.getId().equals(thread.getTrainerId())) {
                accessGuard.requireTrainerAccessClient(sender.getId(), thread.getClientId());
            } else {
                accessGuard.requireClientAccessTrainer(sender.getId(), thread.getTrainerId());
            }
            String finalBody = bodyText;
            if (type == MessageType.CHECKIN) {
                Integer mood = rating(checkinMood), energy = rating(checkinEnergy);
                String notes = checkinNotes == null ? "" : checkinNotes.trim();
                if (notes.length() > 3000 || (mood == null && energy == null && notes.isBlank())) {
                    throw new IllegalArgumentException("Enter a check-in with ratings from 1 to 10 and notes up to 3,000 characters");
                }
                finalBody = "Check-in" + "\nMood: " + (mood == null ? "-" : mood) + "/10"
                        + "\nEnergy: " + (energy == null ? "-" : energy) + "/10"
                        + (notes.isBlank() ? "" : "\nNotes: " + notes);
            }

            messagingService.sendMessage(threadId, sender.getId(), type, finalBody);
        } catch (AccessDeniedException ex) {
            return "redirect:/access-denied";
        } catch (MessagingException ex) {
            retainDraft(redirectAttributes, type, bodyText, checkinMood, checkinEnergy, checkinNotes);
            redirectAttributes.addFlashAttribute("inboxSendError", ex.getReason().name());
            return "redirect:/inbox/" + threadId;
        } catch (IllegalArgumentException ex) {
            retainDraft(redirectAttributes, type, bodyText, checkinMood, checkinEnergy, checkinNotes);
            redirectAttributes.addFlashAttribute("inboxSendError", "INVALID_MESSAGE");
            return "redirect:/inbox/" + threadId;
        }

        redirectAttributes.addFlashAttribute("inboxSent", true);
        return "redirect:/inbox/" + threadId;
    }

    private static Integer rating(String value) {
        if (value == null || value.isBlank()) return null;
        int rating = Integer.parseInt(value);
        if (rating < 1 || rating > 10) throw new IllegalArgumentException("Check-in rating must be from 1 to 10");
        return rating;
    }

    private static void retainDraft(RedirectAttributes redirect, MessageType type, String body,
                                    String mood, String energy, String notes) {
        if (type == MessageType.CHECKIN) {
            redirect.addFlashAttribute("inboxCheckinDraft", true);
            redirect.addFlashAttribute("inboxCheckinMood", mood);
            redirect.addFlashAttribute("inboxCheckinEnergy", energy);
            redirect.addFlashAttribute("inboxCheckinNotes", notes);
        } else {
            redirect.addFlashAttribute("inboxDraftBody", body);
        }
    }
}
