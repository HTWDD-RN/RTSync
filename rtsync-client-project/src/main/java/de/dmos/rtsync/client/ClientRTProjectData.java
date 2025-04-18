package de.dmos.rtsync.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.dmos.rtsync.client.internalinterfaces.CursorUpdater;
import de.dmos.rtsync.listeners.IncompatibleModelResolution;
import de.dmos.rtsync.message.TaggedUserOperation;
import de.dmos.rtsync.project.RTProjectData;
import se.l4.otter.engine.TaggedOperation;
import se.l4.otter.lock.CloseableLock;
import se.l4.otter.operations.ComposeException;
import se.l4.otter.operations.Operation;
import se.l4.otter.operations.TransformException;
import se.l4.otter.operations.combined.CombinedHandler;

public class ClientRTProjectData extends RTProjectData
{
  private static final Logger				LOG					=
	  LoggerFactory.getLogger(ClientRTProjectData.class);

  private final ClientSubscriptionContainer	_subscriptionContainer;

  private boolean							_badSynchronization	= false;

  public ClientRTProjectData(String project, RTProjectClientOperationSync projectSync, CursorUpdater cursorUpdater)
  {
	super(project, projectSync);
	_subscriptionContainer = new ClientSubscriptionContainer(projectSync.getConnectionHandler(), cursorUpdater);
	_subscriptionContainer.setModel(getModel());
  }

  public ClientSubscriptionContainer getSubscriptionContainer()
  {
	return _subscriptionContainer;
  }

  @Override
  public void send(TaggedOperation<Operation<CombinedHandler>> op)
  {
	if ( !_badSynchronization )
	{
	  super.send(op);
	}
  }

  @Override
  public void onTaggedOperationReceived(TaggedUserOperation userOp, boolean wholeState) throws TransformException
  {
	try (CloseableLock lock = _control.lock())
	{
	  //	  if ( !_subscribed || (_badSynchronization && wholeState) )
	  //	  {
	  //		_badSynchronization = false;
	  //		_subscribed = true;
	  //		_connectionHandler.onConnectionStateChanged(ConnectionState.CONNECTED, null);
	  //	  }
	  _badSynchronization &= !wholeState;
	  TaggedUserOperation storedOperation = storeTaggedOperation(userOp, wholeState);
	  LOG.debug("TaggedOperation received: {} stored as {}", userOp, storedOperation);
	  notifyListeners(storedOperation, wholeState);
	}
	catch (ComposeException | TransformException ex)
	{
	  ((RTProjectClientOperationSync) _projectSync).onSynchronizationProblem(this, !wholeState);
	  if ( wholeState )
	  {
		IncompatibleModelResolution resolution = _subscriptionContainer.onIncompatibleOperationReceived(userOp);
		if ( resolution == IncompatibleModelResolution.OVERWRITE_LOCAL_CHANGES )
		{
		  _badSynchronization = false;
		  _control.setBaseOperation(userOp);
		  notifyListeners(userOp, true);
		}
		else if ( resolution == IncompatibleModelResolution.OVERWRITE_REMOTE_CHANGES )
		{
		  // TODO: Overwrite the server's changes.
		  throw new RuntimeException("The function to overwrite the server's state is not yet available.");
		}
	  }
	}
  }
}
