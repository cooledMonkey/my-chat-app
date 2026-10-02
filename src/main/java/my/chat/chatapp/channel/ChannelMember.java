package my.chat.chatapp.channel;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("channel_members")
public class ChannelMember {

    @Id
    private Long id;
    private Long channelId;
    private Long userId;
    private ChannelRole role;
}
