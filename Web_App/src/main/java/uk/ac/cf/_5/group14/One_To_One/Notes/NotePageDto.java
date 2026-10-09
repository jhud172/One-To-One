package uk.ac.cf._5.group14.One_To_One.Notes;

import java.util.List;
import org.springframework.data.domain.Page;

public record NotePageDto(List<NoteSummaryDto> notes, int page, int pageCount, long total) {
    public static NotePageDto from(Page<Note> page) {
        return new NotePageDto(page.getContent().stream().map(NoteSummaryDto::from).toList(),
                page.getNumber() + 1, Math.max(1, page.getTotalPages()), page.getTotalElements());
    }
}
