package de.dmos.rtsync.internalinterfaces;

import de.dmos.rtsync.message.TaggedUserOperation;
import se.l4.otter.engine.OperationSync;
import se.l4.otter.operations.Operation;
import se.l4.otter.operations.combined.CombinedHandler;

public interface UserOperationSync extends OperationSync<Operation<CombinedHandler>>
{
  TaggedUserOperation connectUserOperationListener(TaggedUserOperationListener listener);
}
