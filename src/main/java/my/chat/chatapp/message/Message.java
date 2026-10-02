package my.chat.chatapp.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("messages")
public class Message {

    @Id
    private Long id;
    private Long channelId;
    private Long authorId;
    private String text;
    private Instant sentAt;
}
