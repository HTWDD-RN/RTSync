package de.dmos.rtsync.internalinterfaces;

import de.dmos.rtsync.message.TaggedUserOperation;

public interface TaggedUserOperationListener
{
  void onTaggedUserOperationReceived(TaggedUserOperation userOp, boolean reset);
}
