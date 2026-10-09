package uk.ac.cf._5.group14.One_To_One.Verification;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import uk.ac.cf._5.group14.One_To_One.Users.*;
import uk.ac.cf._5.group14.One_To_One.GymProfile.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
class GymTrainerInvitationServiceTest {
    @Autowired GymTrainerInvitationService invitations;
    @Autowired UserRepository users;
    @Autowired GymProfileRepository gyms;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @MockitoBean TrainerVerificationService verification;

    @Test
    void failedVerificationRollsBackTheNewAccountAndAuthorities() {
        String unique = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        User admin = new User("admin" + unique + "@example.com", "Gym", "Admin", "admin_" + unique, "password123");
        admin.setRole(Role.GYM_ADMIN);
        admin = users.saveAndFlush(admin);
        admin.setGymId(gyms.saveAndFlush(new GymProfile(admin.getId(), "Rollback gym")).getId());
        admin = users.saveAndFlush(admin);
        TrainerVerificationRequestForm form = new TrainerVerificationRequestForm();
        form.setEmail("trainer" + unique + "@example.com"); form.setUsername("trainer_" + unique);
        form.setFirstName("Trainer"); form.setLastName("Example"); form.setTemporaryPassword("LocalExample!42");
        when(verification.createVerificationRequest(anyLong(), eq(admin.getGymId()), isNull(), eq(admin.getId()))).thenThrow(new IllegalStateException("Unavailable"));
        Long adminId = admin.getId();
        assertThrows(IllegalStateException.class, () -> invitations.create(adminId, form));
        assertFalse(users.existsByEmailIgnoreCase(form.getEmail()));
        assertFalse(users.existsByUsernameIgnoreCase(form.getUsername()));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM users_roles WHERE username = ?", Integer.class, form.getUsername()));
    }
}
