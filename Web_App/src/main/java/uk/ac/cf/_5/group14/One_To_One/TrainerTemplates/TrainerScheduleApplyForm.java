package uk.ac.cf._5.group14.One_To_One.TrainerTemplates;

import jakarta.validation.constraints.*;

public class TrainerScheduleApplyForm {

    @NotNull
    private Long clientId;

    @NotBlank
    @Size(max = 10)
    private String start;

    @NotBlank
    @Size(max = 10)
    private String end;

    private boolean idempotent = true;

    @Size(max = 64)
    private String expectedApplyRevision;

    public String getExpectedApplyRevision() { return expectedApplyRevision; }
    public void setExpectedApplyRevision(String revision) { this.expectedApplyRevision = revision; }

    public Long getClientId() { return clientId; }
    public void setClientId(Long clientId) { this.clientId = clientId; }

    public String getStart() { return start; }
    public void setStart(String start) { this.start = start; }

    public String getEnd() { return end; }
    public void setEnd(String end) { this.end = end; }

    public boolean isIdempotent() { return idempotent; }
    public void setIdempotent(boolean idempotent) { this.idempotent = idempotent; }
}
