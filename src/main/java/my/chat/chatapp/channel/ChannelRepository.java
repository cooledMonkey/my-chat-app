package my.chat.chatapp.channel;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface ChannelRepository extends ReactiveCrudRepository<Channel, Long> {

    @Query("""
            SELECT c.* FROM channels c
            JOIN channel_members mine ON mine.channel_id = c.id AND mine.user_id = :userId
            JOIN channel_members theirs ON theirs.channel_id = c.id AND theirs.user_id = :otherUserId
            WHERE c.type = 'DIRECT'
            """)
    Mono<Channel> findDirectChannel(@Param("userId") Long userId, @Param("otherUserId") Long otherUserId);
}
