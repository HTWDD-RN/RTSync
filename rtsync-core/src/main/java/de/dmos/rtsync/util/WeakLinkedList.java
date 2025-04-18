package de.dmos.rtsync.util;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Stream;

/**
 * A {@link LinkedList} that keeps only {@link WeakReference}s to its elements.
 *
 * @author <a href="mailto:michael.danzig@dmos2002.de">Michael Danzig</a>
 * @version $Rev$
 */
public class WeakLinkedList<E> extends LinkedList<WeakReference<E>>
{
  private static final long serialVersionUID = 1;

  public void removeNullReferences()
  {
	removeIf(r -> r.get() == null);
  }

  /**
   * Equivalent of {@link List#isEmpty()} but returns true only if there is at least one strongly reachable object left.
   */
  public boolean hasNoStrongContents()
  {
	removeNullReferences();
	return !isEmpty();
  }

  public int strongContentCount()
  {
	removeNullReferences();
	return size();
  }

  /**
   * Creates a new list of all strongly referencable list items. This also triggers the removal of dereferenced
   * elements.
   */
  public List<E> toStrongList()
  {
	removeNullReferences();
	return stream().map(Reference::get).filter(e -> e != null).toList();
  }

  /**
   * Creates a stream of all strongly referencable elements. This also triggers the removal of dereferenced elements.
   */
  public Stream<E> toStrongStream()
  {
	removeNullReferences();
	return stream().map(r -> r.get()).filter(e -> e != null);
  }

  public boolean addAsWeakReference(E e)
  {
	return add(new WeakReference<>(e));
  }

  public boolean removeReferencedObject(Object o)
  {
	return stream().filter(e -> e.get() == o).findFirst().map(super::remove).orElse(false);
  }
}
