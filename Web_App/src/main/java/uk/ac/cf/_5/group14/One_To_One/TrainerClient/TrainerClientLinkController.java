package uk.ac.cf._5.group14.One_To_One.TrainerClient;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import uk.ac.cf._5.group14.One_To_One.Users.AuthHelper;
import uk.ac.cf._5.group14.One_To_One.Users.Role;
import uk.ac.cf._5.group14.One_To_One.Users.User;
import uk.ac.cf._5.group14.One_To_One.Users.UserRepository;
import uk.ac.cf._5.group14.One_To_One.Users.UserService;

import java.util.Map;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.ui.Model;

@Controller
public class TrainerClientLinkController {

    private final AuthHelper authHelper;
    private final UserService userService;
    private final UserRepository userRepository;
    private final TrainerClientLinkService trainerClientLinkService;

    public TrainerClientLinkController(AuthHelper authHelper,
                                     UserService userService,
                                     UserRepository userRepository,
                                     TrainerClientLinkService trainerClientLinkService) {
        this.authHelper = authHelper;
        this.userService = userService;
        this.userRepository = userRepository;
        this.trainerClientLinkService = trainerClientLinkService;
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

    @PostMapping({"/client/trainers/{trainerId}/request", "/trainers/{trainerId}/request"})
    public ModelAndView requestTrainer(@PathVariable Long trainerId, RedirectAttributes redirectAttributes) {
        User client = currentUserOrThrow();

        if (client.getRole() != Role.CLIENT) {
            return new ModelAndView("redirect:/access-denied");
        }

        if (isAccountIncomplete(client)) {
            redirectAttributes.addFlashAttribute(
                "verifyError",
                "Verify your email and phone before requesting a trainer."
            );
            return new ModelAndView("redirect:/client/trainers");
        }

        try {
            trainerClientLinkService.requestLink(client.getId(), trainerId);
            redirectAttributes.addFlashAttribute("trainerRequestSent", true);
        } catch (TrainerClientLinkException ex) {
            return redirectClientTrainerError(ex.getReason());
        } catch (IllegalArgumentException ex) {
            return new ModelAndView("redirect:/client/trainers?error=invalid");
        }

        return new ModelAndView("redirect:/client/trainers");
    }

    private boolean isAccountIncomplete(User user) {
        if (user == null) {
            return true;
        }
        boolean emailUnverified = !user.isEmailVerified();
        boolean phoneUnverified = user.getPhoneNumber() != null && !user.getPhoneNumber().isBlank() && !user.isPhoneVerified();
        return emailUnverified || phoneUnverified;
    }

    @PostMapping("/client/trainers/{trainerId}/withdraw")
    public ModelAndView withdrawTrainerRequest(@PathVariable Long trainerId, RedirectAttributes redirectAttributes) {
        User client = currentUserOrThrow();
        if (client.getRole() != Role.CLIENT) throw new org.springframework.security.access.AccessDeniedException("Client access required");
        try {
            trainerClientLinkService.withdrawRequest(client.getId(), trainerId);
            redirectAttributes.addFlashAttribute("trainerRequestWithdrawn", true);
            return new ModelAndView("redirect:/client/trainers");
        } catch (IllegalArgumentException ex) {
            return new ModelAndView("redirect:/client/trainers?error=invalid");
        }
    }

    @GetMapping("/trainer/requests")
    public ModelAndView trainerRequestsRedirect() {
        return new ModelAndView("redirect:/trainer/clients");
    }

    @GetMapping("/trainer/clients")
    public ModelAndView trainerClients(@RequestParam(value = "error", required = false) String error, Model model) {
        User trainer = currentUserOrThrow();
        ModelAndView mav = new ModelAndView("trainer-views/trainer/clients");
        for (String key : new String[]{"relationshipAccepted", "relationshipDeclined", "relationshipPaused", "relationshipEnded", "relationshipResumed"}) {
            if (model.containsAttribute(key)) mav.addObject(key, model.getAttribute(key));
        }
        mav.addObject("pageTitle", "Trainer Clients");
        mav.addObject("trainerReady", trainer.isEnabled() && trainer.isTrainerVerified());

        List<TrainerClientLink> allLinks = trainerClientLinkService.listTrainerClients(trainer.getId());
        List<TrainerClientLink> requests = allLinks.stream()
                .filter(link -> link.getStatus() == TrainerClientLinkStatus.REQUESTED)
                .toList();
        List<TrainerClientLink> current = allLinks.stream()
                .filter(link -> link.getStatus() == TrainerClientLinkStatus.ACTIVE || link.getStatus() == TrainerClientLinkStatus.PAUSED)
                .toList();

        mav.addObject("requests", requests);
        mav.addObject("currentLinks", current);

        List<Long> clientIds = allLinks.stream().map(TrainerClientLink::getClientUserId).distinct().toList();
        Map<Long, User> clientsById = userRepository.findAllById(clientIds)
                .stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        mav.addObject("clientsById", clientsById);
        mav.addObject("error", error);
        return mav;
    }

    @PostMapping("/trainer/clients/{clientId}/accept")
    public ModelAndView accept(@PathVariable Long clientId, RedirectAttributes redirectAttributes) {
        User trainer = currentUserOrThrow();
        try {
            trainerClientLinkService.acceptRequest(trainer.getId(), clientId);
            redirectAttributes.addFlashAttribute("relationshipAccepted", true);
        } catch (org.springframework.security.access.AccessDeniedException ex) {
            return new ModelAndView("redirect:/access-denied");
        } catch (TrainerClientLinkException ex) {
            return redirectTrainerClientError(ex.getReason());
        } catch (IllegalArgumentException ex) {
            return new ModelAndView("redirect:/trainer/clients?error=invalid");
        }
        return new ModelAndView("redirect:/trainer/clients");
    }

    @PostMapping("/trainer/clients/{clientId}/decline")
    public ModelAndView decline(@PathVariable Long clientId, RedirectAttributes redirectAttributes) {
        User trainer = currentUserOrThrow();
        try {
            trainerClientLinkService.declineRequest(trainer.getId(), clientId);
            redirectAttributes.addFlashAttribute("relationshipDeclined", true);
        } catch (org.springframework.security.access.AccessDeniedException ex) {
            return new ModelAndView("redirect:/access-denied");
        } catch (TrainerClientLinkException ex) {
            return redirectTrainerClientError(ex.getReason());
        } catch (IllegalArgumentException ex) {
            return new ModelAndView("redirect:/trainer/clients?error=invalid");
        }
        return new ModelAndView("redirect:/trainer/clients");
    }

    @PostMapping("/trainer/clients/{clientId}/pause")
    public ModelAndView pause(@PathVariable Long clientId, RedirectAttributes redirectAttributes) {
        User trainer = currentUserOrThrow();
        try {
            trainerClientLinkService.pauseLink(trainer.getId(), clientId);
            redirectAttributes.addFlashAttribute("relationshipPaused", true);
        } catch (org.springframework.security.access.AccessDeniedException ex) {
            return new ModelAndView("redirect:/access-denied");
        } catch (TrainerClientLinkException ex) {
            return redirectTrainerClientError(ex.getReason());
        } catch (IllegalArgumentException ex) {
            return new ModelAndView("redirect:/trainer/clients?error=invalid");
        }
        return new ModelAndView("redirect:/trainer/clients");
    }

    @PostMapping("/trainer/clients/{clientId}/resume")
    public ModelAndView resume(@PathVariable Long clientId, RedirectAttributes redirectAttributes) {
        User trainer = currentUserOrThrow();
        try {
            trainerClientLinkService.resumeLink(trainer.getId(), clientId);
            redirectAttributes.addFlashAttribute("relationshipResumed", true);
        } catch (org.springframework.security.access.AccessDeniedException ex) {
            return new ModelAndView("redirect:/access-denied");
        } catch (TrainerClientLinkException ex) {
            return redirectTrainerClientError(ex.getReason());
        } catch (IllegalArgumentException ex) {
            return new ModelAndView("redirect:/trainer/clients?error=invalid");
        }
        return new ModelAndView("redirect:/trainer/clients");
    }

    @PostMapping("/trainer/clients/{clientId}/end")
    public ModelAndView end(@PathVariable Long clientId, RedirectAttributes redirectAttributes) {
        User trainer = currentUserOrThrow();
        try {
            trainerClientLinkService.endLink(trainer.getId(), clientId);
            redirectAttributes.addFlashAttribute("relationshipEnded", true);
        } catch (org.springframework.security.access.AccessDeniedException ex) {
            return new ModelAndView("redirect:/access-denied");
        } catch (TrainerClientLinkException ex) {
            return redirectTrainerClientError(ex.getReason());
        } catch (IllegalArgumentException ex) {
            return new ModelAndView("redirect:/trainer/clients?error=invalid");
        }
        return new ModelAndView("redirect:/trainer/clients");
    }

    private ModelAndView redirectClientTrainerError(TrainerClientLinkException.Reason reason) {
        return switch (reason) {
            case CLIENT_ALREADY_HAS_ACTIVE_TRAINER -> new ModelAndView("redirect:/client/trainers?error=active");
            case TRAINER_NOT_VERIFIED -> new ModelAndView("redirect:/client/trainers?error=trainer-unverified");
        };
    }

    private ModelAndView redirectTrainerClientError(TrainerClientLinkException.Reason reason) {
        return switch (reason) {
            case CLIENT_ALREADY_HAS_ACTIVE_TRAINER -> new ModelAndView("redirect:/trainer/clients?error=client-active");
            case TRAINER_NOT_VERIFIED -> new ModelAndView("redirect:/trainer/clients?error=trainer-unverified");
        };
    }

    @GetMapping("/client/my-trainer")
    public ModelAndView myTrainerRedirect() {
        return new ModelAndView("redirect:/client/trainers");
    }

    @GetMapping("/client/trainers")
    public ModelAndView myTrainers(@RequestParam(value = "error", required = false) String error,
                                   @RequestParam(value = "q", required = false) String q, Model model) {
        User client = currentUserOrThrow();
        if (client.getRole() != Role.CLIENT) throw new org.springframework.security.access.AccessDeniedException("Client access required");

        ModelAndView mav = new ModelAndView("client-views/client/trainers");
        for (String key : new String[]{"trainerRequestSent", "trainerRequestWithdrawn", "verifyError"}) {
            if (model.containsAttribute(key)) mav.addObject(key, model.getAttribute(key));
        }
        mav.addObject("pageTitle", "Trainers");

        TrainerClientLink active = trainerClientLinkService.getActiveLinkForClient(client.getId());
        mav.addObject("activeLink", active);
        List<TrainerClientLink> clientLinks = trainerClientLinkService.listClientTrainerLinks(client.getId());
        List<TrainerClientLink> pending = clientLinks.stream().filter(link -> link.getStatus() == TrainerClientLinkStatus.REQUESTED).toList();
        mav.addObject("pendingLinks", pending);
        mav.addObject("pausedLinks", clientLinks.stream().filter(link -> link.getStatus() == TrainerClientLinkStatus.PAUSED).toList());
        mav.addObject("pendingTrainerIds", pending.stream().map(TrainerClientLink::getTrainerUserId).collect(Collectors.toSet()));
        mav.addObject("trainersById", userRepository.findAllById(clientLinks.stream().map(TrainerClientLink::getTrainerUserId).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, trainer -> trainer)));

        if (active != null) {
            mav.addObject("trainer", userRepository.findById(active.getTrainerUserId()).orElse(null));
        } else {
            mav.addObject("trainer", null);
        }

        List<User> trainers = userRepository.findByRoleAndTrainerVerifiedTrueAndEnabledTrue(Role.TRAINER);
        if (q != null && !q.isBlank()) {
            String query = q.trim().toLowerCase(java.util.Locale.ROOT);
            trainers = trainers.stream()
                    .filter(t -> {
                        String fullName = (t.getFullName() == null) ? "" : t.getFullName().toLowerCase(java.util.Locale.ROOT);
                        String username = (t.getUsername() == null) ? "" : t.getUsername().toLowerCase(java.util.Locale.ROOT);
                        return fullName.contains(query) || username.contains(query);
                    })
                    .toList();
        }

        mav.addObject("q", q);
        mav.addObject("trainers", trainers);
        mav.addObject("error", error);
        return mav;
    }
}
