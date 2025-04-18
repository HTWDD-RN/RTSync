package de.dmos.rtsync.message;

import java.io.Serializable;
import java.util.List;

public record RTState(List<UserCursors> userCursors, TaggedUserOperation taggedOperation)
implements
Serializable
{
}
