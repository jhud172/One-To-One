package uk.ac.cf._5.group14.One_To_One.Checkins;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TrainerCheckInQuestionRepository extends JpaRepository<TrainerCheckInQuestion, Long> {

    @org.springframework.data.jpa.repository.Query("select question from TrainerCheckInQuestion question where question.templateId = :templateId order by question.orderIndex, question.id")
    List<TrainerCheckInQuestion> findByTemplateIdOrderByOrderIndexAsc(@org.springframework.data.repository.query.Param("templateId") Long templateId);

    @org.springframework.data.jpa.repository.Query("select question.templateId, count(question) from TrainerCheckInQuestion question where question.templateId in :ids group by question.templateId")
    List<Object[]> countByTemplateIds(@org.springframework.data.repository.query.Param("ids") List<Long> ids);
}
