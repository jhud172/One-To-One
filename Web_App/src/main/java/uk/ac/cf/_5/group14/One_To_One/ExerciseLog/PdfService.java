package uk.ac.cf._5.group14.One_To_One.ExerciseLog;

import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.List;

@Slf4j
@Service
public class PdfService {

    private String rating(Integer value) {
        return value == null ? "-" : value.toString();
    }

    public byte[] generateLogsPdf(List<ExerciseLog> logs) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, out);
            document.open();

            Font titleFont = new Font(Font.HELVETICA, 20, Font.BOLD);
            Paragraph title = new Paragraph("Your Exercise Logs", titleFont);
            title.setSpacingAfter(20);
            document.add(title);

            document.add(new Paragraph("Personal training reflections. Ratings use a 1-4 scale: 1 is low; 4 is high."));
            PdfPTable table = new PdfPTable(6);
            table.setWidthPercentage(100);
            table.setSpacingBefore(12);
            table.setWidths(new float[]{1.4f, 1.1f, 1.1f, 1.1f, 1.1f, 3.2f});
            table.setHeaderRows(1);
            addHeader(table, "Date");
            addHeader(table, "Mood Before");
            addHeader(table, "Mood After");
            addHeader(table, "Confidence");
            addHeader(table, "Duration (min)");
            addHeader(table, "Notes");

            for (ExerciseLog log : logs) {
                table.addCell(log.getDate() == null ? "-" : log.getDate().toString());
                table.addCell(rating(log.getMoodBefore()));
                table.addCell(rating(log.getMoodAfter()));
                table.addCell(rating(log.getConfidence()));
                table.addCell(log.getDurationMinutes() == null ? "-" : log.getDurationMinutes().toString());
                table.addCell(log.getComments() == null ? "" : log.getComments());
            }

            document.add(table);
            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate exercise log PDF", e);
            throw new IllegalStateException("Exercise log export failed", e);
        }
    }

    private void addHeader(PdfPTable table, String text) {
        Font font = new Font(Font.HELVETICA, 9, Font.BOLD);
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(4);
        table.addCell(cell);
    }
}
