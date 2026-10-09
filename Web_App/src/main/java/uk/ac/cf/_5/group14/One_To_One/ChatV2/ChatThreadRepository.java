package uk.ac.cf._5.group14.One_To_One.ChatV2;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.ac.cf._5.group14.One_To_One.Users.User;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;

@Repository
public interface ChatThreadRepository extends JpaRepository<ChatThread, Long> {
    List<ChatThread> findByUserOrderByPinnedDescUpdatedAtDesc(User user);
    long countByUserAndTitleContainingIgnoreCase(User user, String query);
    long countByUserAndFolderAndTitleContainingIgnoreCase(User user, ChatFolder folder, String query);
    @EntityGraph(attributePaths = "folder")
    List<ChatThread> findByUserAndTitleContainingIgnoreCaseOrderByPinnedDescUpdatedAtDescIdDesc(User user, String query, Pageable page);
    @EntityGraph(attributePaths = "folder")
    List<ChatThread> findByUserAndFolderAndTitleContainingIgnoreCaseOrderByPinnedDescUpdatedAtDescIdDesc(User user, ChatFolder folder, String query, Pageable page);

    List<ChatThread> findByUserAndArchivedFalseOrderByPinnedDescUpdatedAtDesc(User user);
    List<ChatThread> findByUserAndFolderAndArchivedFalseOrderByPinnedDescUpdatedAtDesc(User user, ChatFolder folder);
    Optional<ChatThread> findByIdAndUser(Long id, User user);
    long countByUserAndArchivedFalse(User user);
}
