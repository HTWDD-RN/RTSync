package de.dmos.rtsync.customotter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.dmos.rtsync.internalinterfaces.TaggedUserOperationListener;
import de.dmos.rtsync.internalinterfaces.UserOperationSync;
import de.dmos.rtsync.message.TaggedUserOperation;
import se.l4.otter.engine.TaggedOperation;
import se.l4.otter.lock.CloseableLock;
import se.l4.otter.operations.ComposeException;
import se.l4.otter.operations.OTType;
import se.l4.otter.operations.Operation;
import se.l4.otter.operations.TransformException;
import se.l4.otter.operations.combined.CombinedHandler;

public abstract class AbstractOperationSync implements UserOperationSync
{
  private static final Logger									  LOG =
	  LoggerFactory.getLogger(AbstractOperationSync.class);

  protected final CustomEditorControl<Operation<CombinedHandler>> _control;
  protected final Collection<TaggedUserOperationListener> _listeners;

  protected AbstractOperationSync(CustomEditorControl<Operation<CombinedHandler>> control)
  {
	_control = control;
	_listeners = new ArrayList<>();
  }

  @Override
  public OTType<Operation<CombinedHandler>> getType()
  {
	return _control.getType();
  }

  @Override
  public TaggedOperation<Operation<CombinedHandler>> connect(
	Consumer<TaggedOperation<Operation<CombinedHandler>>> listener)
  {
	return connectUserOperationListener((t, r) -> listener.accept(t));
  }

  @Override
  public TaggedUserOperation connectUserOperationListener(TaggedUserOperationListener listener)
  {
	_listeners.add(listener);
	return getLatestUserOperation();
  }

  public TaggedUserOperation getLatestUserOperation()
  {
	return _control.getLatestUserOperation();
  }

  public long getLatestVersion()
  {
	return _control.getLatestVersion();
  }

  public CustomEditorControl<Operation<CombinedHandler>> getControl()
  {
	return _control;
  }

  /**
   * Stores the given {@link TaggedUserOperation} and returns the store result.
   *
   * @param userOp The {@link TaggedUserOperation} to store.
   * @param wholeState indicates whether userOp is a whole state (true) or if it's a delta (false).
   *
   * @implSpec The caller of this method must have acquired the _control's lock in order to prevent incoherent states
   *           with race conditions.
   */
  public TaggedUserOperation storeTaggedOperation(TaggedUserOperation userOp, boolean wholeState)
	  throws TransformException
  {
	return _control.store(userOp, wholeState);
  }

  /**
   * Called when a new {@link TaggedUserOperation} is received.
   */
  public void notifyListeners(TaggedUserOperation userOp, boolean wholeState)
	  throws ComposeException, TransformException
  {
	_listeners.forEach(l -> l.onTaggedUserOperationReceived(userOp, wholeState));
  }

  /**
   * Stores the received operation and notifies local listeners of the store result. This should be called when a
   * network node receives a {@link TaggedOperation} via the network.
   *
   * @implNote If the given operation is a {@link TaggedUserOperation}, then the local listeners are notified of a
   *           {@link TaggedUserOperation} with the user information.
   */
  public void onTaggedOperationReceived(TaggedUserOperation userOp, boolean wholeState) throws TransformException
  {
	try (CloseableLock lock = _control.lock())
	{
	  TaggedUserOperation storedOperation = storeTaggedOperation(userOp, wholeState);
	  LOG.debug("TaggedOperation received: {} stored as {}", userOp, storedOperation);
	  notifyListeners(storedOperation, wholeState);
	}
  }

  @Override
  public void close()
  {
	// Nothing to close here. Subclasses should override this if needed.
  }
}
