package de.dmos.rtsync.swingui;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.swing.SwingUtilities;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;

import de.dmos.rtsync.client.CursorsHandler;
import de.dmos.rtsync.client.SubscriptionSupplier;
import de.dmos.rtsync.customotter.ChangeListener;
import de.dmos.rtsync.customotter.CustomSharedString;
import de.dmos.rtsync.listeners.CursorsListener;
import de.dmos.rtsync.listeners.SelfSubscriberListener;
import de.dmos.rtsync.listeners.SelfSubscriptionSupplier;
import de.dmos.rtsync.listeners.SubscriberListener;
import de.dmos.rtsync.listeners.UserIdAndColorListener;
import de.dmos.rtsync.message.CursorPosition;
import de.dmos.rtsync.message.Subscriber;
import de.dmos.rtsync.message.UserCursors;
import se.l4.otter.model.SharedString;
import se.l4.otter.operations.Operation;
import se.l4.otter.operations.string.AnnotationChange;
import se.l4.otter.operations.string.StringHandler;

/**
 * A class that encapsulates required functionality to draw colored text and a colored background for a
 * {@link SharedString} depending on its modifying user's colors in order to display changes to it in real time to an
 * {@link AttributedTextSetter}.
 *
 * @author <a href="mailto:michael.danzig@dmos2002.de">Michael Danzig</a>
 * @version $Rev$
 *
 */
public class RTSharedTextComponentAdapter
implements
SubscriberListener,
SelfSubscriberListener,
ChangeListener<Operation<StringHandler>>,
DocumentListener,
CursorsListener,
CaretListener
{
  static final Color				   DEFAULT_COLOR	  = Color.BLACK;

  private final SharedString		   _sharedString;
  private final JTextComponent		   _textComponent;
  private final AttributedTextSetter   _attributedTextSetter;
  private final CursorsHandler		   _cursorsHandler;

  private final UserIdAndColorListener _userIdAndColorListener;
  private SortedSet<Insertion>		   _currentInsertions	  = new TreeSet<>();
  private final Map<String, Selection> _currentSelections = new HashMap<>();
  /**
   * Keeps track of a text change which should only be handled once.
   */
  private String					   _locallyChangedText;
  /**
   * Determines whether document updates should update the shared string.
   */
  private boolean					   _settingText		  = false;
  private boolean					   _inLocalDocumentChange = false;
  private Selection					   _selection;

  RTSharedTextComponentAdapter(
	SharedString sharedString,
	SubscriptionSupplier subscriptionSupplier,
	SelfSubscriptionSupplier selfSubscriptionSupplier,
	CursorsHandler cursorsHandler,
	JTextComponent textComponent,
	AttributedTextSetter attributedTextSetter)
  {
	_sharedString = sharedString;
	_textComponent = textComponent;
	_attributedTextSetter = attributedTextSetter;
	_cursorsHandler = cursorsHandler;
	_userIdAndColorListener = new UserIdAndColorListener(selfSubscriptionSupplier, subscriptionSupplier);
	if ( selfSubscriptionSupplier != null )
	{
	  selfSubscriptionSupplier.addSelfSubscriberListener(this);
	}
	if ( subscriptionSupplier != null )
	{
	  subscriptionSupplier.addWeakSubscriberListener(this);
	  onSubscribersReceived(subscriptionSupplier.getSubscribers());
	}
	if ( cursorsHandler != null )
	{
	  cursorsHandler.addCursorsListener(this);
	  textComponent.addCaretListener(this);
	}
	textComponent.getDocument().addDocumentListener(this);
	if ( sharedString instanceof CustomSharedString css )
	{
	  css.addAsWeakChangeListener(this);
	}
  }

  public static interface AttributedTextSetter
  {
	void setAttributedTextAndCursors(
	  String text,
	  List<Insertion> insertions,
	  Map<String, Selection> currentSelections,
	  Selection selection);

	void setCursor(String user, Selection selection);
  }

  private void setSharedStringTextIfDifferent()
  {
	if ( _settingText )
	{
	  return;
	}
	_inLocalDocumentChange = true;
	String text = _textComponent.getText();
	if ( !text.equals(_sharedString.get()) )
	{
	  _locallyChangedText = text;
	  _sharedString.set(text);
	}
  }

  @Override
  public void changedUpdate(DocumentEvent e)
  {
	setSharedStringTextIfDifferent();
  }

  @Override
  public void insertUpdate(DocumentEvent e)
  {
	setSharedStringTextIfDifferent();
  }

  @Override
  public void removeUpdate(DocumentEvent e)
  {
	setSharedStringTextIfDifferent();
  }

  protected void updateSelectionColor(Selection sel)
  {
	_userIdAndColorListener.updateSelectionColor(sel, DEFAULT_COLOR);
  }

  public void updateTextFromOp(Operation<StringHandler> op, String user)
  {
	String oldText = _textComponent.getText();
	String sharedText = _sharedString.get();
	if ( oldText == null || !oldText.equals(sharedText) || oldText.equals(_locallyChangedText) )
	{
	  _locallyChangedText = null;
	  if ( op == null )
	  {
		op = CustomSharedString.diffToOperation(oldText, sharedText);
	  }
	  updateGUI(op, user);
	}
  }

  /**
   * Called when the text of the shared string or the user colors are updated to update the GUI from the new
   * information.
   */
  private void updateGUI(Operation<StringHandler> op, String user)
  {
	String oldText = _textComponent.getText();
	String sharedToUpdate = _sharedString.get();
	if ( oldText != null && oldText.equals(_sharedString.get()) )
	{
	  sharedToUpdate = null;
	}

	if ( op == null )
	{
	  updateGUI(sharedToUpdate, getInsertionList(), _currentSelections, null);
	  return;
	}

	int start = _textComponent.getSelectionStart();
	int end = _textComponent.getSelectionEnd();
	Selection selection = new Selection(start, end - start, null);

	UserStringOperationHandler handler =
		new UserStringOperationHandler(user, selection);
	op.apply(handler);
	Selection currentOpSelection = handler.getCurrentSelection();
	if ( currentOpSelection != null )
	{
	  _currentSelections.put(user, currentOpSelection);
	}
	updateSelectionAndInsertionColors();

	_selection = handler.getOwnSelection();
	updateGUI(sharedToUpdate, getInsertionList(), _currentSelections, _selection);
  }

  private void updateSelectionAndInsertionColors()
  {
	_currentSelections.values().forEach(this::updateSelectionColor);
	_currentInsertions.forEach(this::updateSelectionColor);
  }

  private List<Insertion> getInsertionList()
  {
	return new ArrayList<>(_currentInsertions);
  }

  private void updateGUI(
	String shared,
	List<Insertion> resolvedInsertions,
	Map<String, Selection> currentSelections,
	Selection selection)
  {
	SwingUtilities.invokeLater(() -> {
	  _settingText = true;
	  _attributedTextSetter
	  .setAttributedTextAndCursors(shared, resolvedInsertions, currentSelections, selection);
	  _settingText = false;
	});
  }

  private class UserStringOperationHandler implements StringHandler
  {
	int		  _index = 0;
	String	  _user;
	Selection	  _currentSelection;
	Selection	  _ownSelection;

	public Selection getCurrentSelection()
	{
	  return _currentSelection;
	}

	public Selection getOwnSelection()
	{
	  return _ownSelection;
	}

	UserStringOperationHandler(String user, Selection ownSelection)
	{
	  _user = user;
	  _currentSelection = user != null ? _currentSelections.get(user) : null;
	  _ownSelection = ownSelection;
	}

	private void moveCursorsIfAppropriate(int minIndex, int offset)
	{
	  if ( offset == 0 )
	  {
		return;
	  }
	  @SuppressWarnings("unchecked")
	  List<Insertion> movedInsertions = (List<Insertion>) _currentInsertions
	  .stream()
	  .flatMap(i -> i.move(minIndex, offset).stream())
	  .toList();
	  _currentInsertions = new TreeSet<>(movedInsertions);
	  for ( Map.Entry<String, Selection> entry : _currentSelections.entrySet() )
	  {
		List<? extends Selection> movedSelections = entry.getValue().move(minIndex, offset);
		if ( movedSelections.isEmpty() )
		{
		  _currentSelections.remove(entry.getKey());
		}
		else
		{
		  _currentSelections.put(entry.getKey(), movedSelections.get(0));
		}
	  }
	  if ( _ownSelection != null )
	  {
		List<? extends Selection> newOwnSelections = _ownSelection.move(minIndex, offset);
		_ownSelection = newOwnSelections.isEmpty() ? null : newOwnSelections.getFirst();
	  }
	}

	@Override
	public void retain(int count)
	{
	  _index += count;
	}

	@Override
	public void insert(String s)
	{
	  moveCursorsIfAppropriate(_index, s.length());
	  if ( _user != null )
	  {
		_currentInsertions.add(new Insertion(_index, s.length(), _user));
		_currentSelection = new Selection(_index + s.length(), 0, _user);
	  }
	  _index += s.length();
	}

	@Override
	public void delete(String s)
	{
	  moveCursorsIfAppropriate(_index, -s.length());
	  if ( _user != null )
	  {
		_currentSelection = new Selection(_index, 0, _user);
	  }
	}

	@Override
	public void annotationUpdate(AnnotationChange change)
	{
	  // Nothing to do.
	}
  }

  @Override
  public void valueChanged(Operation<StringHandler> op, boolean local, String user)
  {
	updateTextFromOp(op, user);
  }

  @Override
  public void onSubscribersReceived(List<Subscriber> subscribers)
  {
	if ( subscribers != null )
	{
	  updateOwnColorAndGUI(true);
	}
  }

  public void updateOwnColorAndGUI(boolean updateColors)
  {
	Subscriber selfSubscriber = _userIdAndColorListener.getSelfSubscriber();
	if ( selfSubscriber != null )
	{
	  _currentSelections.remove(selfSubscriber.getName());
	}
	if ( updateColors )
	{
	  updateSelectionAndInsertionColors();
	}
	updateGUI(null, null);
  }

  @Override
  public void onOwnNameOrColorChanged(Subscriber subscriber)
  {
	updateOwnColorAndGUI(false);
  }

  @Override
  public void onUserCursorsReceived(UserCursors cursorsMessage)
  {
	String user = _userIdAndColorListener.getUser(cursorsMessage.userId());
	Optional<CursorPosition> cursorPosition = cursorsMessage.cursors().stream().filter(cp -> cp.id().equals(_sharedString.getObjectId())).findAny();
	if ( cursorPosition.isEmpty() || user == null )
	{
	  return;
	}
	CursorPosition cursor = cursorPosition.get();

	int start = cursor.startIndex() != null ? cursor.startIndex() : cursor.endIndex();
	int length = cursor.endIndex() - start;
	Selection changingSelection = _currentSelections.compute(user, (u, sel) -> {
	  if (sel == null) {
		sel = new Selection(start, cursor.endIndex() - start, u);
		updateSelectionColor(sel);
	  }
	  else
	  {
		sel.setInterval(start, length);
	  }
	  return sel;
	});

	SwingUtilities.invokeLater(() -> _attributedTextSetter.setCursor(user, changingSelection));
  }

  @Override
  public void caretUpdate(CaretEvent e)
  {
	if ( _settingText )
	{
	  return;
	}
	if ( _inLocalDocumentChange )
	{
	  _inLocalDocumentChange = false;
	  return;
	}
	Selection newSelection = new Selection(e.getDot(), e.getMark() - e.getDot(), null);
	if ( _selection == null || !newSelection.hasEqualInterval(_selection) )
	{
	  _selection = newSelection;
	  _cursorsHandler.updateCursor(new CursorPosition(_sharedString.getObjectId(), e.getDot(), e.getMark()));
	}
  }
}
