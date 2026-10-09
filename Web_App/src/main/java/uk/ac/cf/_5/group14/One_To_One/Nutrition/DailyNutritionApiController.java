package uk.ac.cf._5.group14.One_To_One.Nutrition;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.ac.cf._5.group14.One_To_One.Nutrition.DailyNutritionLogService.DailyNutritionRangeSummary;
import uk.ac.cf._5.group14.One_To_One.Users.AuthHelper;
import uk.ac.cf._5.group14.One_To_One.Users.User;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/nutrition")
public class DailyNutritionApiController {

    private final DailyNutritionLogService service;
    private final AuthHelper authHelper;

    public DailyNutritionApiController(DailyNutritionLogService service, AuthHelper authHelper) {
        this.service = service;
        this.authHelper = authHelper;
    }

    @GetMapping("/range")
    public List<DailyNutritionRangeSummary> getRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end
    ) {
        User user = requireUser();
        try {
            return service.getRange(user, start, end);
        } catch (IllegalArgumentException invalid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid nutrition date range");
        }
    }

    private User requireUser() {
        User user = authHelper.getAuthenticatedUser();
        if (user == null || user.getId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return user;
    }
}
