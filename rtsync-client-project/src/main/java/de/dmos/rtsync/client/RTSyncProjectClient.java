package de.dmos.rtsync.client;

import java.awt.Color;

import de.dmos.rtsync.serializers.MessageSerialization;
import de.dmos.rtsync.swingui.ExampleProjectClientGUI;

/**
 * Represents a client in a network of {@link RTSyncSimpleNetworkNode}s. Listeners can register to several events and are
 * stored internally in weak lists.
 *
 * @author <a href="mailto:michael.danzig@dmos2002.de">Michael Danzig</a>
 * @version $Rev$
 */
public class RTSyncProjectClient
{
  private final RTProjectClientOperationSync _sync;

  public static void main(String[] args)
  {
	String name = args.length > 0 ? args[0] : null;
	Color color = args.length > 1 ? MessageSerialization.COLOR_SERIALIZER.tryParseColor(args[1]) : null;
	RTSyncProjectClient client = new RTSyncProjectClient(name, color);
	new ExampleProjectClientGUI(client.getSync()).start();
  }

  public RTSyncProjectClient()
  {
	this(null, null);
  }

  public RTSyncProjectClient(String preferredName)
  {
	this(preferredName, null);
  }

  public RTSyncProjectClient(String preferredName, Color preferredColor)
  {
	_sync = new RTProjectClientOperationSync(preferredName, preferredColor);
  }

  public RTProjectClientOperationSync getSync()
  {
	return _sync;
  }

  public ClientConnectionHandler getConnectionHandler()
  {
	return _sync.getConnectionHandler();
  }
}
