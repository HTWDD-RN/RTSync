package de.dmos.rtsync.client.internalinterfaces;

import java.util.List;

import org.springframework.messaging.simp.stomp.StompHeaders;

import de.dmos.rtsync.listeners.ExceptionListener;
import de.dmos.rtsync.message.UserCursors;
import de.dmos.rtsync.message.Subscriber;
import de.dmos.rtsync.message.TaggedUserOperation;

public interface StompSessionSync extends ExceptionListener
{
  void onSubscribersReceived(StompHeaders headers, List<Subscriber> subscribers);

  void onTaggedOperationReceived(StompHeaders headers, TaggedUserOperation userOp, boolean isWholeState);

  void onCursorsMessageReceived(StompHeaders headers, UserCursors cursorsMessage);

  void onServerMessageException(StompHeaders headers, String exceptionMessage);

  default void onException(StompHeaders headers, Throwable exception)
  {
	onException(exception);
  }
}
