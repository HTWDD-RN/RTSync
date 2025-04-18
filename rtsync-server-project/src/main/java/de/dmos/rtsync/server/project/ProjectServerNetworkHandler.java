package de.dmos.rtsync.server.project;

import de.dmos.rtsync.message.TaggedUserOperation;

public interface ProjectServerNetworkHandler
{
  void brodcastTaggedOperation(String project, TaggedUserOperation taggedOperation);

  void handleException(Throwable throwable, String project, String sender);
}
