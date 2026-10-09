package uk.ac.cf._5.group14.One_To_One.Verification;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import uk.ac.cf._5.group14.One_To_One.Users.*;
import java.nio.charset.StandardCharsets;

/** Account and review request must commit together. This does not send an invitation email. */
@Service
@Validated
@RequiredArgsConstructor
public class GymTrainerInvitationService {
    private final UserRepository users;
    private final UserService userService;
    private final TrainerVerificationService verification;

    @Transactional
    public User create(Long adminId, @Valid TrainerVerificationRequestForm form) {
        User admin = users.findById(adminId)
            .filter(user -> user.getRole() == Role.GYM_ADMIN && user.getGymId() != null)
            .orElseThrow(() -> new IllegalArgumentException("Access denied"));
        if (form.getTemporaryPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Password exceeds the supported length");
        }
        User trainer = new User(form.getEmail(), form.getFirstName().trim(), form.getLastName().trim(), form.getUsername(), form.getTemporaryPassword());
        trainer.setRole(Role.TRAINER);
        trainer.setGymId(admin.getGymId());
        trainer.setTrainerVerified(false);
        User saved = userService.saveUser(trainer);
        verification.createVerificationRequest(saved.getId(), admin.getGymId(), form.getNotes(), admin.getId());
        return saved;
    }
}
