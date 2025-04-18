package de.dmos.rtsync.project;

import de.dmos.rtsync.internalinterfaces.TaggedUserOperationListener;
import de.dmos.rtsync.message.TaggedUserOperation;
import se.l4.otter.engine.TaggedOperation;
import se.l4.otter.operations.Operation;
import se.l4.otter.operations.combined.CombinedHandler;

public interface ProjectOperationSync extends AutoCloseable, TaggedUserOperationListener
{
  /**
   * Connect and start listening for changes. This will return the latest
   * version of the document/model being edited.
   *
   * @param listener
   *   listener that will receive updates
   * @return
   */
  TaggedUserOperation connectUserOperationListener(String project, TaggedUserOperationListener listener);

  /**
   * Send an edit to other editors.
   */
  void send(String project, TaggedOperation<Operation<CombinedHandler>> op);

  /**
   * Close this sync. The sync will no longer be able to receive operations.
   */
  @Override
  void close();
}
