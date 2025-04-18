package de.dmos.rtsync.server.project;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;

import de.dmos.rtsync.serializers.ProjectMessageSerialization;
import de.dmos.rtsync.server.WebSocketMessageBrokerConfig;

@Configuration
@EnableWebSocketMessageBroker
public class ProjectWebSocketMessageBrokerConfig extends WebSocketMessageBrokerConfig
{
  public ProjectWebSocketMessageBrokerConfig()
  {
	super(ProjectMessageSerialization.getCombinedMessageConverter());
  }
}