package de.dmos.rtsync.client;

import java.util.List;

import de.dmos.rtsync.listeners.SubscriberListener;
import de.dmos.rtsync.message.Subscriber;

public interface SubscriptionSupplier
{
  void addWeakSubscriberListener(SubscriberListener listener);

  boolean removeWeakSubscriberListener(SubscriberListener listener);

  List<Subscriber> getSubscribers();
}
