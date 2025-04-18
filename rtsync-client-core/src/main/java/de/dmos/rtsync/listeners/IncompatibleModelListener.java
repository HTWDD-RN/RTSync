package de.dmos.rtsync.listeners;

import de.dmos.rtsync.message.TaggedUserOperation;

public interface IncompatibleModelListener
{
  /**
   * Called when the ConnectionHandler receives a {@link TaggedUserOperation} which is incompatible with the current
   * model.
   */
  IncompatibleModelResolution onIncompatibleModelReceived(TaggedUserOperation taggedUserOperation);
}
