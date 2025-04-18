package de.dmos.rtsync.customotter;

import de.dmos.rtsync.internalinterfaces.WholeStateStore;
import de.dmos.rtsync.message.TaggedUserOperation;
import se.l4.otter.engine.OperationHistory;
import se.l4.otter.operations.Operation;
import se.l4.otter.operations.combined.CombinedHandler;

public interface TaggedUserOperationHistory<T extends Operation<CombinedHandler>>
extends
OperationHistory<T>,
WholeStateStore
{
  @Override
  TaggedUserOperation store(TaggedUserOperation userOp, boolean wholeState);

  /**
   * Clears the stored operations and uses the given user operation as new base.
   */
  void resetOperationsTo(TaggedUserOperation taggedBaseOperation);

  long getLatestWholeStateVersion();
}
