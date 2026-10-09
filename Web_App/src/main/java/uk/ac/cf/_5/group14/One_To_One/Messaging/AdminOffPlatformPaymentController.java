package uk.ac.cf._5.group14.One_To_One.Messaging;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import java.util.regex.Pattern;
import org.springframework.web.servlet.ModelAndView;
import uk.ac.cf._5.group14.One_To_One.Users.AuthHelper;
import uk.ac.cf._5.group14.One_To_One.Users.Role;
import uk.ac.cf._5.group14.One_To_One.Users.User;
import uk.ac.cf._5.group14.One_To_One.Users.UserRepository;
import uk.ac.cf._5.group14.One_To_One.Users.UserService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class AdminOffPlatformPaymentController {

    private final AuthHelper authHelper;
    private final UserService userService;
    private final UserRepository userRepository;
    private final OffPlatformPaymentAttemptRepository attemptRepository;

    public AdminOffPlatformPaymentController(AuthHelper authHelper,
                                             UserService userService,
                                             UserRepository userRepository,
                                             OffPlatformPaymentAttemptRepository attemptRepository) {
        this.authHelper = authHelper;
        this.userService = userService;
        this.userRepository = userRepository;
        this.attemptRepository = attemptRepository;
    }

    private User currentUserOrThrow() {
        User sessionUser = authHelper.getAuthenticatedUser();
        if (sessionUser != null) {
            return sessionUser;
        }

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

    @GetMapping("/admin/off-platform-payments")
    public ModelAndView offPlatformPayments(@RequestParam(defaultValue = "") String keyword,
                                            @RequestParam(defaultValue = "0") int page) {
        User admin = currentUserOrThrow();
        if (admin.getRole() != Role.PLATFORM_ADMIN && admin.getRole() != Role.SUPER_ADMIN) {
            throw new org.springframework.security.access.AccessDeniedException("Admin only");
        }

        int safePage = Math.min(10000, Math.max(0, page));
        String query = keyword.trim();
        var result = attemptRepository.findByMatchedKeywordContainingIgnoreCase(query,
            PageRequest.of(safePage, 25, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        Map<Long, User> usersById = new HashMap<>();
        userRepository.findAllById(result.getContent().stream().map(OffPlatformPaymentAttempt::getSenderUserId).distinct().toList())
            .forEach(account -> usersById.put(account.getId(), account));
        Map<Long, String> previews = new HashMap<>();
        result.getContent().forEach(attempt -> previews.put(attempt.getId(), redactPreview(attempt.getBodyText())));
        ModelAndView mav = new ModelAndView("admin-views/admin/off-platform-payments");
        mav.addObject("pageTitle", "Payment policy review");
        mav.addObject("attempts", result.getContent()); mav.addObject("usersById", usersById);
        mav.addObject("redactedPreviews", previews); mav.addObject("keyword", keyword);
        mav.addObject("currentPage", safePage); mav.addObject("totalAttempts", result.getTotalElements());
        mav.addObject("hasNext", result.hasNext()); mav.addObject("hasPrevious", result.hasPrevious());
        if (keyword.length() > 100 || page < 0 || page > 10000) { mav.setStatus(HttpStatus.BAD_REQUEST); mav.addObject("errorMessage", "Use a filter of up to 100 characters and a non-negative page."); }
        return mav;
    }
    private static final Pattern CONTACT = Pattern.compile("(?i)[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-z0-9.-]+\\.[a-z]{2,}");
    private static final Pattern LINK = Pattern.compile("(?i)https?://[^\\s<>]+");
    private static final Pattern IBAN = Pattern.compile("(?i)\\b[A-Z]{2}[0-9]{2}(?: ?[A-Z0-9]){11,30}\\b");
    private static final Pattern NUMBERS = Pattern.compile("(?<![a-zA-Z0-9])\\+?\\d(?:[\\d ()-]{3,}\\d)(?![a-zA-Z0-9])");

    static String redactPreview(String text) {
        String redacted = text == null ? "" : text;
        redacted = CONTACT.matcher(redacted).replaceAll("[contact removed]");
        redacted = LINK.matcher(redacted).replaceAll("[link removed]");
        redacted = IBAN.matcher(redacted).replaceAll("[bank detail removed]");
        redacted = NUMBERS.matcher(redacted).replaceAll("[number removed]");
        return redacted.length() > 180 ? redacted.substring(0, 180) + "…" : redacted;
    }
}
