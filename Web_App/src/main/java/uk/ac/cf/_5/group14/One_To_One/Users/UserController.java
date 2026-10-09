package uk.ac.cf._5.group14.One_To_One.Users;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import uk.ac.cf._5.group14.One_To_One.Config.DevModeProperties;
import uk.ac.cf._5.group14.One_To_One.GymApplications.GymApplication;
import uk.ac.cf._5.group14.One_To_One.GymApplications.GymApplicationService;
import uk.ac.cf._5.group14.One_To_One.GymProfile.GymProfile;
import uk.ac.cf._5.group14.One_To_One.GymProfile.GymProfileService;
import uk.ac.cf._5.group14.One_To_One.Security.SecurityUtils;
import uk.ac.cf._5.group14.One_To_One.Security.SocialAuth.SocialAuthAvailabilityService;
import uk.ac.cf._5.group14.One_To_One.TrainerProfile.TrainerProfile;
import uk.ac.cf._5.group14.One_To_One.TrainerProfile.TrainerProfileService;
import uk.ac.cf._5.group14.One_To_One.Verification.EmailVerificationService;

@Slf4j
@Controller
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private TrainerProfileService trainerProfileService;

    @Autowired
    private GymProfileService gymProfileService;
    
    @Autowired
    private DevModeProperties devModeProperties;

    @Autowired
    private EmailVerificationService emailVerificationService;

    @Autowired
    private SocialAuthAvailabilityService socialAuthAvailabilityService;

    @Autowired
    private GymApplicationService gymApplicationService;

    @ModelAttribute("socialProviders")
    public java.util.List<uk.ac.cf._5.group14.One_To_One.Security.SocialAuth.SocialProviderOption> socialProviders() {
        return socialAuthAvailabilityService.getVisibleProviders();
    }

    // signup choice
    @GetMapping("/signup")
    public String showSignupForm(Model model) {
        if (isAuthenticatedUser()) {
            return "redirect:/dashboard";
        }
        applyAuthLayout(model);
        return "public-views/auth/signup-choice";
    }

    @GetMapping("/signup/client")
    public String showClientSignup(Model model) {
        if (isAuthenticatedUser()) {
            return "redirect:/dashboard";
        }
        applyAuthLayout(model);
        model.addAttribute("signupForm", new ClientSignupForm());
        return "public-views/auth/signup-client";
    }

    @PostMapping("/signup/client")
    public String signupClient(@Valid @ModelAttribute("signupForm") ClientSignupForm signupForm,
                               BindingResult result,
                               Model model,
                               RedirectAttributes redirectAttributes) {
        if (isAuthenticatedUser()) {
            return "redirect:/dashboard";
        }
        applyAuthLayout(model);

        // check for validation errors
        if (result.hasErrors()) {
            return "public-views/auth/signup-client";
        }

        // check whether email exists
        if (userService.emailExists(signupForm.getEmail())) {
            result.rejectValue("email", "email.taken", "Email already registered!");
            return "public-views/auth/signup-client";
        }

        // check whether username exists
        if (userService.usernameExists(signupForm.getUsername())) {
            result.rejectValue("username", "username.taken", "Username already taken!");
            return "public-views/auth/signup-client";
        }

        String normalizedUsername = normalizeUsername(signupForm.getUsername());
        String displayName = normalizedUsername != null ? normalizedUsername : "client";

        // save user to database
        User user = new User(
                signupForm.getEmail(),
                displayName,
                "Client",
                normalizedUsername,
                signupForm.getPassword()
        );
        user.setRole(Role.CLIENT);

        userService.saveUser(user);

        try {
            emailVerificationService.sendVerification(user);
        } catch (Exception e) {
            log.warn("Failed to send verification email to {} after client signup", user.getEmail(), e);
        }

        redirectAttributes.addFlashAttribute("verifyRegistered", true);
        return "redirect:/verify/email/code?email=" + encodeEmail(user.getEmail());
    }

    @GetMapping("/signup/trainer")
    public String showTrainerSignup(Model model) {
        if (isAuthenticatedUser()) {
            return "redirect:/dashboard";
        }
        applyAuthLayout(model);
        model.addAttribute("signupForm", new TrainerSignupForm());
        return "public-views/auth/signup-trainer";
    }

    @GetMapping("/signup/trainer/success")
    public String showTrainerSignupSuccess(Model model) {
        if (isAuthenticatedUser()) {
            return "redirect:/dashboard";
        }

        Object trainerCode = model.asMap().get("trainerCode");
        Object verifyEmail = model.asMap().get("verifyEmail");
        if (!(trainerCode instanceof String code) || code.isBlank()
                || !(verifyEmail instanceof String email) || email.isBlank()) {
            return "redirect:/signup/trainer";
        }

        applyAuthLayout(model);
        return "public-views/auth/signup-trainer-success";
    }

    @PostMapping("/signup/trainer")
    public String signupTrainer(@Valid @ModelAttribute("signupForm") TrainerSignupForm signupForm,
                                BindingResult result,
                                Model model,
                                RedirectAttributes redirectAttributes) {
        if (isAuthenticatedUser()) {
            return "redirect:/dashboard";
        }
        applyAuthLayout(model);

        if (result.hasErrors()) {
            return "public-views/auth/signup-trainer";
        }

        if (userService.emailExists(signupForm.getEmail())) {
            result.rejectValue("email", "email.taken", "Email already registered!");
            return "public-views/auth/signup-trainer";
        }

        if (userService.usernameExists(signupForm.getUsername())) {
            result.rejectValue("username", "username.taken", "Username already taken!");
            return "public-views/auth/signup-trainer";
        }

        String normalizedUsername = normalizeUsername(signupForm.getUsername());
        String displayName = normalizedUsername != null ? normalizedUsername : "trainer";

        User user = new User(
                signupForm.getEmail(),
                displayName,
                "Trainer",
                normalizedUsername,
                signupForm.getPassword()
        );
        user.setRole(Role.TRAINER);

        User savedUser = userService.saveUser(user);

        TrainerProfile profile = trainerProfileService.getOrCreateProfile(savedUser.getId());
        profile.setBio(signupForm.getBio());
        profile.setLocation(signupForm.getLocation());
        profile.setInstagramUrl(signupForm.getInstagramUrl());
        profile.setTiktokUrl(signupForm.getTiktokUrl());
        profile.setYoutubeUrl(signupForm.getYoutubeUrl());
        profile.setLinkedInUrl(signupForm.getLinkedInUrl());
        profile.setWebsiteUrl(signupForm.getWebsiteUrl());
        trainerProfileService.updateProfile(savedUser.getId(), profile);

        boolean verificationEmailSent = true;
        try {
            emailVerificationService.sendVerification(savedUser);
        } catch (Exception e) {
            verificationEmailSent = false;
            log.warn("Failed to send verification email to {} after trainer signup", savedUser.getEmail(), e);
        }

        String trainerCode = TrainerProfileService.normalizeTrainerCode(profile.getTrainerCode());
        if (trainerCode == null) {
            log.error("Trainer profile {} was created without a valid trainer code", savedUser.getId());
            redirectAttributes.addFlashAttribute("verifyRegistered", true);
            return "redirect:/verify/email/code?email=" + encodeEmail(savedUser.getEmail());
        }

        String formattedCode = trainerCode.substring(0, 4)
                + "-" + trainerCode.substring(4, 8)
                + "-" + trainerCode.substring(8, 12);
        redirectAttributes.addFlashAttribute("trainerCode", formattedCode);
        redirectAttributes.addFlashAttribute("verifyEmail", savedUser.getEmail());
        redirectAttributes.addFlashAttribute("verificationEmailSent", verificationEmailSent);
        return "redirect:/signup/trainer/success";
    }

    @GetMapping("/signup/gym")
    public String showGymSignup(Model model) {
        if (isAuthenticatedUser()) {
            return "redirect:/dashboard";
        }
        applyAuthLayout(model);
        model.addAttribute("signupForm", new GymSignupForm());
        return "public-views/auth/signup-gym";
    }

    @PostMapping("/signup/gym")
    public String signupGym(@Valid @ModelAttribute("signupForm") GymSignupForm signupForm,
                            BindingResult result,
                            Model model,
                            RedirectAttributes redirectAttributes) {
        if (isAuthenticatedUser()) {
            return "redirect:/dashboard";
        }
        applyAuthLayout(model);

        if (result.hasErrors()) {
            return "public-views/auth/signup-gym";
        }

        if (userService.emailExists(signupForm.getAdminEmail())) {
            result.rejectValue("adminEmail", "email.taken", "Email already registered!");
            return "public-views/auth/signup-gym";
        }

        String normalizedGymUsername = normalizeUsername(signupForm.getGymUsername());
        if (normalizedGymUsername == null) {
            result.rejectValue("gymUsername", "username.invalid", "Please provide a valid gym username.");
            return "public-views/auth/signup-gym";
        }

        if (userService.usernameExists(normalizedGymUsername)) {
            result.rejectValue("gymUsername", "username.taken", "Gym username already taken!");
            return "public-views/auth/signup-gym";
        }

        try {
            GymApplication application = gymApplicationService.submitApplication(signupForm);
            redirectAttributes.addFlashAttribute("gymApplicationSuccess", "Application submitted. We will review it before activating the gym account.");
            return "redirect:/signup/gym/application/" + application.getAccessToken();
        } catch (Exception ex) {
            log.warn("Failed to submit gym application for {}", signupForm.getAdminEmail(), ex);
            result.reject("gym.application.failed", "We could not submit the gym application. Please try again.");
            return "public-views/auth/signup-gym";
        }
    }

    @GetMapping("/confirm-logout")
    public String confirmLogout(Model model) {
        applyAuthLayout(model);
        return "public-views/auth/confirm-logout";
    }

    // login page
    @GetMapping("/login")
    public String showLoginForm(@RequestParam(value = "expired", required = false) String expired,
                                @RequestParam(value = "role", required = false) String role,
                                @RequestParam(value = "error", required = false) String error,
                                HttpSession session,
                                Model model) {
        if (isAuthenticatedUser()) {
            return "redirect:/dashboard";
        }
        applyAuthLayout(model);
        if (expired != null) {
            return "redirect:/";
        }

        // Pass a validated role so the server-rendered form is correct before JavaScript runs.
        String selectedRole = role != null && (role.equals("client") || role.equals("trainer") || role.equals("gym"))
                ? role
                : "client";
        model.addAttribute("preselectedRole", selectedRole);
        model.addAttribute("signupPath", switch (selectedRole) {
            case "trainer" -> "/signup/trainer";
            case "gym" -> "/signup/gym";
            default -> "/signup/client";
        });

        Object previousIdentifier = session.getAttribute(
                uk.ac.cf._5.group14.One_To_One.Security.CustomAuthenticationFailureHandler.LOGIN_IDENTIFIER_SESSION_ATTRIBUTE
        );
        session.removeAttribute(
                uk.ac.cf._5.group14.One_To_One.Security.CustomAuthenticationFailureHandler.LOGIN_IDENTIFIER_SESSION_ATTRIBUTE
        );
        if (error != null && previousIdentifier instanceof String identifier && !identifier.isBlank()) {
            model.addAttribute("loginIdentifier", identifier);
        }
        model.addAttribute("loginError", error);
        
        // In dev mode, keep the normal login form and layer the dev alert onto it.
        if (devModeProperties.isDevMode()) {
            model.addAttribute("compactTopContent", true);
            model.addAttribute("isDevMode", true);
            return "public-views/auth/login";
        }
        
        return "public-views/auth/login";
    }

    @PostMapping("/login")
    public String loginUser() {
        // Handled by Spring Security
        return "redirect:/";
    }

    private void applyAuthLayout(Model model) {
        model.addAttribute("authPageLayout", true);
        model.addAttribute("compactTopContent", true);
        model.addAttribute("disableGlobalChatbot", true);
    }

    private boolean isAuthenticatedUser() {
        return SecurityUtils.isAuthenticated();
    }

    private String normalizeUsername(String username) {
        if (username == null) {
            return null;
        }
        return username.trim().toLowerCase();
    }

    private String encodeEmail(String email) {
        if (email == null) {
            return "";
        }
        return java.net.URLEncoder.encode(email, java.nio.charset.StandardCharsets.UTF_8);
    }
}
