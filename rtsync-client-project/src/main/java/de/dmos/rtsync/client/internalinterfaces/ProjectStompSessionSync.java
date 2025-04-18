package de.dmos.rtsync.client.internalinterfaces;

import java.util.List;

import org.springframework.messaging.simp.stomp.StompHeaders;

import de.dmos.rtsync.message.RTState;
import de.dmos.rtsync.message.Subscriber;

public interface ProjectStompSessionSync extends StompSessionSync
{
  void onSelfSubscriberReceived(StompHeaders headers, Subscriber selfSubscriber);

  void onProjectStateMessageReceived(StompHeaders headers, RTState projectStateMessage);

  void onProjectListUpdated(List<String> projects);
}
