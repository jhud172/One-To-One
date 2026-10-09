package uk.ac.cf._5.group14.One_To_One.Verification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import uk.ac.cf._5.group14.One_To_One.Users.User;
import uk.ac.cf._5.group14.One_To_One.Users.UserRepository;
import uk.ac.cf._5.group14.One_To_One.Users.UserService;
import uk.ac.cf._5.group14.One_To_One.GymProfile.GymTrainerRosterService;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@Slf4j
@Controller
@RequestMapping("/gym/admin/trainers")
@RequiredArgsConstructor
public class GymAdminTrainerController {
    
    private final TrainerVerificationService verificationService;
    private final UserRepository userRepository;
    private final UserService userService;
    private final GymTrainerInvitationService invitations;
    private final GymTrainerRosterService roster;
    private final org.springframework.context.MessageSource messages;

    @InitBinder("newRequest")
    void bindInvitation(WebDataBinder binder) {
        binder.setAllowedFields("firstName", "lastName", "email", "username", "temporaryPassword", "notes");
    }

    @InitBinder("updateNotesForm")
    void bindNotes(WebDataBinder binder) { binder.setAllowedFields("notes"); }

    
    /**
     * List trainers and verification requests for the gym
     */
    @GetMapping
    public String listTrainers(@AuthenticationPrincipal UserDetails userDetails, Model model,
            @RequestParam(defaultValue = "") String search, @RequestParam(defaultValue = "") String review,
            @RequestParam(defaultValue = "false") boolean verifiedOnly, @RequestParam(defaultValue = "1") int page,
            HttpServletResponse response) {
        User admin = getUserFromDetails(userDetails);
        
        requireOwnedGym(admin);
        boolean invalid = search.length() > 120 || !GymTrainerRosterService.REVIEW_FILTERS.contains(review) || page < 1;
        populateTrainerModel(admin, model, invalid ? "" : search, invalid ? "" : review, verifiedOnly, Math.max(1, page));
        if (invalid) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            model.addAttribute("rosterFilterInvalid", true);
            model.addAttribute("search", search.substring(0, Math.min(121, search.length())));
            model.addAttribute("review", review.substring(0, Math.min(31, review.length())));
            model.addAttribute("trainers", List.of());
            model.addAttribute("rosterPage", org.springframework.data.domain.Page.empty());
            model.addAttribute("firstTrainer", 0L); model.addAttribute("lastTrainer", 0L);
        }
        
        return "gym-views/gym-admin/trainers";
    }
    
    /**
     * Create a new trainer and submit for verification
     */
    @PostMapping("/create")
    public String createTrainer(
        @AuthenticationPrincipal UserDetails userDetails,
        @Valid @ModelAttribute("newRequest") TrainerVerificationRequestForm form,
        BindingResult result,
        HttpServletResponse response,
        RedirectAttributes redirectAttributes,
        Model model,
        @RequestParam(defaultValue = "") String search, @RequestParam(defaultValue = "") String review,
        @RequestParam(defaultValue = "false") boolean verifiedOnly, @RequestParam(defaultValue = "1") int page
    ) {
        User admin = getUserFromDetails(userDetails);
        requireOwnedGym(admin);
        
        if (form.getTemporaryPassword() != null && form.getTemporaryPassword().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            result.rejectValue("temporaryPassword", "Length", "Password exceeds the supported length");
        }
        if (userService.emailExists(form.getEmail())) {
            result.rejectValue("email", "Duplicate", "Email already registered");
        }
        if (userService.usernameExists(form.getUsername())) {
            result.rejectValue("username", "Duplicate", "Username already taken");
        }
        if (result.hasErrors()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            populateTrainerModel(admin, model, search, review, verifiedOnly, page);
            model.addAttribute("errorMessage", message("ui.00958"));
            return "gym-views/gym-admin/trainers";
        }

        try {
            invitations.create(admin.getId(), form);

            redirectAttributes.addFlashAttribute("successMessage",
                message("ui.gymRoster.saved"));
        } catch (Exception e) {
            log.error("Error creating trainer verification request", e);
            populateTrainerModel(admin, model, search, review, verifiedOnly, page);
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            model.addAttribute("errorMessage", message("ui.gymRoster.saveFailed"));
            return "gym-views/gym-admin/trainers";
        }
        
        return trainerRedirect(search, review, verifiedOnly, page);
    }
    
    /**
     * Update notes for a verification request that needs more info
     */
    @PostMapping("/{id}/update-notes")
    public String updateNotes(
        @PathVariable Long id,
        @Valid @ModelAttribute("updateNotesForm") UpdateTrainerNotesForm form,
        BindingResult result,
        HttpServletResponse response,
        @AuthenticationPrincipal UserDetails userDetails,
        RedirectAttributes redirectAttributes,
        Model model,
        @RequestParam(defaultValue = "") String search, @RequestParam(defaultValue = "") String review,
        @RequestParam(defaultValue = "false") boolean verifiedOnly, @RequestParam(defaultValue = "1") int page
    ) {
        User admin = getUserFromDetails(userDetails);
        requireOwnedGym(admin);
        
        TrainerVerificationRequest request;
        try {
            request = verificationService.getRequestForGym(id, admin.getGymId());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        if (result.hasErrors()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            populateTrainerModel(admin, model, search, review, verifiedOnly, page);
            model.addAttribute("updateNotesRequestId", id);
            model.addAttribute("updateNotesTrainerName", resolveTrainerName(request.getTrainerUserId()));
            model.addAttribute("updateNotesNotes", form.getNotes());
            return "gym-views/gym-admin/trainers";
        }

        try {
            verificationService.updateTrainerNotesForGym(id, admin.getGymId(), form.getNotes(), admin.getId());
            redirectAttributes.addFlashAttribute("successMessage", 
                message("ui.gymRoster.saved"));
        } catch (Exception e) {
            log.error("Error updating trainer notes", e);
            populateTrainerModel(admin, model, search, review, verifiedOnly, page);
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            model.addAttribute("errorMessage", message("ui.gymRoster.saveFailed"));
            model.addAttribute("updateNotesRequestId", id);
            model.addAttribute("updateNotesTrainerName", resolveTrainerName(request.getTrainerUserId()));
            model.addAttribute("updateNotesNotes", form.getNotes());
            return "gym-views/gym-admin/trainers";
        }
        
        return trainerRedirect(search, review, verifiedOnly, page);
    }
    
    private User getUserFromDetails(UserDetails userDetails) {
        if (userDetails == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return userRepository.findByUsername(userDetails.getUsername())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private void requireOwnedGym(User admin) {
        if (roster.ownedGym(admin).isEmpty()) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }

    private void populateTrainerModel(User admin, Model model, String search, String review, boolean verifiedOnly, int page) {
        search = search == null || search.length() > 120 ? "" : search;
        review = review == null || !GymTrainerRosterService.REVIEW_FILTERS.contains(review) ? "" : review;
        var result = roster.search(admin, search, review, verifiedOnly, Math.max(1, page));
        model.addAttribute("search", search); model.addAttribute("review", review); model.addAttribute("verifiedOnly", verifiedOnly);
        model.addAttribute("rosterFilterInvalid", false);
        model.addAttribute("rosterPage", result);
        model.addAttribute("firstTrainer", result.isEmpty() ? 0L : result.getPageable().getOffset() + 1);
        model.addAttribute("lastTrainer", result.isEmpty() ? 0L : result.getPageable().getOffset() + result.getNumberOfElements());
        model.addAttribute("trainerCount", userRepository.countCurrentGymTrainers(admin.getGymId(), false));
        model.addAttribute("trainers", result.getContent());
        model.addAttribute("latestRequests", roster.latestReviews(admin, result.getContent()));
        model.addAttribute("reviewFilters", List.of("NEEDS_INFO", "PENDING", "APPROVED", "REJECTED", "NOT_SUBMITTED"));
        model.addAttribute("reviewLabels", java.util.Map.of("PENDING", "ui.00168", "NEEDS_INFO", "ui.00169",
            "APPROVED", "ui.00170", "REJECTED", "ui.00171", "NOT_SUBMITTED", "ui.01064"));
        if (!model.containsAttribute("newRequest")) {
            model.addAttribute("newRequest", new TrainerVerificationRequestForm());
        }
        if (!model.containsAttribute("updateNotesForm")) {
            model.addAttribute("updateNotesForm", new UpdateTrainerNotesForm());
        }
    }

    private String trainerRedirect(String search, String review, boolean verifiedOnly, int page) {
        if (search.isEmpty() && review.isEmpty() && !verifiedOnly && page == 1) return "redirect:/gym/admin/trainers";
        if (search.length() > 120 || !GymTrainerRosterService.REVIEW_FILTERS.contains(review) || page < 1) return "redirect:/gym/admin/trainers";
        return "redirect:/gym/admin/trainers?search=" + java.net.URLEncoder.encode(search, java.nio.charset.StandardCharsets.UTF_8)
            + "&review=" + review + "&verifiedOnly=" + verifiedOnly + "&page=" + page + "#trainer-results";
    }

    private String message(String key) {
        return messages.getMessage(key, null, org.springframework.context.i18n.LocaleContextHolder.getLocale());
    }

    private String resolveTrainerName(Long trainerUserId) {
        return userRepository.findById(trainerUserId)
            .map(user -> user.getFirstName() + " " + user.getLastName())
            .orElse("Trainer");
    }
}
