package my.chat.chatapp.channel;

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
@Table("channels")
public class Channel {

    @Id
    private Long id;
    private String name;
    private ChannelType type;
    private Long createdBy;
    private Instant createdAt;
}
