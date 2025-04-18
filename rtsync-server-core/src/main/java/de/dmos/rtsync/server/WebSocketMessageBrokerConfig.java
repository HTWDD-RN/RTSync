package de.dmos.rtsync.server;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.StompWebSocketEndpointRegistration;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;

import de.dmos.rtsync.network.CommunicationConstants;
import de.dmos.rtsync.network.EndpointPaths;

public abstract class WebSocketMessageBrokerConfig implements WebSocketMessageBrokerConfigurer
{
  private final MessageConverter _combinedMessageConverter;

  protected WebSocketMessageBrokerConfig(MessageConverter combinedMessageConverter)
  {
	_combinedMessageConverter = combinedMessageConverter;
  }

  @Override
  public void configureMessageBroker(MessageBrokerRegistry config) {
	config.enableSimpleBroker(EndpointPaths.TOPIC, EndpointPaths.QUEUE);
	config.setApplicationDestinationPrefixes(EndpointPaths.APP);
  }

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
	StompWebSocketEndpointRegistration wsEndpoint = registry
		.addEndpoint(EndpointPaths.WEB_SOCKET)
		.setHandshakeHandler(new SimpleUnauthorizedPrincipalSettingHandshakeHandler());
	if ( CommunicationConstants.USE_SOCK_JS )
	{
	  wsEndpoint.withSockJS().setStreamBytesLimit(1024 * 1024);
	}

	registry.setErrorHandler(new StompErrorHandler()); // This seems not to log all the errors.
  }

  @Override
  public boolean configureMessageConverters(List<MessageConverter> messageConverters)
  {
	// If @EnableAutoConfiguration is used, then it finds the WebSocketMessagingAutoConfiguration which in turn adds the MappingJackson2MessageConverter.
	// That would ruin otter's and our custom message conversion and lead to communication problems between server and client.
	// We go safe here by discarding all other converters, even if this may be redundant since the RTSyncSimpleServer explicitly excludes the WebSocketMessagingAutoConfiguration.
	messageConverters.clear();

	messageConverters.add(_combinedMessageConverter);
	return false;
  }

  private static class StompErrorHandler extends StompSubProtocolErrorHandler
  {
	private static final Logger LOG = LoggerFactory.getLogger(StompErrorHandler.class);

	@Override
	public Message<byte[]> handleErrorMessageToClient(Message<byte[]> errorMessage)
	{
	  LOG.error("An error occured when handling error message to client: {}", errorMessage);
	  return super.handleErrorMessageToClient(errorMessage);
	}

	@Override
	public Message<byte[]> handleClientMessageProcessingError(Message<byte[]> clientMessage, Throwable ex)
	{
	  LOG.error("Error while processing client message.", ex);
	  return super.handleClientMessageProcessingError(clientMessage, ex);
	}
  }
}