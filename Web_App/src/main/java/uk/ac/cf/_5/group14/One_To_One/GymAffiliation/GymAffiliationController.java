package uk.ac.cf._5.group14.One_To_One.GymAffiliation;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import uk.ac.cf._5.group14.One_To_One.Users.*;

@Controller @RequiredArgsConstructor
public class GymAffiliationController {
    private final GymAffiliationService service;
    private final UserRepository users;
    private final AuthHelper auth;

    @GetMapping({"/trainer/gyms", "/gym/admin/trainers/affiliations"})
    public String list(Model model, HttpServletResponse response) {
        User viewer = current();
        if (viewer.getRole() != Role.TRAINER && viewer.getRole() != Role.GYM_ADMIN) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        response.setHeader("Cache-Control", "no-store"); response.setHeader("Referrer-Policy", "no-referrer");
        model.addAttribute("trainerSide", viewer.getRole() == Role.TRAINER);
        try { model.addAttribute("connections", service.connections(viewer)); }
        catch (IllegalArgumentException ex) { throw new ResponseStatusException(HttpStatus.FORBIDDEN); }
        return "gym-views/affiliations";
    }

    @PostMapping("/trainer/gyms/request")
    public String request(@RequestParam(defaultValue = "") String gymUsername, Model model, HttpServletResponse response) {
        User viewer = require(Role.TRAINER);
        try {
            service.requestToJoin(viewer.getId(), gymUsername.trim());
            return "redirect:/trainer/gyms?saved=1";
        } catch (IllegalArgumentException | IllegalStateException ex) {
            response.setStatus(400); model.addAttribute("operationError", ex.getMessage());
            model.addAttribute("draftIdentifier", gymUsername.length() <= 100 ? gymUsername : "");
            return list(model, response);
        }
    }

    @PostMapping("/gym/admin/trainers/affiliations/invite")
    public String invite(@RequestParam(defaultValue = "") String trainerUsername, Model model, HttpServletResponse response) {
        User viewer = require(Role.GYM_ADMIN);
        try {
            service.invite(viewer.getId(), trainerUsername.trim());
            return "redirect:/gym/admin/trainers/affiliations?saved=1";
        } catch (IllegalArgumentException | IllegalStateException ex) {
            response.setStatus(400); model.addAttribute("operationError", ex.getMessage());
            model.addAttribute("draftIdentifier", trainerUsername.length() <= 100 ? trainerUsername : "");
            return list(model, response);
        }
    }

    @PostMapping({"/trainer/gyms/decide", "/gym/admin/trainers/affiliations/decide"})
    public String decide(@RequestParam Long trainerId, @RequestParam Long gymId, @RequestParam String action, @RequestParam(defaultValue = "false") boolean confirmed, Model model, HttpServletResponse response) {
        User viewer = current();
        if (viewer.getRole() != Role.TRAINER && viewer.getRole() != Role.GYM_ADMIN) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        try {
            if (!confirmed) throw new IllegalArgumentException("Confirm the effect on this gym connection before continuing.");
            service.decide(viewer.getId(), trainerId, gymId, action);
            return "redirect:" + (viewer.getRole() == Role.TRAINER ? "/trainer/gyms" : "/gym/admin/trainers/affiliations") + "?saved=1";
        } catch (IllegalArgumentException | IllegalStateException ex) {
            response.setStatus(400); model.addAttribute("operationError", ex.getMessage());
            return list(model, response);
        }
    }

    @GetMapping({"/trainer/gyms/history", "/gym/admin/trainers/affiliations/history"})
    public String history(@RequestParam Long trainerId, @RequestParam Long gymId, Model model, HttpServletResponse response) {
        User viewer = current();
        try { model.addAttribute("events", service.history(viewer, trainerId, gymId)); }
        catch (IllegalArgumentException ex) { throw new ResponseStatusException(HttpStatus.NOT_FOUND); }
        model.addAttribute("trainerSide", viewer.getRole() == Role.TRAINER);
        model.addAttribute("viewerId", viewer.getId());
        response.setHeader("Cache-Control", "no-store"); response.setHeader("Referrer-Policy", "no-referrer");
        return "gym-views/affiliation-history";
    }

    private User current() {
        User sessionUser = auth.getAuthenticatedUser();
        if (sessionUser == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return users.findById(sessionUser.getId()).filter(User::isEnabled).orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
    }
    private User require(Role role) {
        User user = current();
        if (user.getRole() != role) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return user;
    }
}
