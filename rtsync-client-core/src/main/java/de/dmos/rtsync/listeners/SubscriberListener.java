package de.dmos.rtsync.listeners;

import java.util.List;

import de.dmos.rtsync.message.Subscriber;

public interface SubscriberListener
{
  void onSubscribersReceived(List<Subscriber> subscribers);
}
