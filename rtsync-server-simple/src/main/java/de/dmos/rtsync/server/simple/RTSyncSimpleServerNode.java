package de.dmos.rtsync.server.simple;

import de.dmos.rtsync.network.RTSyncSimpleNetworkNode;

public class RTSyncSimpleServerNode extends RTSyncSimpleNetworkNode
{
  protected RTSyncSimpleServerNode(OperationSyncCreator syncCreator)
  {
	super(syncCreator);
  }

  @Override
  protected boolean isServer()
  {
	return true;
  }
}
