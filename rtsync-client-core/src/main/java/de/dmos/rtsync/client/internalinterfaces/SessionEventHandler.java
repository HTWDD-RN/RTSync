package de.dmos.rtsync.client.internalinterfaces;

import de.dmos.rtsync.listeners.ConnectionListener;
import de.dmos.rtsync.listeners.CursorsListener;
import de.dmos.rtsync.listeners.IncompatibleModelResolution;
import de.dmos.rtsync.listeners.SelfSubscriberListener;
import de.dmos.rtsync.listeners.SubscriberListener;
import de.dmos.rtsync.message.TaggedUserOperation;

/**
 * An interface for classes that listen to events which are related to the client's session with a server. The Real-Time
 * updates are not considered to be session events.
 *
 * @author <a href="mailto:michael.danzig@dmos2002.de">Michael Danzig</a>
 * @version $Rev$
 */
public interface SessionEventHandler
extends
SubscriberListener,
	CursorsListener,
ConnectionListener,
SelfSubscriberListener,
HasPreferredNameAndColor
{
  IncompatibleModelResolution onIncompatibleOperationReceived(TaggedUserOperation receivedOperation);
}
