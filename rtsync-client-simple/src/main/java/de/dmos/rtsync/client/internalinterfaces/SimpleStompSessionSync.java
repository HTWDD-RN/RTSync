package de.dmos.rtsync.client.internalinterfaces;

import org.springframework.messaging.simp.stomp.StompHeaders;

import de.dmos.rtsync.client.internalinterfaces.StompSessionSync;
import de.dmos.rtsync.message.SimpleStateMessage;

public interface SimpleStompSessionSync extends StompSessionSync
{
  void onStateMessageReceived(StompHeaders headers, SimpleStateMessage simpleStateMessage);
}
