package de.dmos.rtsync.server.simple;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;

import de.dmos.rtsync.serializers.SimpleMessageSerialization;
import de.dmos.rtsync.server.WebSocketMessageBrokerConfig;

@Configuration
@EnableWebSocketMessageBroker
public class SimpleWebSocketMessageBrokerConfig extends WebSocketMessageBrokerConfig
{
  public SimpleWebSocketMessageBrokerConfig()
  {
	super(SimpleMessageSerialization.getCombinedMessageConverter());
  }
}