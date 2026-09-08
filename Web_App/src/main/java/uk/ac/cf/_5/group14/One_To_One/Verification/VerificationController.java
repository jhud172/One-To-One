package uk.ac.cf._5.group14.One_To_One.Verification;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import uk.ac.cf._5.group14.One_To_One.Users.AuthHelper;
import uk.ac.cf._5.group14.One_To_One.Users.User;
import uk.ac.cf._5.group14.One_To_One.Users.UserService;

import java.util.Locale;
import java.util.Optional;
import java.time.Instant;

@Controller
public class VerificationController {

    private static final String RETURN_PHONE_CODE = "phone-code";

    private final EmailVerificationService emailVerificationService;
    private final PhoneVerificationService phoneVerificationService;
    private final AuthHelper authHelper;
    private final UserService userService;
    private final MessageSource messageSource;

    public VerificationController(EmailVerificationService emailVerificationService,
                                  PhoneVerificationService phoneVerificationService,
                                  AuthHelper authHelper,
                                  UserService userService,
                                  MessageSource messageSource) {
        this.emailVerificationService = emailVerificationService;
        this.phoneVerificationService = phoneVerificationService;
        this.authHelper = authHelper;
        this.userService = userService;
        this.messageSource = messageSource;
    }

    @PostMapping("/verify/email/send")
    public String sendEmailVerification(@RequestParam(name = "email", required = false) String email,
                                        RedirectAttributes redirectAttributes,
                                        Locale locale) {
        User sessionUser = authHelper.getAuthenticatedUser();
        User user = sessionUser;
        if (user == null && email != null && !email.isBlank()) {
            user = userService.findByEmail(email);
        }
        if (user == null) {
            return "redirect:/login";
        }

        if (user.isEmailVerified()) {
            redirectAttributes.addFlashAttribute(
                    "verifySuccess",
                    messageSource.getMessage("verify.email.already", null, locale)
            );
            return sessionUser != null ? "redirect:/profile" : "redirect:/login";
        }

        long cooldownRemaining = emailVerificationService.getResendCooldownRemainingSeconds(user);
        if (cooldownRemaining > 0) {
            redirectAttributes.addFlashAttribute(
                    "verifyError",
                    messageSource.getMessage("verify.email.code.cooldown", new Object[]{cooldownRemaining}, locale)
            );
            return buildEmailCodeRedirect(user.getEmail());
        }

        emailVerificationService.sendVerification(user);
        redirectAttributes.addFlashAttribute(
                "verifySuccess",
                messageSource.getMessage("verify.email.sent", null, locale)
        );
        return buildEmailCodeRedirect(user.getEmail());
    }

    @GetMapping("/verify/email")
    public String verifyEmail(@RequestParam("token") String token, Model model, Locale locale) {
        applyAuthLayout(model);
        Optional<String> error = emailVerificationService.verifyToken(token);
        if (error.isPresent()) {
            String key = mapEmailErrorKey(error.get());
            model.addAttribute("verificationError", messageSource.getMessage(key, null, locale));
        } else {
            User sessionUser = authHelper.getAuthenticatedUser();
            if (sessionUser != null) {
                sessionUser.setEmailVerified(true);
                sessionUser.setEmailVerifiedAt(Instant.now());
            }
            model.addAttribute("verificationSuccess", messageSource.getMessage("verify.email.success", null, locale));
        }
        return "public-views/verify/email-confirm";
    }

    @GetMapping("/verify/email/code")
    public String showEmailCodePage(@RequestParam(required = false) String email,
                                    @RequestParam(name = "resent", required = false) String resent,
                                    @RequestParam(name = "cooldown", required = false) String cooldown,
                                    @RequestParam(name = "resendError", required = false) String resendError,
                                    Model model,
                                    Locale locale) {
        applyAuthLayout(model);
        User sessionUser = authHelper.getAuthenticatedUser();
        if (sessionUser == null && (email == null || email.isBlank())) {
            return "redirect:/login";
        }
        User user = sessionUser;
        if (user == null && email != null && !email.isBlank()) {
            user = userService.findByEmail(email);
        }

        if (user != null && user.getEmail() != null) {
            model.addAttribute("verifyEmail", user.getEmail());
            model.addAttribute("resendCooldownSeconds", emailVerificationService.getResendCooldownRemainingSeconds(user));
        } else if (email != null && !email.isBlank()) {
            model.addAttribute("verifyEmail", email);
            model.addAttribute("resendCooldownSeconds", 0L);
        }

        if (resent != null) {
            model.addAttribute("verifySuccess", messageSource.getMessage("verify.email.sent", null, locale));
        } else if (cooldown != null && user != null) {
            long cooldownRemaining = emailVerificationService.getResendCooldownRemainingSeconds(user);
            model.addAttribute(
                    "verifyError",
                    messageSource.getMessage("verify.email.code.cooldown", new Object[]{cooldownRemaining}, locale)
            );
        } else if (resendError != null) {
            model.addAttribute("verifyError", messageSource.getMessage("verify.email.error.generic", null, locale));
        }
        return "public-views/verify/email-code";
    }

    @PostMapping("/verify/email/confirm")
    public String confirmEmailCode(@RequestParam("code") String code,
                                   @RequestParam(name = "email", required = false) String email,
                                   RedirectAttributes redirectAttributes,
                                   Locale locale) {
        User sessionUser = authHelper.getAuthenticatedUser();
        User user = sessionUser;
        if (user == null && email != null && !email.isBlank()) {
            user = userService.findByEmail(email);
        }
        if (user == null) {
            return "redirect:/login";
        }
        Optional<String> error = emailVerificationService.confirmCode(user, code);
        String redirectBase = "redirect:/verify/email/code";
        if (email != null && !email.isBlank()) {
            redirectBase += "?email=" + java.net.URLEncoder.encode(email, java.nio.charset.StandardCharsets.UTF_8);
        }
        if (error.isPresent()) {
            String key = mapEmailCodeErrorKey(error.get());
            redirectAttributes.addFlashAttribute(
                    "verificationError",
                    messageSource.getMessage(key, null, locale)
            );
        } else {
            if (sessionUser != null) {
                sessionUser.setEmailVerified(true);
                sessionUser.setEmailVerifiedAt(Instant.now());
            }
            redirectAttributes.addFlashAttribute(
                    "verificationSuccess",
                    messageSource.getMessage("verify.email.success", null, locale)
            );

            // For signed-in users, go back to profile where status badges update immediately.
            if (sessionUser != null && (email == null || email.isBlank())) {
                return "redirect:/profile";
            }
        }
        return redirectBase;
    }

    private String buildEmailCodeRedirect(String email) {
        String redirect = "redirect:/verify/email/code";
        if (email != null && !email.isBlank()) {
            redirect += "?email=" + java.net.URLEncoder.encode(email, java.nio.charset.StandardCharsets.UTF_8);
        }
        return redirect;
    }

    @PostMapping("/verify/phone/send")
    public String sendPhoneVerification(RedirectAttributes redirectAttributes, Locale locale) {
        User user = authHelper.getAuthenticatedUser();
        if (user == null) {
            return "redirect:/login";
        }

        if (user.isPhoneVerified()) {
            redirectAttributes.addFlashAttribute(
                    "verifySuccess",
                    messageSource.getMessage("verify.phone.already", null, locale)
            );
            return "redirect:/profile";
        }

        if (user.getPhoneNumber() == null || user.getPhoneNumber().isBlank()) {
            redirectAttributes.addFlashAttribute(
                    "verifyError",
                    messageSource.getMessage("verify.phone.missing", null, locale)
            );
            return "redirect:/profile";
        }
        try {
            phoneVerificationService.sendCode(user);
        } catch (Exception ex) {
            String detail = ex.getMessage() != null && !ex.getMessage().isBlank()
                    ? ex.getMessage()
                    : messageSource.getMessage("verify.phone.error.generic", null, locale);
            redirectAttributes.addFlashAttribute(
                "verifyError",
                messageSource.getMessage("verify.phone.error.send.detail", new Object[]{detail}, locale)
            );
            return "redirect:/profile";
        }
        redirectAttributes.addFlashAttribute(
                "verifySuccess",
                messageSource.getMessage("verify.phone.sent", null, locale)
        );
        return "redirect:/verify/phone/code";
    }

    @GetMapping("/verify/phone/code")
    public String showPhoneCodePage(Model model) {
        if (authHelper.getAuthenticatedUser() == null) {
            return "redirect:/login";
        }
        applyAuthLayout(model);
        return "public-views/verify/phone-code";
    }

    @PostMapping("/verify/phone/confirm")
    public String confirmPhoneVerification(@RequestParam("code") String code,
                                           @RequestParam(name = "returnTo", required = false) String returnTo,
                                           RedirectAttributes redirectAttributes,
                                           Locale locale) {
        User user = authHelper.getAuthenticatedUser();
        if (user == null) {
            return "redirect:/login";
        }
        Optional<String> error = phoneVerificationService.confirmCode(user, code);
        if (error.isPresent()) {
            String key = mapPhoneErrorKey(error.get());
            redirectAttributes.addFlashAttribute(
                    "verifyError",
                    messageSource.getMessage(key, null, locale)
            );
            return RETURN_PHONE_CODE.equals(returnTo) ? "redirect:/verify/phone/code" : "redirect:/profile";
        } else {
            user.setPhoneVerified(true);
            user.setPhoneVerifiedAt(Instant.now());
            redirectAttributes.addFlashAttribute(
                    "verifySuccess",
                    messageSource.getMessage("verify.phone.success", null, locale)
            );
        }
        return "redirect:/profile";
    }

    private void applyAuthLayout(Model model) {
        model.addAttribute("authPageLayout", true);
        model.addAttribute("compactTopContent", true);
        model.addAttribute("disableGlobalChatbot", true);
    }

    private String mapEmailErrorKey(String message) {
        if (message == null) {
            return "verify.email.error.generic";
        }
        return switch (message) {
            case "Invalid verification token." -> "verify.email.error.invalid";
            case "Verification token not found." -> "verify.email.error.notFound";
            case "Verification token already used." -> "verify.email.error.used";
            case "Verification token expired." -> "verify.email.error.expired";
            default -> "verify.email.error.generic";
        };
    }

    private String mapEmailCodeErrorKey(String message) {
        if (message == null) {
            return "verify.email.error.generic";
        }
        return switch (message) {
            case "User not found." -> "verify.email.error.generic";
            case "No verification code found. Please request a new code." -> "verify.email.code.error.missing";
            case "Verification code already used." -> "verify.email.error.used";
            case "Verification code expired. Please request a new code." -> "verify.email.error.expired";
            case "Too many attempts. Please request a new code." -> "verify.email.code.error.attempts";
            case "Invalid code. Please try again." -> "verify.email.code.error.invalid";
            default -> "verify.email.error.generic";
        };
    }

    private String mapPhoneErrorKey(String message) {
        if (message == null) {
            return "verify.phone.error.generic";
        }
        return switch (message) {
            case "User not found." -> "verify.phone.error.user";
            case "No verification code found. Please request a new code." -> "verify.phone.error.missing";
            case "Verification code already used." -> "verify.phone.error.used";
            case "Verification code expired. Please request a new code." -> "verify.phone.error.expired";
            case "Too many attempts. Please request a new code." -> "verify.phone.error.attempts";
            case "Invalid code. Please try again." -> "verify.phone.error.invalid";
            default -> "verify.phone.error.generic";
        };
    }
}
