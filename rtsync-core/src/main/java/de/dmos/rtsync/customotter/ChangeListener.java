package de.dmos.rtsync.customotter;

public interface ChangeListener<T>
{
  void valueChanged(T op, boolean local, String user);
}