package uk.ac.cf._5.group14.One_To_One.Messaging;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MessageReadStateRepository extends JpaRepository<MessageReadState, Long> {

    @Query("select r from MessageReadState r where r.userId = :userId and r.messageId in :messageIds")
    List<MessageReadState> findByUserIdAndMessageIdIn(@Param("userId") Long userId,
                                                      @Param("messageIds") List<Long> messageIds);

    @Query("select count(m) from MessageThreadMessage m where m.thread.id = :threadId and m.senderUserId <> :userId and not exists (select 1 from MessageReadState r where r.messageId = m.id and r.userId = :userId)")
    long countUnreadForThread(@Param("threadId") Long threadId, @Param("userId") Long userId);

    @Query("select count(m) from MessageThreadMessage m where m.thread.trainerId = :trainerId "
            + "and m.senderUserId = m.thread.clientId and m.thread.status = uk.ac.cf._5.group14.One_To_One.Messaging.MessageThreadStatus.OPEN "
            + "and exists (select 1 from TrainerClientLink l where l.id = m.thread.linkId and l.trainerUserId = :trainerId "
            + "and l.clientUserId = m.thread.clientId and l.status = uk.ac.cf._5.group14.One_To_One.TrainerClient.TrainerClientLinkStatus.ACTIVE) "
            + "and not exists (select 1 from MessageReadState r where r.messageId = m.id and r.userId = :trainerId)")
    long countUnreadForActiveTrainer(@Param("trainerId") Long trainerId);
}
