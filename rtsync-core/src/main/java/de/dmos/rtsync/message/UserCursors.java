package de.dmos.rtsync.message;

import java.io.Serializable;
import java.util.List;

public record UserCursors(Long userId, List<CursorPosition> cursors) implements Serializable
{
}
