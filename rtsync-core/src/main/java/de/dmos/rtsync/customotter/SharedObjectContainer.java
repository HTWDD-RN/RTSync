package de.dmos.rtsync.customotter;

import java.util.stream.Stream;

import se.l4.otter.operations.Operation;

public abstract class SharedObjectContainer<T extends Operation<?>> extends AbstractCustomSharedObject<T>
{
  protected SharedObjectContainer(CustomSharedObjectEditor<T> editor)
  {
	super(editor);
  }

  public abstract Object getObjectAtPathPart(String pathPart)
	  throws IllegalArgumentException, IndexOutOfBoundsException;

  public abstract void insertObjectAtPathPart(Object obj, String pathPart)
	  throws IndexOutOfBoundsException, ClassCastException;

  public abstract boolean removeObject(Object obj);

  public abstract Stream<Object> getContents();
}
