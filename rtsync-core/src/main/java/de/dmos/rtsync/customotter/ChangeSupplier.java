package de.dmos.rtsync.customotter;

public interface ChangeSupplier<T>
{
  void addAsWeakChangeListener(ChangeListener<T> changeListener);

  boolean removeWeakChangeListener(Object changeListener);
}
