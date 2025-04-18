package de.dmos.rtsync.customotter;

import se.l4.otter.engine.EditorListener;
import se.l4.otter.operations.Operation;

public interface CustomEditorListener<T extends Operation<?>> extends EditorListener<T>
{
  default void onReset(T op, String user)
  {
  }
}
