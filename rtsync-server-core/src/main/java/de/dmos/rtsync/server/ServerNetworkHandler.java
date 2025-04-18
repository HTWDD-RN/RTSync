package de.dmos.rtsync.server;

import de.dmos.rtsync.message.TaggedUserOperation;

public interface ServerNetworkHandler
{
  void brodcastTaggedOperation(TaggedUserOperation taggedOperation);

  void handleException(Throwable throwable, String sender);
}
