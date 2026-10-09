package uk.ac.cf._5.group14.One_To_One.Messaging;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ThreadMessageRepository extends JpaRepository<Message, Long> {

    List<Message> findByThread_IdOrderByCreatedAtAsc(Long threadId);

    Optional<Message> findTop1ByThread_IdOrderByCreatedAtDesc(Long threadId);

    List<Message> findByThread_IdOrderByCreatedAtAscIdAsc(Long threadId);

    Optional<Message> findTop1ByThread_IdOrderByCreatedAtDescIdDesc(Long threadId);

    List<Message> findTop30ByThread_IdOrderByIdDesc(Long threadId);

    List<Message> findTop30ByThread_IdAndIdLessThanOrderByIdDesc(Long threadId, Long beforeId);

    @Query("select m.id from MessageThreadMessage m where m.thread.id = :threadId and m.senderUserId <> :userId and not exists (select 1 from MessageReadState r where r.messageId = m.id and r.userId = :userId)")
    List<Long> findUnreadMessageIds(@Param("threadId") Long threadId, @Param("userId") Long userId);

    @Query("select m.id from MessageThreadMessage m where m.thread.id = :threadId and m.id <= :upToId and m.senderUserId <> :userId and not exists (select 1 from MessageReadState r where r.messageId = m.id and r.userId = :userId)")
    List<Long> findUnreadMessageIdsThrough(@Param("threadId") Long threadId, @Param("userId") Long userId, @Param("upToId") Long upToId);
}
