package de.dmos.rtsync.client;

import java.lang.reflect.Type;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;

import de.dmos.rtsync.client.internalinterfaces.StompSessionSync;
import de.dmos.rtsync.message.Subscriber;
import de.dmos.rtsync.message.TaggedUserOperation;
import de.dmos.rtsync.message.UserCursors;
import de.dmos.rtsync.network.CommunicationConstants;
import de.dmos.rtsync.network.EndpointPaths;
import se.l4.otter.engine.TaggedOperation;

public abstract class AbstractRTSyncStompSessionHandler extends StompSessionHandlerAdapter
{
  private static final Logger		   LOG = LoggerFactory.getLogger(AbstractRTSyncStompSessionHandler.class);

  protected final StompSessionSync _stompSync;

  protected AbstractRTSyncStompSessionHandler(StompSessionSync stompSync)
  {
	_stompSync = stompSync;
  }

  @Override
  public Type getPayloadType(StompHeaders headers)
  {
	LOG.debug("getPayloadType({})", headers);
	String destination = headers.getDestination();
	Type type = destination != null ? getTypeForDestination(destination) : null;
	return type != null ? type : super.getPayloadType(headers);
  }

  protected abstract Type getTypeForDestination(String destination);

  @Override
  public void handleFrame(StompHeaders headers, Object payload)
  {
	LOG.debug("received payload: {}", payload);
	if ( payload == null )
	{
	  return;
	}
	if ( payload instanceof String string
		&& string.startsWith(CommunicationConstants.SERVER_EXCEPTION_MESSAGE_START) )
	{
	  LOG.trace("Interpreted payload as server message exception string.");
	  String exceptionMessage = string.substring(CommunicationConstants.SERVER_EXCEPTION_MESSAGE_START.length());
	  _stompSync.onServerMessageException(headers, exceptionMessage);
	}
	else if ( payload instanceof Subscriber[] subscribers )
	{
	  LOG.trace("Interpreted payload as Subscriber[]. -> Forwarding to subscriber listeners.");
	  _stompSync.onSubscribersReceived(headers, Arrays.asList(subscribers));
	}
	else if ( payload instanceof TaggedOperation receivedTaggedOp )
	{
	  LOG.trace("Interpreted payload as TaggedOperation. -> Forwarding to operation listeners.");
	  boolean isWholeState = isWholeOperationState(headers);
	  _stompSync
	  .onTaggedOperationReceived(
		headers,
		TaggedUserOperation.toTaggedUserOperation(receivedTaggedOp),
		isWholeState);
	}
	else if ( payload instanceof UserCursors cursorsMessage )
	{
	  _stompSync.onCursorsMessageReceived(headers, cursorsMessage);
	}
	else
	{
	  handleSpecialFrame(headers, payload);
	}
  }

  protected boolean isWholeOperationState(StompHeaders headers)
  {
	return !EndpointPaths.TOPIC_OPERATIONS.equals(headers.getDestination());
  }

  /**
   * Called when a frame is received that has a payload which is not of one of the types
   * {@link AbstractRTSyncStompSessionHandler} expects.
   */
  protected void handleSpecialFrame(StompHeaders headers, Object payload)
  {
	LOG.error("Unexpected payload type '{}' from {}", payload.getClass(), headers.getDestination());
  }

  @Override
  public void handleTransportError(StompSession session, Throwable exception)
  {
	onException(exception);
  }

  public void onException(Throwable exception)
  {
	_stompSync.onException(exception);
  }

  public static class ServerMessageException extends Exception
  {
	/**
	 * docme: serialVersionUID
	 */
	private static final long serialVersionUID = 350075473195533653L;

	public ServerMessageException(String message)
	{
	  super(message);
	}
  }
}