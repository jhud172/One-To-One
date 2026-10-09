package uk.ac.cf._5.group14.One_To_One.Verification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import uk.ac.cf._5.group14.One_To_One.Users.User;
import uk.ac.cf._5.group14.One_To_One.Users.UserRepository;

import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.Locale;

@Slf4j
@Controller
@RequestMapping("/super-admin/verification")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'SUPER_ADMIN')")
@RequiredArgsConstructor
public class SuperAdminVerificationController {
    
    private final TrainerVerificationService verificationService;
    private final UserRepository userRepository;
    private final VerificationEvidenceService evidence;
    
    /**
     * View the verification queue (pending requests)
     */
    @GetMapping("/queue")
    public String viewQueue(Model model, @RequestParam(defaultValue = "") String search,
                            @RequestParam(defaultValue = "") String status, HttpServletResponse response,
                            @AuthenticationPrincipal UserDetails principal) {
        getUserFromDetails(principal);
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
        List<TrainerVerificationRequest> verificationRequests = verificationService.getQueueRequests();
        long pendingCount = verificationService.countByStatus(VerificationStatus.PENDING);
        long needsInfoCount = verificationService.countByStatus(VerificationStatus.NEEDS_INFO);
        long approvedCount = verificationService.countByStatus(VerificationStatus.APPROVED);
        long rejectedCount = verificationService.countByStatus(VerificationStatus.REJECTED);
        
        java.util.Map<Long, User> trainers = new java.util.HashMap<>();
        userRepository.findAllById(verificationRequests.stream().map(TrainerVerificationRequest::getTrainerUserId).distinct().toList())
            .forEach(trainer -> trainers.put(trainer.getId(), trainer));
        String query = search.trim().toLowerCase(Locale.ROOT);
        model.addAttribute("totalRequests", verificationRequests.size());
        model.addAttribute("verificationRequests", verificationRequests.stream()
            .filter(request -> status.isEmpty() || request.getStatus().name().equals(status))
            .filter(request -> query.isEmpty() || (trainers.containsKey(request.getTrainerUserId())
                && (trainers.get(request.getTrainerUserId()).getFullName() + " " + trainers.get(request.getTrainerUserId()).getUsername()).toLowerCase(Locale.ROOT).contains(query))).toList());
        model.addAttribute("search", search); model.addAttribute("selectedStatus", status);
        if (search.length() > 120 || (!status.isEmpty() && !"PENDING".equals(status) && !"NEEDS_INFO".equals(status))) {
            response.setStatus(400); model.addAttribute("errorMessage", "Use a search of up to 120 characters and an open verification status.");
        }
        model.addAttribute("trainers", trainers);
        model.addAttribute("pendingCount", pendingCount);
        model.addAttribute("needsInfoCount", needsInfoCount);
        model.addAttribute("approvedCount", approvedCount);
        model.addAttribute("rejectedCount", rejectedCount);
        return "admin-views/super-admin/verification-queue";
    }
    
    /**
     * View details of a specific verification request
     */
    @GetMapping("/{id}")
    public String viewRequest(@PathVariable Long id, Model model, HttpServletResponse response,
                              @AuthenticationPrincipal UserDetails principal) {
        getUserFromDetails(principal);
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
        TrainerVerificationRequest request;
        try {
            request = verificationService.getRequestById(id);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        
        User trainer = userRepository.findById(request.getTrainerUserId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        User reviewer = null;
        if (request.getReviewedByUserId() != null) {
            reviewer = userRepository.findById(request.getReviewedByUserId()).orElse(null);
        }
        
        model.addAttribute("request", request);
        model.addAttribute("reviewEvents", verificationService.getHistoryForRequests(List.of(request)));
        model.addAttribute("reviewDocuments", evidence.summaries(List.of(request)));
        model.addAttribute("reviewClosed", request.getStatus() == VerificationStatus.APPROVED || request.getStatus() == VerificationStatus.REJECTED);
        model.addAttribute("trainer", trainer);
        model.addAttribute("reviewer", reviewer);

        return "admin-views/super-admin/verification-detail";
    }
    
    /**
     * Approve a trainer verification request
     */
    @PostMapping("/{id}/approve")
    public String approveRequest(
        @PathVariable Long id,
        @RequestParam(required = false) String adminNotes,
        @RequestParam(defaultValue = "false") boolean qualificationsChecked,
        @AuthenticationPrincipal UserDetails userDetails,
        RedirectAttributes redirectAttributes
    ) {
        User admin = getUserFromDetails(userDetails);
        redirectAttributes.addFlashAttribute("verificationDraftAction", "approve");
        redirectAttributes.addFlashAttribute("verificationDraftNotes", adminNotes == null ? "" : adminNotes);
        
        if (!qualificationsChecked) {
            redirectAttributes.addFlashAttribute("errorMessage", "Confirm that you checked the trainer’s qualifications and professional details before approval.");
            return "redirect:/super-admin/verification/" + id;
        }
        try {
            verificationService.approveTrainer(id, admin.getId(), adminNotes);
            redirectAttributes.addFlashAttribute(
                "successMessage",
                "Trainer approved successfully. Email delivery was attempted immediately; delivery is not tracked."
            );
        } catch (Exception e) {
            log.error("Error approving trainer", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Unable to save this review. Check the notes and current request status. Your draft is retained.");
        }
        
        return "redirect:/super-admin/verification/" + id;
    }
    
    /**
     * Reject a trainer verification request
     */
    @PostMapping("/{id}/reject")
    public String rejectRequest(
        @PathVariable Long id,
        @RequestParam(required = false) String adminNotes,
        @AuthenticationPrincipal UserDetails userDetails,
        RedirectAttributes redirectAttributes
    ) {
        User admin = getUserFromDetails(userDetails);
        redirectAttributes.addFlashAttribute("verificationDraftAction", "reject");
        redirectAttributes.addFlashAttribute("verificationDraftNotes", adminNotes == null ? "" : adminNotes);

        if (adminNotes == null || adminNotes.isBlank()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Please provide a rejection reason");
            return "redirect:/super-admin/verification/" + id;
        }
        
        try {
            verificationService.rejectTrainer(id, admin.getId(), adminNotes);
            redirectAttributes.addFlashAttribute(
                "successMessage",
                "Trainer verification rejected. Email delivery was attempted immediately; delivery is not tracked."
            );
        } catch (Exception e) {
            log.error("Error rejecting trainer", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Unable to save this review. Check the notes and current request status. Your draft is retained.");
        }
        
        return "redirect:/super-admin/verification/" + id;
    }
    
    /**
     * Request more information from the trainer
     */
    @PostMapping("/{id}/request-info")
    public String requestMoreInfo(
        @PathVariable Long id,
        @RequestParam String adminNotes,
        @AuthenticationPrincipal UserDetails userDetails,
        RedirectAttributes redirectAttributes
    ) {
        User admin = getUserFromDetails(userDetails);
        redirectAttributes.addFlashAttribute("verificationDraftAction", "request-info");
        redirectAttributes.addFlashAttribute("verificationDraftNotes", adminNotes == null ? "" : adminNotes);
        
        if (adminNotes == null || adminNotes.isBlank()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Please provide details about what information is needed");
            return "redirect:/super-admin/verification/" + id;
        }
        
        try {
            verificationService.requestMoreInfo(id, admin.getId(), adminNotes);
            redirectAttributes.addFlashAttribute(
                "successMessage",
                "Information requested from trainer. Email delivery was attempted immediately; delivery is not tracked."
            );
        } catch (Exception e) {
            log.error("Error requesting more info", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Unable to save this review. Check the notes and current request status. Your draft is retained.");
        }
        
        return "redirect:/super-admin/verification/" + id;
    }
    
    private User getUserFromDetails(UserDetails userDetails) {
        if (userDetails == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return userRepository.findByUsername(userDetails.getUsername())
            .filter(user -> user.isEnabled() && (user.getRole() == uk.ac.cf._5.group14.One_To_One.Users.Role.PLATFORM_ADMIN
                || user.getRole() == uk.ac.cf._5.group14.One_To_One.Users.Role.SUPER_ADMIN))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
    }
}
