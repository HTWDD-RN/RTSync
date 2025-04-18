package de.dmos.rtsync.listeners;

import de.dmos.rtsync.message.UserCursors;

public interface CursorsListener
{
  void onUserCursorsReceived(UserCursors cursorsMessage);
}
