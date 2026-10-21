package com.devhub.chat;

import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface ChatChannelRepository extends MongoRepository<ChatChannel, String> {
    List<ChatChannel> findByOrganizationIdAndScope(String organizationId, ChannelScope scope);
    List<ChatChannel> findByProjectId(String projectId);
}
