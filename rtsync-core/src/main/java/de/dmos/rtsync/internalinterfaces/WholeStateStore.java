package de.dmos.rtsync.internalinterfaces;

import de.dmos.rtsync.message.TaggedUserOperation;

public interface WholeStateStore
{
  TaggedUserOperation store(TaggedUserOperation userOp, boolean wholeState);

  TaggedUserOperation getLatestUserOperation();
}
