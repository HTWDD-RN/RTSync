package de.dmos.rtsync.server.project;

import de.dmos.rtsync.message.TaggedUserOperation;
import de.dmos.rtsync.project.RTProjectData;

public record ProjectUserOperation(RTProjectData rtpData, TaggedUserOperation userOp)
{

}
