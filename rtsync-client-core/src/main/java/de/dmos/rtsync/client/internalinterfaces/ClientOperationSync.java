package de.dmos.rtsync.client.internalinterfaces;

import java.net.URI;

import de.dmos.rtsync.client.ClientConnectionHandler;
import de.dmos.rtsync.client.ClientSynchronizingThread;

public interface ClientOperationSync
{
  public ClientConnectionHandler getConnectionHandler();

  public void startSynchronizingWithServer(URI uri);

  public void stopSynchronizingWithServer();

  public boolean isConnected();

  ClientSynchronizingThread getSyncThread();
}
