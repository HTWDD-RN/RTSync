package de.dmos.rtsync.message;

import java.io.Serializable;

public record SimpleStateMessage(
  Subscriber selfSubscriber,
  RTState rtState) implements Serializable
{
}
