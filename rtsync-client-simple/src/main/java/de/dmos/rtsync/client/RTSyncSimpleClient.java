package de.dmos.rtsync.client;

import java.awt.Color;

import de.dmos.rtsync.network.RTSyncSimpleNetworkNode;
import de.dmos.rtsync.serializers.MessageSerialization;

/**
 * Represents a client in a network of {@link RTSyncSimpleNetworkNode}s. Listeners can register to several events and are
 * stored internally in weak lists.
 *
 * @author <a href="mailto:michael.danzig@dmos2002.de">Michael Danzig</a>
 * @version $Rev$
 */
public class RTSyncSimpleClient extends RTSyncSimpleNetworkNode
{
  private final RTSimpleClientOperationSync							_simpleClientSync;

  public static void main(String[] args)
  {
	String name = args.length > 0 ? args[0] : null;
	Color color = args.length > 1 ? MessageSerialization.COLOR_SERIALIZER.tryParseColor(args[1]) : null;
	RTSyncSimpleClient client = new RTSyncSimpleClient(name, color);
	new ExampleSimpleClientGUI(
	  client.getConnectionHandler(),
	  client.getSubscriptionContainer(),
	  client.getModel()).start();
  }

  @Override
  protected boolean isServer()
  {
	return false;
  }

  public RTSyncSimpleClient()
  {
	this(null, null);
  }

  public RTSyncSimpleClient(String preferredName)
  {
	this(preferredName, null);
  }

  public RTSyncSimpleClient(String preferredName, Color preferredColor)
  {
	super(control -> new RTSimpleClientOperationSync(control, preferredName, preferredColor));
	_simpleClientSync = (RTSimpleClientOperationSync) _sync;
	getSubscriptionContainer().setModel(_model);
  }

  public ClientSubscriptionContainer getSubscriptionContainer()
  {
	return _simpleClientSync.getSubscriptionContainer();
  }

  public ClientConnectionHandler getConnectionHandler()
  {
	return _simpleClientSync.getConnectionHandler();
  }

  @Override
  public RTSimpleClientOperationSync getSync()
  {
	return _simpleClientSync;
  }
}
