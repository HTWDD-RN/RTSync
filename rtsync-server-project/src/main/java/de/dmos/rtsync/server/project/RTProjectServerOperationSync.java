package de.dmos.rtsync.server.project;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import de.dmos.rtsync.message.TaggedUserOperation;
import de.dmos.rtsync.project.AbstractProjectOperationSync;
import de.dmos.rtsync.project.RTProjectData;
import se.l4.otter.engine.TaggedOperation;
import se.l4.otter.lock.CloseableLock;
import se.l4.otter.operations.Operation;
import se.l4.otter.operations.combined.CombinedHandler;

public class RTProjectServerOperationSync extends AbstractProjectOperationSync<RTProjectData>
{
  private final Thread							   _triggerThread;
  private final BlockingQueue<ProjectUserOperation>	_messageQueue;
  private final ProjectServerNetworkHandler			_serverNetworkHandler;

  public RTProjectServerOperationSync(ProjectServerNetworkHandler networkHandler)
  {
	super();
	_serverNetworkHandler = networkHandler;
	_messageQueue = new LinkedBlockingQueue<>();
	_triggerThread = new Thread(this::send, "rt-server-operation-sync-send-thread");
	_triggerThread.start();
  }

  @Override
  public void send(RTProjectData data, TaggedOperation<Operation<CombinedHandler>> op)
  {
	_messageQueue.add(new ProjectUserOperation(data, TaggedUserOperation.toTaggedUserOperation(op)));
  }

  private void send()
  {
	while (!Thread.interrupted())
	{
	  ProjectUserOperation puOp = null;
	  try
	  {
		puOp = _messageQueue.take();
		applyAndSendTaggedOperationToNetwork(puOp);
	  }
	  catch (InterruptedException e)
	  {
		return;
	  }
	  catch (Exception e)
	  {
		if ( puOp != null )
		{
		  _serverNetworkHandler
		  .handleException(e, puOp.rtpData().getProject(), puOp.userOp().getUser());
		}
		else
		{
		  _serverNetworkHandler.handleException(e, null, null);
		}
	  }
	}
  }

  /**
   * This stores the given {@link TaggedOperation}, notifies local listeners and then the network nodes of the store
   * result.
   */
  protected void applyAndSendTaggedOperationToNetwork(ProjectUserOperation puOp)
  {
	try (CloseableLock lock = puOp.rtpData().getControl().lock())
	{
	  TaggedUserOperation storedOp = puOp.rtpData().storeTaggedOperation(puOp.userOp(), false);
	  puOp.rtpData().notifyListeners(storedOp, false);
	  _serverNetworkHandler.brodcastTaggedOperation(puOp.rtpData().getProject(), storedOp);
	}
  }

  @Override
  public void close()
  {
	_triggerThread.interrupt();
	super.close();
  }

  @Override
  protected boolean isServer()
  {
	return true;
  }

  @Override
  protected RTProjectData createRTProjectData(String project)
  {
	return new RTProjectData(project, this);
  }
}
