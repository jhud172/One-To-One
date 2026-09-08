package uk.ac.cf._5.group14.One_To_One.Users;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import uk.ac.cf._5.group14.One_To_One.Security.SecurityUtils;

@Controller
@RequiredArgsConstructor
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    @GetMapping("/forgot-password")
    public String showForgotPassword(Model model,
                                     @RequestParam(value = "sent", required = false) String sent) {
        if (SecurityUtils.isAuthenticated()) {
            return "redirect:/dashboard";
        }
        applyAuthLayout(model);
        model.addAttribute("forgotPasswordForm", new ForgotPasswordForm());
        model.addAttribute("sent", sent != null);
        return "public-views/auth/forgot-password";
    }

    @PostMapping("/forgot-password")
    public String submitForgotPassword(@Valid @ModelAttribute("forgotPasswordForm") ForgotPasswordForm form,
                                       BindingResult result,
                                       Model model) {
        if (SecurityUtils.isAuthenticated()) {
            return "redirect:/dashboard";
        }
        applyAuthLayout(model);
        if (result.hasErrors()) {
            model.addAttribute("sent", false);
            return "public-views/auth/forgot-password";
        }

        passwordResetService.requestPasswordReset(form.getEmail());
        return "redirect:/forgot-password?sent=1";
    }

    @GetMapping("/reset-password")
    public String showResetPassword(@RequestParam("token") String token, Model model) {
        applyAuthLayout(model);
        model.addAttribute("resetPasswordForm", new ResetPasswordForm());
        model.addAttribute("token", token);
        model.addAttribute("tokenValid", passwordResetService.getValidToken(token).isPresent());
        return "public-views/auth/reset-password";
    }

    @PostMapping("/reset-password")
    public String submitResetPassword(@RequestParam("token") String token,
                                      @Valid @ModelAttribute("resetPasswordForm") ResetPasswordForm form,
                                      BindingResult result,
                                      Model model) {
        applyAuthLayout(model);
        if (result.hasErrors()) {
            model.addAttribute("token", token);
            model.addAttribute("tokenValid", passwordResetService.getValidToken(token).isPresent());
            return "public-views/auth/reset-password";
        }

        boolean success = passwordResetService.resetPassword(token, form.getPassword());
        if (!success) {
            model.addAttribute("token", token);
            model.addAttribute("tokenValid", false);
            return "public-views/auth/reset-password";
        }

        return "redirect:/login?reset=1";
    }

    private void applyAuthLayout(Model model) {
        model.addAttribute("authPageLayout", true);
        model.addAttribute("compactTopContent", true);
        model.addAttribute("disableGlobalChatbot", true);
    }
}
