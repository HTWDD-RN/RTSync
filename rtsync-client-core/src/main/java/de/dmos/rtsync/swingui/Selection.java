package de.dmos.rtsync.swingui;

import java.awt.Color;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

/**
 * Represents a user's selected indices of list-like object.
 *
 * @author <a href="mailto:michael.danzig@dmos2002.de">Michael Danzig</a>
 * @version $Rev$
 *
 */
public class Selection implements Comparable<Selection>
{
  protected final String _user;
  protected int	  _start;
  protected int	  _length;
  private Color	  _color;

  Selection(int start, int length)
  {
	this(start, length, null);
  }

  Selection(int start, int length, String user)
  {
	_start = start;
	_length = length;
	_user = user;
  }

  private int getNewCursorPosition(int currentIndex, int minIndex, int offset)
  {
	if ( currentIndex < minIndex )
	{
	  return currentIndex;
	}
	if ( currentIndex + offset < minIndex )
	{
	  return minIndex;
	}
	return currentIndex + offset;
  }

  protected boolean discardIfEmpty()
  {
	return false;
  }

  public int getLength()
  {
	return _length;
  }

  public int getStart()
  {
	return _start;
  }

  public Color getColor()
  {
	return _color;
  }

  public String getUser()
  {
	return _user;
  }

  int getEnd()
  {
	return _start + _length;
  }

  void setInterval(int newStart, int newLength)
  {
	_start = newStart;
	_length = newLength;
  }

  public boolean contains(int index)
  {
	return index >= _start && index < getEnd();
  }

  public boolean setColor(Color newColor)
  {
	if ( !Objects.equals(newColor, _color) )
	{
	  _color = newColor;
	  return true;
	}
	return false;
  }

  public Selection move(int offset)
  {
	return createResized(_start + offset, _length);
  }

  public List<? extends Selection> move(int minIndex, int offset)
  {
	int newStart = getNewCursorPosition(_start, minIndex, offset);
	int newEnd = getNewCursorPosition(getEnd(), minIndex, offset);
	int newLength = newEnd - newStart;
	return newLength == 0 && discardIfEmpty() ? new LinkedList<>() : List.of(createResized(newStart, newLength));
  }

  protected Selection createResized(int newStart, int newLength)
  {
	return new Selection(newStart, newLength, _user);
  }

  @Override
  public int compareTo(Selection o)
  {
	int startDiff = _start - o._start;
	return startDiff != 0 ? startDiff : _length - o._length;
  }

  public boolean hasEqualInterval(Selection other)
  {
	return _start == other._start && _length == other._length;
  }

  @Override
  public boolean equals(Object obj)
  {
	if ( obj == null || getClass() != obj.getClass() )
	{
	  return false;
	}
	Selection other = (Selection) obj;
	return _start == other._start
		&& _length == other._length
		&& Objects.equals(_color, other._color)
		&& Objects.equals(_user, other._user);
  }

  @Override
  public int hashCode()
  {
	final int prime = 31;
	int result = 1;
	result = prime * result + _start;
	result = prime * result + _length;
	result = prime * result + (_user == null ? 0 : _user.hashCode());
	result = prime * result + (_color == null ? 0 : _color.hashCode());
	return result;
  }

  @Override
  public String toString()
  {
	StringBuilder builder = new StringBuilder();
	builder.append(_start);
	builder.append(" - ");
	builder.append(getEnd());
	builder.append(", user: ");
	builder.append(_user);
	builder.append(", color: ");
	builder.append(_color != null ? _color.toString() : null);
	return builder.toString();
  }

}