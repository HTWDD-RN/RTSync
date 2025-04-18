package de.dmos.rtsync.client;

import java.lang.reflect.Type;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;

import de.dmos.rtsync.client.internalinterfaces.SimpleStompSessionSync;
import de.dmos.rtsync.message.UserCursors;
import de.dmos.rtsync.message.SimpleStateMessage;
import de.dmos.rtsync.message.Subscriber;
import de.dmos.rtsync.message.TaggedUserOperation;
import de.dmos.rtsync.network.EndpointPaths;

public class SimpleStompSessionHandler extends AbstractRTSyncStompSessionHandler
{
  private static final Logger LOG = LoggerFactory.getLogger(SimpleStompSessionHandler.class);

  public SimpleStompSessionHandler(SimpleStompSessionSync stompSync)
  {
	super(stompSync);
  }

  @Override
  protected Type getTypeForDestination(String destination)
  {
	switch (destination)
	{
	  case EndpointPaths.TOPIC_OPERATIONS, EndpointPaths.USER_QUEUE_LATEST_OPERATION, EndpointPaths.APP_GET_LATEST_OPERATION:
		return TaggedUserOperation.class;
	  case EndpointPaths.TOPIC_SUBSCRIBERS:
		return Subscriber[].class;
	  case EndpointPaths.USER_QUEUE_EXCEPTION:
		return String.class;
	  case EndpointPaths.APP_INIT_CLIENT:
		return SimpleStateMessage.class;
	  case EndpointPaths.TOPIC_CURSORS:
		return UserCursors.class;
	  default:
		LOG.warn("unexpected destination '{}'", destination);
		return null;
	}
  }

  @Override
  protected void handleSpecialFrame(StompHeaders headers, Object payload)
  {
	if ( payload instanceof SimpleStateMessage stateMessage )
	{
	  LOG.trace("Interpreted payload as state message.");
	  ((SimpleStompSessionSync) _stompSync).onStateMessageReceived(headers, stateMessage);
	}
	else
	{
	  super.handleSpecialFrame(headers, payload);
	}
  }

  @Override
  public void handleException(
	StompSession session,
	StompCommand command,
	StompHeaders headers,
	byte[] payload,
	Throwable exception)
  {
	_stompSync.onException(headers, exception);
  }

}