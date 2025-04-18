package de.dmos.rtsync.client.internalinterfaces;

import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompSession;

/**
 * A message from a client which is going to be sent by a StompSession.
 *
 * @author <a href="mailto:michael.danzig@dmos2002.de">Michael Danzig</a>
 * @version $Rev$
 *
 */
public interface OutgoingClientMessage
{
  public abstract void send(StompSession stompSession, StompFrameHandler frameHandler)
	  throws IllegalStateException, MessageDeliveryException;
}
