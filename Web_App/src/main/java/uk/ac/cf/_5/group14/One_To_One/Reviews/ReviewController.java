package uk.ac.cf._5.group14.One_To_One.Reviews;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import uk.ac.cf._5.group14.One_To_One.TrainerProfile.TrainerProfileService;
import uk.ac.cf._5.group14.One_To_One.Users.AuthHelper;
import uk.ac.cf._5.group14.One_To_One.Users.User;
import uk.ac.cf._5.group14.One_To_One.Users.UserRepository;
import uk.ac.cf._5.group14.One_To_One.Users.UserService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class ReviewController {

    private final AuthHelper authHelper;
    private final UserService userService;
    private final UserRepository userRepository;
    private final TrainerReviewService reviewService;
    private final ClientAssessmentService assessmentService;
    private final ReviewModerationService moderationService;
    private final TrainerProfileService profileService;
    private final uk.ac.cf._5.group14.One_To_One.TrainerClient.TrainerClientLinkRepository links;

    public ReviewController(AuthHelper authHelper,
                          UserService userService,
                          UserRepository userRepository,
                          TrainerReviewService reviewService,
                          ClientAssessmentService assessmentService,
                          ReviewModerationService moderationService,
                          TrainerProfileService profileService,
                          uk.ac.cf._5.group14.One_To_One.TrainerClient.TrainerClientLinkRepository links) {
        this.authHelper = authHelper;
        this.userService = userService;
        this.userRepository = userRepository;
        this.reviewService = reviewService;
        this.assessmentService = assessmentService;
        this.moderationService = moderationService;
        this.profileService = profileService;
        this.links = links;
    }

    private User currentUserOrThrow() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new org.springframework.security.access.AccessDeniedException("Not authenticated");
        }
        User user = userService.findByUsername(auth.getName());
        if (user == null) {
            throw new org.springframework.security.access.AccessDeniedException("User not found");
        }
        return user;
    }

    private User publicTrainerOr404(Long trainerId) {
        return userRepository.findById(trainerId)
                .filter(trainer -> trainer.getRole() == uk.ac.cf._5.group14.One_To_One.Users.Role.TRAINER
                        && trainer.isTrainerVerified() && trainer.isEnabled())
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
    }

    // ==================== TRAINER REVIEWS (PUBLIC) ====================

    /**
     * Show trainer public profile with reviews.
     */
    @GetMapping("/trainers/{trainerId}/profile")
    public ModelAndView trainerProfile(@PathVariable Long trainerId, Model model) {
        User trainer = userRepository.findById(trainerId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));

        if (trainer.getRole() != uk.ac.cf._5.group14.One_To_One.Users.Role.TRAINER || !trainer.isTrainerVerified() || !trainer.isEnabled()) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
        }

        ModelAndView mav = new ModelAndView("trainer-views/trainer/profile/view");
        for (String key : new String[]{"successMessage", "reviewSaved", "reviewReported", "reportError", "reportDraft"}) {
            if (model.containsAttribute(key)) mav.addObject(key, model.getAttribute(key));
        }
        mav.addObject("pageTitle", trainer.getFirstName() + " " + trainer.getLastName());
        mav.addObject("trainer", trainer);

        // Add trainer profile with social links
        var profile = profileService.getProfileByUserId(trainerId).orElse(null);
        mav.addObject("trainerProfile", profile);
        mav.addObject("socialLinks", profileService.getVisibleSocialLinks(profile));
        mav.addObject("canRequestTrainer", false);
        mav.addObject("hasPendingRequest", false);
        mav.addObject("isCurrentTrainer", false);

        List<TrainerReview> reviews = reviewService.getVisibleReviewsForTrainer(trainerId);
        mav.addObject("reviews", reviews);
        mav.addObject("averageRating", reviewService.getAverageRating(trainerId));
        mav.addObject("reviewCount", reviewService.getReviewCount(trainerId));

        // Add client names
        Map<Long, User> clientsById = new HashMap<>();
        for (TrainerReview review : reviews) {
            userRepository.findById(review.getClientId())
                    .ifPresent(client -> clientsById.put(client.getId(), client));
        }
        mav.addObject("clientsById", clientsById);

        // Check if current user can review, and whether they are the owner
        try {
            User currentUser = currentUserOrThrow();
            boolean canReview = reviewService.canClientReviewTrainer(currentUser.getId(), trainerId);
            mav.addObject("canReview", canReview);
            mav.addObject("isOwner", currentUser.getId().equals(trainerId));
            if (currentUser.getId().equals(trainerId) && profile != null) {
                String code = TrainerProfileService.normalizeTrainerCode(profile.getTrainerCode());
                if (code != null) mav.addObject("formattedTrainerCode", code.substring(0,4) + "-" + code.substring(4,8) + "-" + code.substring(8));
            }
            if (currentUser.getRole() == uk.ac.cf._5.group14.One_To_One.Users.Role.CLIENT) {
                var active = links.findActiveByClientId(currentUser.getId());
                boolean pending = links.existsByTrainerUserIdAndClientUserIdAndStatus(trainerId, currentUser.getId(),
                        uk.ac.cf._5.group14.One_To_One.TrainerClient.TrainerClientLinkStatus.REQUESTED);
                mav.addObject("hasPendingRequest", pending);
                mav.addObject("canRequestTrainer", active.isEmpty() && !pending);
                mav.addObject("isCurrentTrainer", active.map(link -> trainerId.equals(link.getTrainerUserId())).orElse(false));
            }
        } catch (Exception e) {
            mav.addObject("canReview", false);
            mav.addObject("isOwner", false);
        }

        return mav;
    }

    /**
     * Show review creation form.
     */
    @GetMapping("/trainers/{trainerId}/review")
    public ModelAndView showReviewForm(@PathVariable Long trainerId, Model model) {
        User client = currentUserOrThrow();
        User trainer = publicTrainerOr404(trainerId);

        if (!reviewService.canClientReviewTrainer(client.getId(), trainerId)) {
            return new ModelAndView("redirect:/trainers/" + trainerId + "/profile?error=cannot_review");
        }

        ModelAndView mav = new ModelAndView("shared-views/review/form");
        mav.addObject("pageTitle", "Review " + trainer.getFirstName() + " " + trainer.getLastName());
        mav.addObject("trainer", trainer);
        for (String key : new String[]{"reviewError", "reviewDraft", "reviewFieldErrors"}) {
            if (model.containsAttribute(key)) mav.addObject(key, model.getAttribute(key));
        }
        return mav;
    }

    /**
     * Submit a new review.
     */
    @PostMapping("/trainers/{trainerId}/review")
    public ModelAndView submitReview(@PathVariable Long trainerId,
                                    @RequestParam(required = false) Integer stars,
                                    @RequestParam(required = false) List<String> tags,
                                    @RequestParam(required = false) String comment,
                                    RedirectAttributes redirectAttributes) {
        User client = currentUserOrThrow();
        User trainer = publicTrainerOr404(trainerId);

        try {
            reviewService.createReview(client.getId(), trainerId, stars, tags == null ? null : String.join(",", tags), comment);
            redirectAttributes.addFlashAttribute("reviewSaved", true);
            return new ModelAndView("redirect:/trainers/" + trainerId + "/profile");
        } catch (TrainerReviewException ex) {
            ModelAndView blocked = new ModelAndView("shared-views/review/form");
            blocked.setStatus(org.springframework.http.HttpStatus.CONFLICT);
            blocked.addObject("trainer", trainer);
            blocked.addObject("reviewBlocked", reviewErrorParam(ex.getReason()));
            blocked.addObject("reviewDraft", reviewDraft(stars, tags, comment));
            return blocked;
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("reviewError", true);
            redirectAttributes.addFlashAttribute("reviewFieldErrors", ex instanceof ReviewValidationException invalid
                    ? invalid.getFields() : List.of());
            redirectAttributes.addFlashAttribute("reviewDraft", reviewDraft(stars, tags, comment));
            return new ModelAndView("redirect:/trainers/" + trainerId + "/review");
        }
    }

    public record ReviewDraft(Integer stars, List<String> tags, String comment) implements java.io.Serializable { }

    private ReviewDraft reviewDraft(Integer stars, List<String> tags, String comment) {
        return new ReviewDraft(stars, tags == null ? List.of() : tags.stream().limit(20).toList(),
                comment == null ? "" : comment.substring(0, Math.min(comment.length(), 12000)));
    }

    /**
     * Report a review.
     */
    @PostMapping("/reviews/{reviewId}/report")
    public ModelAndView reportReview(@PathVariable Long reviewId,
                                    @RequestParam String reason,
                                    @RequestParam Long trainerId,
                                    RedirectAttributes redirectAttributes) {
        User currentUser = currentUserOrThrow();

        try {
            moderationService.reportReview(reviewId, currentUser.getId(), reason);
            redirectAttributes.addFlashAttribute("reviewReported", true);
        } catch (IllegalStateException | IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("reportError", true);
            redirectAttributes.addFlashAttribute("reportDraft", new ReportDraft(reviewId,
                    reason == null ? "" : reason.substring(0, Math.min(reason.length(), 2000))));
        }

        return new ModelAndView("redirect:/trainers/" + trainerId + "/profile");
    }

    public record ReportDraft(Long reviewId, String reason) implements java.io.Serializable { }

    // ==================== CLIENT ASSESSMENTS (TRAINER-ONLY) ====================

    /**
     * Show assessment form for a client.
     */
    @GetMapping("/trainer/clients/{clientId}/assessment")
    public ModelAndView showAssessmentForm(@PathVariable Long clientId, Model model) {
        User trainer = currentUserOrThrow();
        var assessment = assessmentService.getAssessment(trainer.getId(), clientId);
        User client = userRepository.findById(clientId)
                .orElseThrow(() -> new IllegalArgumentException("Client not found"));

        ModelAndView mav = new ModelAndView("client-views/client/assessment-form");
        mav.addObject("pageTitle", "Assess " + client.getFirstName() + " " + client.getLastName());
        mav.addObject("client", client);

        assessment.ifPresent(value -> mav.addObject("assessment", value));
        for (String key : new String[]{"assessmentSaved", "assessmentError", "assessmentDraft"}) {
            if (model.containsAttribute(key)) mav.addObject(key, model.getAttribute(key));
        }

        return mav;
    }

    /**
     * Save/update client assessment.
     */
    @PostMapping("/trainer/clients/{clientId}/assessment")
    public ModelAndView saveAssessment(@PathVariable Long clientId,
                                      @RequestParam(required = false) Integer reliabilityScore,
                                      @RequestParam(required = false) Integer communicationScore,
                                      @RequestParam(required = false) String privateNotes,
                                      RedirectAttributes redirectAttributes) {
        User trainer = currentUserOrThrow();

        try {
            assessmentService.saveAssessment(trainer.getId(), clientId, 
                    reliabilityScore, communicationScore, privateNotes);
            redirectAttributes.addFlashAttribute("assessmentSaved", true);
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("assessmentError", true);
            redirectAttributes.addFlashAttribute("assessmentDraft", new AssessmentDraft(reliabilityScore,
                    communicationScore, privateNotes == null ? "" : privateNotes.substring(0, Math.min(privateNotes.length(), 12000))));
        }
        return new ModelAndView("redirect:/trainer/clients/" + clientId + "/assessment");
    }

    public record AssessmentDraft(Integer reliabilityScore, Integer communicationScore, String privateNotes)
            implements java.io.Serializable { }

    /**
     * View all assessments by trainer.
     */
    @GetMapping("/trainer/assessments")
    public ModelAndView viewAssessments() {
        User trainer = currentUserOrThrow();

        ModelAndView mav = new ModelAndView("trainer-views/trainer/assessments");
        mav.addObject("pageTitle", "Client Assessments");

        List<ClientAssessment> assessments = assessmentService.getAllAssessmentsByTrainer(trainer.getId());
        mav.addObject("assessments", assessments);

        // Add client names
        Map<Long, User> clientsById = new HashMap<>();
        for (ClientAssessment assessment : assessments) {
            userRepository.findById(assessment.getClientId())
                    .ifPresent(client -> clientsById.put(client.getId(), client));
        }
        mav.addObject("clientsById", clientsById);

        return mav;
    }

    // ==================== MODERATION (ADMIN-ONLY) ====================

    /**
     * Show moderation dashboard.
     */
    @GetMapping("/admin/moderation")
    public ModelAndView moderationDashboard(Model model) {
        User admin = currentUserOrThrow();

        ModelAndView mav = new ModelAndView("admin-views/reviews/moderation");
        for (String key : new String[]{"moderationSaved", "moderationError", "moderationDraft"}) {
            if (model.containsAttribute(key)) mav.addObject(key, model.getAttribute(key));
        }
        mav.addObject("pageTitle", "Review Moderation");

        List<ReviewModeration> pending = moderationService.getPendingModerations(admin.getId());
        mav.addObject("pendingModerations", pending);

        // Add review and user details
        Map<Long, TrainerReview> reviewsById = new HashMap<>();
        Map<Long, User> usersById = new HashMap<>();
        
        for (TrainerReview review : moderationService.getReviewsForModeration(
                pending.stream().map(ReviewModeration::getReviewId).distinct().toList(), admin.getId())) {
            reviewsById.put(review.getId(), review);
        }
        for (ReviewModeration moderation : pending) {
            userRepository.findById(moderation.getReportedByUserId())
                    .ifPresent(user -> usersById.put(user.getId(), user));
        }
        mav.addObject("reviewsById", reviewsById);
        mav.addObject("usersById", usersById);
        return mav;
    }

    /**
     * Resolve moderation and hide review.
     */
    @PostMapping("/admin/moderation/{moderationId}/hide")
    public ModelAndView hideModerationReview(@PathVariable Long moderationId,
                                            @RequestParam String notes,
                                            RedirectAttributes redirectAttributes) {
        User admin = currentUserOrThrow();

        try {
            moderationService.resolveModerationAndHideReview(moderationId, admin.getId(), notes);
            redirectAttributes.addFlashAttribute("moderationSaved", true);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            retainModerationDraft(moderationId, notes, redirectAttributes);
        }

        return new ModelAndView("redirect:/admin/moderation");
    }

    /**
     * Resolve moderation but keep review visible.
     */
    @PostMapping("/admin/moderation/{moderationId}/keep")
    public ModelAndView keepModerationReview(@PathVariable Long moderationId,
                                            @RequestParam String notes,
                                            RedirectAttributes redirectAttributes) {
        User admin = currentUserOrThrow();

        try {
            moderationService.resolveModerationKeepVisible(moderationId, admin.getId(), notes);
            redirectAttributes.addFlashAttribute("moderationSaved", true);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            retainModerationDraft(moderationId, notes, redirectAttributes);
        }

        return new ModelAndView("redirect:/admin/moderation");
    }

    private String reviewErrorParam(TrainerReviewException.Reason reason) {
        return switch (reason) {
            case REVIEW_ALREADY_EXISTS -> "already_reviewed";
            case LINK_NOT_ELIGIBLE -> "not_eligible";
            case USER_NOT_CLIENT -> "invalid";
        };
    }

    private void retainModerationDraft(Long id, String notes, RedirectAttributes attributes) {
        attributes.addFlashAttribute("moderationError", true);
        attributes.addFlashAttribute("moderationDraft", new ModerationDraft(id,
                notes == null ? "" : notes.substring(0, Math.min(notes.length(), 12000))));
    }

    public record ModerationDraft(Long id, String notes) implements java.io.Serializable { }
}
