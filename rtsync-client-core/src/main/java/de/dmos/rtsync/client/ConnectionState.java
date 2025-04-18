package de.dmos.rtsync.client;

public enum ConnectionState
{
  NOT_CONNECTED, CONNECTING, INITIAL_SUBSCRIBING, CONNECTED, CONNECTED_BAD_SYNCHRONIZATION;

  public boolean isConnected()
  {
    return this != NOT_CONNECTED && this != CONNECTING;
  }

  @Override
  public String toString()
  {
    switch (this)
    {
      case NOT_CONNECTED:
        return "not connected";
      case CONNECTED:
        return "connected";
      case CONNECTING:
        return "connecting";
      case INITIAL_SUBSCRIBING:
        return "sending initial subscriptions";
      case CONNECTED_BAD_SYNCHRONIZATION:
        return "connected - synchronization problems";
      default:
        return "unexpected connection state";
    }
  }
}