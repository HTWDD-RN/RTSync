package de.dmos.rtsync.client;

import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSession.Receiptable;

import de.dmos.rtsync.client.internalinterfaces.OutgoingClientMessage;
import de.dmos.rtsync.client.internalinterfaces.ReceiptHandler;

/**
 * A message that is sent via {@link StompSession#send} to transmit data or trigger a server event.
 *
 * @author <a href="mailto:michael.danzig@dmos2002.de">Michael Danzig</a>
 * @version $Rev$
 *
 */
public record BasicMessage(String destination, Object payload, ReceiptHandler<Receiptable> receiptHandler)
implements
OutgoingClientMessage
{
  public BasicMessage(String destination)
  {
	this(destination, null, null);
  }

  public BasicMessage(String destination, Object payload)
  {
	this(destination, payload, null);
  }

  @Override
  public void send(StompSession stompSession, StompFrameHandler frameHandler)
	  throws IllegalStateException, MessageDeliveryException
  {
	Receiptable receiptable = stompSession.send(destination, payload);
	if ( receiptHandler != null )
	{
	  receiptHandler.receiptReceived(receiptable);
	}
  }
}
