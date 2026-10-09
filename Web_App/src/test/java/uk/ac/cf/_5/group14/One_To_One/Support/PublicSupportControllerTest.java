package uk.ac.cf._5.group14.One_To_One.Support;

import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import uk.ac.cf._5.group14.One_To_One.Users.AuthHelper;
import uk.ac.cf._5.group14.One_To_One.Users.User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PublicSupportControllerTest {
    private final SupportRequestRepository repository = mock(SupportRequestRepository.class);
    private final AuthHelper auth = mock(AuthHelper.class);
    private final PublicSupportController controller = new PublicSupportController(repository, auth);

    @Test
    void signedInQuestionUsesAccountEmailWithoutAnAbsentFormField() {
        User member = new User();
        member.setEmail("member@example.test");
        member.setFirstName("Demo");
        member.setLastName("Member");
        when(auth.getAuthenticatedUser()).thenReturn(member);
        var redirect = new RedirectAttributesModelMap();

        assertThat(controller.submitFeedback("QUERY", "Calendar question", "How do I move a session?", null, null, "on", redirect))
                .isEqualTo("redirect:/support");
        var request = org.mockito.ArgumentCaptor.forClass(SupportRequest.class);
        verify(repository).save(request.capture());
        assertThat(request.getValue().getSubmitterEmail()).isEqualTo("member@example.test");
        assertThat(request.getValue().getUser()).isSameAs(member);
        assertThat(redirect.getFlashAttributes()).containsKey("feedbackSuccess").doesNotContainKey("feedbackMessage");
    }

    @Test
    void validationPreservesDraftWithoutSavingInvalidGuestQuestion() {
        var redirect = new RedirectAttributesModelMap();
        controller.submitFeedback("QUERY", "Need help", "Keep my details", "Guest", "invalid", "on", redirect);

        verify(repository, never()).save(any());
        assertThat(redirect.getFlashAttributes().get("feedbackMessage")).isEqualTo("Keep my details");
        assertThat(redirect.getFlashAttributes().get("feedbackSubject")).isEqualTo("Need help");
        assertThat(redirect.getFlashAttributes().get("feedbackReply")).isEqualTo(true);
        assertThat(redirect.getFlashAttributes()).containsKey("feedbackError");
        assertThat(redirect.asMap()).isEmpty();
    }

    @Test
    void contextIsBoundedAndCannotReplaceValidationDraft() {
        var model = new ExtendedModelMap();
        controller.support(model, "x".repeat(200));
        assertThat(model.get("feedbackSubject")).isEqualTo("x".repeat(180));
        model.addAttribute("feedbackSubject", "My existing draft");
        controller.support(model, "Different question");
        assertThat(model.get("feedbackSubject")).isEqualTo("My existing draft");
    }
}
