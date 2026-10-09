package uk.ac.cf._5.group14.One_To_One.Web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import uk.ac.cf._5.group14.One_To_One.Config.DevModeProperties;
import uk.ac.cf._5.group14.One_To_One.DevMode.DevModePageAccessService;
import uk.ac.cf._5.group14.One_To_One.Waitlist.WaitlistEmail;
import uk.ac.cf._5.group14.One_To_One.Waitlist.WaitlistEmailRepository;

/**
 * Controller for development mode features
 * Provides navigation hub and demo pages when DEV_MODE is enabled
 */
@Controller
@RequestMapping("/dev-mode")
public class DevModeController {
    
    @Autowired
    private DevModeProperties devModeProperties;

    @Autowired
    private WaitlistEmailRepository waitlistEmailRepository;

    @Autowired
    private DevModePageAccessService devModePageAccessService;
    
    /**
     * Development mode navigation hub
     * Shows available pages users can browse in dev mode
     */
    @GetMapping
    public String devModeHub(Authentication authentication,
                             @RequestParam(defaultValue = "") String search,
                             jakarta.servlet.http.HttpServletResponse response, Model model) {
        if (!devModeProperties.isDevMode()) {
            return "redirect:/";
        }

        model.addAttribute("compactTopContent", true);
        model.addAttribute("isDevMode", true);
        var hub = devModePageAccessService.buildHubView(authentication);
        String keyword = search.trim();
        if (keyword.length() > 100) {
            response.setStatus(400);
            model.addAttribute("devHubError", "Use no more than 100 characters to search routes.");
            keyword = keyword.substring(0, 100);
        }
        model.addAttribute("devHubSearch", keyword);
        model.addAttribute("devHubTotal", hub.publicPages().size() + hub.loginRequiredPages().size() + hub.restrictedPages().size());
        String needle = keyword.toLowerCase(java.util.Locale.ROOT);
        java.util.function.Predicate<DevModePageAccessService.DevModePageHubCard> matches = page ->
                (page.title() + " " + page.path() + " " + page.description()).toLowerCase(java.util.Locale.ROOT).contains(needle);
        model.addAttribute("devHubView", new DevModePageAccessService.DevModeHubView(
                hub.publicPages().stream().filter(matches).toList(),
                hub.loginRequiredPages().stream().filter(matches).toList(),
                hub.restrictedPages().stream().filter(matches).toList()));
        return "system-views/dev-mode/hub";
    }
    
    /**
     * Alternative routing for protected pages in dev mode
     * When user tries to access a protected page without auth in dev mode,
     * they can be redirected here
     */
    @GetMapping("/unauthorized")
    public String devUnauthorized(Model model) {
        if (!devModeProperties.isDevMode()) {
            return "redirect:/";
        }

        model.addAttribute("compactTopContent", true);
        model.addAttribute("isDevMode", true);
        return "system-views/dev-mode/unauthorized";
    }

    @GetMapping("/restricted")
    public String devRestricted(@RequestParam(value = "pageKey", required = false) String pageKey,
                                Model model) {
        if (!devModeProperties.isDevMode()) {
            return "redirect:/";
        }

        model.addAttribute("compactTopContent", true);
        model.addAttribute("isDevMode", true);
        model.addAttribute("restrictedNotice", devModePageAccessService.resolveRestrictedNotice(pageKey));
        return "system-views/dev-mode/restricted";
    }

    /**
     * Accepts email sign-ups from the dev mode landing page.
     * Saves the email so the user can be notified when the site launches.
     */
    @PostMapping("/waitlist")
    public String joinWaitlist(@RequestParam("email") String email,
                               RedirectAttributes redirectAttributes) {
        if (!devModeProperties.isDevMode()) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
        }
        String trimmed = (email == null) ? "" : email.trim().toLowerCase(java.util.Locale.ROOT);
        if (trimmed.length() > 255 || trimmed.isEmpty() || !trimmed.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$")) {
            redirectAttributes.addFlashAttribute("waitlistError", "Please enter a valid email address.");
            redirectAttributes.addFlashAttribute("waitlistDraft", trimmed.length() <= 255 ? trimmed : "");
            return "redirect:/login";
        }
        if (!waitlistEmailRepository.existsByEmailIgnoreCase(trimmed)) {
            try {
                waitlistEmailRepository.saveAndFlush(new WaitlistEmail(trimmed));
            } catch (org.springframework.dao.DataIntegrityViolationException collision) {
                if (!waitlistEmailRepository.existsByEmailIgnoreCase(trimmed)) throw collision;
            }
        }
        // Registration is interest only; it does not prove inbox ownership or send mail.
        redirectAttributes.addFlashAttribute("waitlistSuccess", "Launch interest recorded. No email has been sent; address confirmation is required before outreach.");
        return "redirect:/login";
    }
}
