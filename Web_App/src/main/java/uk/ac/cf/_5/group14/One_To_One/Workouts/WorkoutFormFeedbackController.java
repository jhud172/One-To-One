package uk.ac.cf._5.group14.One_To_One.Workouts;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import uk.ac.cf._5.group14.One_To_One.Users.AuthHelper;
import uk.ac.cf._5.group14.One_To_One.Users.User;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/workouts")
public class WorkoutFormFeedbackController {

    private final WorkoutFormFeedbackService feedbackService;
    private final AuthHelper authHelper;

    public WorkoutFormFeedbackController(WorkoutFormFeedbackService feedbackService, AuthHelper authHelper) {
        this.feedbackService = feedbackService;
        this.authHelper = authHelper;
    }

    @PostMapping("/studio/{sessionId}/sets/{setId}/video")
    public ResponseEntity<?> upload(@PathVariable Long sessionId,
                                    @PathVariable Long setId,
                                    @RequestParam("video") MultipartFile video) {
        User user = authHelper.getAuthenticatedUser();
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        try {
            WorkoutSetVideo saved = feedbackService.storeVideo(user, sessionId, setId, video);
            return ResponseEntity.ok(Map.of("status", saved.getStatus().name(), "videoId", saved.getId()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        } catch (IOException ex) {
            return ResponseEntity.status(500).body(Map.of("message", "Unable to store video"));
        }
    }

    @GetMapping("/studio/{sessionId}/sets/{setId}/video/latest")
    public ResponseEntity<?> latest(@PathVariable Long sessionId,
                                    @PathVariable Long setId) {
        User user = authHelper.getAuthenticatedUser();
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        try {
            WorkoutSetVideo video = feedbackService.getLatestVideo(user, sessionId, setId);
            return ResponseEntity.ok(feedbackService.buildFeedbackPayload(video));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/studio/{sessionId}/sets/{setId}/video/{videoId}")
    public ResponseEntity<?> delete(@PathVariable Long sessionId,
                                    @PathVariable Long setId,
                                    @PathVariable Long videoId) {
        User user = authHelper.getAuthenticatedUser();
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        try {
            feedbackService.deleteVideo(user, sessionId, setId, videoId);
            return ResponseEntity.ok(Map.of("deleted", true));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.notFound().build();
        } catch (IOException ex) {
            return ResponseEntity.status(500).body(Map.of("message", "Unable to remove video"));
        }
    }
}
