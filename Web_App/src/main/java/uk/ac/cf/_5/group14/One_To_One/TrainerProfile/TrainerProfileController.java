package uk.ac.cf._5.group14.One_To_One.TrainerProfile;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import jakarta.servlet.http.HttpServletResponse;
import uk.ac.cf._5.group14.One_To_One.Users.AuthHelper;
import uk.ac.cf._5.group14.One_To_One.Users.User;

@Controller
@RequestMapping("/trainer/profile")
public class TrainerProfileController {
    private final TrainerProfileService profileService;
    private final AuthHelper authHelper;
    private final SocialLinkValidator linkValidator;

    public TrainerProfileController(TrainerProfileService profileService, AuthHelper authHelper, SocialLinkValidator linkValidator) {
        this.profileService = profileService;
        this.authHelper = authHelper;
        this.linkValidator = linkValidator;
    }

    @InitBinder("profile")
    public void bindPublicFields(WebDataBinder binder) {
        binder.setAllowedFields("bio", "specializations", "location", "primaryGym", "pricePerSession",
                "instagramUrl", "tiktokUrl", "youtubeUrl", "linkedInUrl", "websiteUrl",
                "showInstagram", "showTikTok", "showYouTube", "showLinkedIn", "showWebsite");
    }

    @GetMapping("/edit")
    public String showEditProfile(Model model) {
        User currentUser = authHelper.getAuthenticatedUser();
        TrainerProfile saved = profileService.getOrCreateProfile(currentUser.getId());
        model.addAttribute("profile", saved);
        addEditorContext(model, currentUser, saved);
        return "trainer-views/trainer/profile/edit";
    }

    @PostMapping("/save")
    public String saveProfile(@Valid @ModelAttribute("profile") TrainerProfile draft, BindingResult errors,
                              Model model, HttpServletResponse response, RedirectAttributes redirectAttributes) {
        User currentUser = authHelper.getAuthenticatedUser();
        TrainerProfile saved = profileService.getOrCreateProfile(currentUser.getId());
        validateLink(errors, "instagramUrl", linkValidator.isValidInstagramUrl(draft.getInstagramUrl()));
        validateLink(errors, "tiktokUrl", linkValidator.isValidTikTokUrl(draft.getTiktokUrl()));
        validateLink(errors, "youtubeUrl", linkValidator.isValidYouTubeUrl(draft.getYoutubeUrl()));
        validateLink(errors, "linkedInUrl", linkValidator.isValidLinkedInUrl(draft.getLinkedInUrl()));
        validateLink(errors, "websiteUrl", linkValidator.isValidWebsiteUrl(draft.getWebsiteUrl()));
        if (!errors.hasErrors()) {
            try {
                profileService.updateProfile(currentUser.getId(), draft);
                redirectAttributes.addFlashAttribute("profileSaved", true);
                return "redirect:/trainer/profile/edit";
            } catch (IllegalArgumentException invalidInput) {
                errors.reject("ui.profile.editorInvalid");
            }
        }
        addEditorContext(model, currentUser, saved);
        model.addAttribute("profileInvalid", true);
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        return "trainer-views/trainer/profile/edit";
    }

    private void validateLink(BindingResult errors, String field, boolean valid) {
        if (!valid && !errors.hasFieldErrors(field)) errors.rejectValue(field, "ui.profile.editorInvalid");
    }

    private void addEditorContext(Model model, User user, TrainerProfile saved) {
        model.addAttribute("user", user);
        String code = TrainerProfileService.normalizeTrainerCode(saved.getTrainerCode());
        model.addAttribute("formattedTrainerCode", code == null ? null
                : code.substring(0, 4) + "-" + code.substring(4, 8) + "-" + code.substring(8, 12));
    }
}
