package de.dmos.rtsync.swingui;

import java.awt.Color;
import java.awt.Graphics;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JTextPane;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;

import de.dmos.rtsync.client.ClientSubscriptionContainer;
import de.dmos.rtsync.client.CursorsHandler;
import de.dmos.rtsync.client.SubscriptionSupplier;
import de.dmos.rtsync.listeners.SelfSubscriptionSupplier;
import de.dmos.rtsync.swingui.RTSharedTextComponentAdapter.AttributedTextSetter;
import se.l4.otter.engine.Editor;
import se.l4.otter.model.SharedString;

/**
 * A JTextField that can be used to propagate updates to its string content to the {@link SharedString} string. Use a
 * constructor with an {@link Editor<Operation<?>>} in order to receive and display updates from the
 * {@link SharedString} or alternatively use {@link #addToListenersOfEditor(Editor)}. listeners.
 */
public class RTJTextPane extends JTextPane implements AttributedTextSetter
{
  private static final long								 serialVersionUID = 1L;

  protected final transient RTSharedTextComponentAdapter _sharedTextAdapter;
  private final Map<String, CustomCaret>				 _otherCarets	  = new HashMap<>();
  private final ForegroundColorKeepingStyledDocument					 _customDocument;

  public RTJTextPane(SharedString sharedString)
  {
	this(sharedString, null, null, null);
  }

  public RTJTextPane(SharedString sharedString, ClientSubscriptionContainer subscriptionContainer)
  {
	this(
	  sharedString,
	  subscriptionContainer,
	  subscriptionContainer.getClientConnectionHandler(),
	  subscriptionContainer);
  }

  public RTJTextPane(
	SharedString sharedString,
	SubscriptionSupplier subscriptionSupplier,
	SelfSubscriptionSupplier selfSubscriptionSupplier,
	CursorsHandler cursorsHandler)
  {
	super(new ForegroundColorKeepingStyledDocument(sharedString, RTSharedTextComponentAdapter.DEFAULT_COLOR));
	setText(sharedString.get());
	_customDocument = (ForegroundColorKeepingStyledDocument) getDocument();
	_sharedTextAdapter =
		new RTSharedTextComponentAdapter(
		  sharedString,
		  subscriptionSupplier,
		  selfSubscriptionSupplier,
		  cursorsHandler,
		  this,
		  this);
  }

  public RTSharedTextComponentAdapter getSharedTextAdapter()
  {
	return _sharedTextAdapter;
  }

  @Override
  public void setAttributedTextAndCursors(
	String text,
	List<Insertion> insertions,
	Map<String, Selection> selections,
	Selection selection)
  {
	_customDocument.setSettingColoredText(true);
	if ( text != null )
	{
	  setText(text);
	}

	for ( Insertion ins : insertions )
	{
	  setAttributedText(ins);
	}
	_customDocument.setSettingColoredText(false);

	List<String> toRemove = _otherCarets.keySet().stream().filter(e -> !selections.containsKey(e)).toList();
	toRemove.forEach(_otherCarets::remove);
	selections.entrySet().forEach(entry -> setCursorBase(entry.getKey(), entry.getValue()));

	if ( selection != null )
	{
	  try
	  {
		setCaretPosition(selection.getStart());
		moveCaretPosition(selection.getEnd());
	  }
	  catch (IllegalArgumentException iae)
	  {
		// This means that the text was updated after the position calculation started.
		// The cursor position is probably not important enough for a special handling.
	  }
	}
  }

  private void setAttributedText(Insertion ins)
  {
	SimpleAttributeSet attributes = new SimpleAttributeSet();
	attributes.addAttribute(StyleConstants.Foreground, ins.getColor());
	getStyledDocument().setCharacterAttributes(ins.getStart(), ins.getLength(), attributes, true);
  }

  @Override
  public void paint(Graphics g)
  {
	super.paint(g);
	_otherCarets.values().forEach(c -> c.paint(g));
  }

  @Override
  public void setCursor(String user, Selection selection)
  {
	setCursorBase(user, selection);
	repaint();
	//	 revalidate();
  }

  private void setCursorBase(String user, Selection selection)
  {
	CustomCaret caret = _otherCarets.computeIfAbsent(user, u -> {
	  CustomCaret newCaret = new CustomCaret(this);
	  newCaret.setWidth(2);
	  return newCaret;
	});
	caret.setColor(selection.getColor());
	caret.setIndex(selection.getEnd());
  }

  private static class ForegroundColorKeepingStyledDocument extends DefaultStyledDocument
  {
	/**
	 * docme: serialVersionUID
	 */
	private static final long  serialVersionUID	   = 1L;

	private boolean			   _settingColoredText = false;

	private final SharedString _sharedString;
	private final Color		   _defaultColor;

	public ForegroundColorKeepingStyledDocument(SharedString sharedString, Color defaultColor)
	{
	  _sharedString = sharedString;
	  _defaultColor = defaultColor;
	}

	public void setSettingColoredText(boolean settingColoredText)
	{
	  _settingColoredText = settingColoredText;
	}

	@Override
	public void insertString(int offs, String str, AttributeSet a) throws BadLocationException
	{
	  Object colorAttribute = a.getAttribute(StyleConstants.Foreground);
	  if ( colorAttribute != null && (!_settingColoredText || isCompleteColorChange(offs, str, colorAttribute))  )
	  {
		// Instead of the color of the previous character the new locally inserted text gets the default color (black).
		((MutableAttributeSet) a)
		.addAttribute(StyleConstants.Foreground, _defaultColor);
	  }
	  super.insertString(offs, str, a);
	}

	/**
	 * This check is true, if another user wrote so much text, that their color becomes the document's predominant one.
	 * It is unclear, how the {@link DefaultStyledDocument} does it and if it can be prevented somehow else. We want to
	 * prevent this because it would overwrite all text colors, including those of the locally written text.
	 */
	private boolean isCompleteColorChange(int offs, String str, Object colorAttribute)
	{
	  return offs == 0
		  && !colorAttribute.equals(_defaultColor)
		  && str.equals(_sharedString.get()); // We could probably also compare the lengths.
	}
  }
}