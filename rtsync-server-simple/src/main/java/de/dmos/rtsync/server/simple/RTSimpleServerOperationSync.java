package de.dmos.rtsync.server.simple;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import de.dmos.rtsync.customotter.AbstractOperationSync;
import de.dmos.rtsync.customotter.CustomEditorControl;
import de.dmos.rtsync.message.TaggedUserOperation;
import de.dmos.rtsync.server.ServerNetworkHandler;
import se.l4.otter.engine.TaggedOperation;
import se.l4.otter.lock.CloseableLock;
import se.l4.otter.operations.Operation;
import se.l4.otter.operations.combined.CombinedHandler;

public class RTSimpleServerOperationSync extends AbstractOperationSync
{
  private final Thread                               _triggerThread;
  private final BlockingQueue<TaggedUserOperation> _messageQueue;
  private final ServerNetworkHandler               _serverNetworkHandler;

  public RTSimpleServerOperationSync(
	CustomEditorControl<Operation<CombinedHandler>> control,
	ServerNetworkHandler networkHandler)
  {
	super(control);
	_serverNetworkHandler = networkHandler;
	_messageQueue = new LinkedBlockingQueue<>();
	_triggerThread = new Thread(this::send, "rt-server-operation-sync-send-thread");
	_triggerThread.start();
  }

  @Override
  public void send(TaggedOperation<Operation<CombinedHandler>> op)
  {
	_messageQueue.add(TaggedUserOperation.toTaggedUserOperation(op));
  }

  public void send(TaggedUserOperation userOp)
  {
	_messageQueue.add(userOp);
  }

  private void send()
  {
	while (!Thread.interrupted())
	{
	  TaggedUserOperation taggedOp = null;
	  try
	  {
		taggedOp = _messageQueue.take();
		applyAndSendTaggedOperationToNetwork(taggedOp);
	  }
	  catch (InterruptedException e)
	  {
		_triggerThread.interrupt();
		return;
	  }
	  catch (Exception e)
	  {
		_serverNetworkHandler.handleException(e, taggedOp != null ? taggedOp.getUser() : null);
	  }
	}
  }

  /**
   * This stores the given {@link TaggedOperation}, notifies local listeners and then the network nodes of the store
   * result.
   *
   * @implNote If the given operation is a {@link TaggedUserOperation}, then the local listeners and network nodes are
   *           notified of a {@link TaggedUserOperation} with the user information.
   */
  protected void applyAndSendTaggedOperationToNetwork(TaggedUserOperation taggedOperation)
  {
	try (CloseableLock lock = _control.lock())
	{
	  TaggedUserOperation storedOp = storeTaggedOperation(taggedOperation, false);
	  notifyListeners(storedOp, false);
	  _serverNetworkHandler.brodcastTaggedOperation(storedOp);
	}
  }

  @Override
  public void close()
  {
	_triggerThread.interrupt();
  }
}
