package de.dmos.rtsync.client;

import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSession.Subscription;

import de.dmos.rtsync.client.internalinterfaces.OutgoingClientMessage;

public record UnsubscribeMessage(Subscription subscription) implements OutgoingClientMessage
{
  @Override
  public void send(StompSession stompSession, StompFrameHandler frameHandler)
	  throws IllegalStateException, MessageDeliveryException
  {
	subscription().unsubscribe();
  }
}
