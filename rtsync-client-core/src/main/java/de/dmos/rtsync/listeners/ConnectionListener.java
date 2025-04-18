package de.dmos.rtsync.listeners;

import de.dmos.rtsync.client.ConnectionState;

public interface ConnectionListener extends ExceptionListener
{
  void onConnectionStateChanged(ConnectionState currentState, Throwable throwable);
}