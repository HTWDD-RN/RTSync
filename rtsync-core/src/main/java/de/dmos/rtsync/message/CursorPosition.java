package de.dmos.rtsync.message;

public record CursorPosition(String id, Integer startIndex, int endIndex)
{
  public CursorPosition(String id, int endIndex)
  {
	this(id, null, endIndex);
  }
}
