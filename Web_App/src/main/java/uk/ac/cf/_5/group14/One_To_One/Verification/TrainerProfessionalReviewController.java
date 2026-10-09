package uk.ac.cf._5.group14.One_To_One.Verification;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import uk.ac.cf._5.group14.One_To_One.Users.Role;
import uk.ac.cf._5.group14.One_To_One.Users.User;
import uk.ac.cf._5.group14.One_To_One.Users.UserRepository;

@Controller
@RequestMapping("/trainer/verification")
@PreAuthorize("hasRole('TRAINER')")
@RequiredArgsConstructor
public class TrainerProfessionalReviewController {
    private final UserRepository users;
    private final TrainerVerificationService verification;
    private final VerificationEvidenceService evidence;

    @GetMapping
    public String overview(@AuthenticationPrincipal UserDetails principal, Model model, HttpServletResponse response) {
        return render(trainer(principal), model, response);
    }

    @PostMapping("/request")
    public String request(@AuthenticationPrincipal UserDetails principal,
                          @RequestParam(defaultValue = "") String notes,
                          @RequestParam(defaultValue = "false") boolean confirmed,
                          Model model, HttpServletResponse response, RedirectAttributes redirect) {
        User trainer = trainer(principal);
        if (!validNotes(notes) || !confirmed) return invalid(trainer, notes, model, response);
        try {
            verification.createVerificationRequest(trainer.getId(), null, notes);
        } catch (IllegalArgumentException | IllegalStateException rejected) {
            return invalid(trainer, notes, model, response);
        }
        redirect.addFlashAttribute("reviewSaved", true);
        return "redirect:/trainer/verification";
    }

    @PostMapping("/{id}/respond")
    public String respond(@PathVariable Long id, @AuthenticationPrincipal UserDetails principal,
                          @RequestParam(defaultValue = "") String notes,
                          @RequestParam(defaultValue = "false") boolean confirmed,
                          Model model, HttpServletResponse response, RedirectAttributes redirect) {
        User trainer = trainer(principal);
        // Check ownership before rendering validation feedback about any request.
        boolean owned = verification.getRequestsForTrainer(trainer.getId()).stream().anyMatch(request -> request.getId().equals(id));
        if (!owned) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if (!validNotes(notes) || !confirmed) return invalid(trainer, notes, model, response);
        try {
            verification.updateTrainerNotesForTrainer(id, trainer.getId(), notes);
        } catch (IllegalArgumentException | IllegalStateException rejected) {
            return invalid(trainer, notes, model, response);
        }
        redirect.addFlashAttribute("reviewSaved", true);
        return "redirect:/trainer/verification";
    }

    @PostMapping("/{id}/documents")
    public String upload(@PathVariable Long id, @AuthenticationPrincipal UserDetails principal,
                         @RequestParam(required = false) java.util.List<org.springframework.web.multipart.MultipartFile> files,
                         Model model, HttpServletResponse response, RedirectAttributes redirect) {
        User trainer = trainer(principal);
        if (verification.getRequestsForTrainer(trainer.getId()).stream().noneMatch(request -> request.getId().equals(id))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        try {
            evidence.upload(id, trainer.getId(), files);
        } catch (IllegalArgumentException | IllegalStateException rejected) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            model.addAttribute("reviewFilesError", true);
            return render(trainer, model, response);
        }
        redirect.addFlashAttribute("reviewFilesSaved", true);
        return "redirect:/trainer/verification";
    }

    @PostMapping("/{id}/documents/{documentId}/remove")
    public String removeDocument(@PathVariable Long id, @PathVariable Long documentId,
                                 @AuthenticationPrincipal UserDetails principal, Model model,
                                 HttpServletResponse response, RedirectAttributes redirect) {
        User trainer = trainer(principal);
        if (verification.getRequestsForTrainer(trainer.getId()).stream().noneMatch(request -> request.getId().equals(id))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        try {
            evidence.remove(id, trainer.getId(), documentId);
        } catch (IllegalArgumentException missing) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        } catch (IllegalStateException closed) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            model.addAttribute("reviewFilesError", true);
            return render(trainer, model, response);
        }
        redirect.addFlashAttribute("reviewFileRemoved", true);
        return "redirect:/trainer/verification";
    }

    private User trainer(UserDetails principal) {
        if (principal == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        User trainer = users.findByUsername(principal.getUsername())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
        if (!trainer.isEnabled() || trainer.getRole() != Role.TRAINER) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return trainer;
    }

    private boolean validNotes(String notes) { return !notes.isBlank() && notes.length() <= 1000; }

    private String invalid(User trainer, String notes, Model model, HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        model.addAttribute("reviewError", true);
        model.addAttribute("reviewDraft", notes.substring(0, Math.min(notes.length(), 2000)));
        return render(trainer, model, response);
    }

    private String render(User trainer, Model model, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
        var history = verification.getRequestsForTrainer(trainer.getId());
        var latest = history.isEmpty() ? null : history.getFirst();
        model.addAttribute("reviewHistory", history);
        model.addAttribute("reviewEvents", verification.getHistoryForRequests(history));
        model.addAttribute("reviewDocuments", evidence.summaries(history));
        model.addAttribute("latestReview", latest);
        model.addAttribute("trainerVerified", trainer.isTrainerVerified());
        model.addAttribute("canRequestReview", !trainer.isTrainerVerified()
            && history.stream().noneMatch(request -> request.getStatus() == VerificationStatus.PENDING || request.getStatus() == VerificationStatus.NEEDS_INFO));
        model.addAttribute("canRespondReview", latest != null && latest.getStatus() == VerificationStatus.NEEDS_INFO);
        model.addAttribute("canUploadEvidence", !trainer.isTrainerVerified() && latest != null
            && (latest.getStatus() == VerificationStatus.PENDING || latest.getStatus() == VerificationStatus.NEEDS_INFO));
        return "trainer-views/verification/overview";
    }
}
