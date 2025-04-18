package de.dmos.rtsync.client;

import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSession.Subscription;

import de.dmos.rtsync.client.internalinterfaces.OutgoingClientMessage;
import de.dmos.rtsync.client.internalinterfaces.ReceiptHandler;

/**
 * A message that is sent via {@link StompSession#subscribe} to create a subscription or to receive data once.
 *
 * @author <a href="mailto:michael.danzig@dmos2002.de">Michael Danzig</a>
 * @version $Rev$
 *
 */
public record SubscribeMessage(String destination, ReceiptHandler<Subscription> subscriptionHandler)
implements
OutgoingClientMessage
{
  public SubscribeMessage(String destination)
  {
	this(destination, null);
  }

  @Override
  public void send(StompSession stompSession, StompFrameHandler frameHandler)
	  throws IllegalStateException, MessageDeliveryException
  {
	Subscription subscription = stompSession.subscribe(destination, frameHandler);
	if ( subscriptionHandler != null )
	{
	  subscriptionHandler.receiptReceived(subscription);
	}
  }
}
