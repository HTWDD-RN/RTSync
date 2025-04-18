package de.dmos.rtsync.client.internalinterfaces;

import de.dmos.rtsync.message.CursorPosition;

public interface CursorUpdater
{
  void updateCursor(CursorPosition cursorPosition);
}