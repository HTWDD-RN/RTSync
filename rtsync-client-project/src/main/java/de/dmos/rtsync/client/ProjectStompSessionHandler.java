package de.dmos.rtsync.client;

import java.lang.reflect.Type;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;

import de.dmos.rtsync.client.internalinterfaces.ProjectStompSessionSync;
import de.dmos.rtsync.message.RTState;
import de.dmos.rtsync.message.Subscriber;
import de.dmos.rtsync.message.TaggedUserOperation;
import de.dmos.rtsync.message.UserCursors;
import de.dmos.rtsync.network.EndpointPaths;

public class ProjectStompSessionHandler extends AbstractRTSyncStompSessionHandler
{
  private static final Logger LOG = LoggerFactory.getLogger(ProjectStompSessionHandler.class);

  private final ProjectStompSessionSync	_projectStompSync;

  public ProjectStompSessionHandler(ProjectStompSessionSync stompSync)
  {
	super(stompSync);
	_projectStompSync = stompSync;
  }

  @Override
  protected Type getTypeForDestination(String destination)
  {
	int lastDelimiterIndex = destination.lastIndexOf(EndpointPaths.DELIMITER);
	if (lastDelimiterIndex < 0) {
	  return null;
	}
	String lastPart = destination.substring(lastDelimiterIndex + 1);
	switch (lastPart)
	{
	  case EndpointPaths.OPERATIONS, EndpointPaths.LATEST_OPERATION, EndpointPaths.GET_LATEST_OPERATION:
		return TaggedUserOperation.class;
	  case EndpointPaths.SUBSCRIBERS:
		return Subscriber[].class;
	  case EndpointPaths.EXCEPTION:
		return String.class;
	  case EndpointPaths.INIT_CLIENT:
		return Subscriber.class;
	  case EndpointPaths.CURSORS:
		return UserCursors.class;
	  case EndpointPaths.PROJECTS:
		return String[].class;
	  case EndpointPaths.ALL_TOPICS:
		return RTState.class;
	  default:
		LOG.warn("unexpected destination '{}'", destination);
		return null;
	}
  }

  @Override
  protected void handleSpecialFrame(StompHeaders headers, Object payload)
  {
	switch (payload)
	{
	  case RTState stateMessage -> {
		LOG.trace("Interpreted payload as project state message.");
		_projectStompSync.onProjectStateMessageReceived(headers, stateMessage);
	  }
	  case Subscriber subscriber -> _projectStompSync.onSelfSubscriberReceived(headers, subscriber);
	  case String[] stringArray -> _projectStompSync.onProjectListUpdated(Arrays.asList(stringArray));
	  default -> super.handleSpecialFrame(headers, payload);
	}
  }

  @Override
  protected boolean isWholeOperationState(StompHeaders headers)
  {
	String destination = headers.getDestination();
	return destination == null || !destination.endsWith(EndpointPaths.PATH_OPERATIONS);
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