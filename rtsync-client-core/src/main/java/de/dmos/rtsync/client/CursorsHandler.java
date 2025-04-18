package de.dmos.rtsync.client;

import de.dmos.rtsync.client.internalinterfaces.CursorUpdater;
import de.dmos.rtsync.listeners.CursorsListener;

public interface CursorsHandler extends CursorUpdater
{
  void addCursorsListener(CursorsListener listener);

  boolean removeCursorsListener(CursorsListener listener);
}
