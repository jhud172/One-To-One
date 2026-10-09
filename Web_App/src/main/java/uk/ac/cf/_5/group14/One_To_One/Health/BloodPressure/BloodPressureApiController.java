package uk.ac.cf._5.group14.One_To_One.Health.BloodPressure;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.ac.cf._5.group14.One_To_One.Users.AuthHelper;
import uk.ac.cf._5.group14.One_To_One.Users.User;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/blood-pressure")
public class BloodPressureApiController {

    private final AuthHelper authHelper;
    private final BloodPressureService service;

    public BloodPressureApiController(AuthHelper authHelper, BloodPressureService service) {
        this.authHelper = authHelper;
        this.service = service;
    }

    @PostMapping(consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> create(@Valid @RequestBody BloodPressureReading reading, HttpSession session) {
        User user = authHelper.getAuthenticatedUser(session);
        if (user == null) return ResponseEntity.status(401).build();
        reading.setUser(user);
        reading.setSource(BloodPressureReading.ReadingSource.MANUAL);
        try {
            BloodPressureReading saved = service.save(reading);
            return ResponseEntity.ok(Map.of(
                    "id", saved.getId(),
                    "category", BpCategory.classify(saved.getSystolic(), saved.getDiastolic()).label));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping(produces = "application/json")
    public ResponseEntity<?> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            HttpSession session) {
        User user = authHelper.getAuthenticatedUser(session);
        if (user == null) return ResponseEntity.status(401).build();
        LocalDate resolvedFrom = from != null ? from : LocalDate.now().minusDays(30);
        LocalDate resolvedTo = to != null ? to : LocalDate.now();
        if (resolvedFrom.isAfter(resolvedTo)) return ResponseEntity.badRequest().body(Map.of("error", "Invalid date range"));
        return ResponseEntity.ok(service.getRange(user, resolvedFrom, resolvedTo));
    }
}
