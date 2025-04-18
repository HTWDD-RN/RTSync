package de.dmos.rtsync.listeners;

import de.dmos.rtsync.message.Subscriber;

public interface SelfSubscriberListener
{
  void onOwnNameOrColorChanged(Subscriber subscriber);
}
