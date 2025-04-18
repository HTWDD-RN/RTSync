package de.dmos.rtsync.swingui;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a user's insertion of objects into a list-like object.
 *
 * @author <a href="mailto:michael.danzig@dmos2002.de">Michael Danzig</a>
 * @version $Rev$
 *
 */
public class Insertion extends Selection
{
  Insertion(int start, int length, String user)
  {
	super(start, length, user);
  }

  @Override
  protected boolean discardIfEmpty()
  {
	return true;
  }

  @Override
  protected Insertion createResized(int newStart, int newLength)
  {
	return new Insertion(newStart, newLength, _user);
  }

  @Override
  public List<? extends Selection> move(int minIndex, int offset)
  {
	List<? extends Selection> superList = super.move(minIndex, offset);
	if ( superList.isEmpty() )
	{
	  return superList;
	}
	Selection superSelection = superList.get(0);
	if ( superSelection._length <= _length )
	{
	  return superList;
	}
	int leftLength = minIndex - superSelection._start;
	int rightLength = _length - leftLength;
	List<Insertion> splitInsertions = new ArrayList<>(2);
	if ( leftLength > 0 )
	{
	  splitInsertions.add(createResized(superSelection._start, leftLength));
	}
	if ( rightLength > 0 )
	{
	  splitInsertions.add(createResized(superSelection.getEnd() - rightLength, rightLength));
	}
	return splitInsertions;
  }
}