package de.dmos.rtsync.swingui;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;

import javax.swing.plaf.TextUI;
import javax.swing.text.AbstractDocument;
import javax.swing.text.BadLocationException;
import javax.swing.text.Caret;
import javax.swing.text.DefaultCaret;
import javax.swing.text.Element;
import javax.swing.text.JTextComponent;
import javax.swing.text.Position;

/**
 * This serves the purpose to get more than just one caret with different width and color than the default one. It needs
 * a width and a color before it can be painted, so the setters must be used.
 *
 * @see DefaultCaret
 *
 * @author <a href="mailto:michael.danzig@dmos2002.de">Michael Danzig</a>
 * @version $Rev$
 */
class CustomCaret
{
  // Change this if needed.
  private static final boolean dotLTR			= true;

  private final JTextComponent _component;

  private Color				   _color;
  private int				   _dot;
  private int				   _width;

  private final int[]		   _flagXPoints	= new int[3];
  private final int[]		   _flagYPoints	= new int[3];

  public CustomCaret(JTextComponent component)
  {
	super();
	_component = component;
  }

  public CustomCaret(JTextComponent component, int width)
  {
	super();
	_component = component;
	_width = width;
  }

  /**
   * Sets the color of the caret. As long as this caret's color is null, it is not going to be painted.
   */
  public void setColor(Color color)
  {
	_color = color;
  }

  /**
   * Sets the width in pixels of the caret. Values smaller than 1 prevent the caret from being painted.
   */
  public void setWidth(int width)
  {
	_width = width;
  }

  /**
   * This positions the caret after the indexed letter. This works similarly to {@link Caret#moveDot(int)}.
   */
  public void setIndex(int dot)
  {
	_dot = dot;
  }

  public void paint(Graphics g)
  {
	if ( _color == null || _width < 1 )
	{
	  return;
	}

	try
	{
	  int length = _component.getDocument().getLength();
	  if ( _dot > length )
	  {
		_dot = length;
	  }
	  paintBase(g);
	}
	catch (BadLocationException e)
	{
	  // This can happen, if the cursor is at the end of the text and the local user deletes some characters.
	  // The problem usually solves itself shortly after it occurs.
	}
  }

  /**
   * Paints this caret as a simple rectangle with the width given by {@link #setWidth(int)} at the coordinates of the dot in
   * in the text component.
   *
   * @see DefaultCaret#paint(Graphics)
   */
  private void paintBase(Graphics g) throws BadLocationException
  {
	TextUI mapper = _component.getUI();
	Rectangle2D r2d = mapper.modelToView2D(_component, _dot, Position.Bias.Backward);
	Rectangle r = new Rectangle((int) r2d.getX(), (int) r2d.getY(), (int) r2d.getWidth(), (int) r2d.getHeight());

	if ( r.width == 0 && r.height == 0 )
	{
	  return;
	}

	g.setColor(_color);
	int paintWidth = _width;
	r.x -= paintWidth >> 1;
	g.fillRect(r.x, r.y, paintWidth, r.height);

	// see if we should paint a flag to indicate the bias
	// of the caret.
	// PENDING(prinz) this should be done through
	// protected methods so that alternative LAF
	// will show bidi information.
	if ( !(_component.getDocument() instanceof AbstractDocument abstractDoc) )
	{
	  return;
	}
	Element bidi = abstractDoc.getBidiRootElement();
	if ( (bidi != null) && (bidi.getElementCount() > 1) )
	{
	  //// there are multiple directions present.
	  _flagXPoints[0] = r.x + (dotLTR ? paintWidth : 0);
	  _flagYPoints[0] = r.y;
	  _flagXPoints[1] = _flagXPoints[0];
	  _flagYPoints[1] = _flagYPoints[0] + 4;
	  _flagXPoints[2] = _flagXPoints[0] + (dotLTR ? 4 : -4);
	  _flagYPoints[2] = _flagYPoints[0];
	  g.fillPolygon(_flagXPoints, _flagYPoints, 3);
	}
  }
}