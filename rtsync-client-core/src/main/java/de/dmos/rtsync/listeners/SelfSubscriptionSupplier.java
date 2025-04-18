package de.dmos.rtsync.listeners;

import de.dmos.rtsync.message.Subscriber;

public interface SelfSubscriptionSupplier
{
  void addSelfSubscriberListener(SelfSubscriberListener listener);

  boolean removeSelfSubscriberListener(SelfSubscriberListener listener);

  Subscriber getSelfSubscriber();
}
