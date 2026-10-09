package uk.ac.cf._5.group14.One_To_One.TrainerTemplates;

import jakarta.validation.constraints.*;

public class TrainerScheduleQuestionForm {

    @NotBlank
    @Size(max = 300)
    private String prompt;

    private boolean required = true;

    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }

    public boolean isRequired() { return required; }
    public void setRequired(boolean required) { this.required = required; }
}
