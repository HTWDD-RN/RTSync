//package de.dmos.rtsync.swingui;
//
//import java.util.List;
//import java.util.Map;
//
//import javax.swing.JTextField;
//import javax.swing.text.Document;
//
//import de.dmos.rtsync.client.CursorsHandler;
//import de.dmos.rtsync.client.SubscriptionSupplier;
//import de.dmos.rtsync.client.internalinterfaces.ConnectionHandler;
//import de.dmos.rtsync.listeners.SelfSubscriptionSupplier;
//import de.dmos.rtsync.swingui.RTSharedTextComponentAdapter.AttributedTextSetter;
//import de.dmos.rtsync.swingui.RTSharedTextComponentAdapter.Insertion;
//import de.dmos.rtsync.swingui.RTSharedTextComponentAdapter.Selection;
//import se.l4.otter.engine.Editor;
//import se.l4.otter.model.SharedString;
//
///**
// * A JTextField that can be used to propagate updates to its string content to the {@link SharedString} string. Use a
// * constructor with an {@link Editor<Operation<?>>} in order to receive and display updates from the
// * {@link SharedString} or alternatively use {@link #addToListenersOfEditor(Editor)}. listeners.
// */
//public class RTJTextField extends JTextField implements AttributedTextSetter
//{
//  private static final long								 serialVersionUID = 1L;
//
//  protected final transient RTSharedTextComponentAdapter _sharedTextAdapter;
//
//  //  /**
//  //   * True, iff this text field just triggered an update to its text or the shared string. This prevents update
//  //   * recursions.
//  //   */
//  //  protected boolean                      _updateInProgress = false;
//  //
//  //  public static interface VoidFunction
//  //  {
//  //	void execute();
//  //  }
//
//  public RTJTextField(SharedString sharedString)
//  {
//	this(sharedString, null);
//  }
//
//  public RTJTextField(SharedString sharedString, ConnectionHandler connectionHandler)
//  {
//	this(null, sharedString, connectionHandler, connectionHandler, connectionHandler, 0);
//  }
//
//  public RTJTextField(SharedString sharedString, int columns)
//  {
//	this(null, sharedString, null, null, null, columns);
//  }
//
//  public RTJTextField(SharedString sharedString, ConnectionHandler connectionHandler, int columns)
//  {
//	this(null, sharedString, connectionHandler, connectionHandler, connectionHandler, columns);
//  }
//
//  public RTJTextField(
//	Document doc,
//	SharedString sharedString,
//	SubscriptionSupplier subscriptionSupplier,
//	SelfSubscriptionSupplier selfSubscriptionSupplier,
//	CursorsHandler cursorsHandler,
//	int columns)
//  {
//	super(doc, sharedString.get(), columns);
//	_sharedTextAdapter =
//		new RTSharedTextComponentAdapter(
//		  sharedString,
//		  subscriptionSupplier,
//		  selfSubscriptionSupplier,
//		  cursorsHandler,
//		  this,
//		  this);
//  }
//
//  @Override
//  public void setAttributedTextAndCursors(
//	String text,
//	List<Insertion> insertions,
//	Map<String, Selection> currentSelections,
//	Integer selectionStart,
//	Integer selectionEnd)
//  {
//	setText(text);
//	//	StyledDocument styledDoc = getStyledDocument();
//	//	for ( Insertion ins : insertions )
//	//	{
//	//	  styledDoc.setCharacterAttributes(ins.getStart(), ins.getLength(), ins.getAttributes(), true);
//	//	}
//
//	setCaretPosition(selectionStart);
//	moveCaretPosition(selectionEnd);
//  }
//
//  @Override
//  public void setAttributedText(Insertion insertion)
//  {
//	// TODO Auto-generated method stub
//
//  }
//
//  @Override
//  public void setCursor(String user, Selection selection)
//  {
//	// TODO Auto-generated method stub
//
//  }
//
//  //  public void setSharedStringTextIfDifferent()
//  //  {
//  //	String text = getText();
//  //	if ( !text.equals(_sharedString.get()) )
//  //	{
//  //	  _sharedString.set(text);
//  //	}
//  //  }
//  //
//  //  @Override
//  //  public void changedUpdate(DocumentEvent e)
//  //  {
//  //	setSharedStringTextIfDifferent();
//  //  }
//  //
//  //  @Override
//  //  public void insertUpdate(DocumentEvent e)
//  //  {
//  //	setSharedStringTextIfDifferent();
//  //  }
//  //
//  //  @Override
//  //  public void removeUpdate(DocumentEvent e)
//  //  {
//  //	setSharedStringTextIfDifferent();
//  //  }
//  //
//  //  // TODO: Find out, if this is needed. If no, then remove it and the "implements ActionListener", otherwise change the method body to do what it should do.
//  //  @Override
//  //  public void actionPerformed(ActionEvent e)
//  //  {
//  //	String eventString = e.toString();
//  //	System.out.println(eventString);
//  //  }
//  //
//  //  public void updateTextFromOp(Operation<StringHandler> op, String user)
//  //  {
//  //	String shared = _sharedString.get();
//  //	String text = getText();
//  //	if ( !text.equals(shared) )
//  //	{
//  //	  _caretPosition = getCaretPosition();
//  //	  setText(shared);
//  //	  op.apply(new UserStringOperationHandler(user));
//  //	  setCaretPosition(_caretPosition);
//  //
//  //	  //	  textField1.setText("<html><font color=\"blue\">" + "asdfds" + "</font></html>");
//  //	}
//  //  }
//  //
//  //  private class UserStringOperationHandler implements StringHandler
//  //  {
//  //	int _index = 0;
//  //	String _user;
//  //
//  //	UserStringOperationHandler(String user)
//  //	{
//  //	  _user = user;
//  //	}
//  //
//  //	@Override
//  //	public void retain(int count)
//  //	{
//  //	  _index += count;
//  //	}
//  //
//  //	@Override
//  //	public void insert(String s)
//  //	{
//  //	  moveCursorsIfAppropriate(_index, s.length());
//  //	  updateInsertions(new Insertion(_index, s.length(), _user));
//  //	  _index += s.length();
//  //	  if ( _user != null )
//  //	  {
//  //		_userCursors.put(_user, _index);
//  //	  }
//  //	}
//  //
//  //	@Override
//  //	public void delete(String s)
//  //	{
//  //	  moveCursorsIfAppropriate(_index, s.length());
//  //	  if ( _user != null )
//  //	  {
//  //		_userCursors.put(_user, _index);
//  //	  }
//  //	}
//  //
//  //	@Override
//  //	public void annotationUpdate(AnnotationChange change)
//  //	{
//  //	  // Nothing to do.
//  //	}
//  //  }
//  //
//  //  private static record Insertion(int start, int length, String user) implements Comparable<Insertion>
//  //  {
//  //	int end()
//  //	{
//  //	  return start + length;
//  //	}
//  //
//  //	Collection<Insertion> applyCursorMovement(int minIndex, int offset) {
//  //	  int newStart = getNewCursorPosition(start, minIndex, offset);
//  //	  int newEnd = getNewCursorPosition(end(), minIndex, offset);
//  //	  int newLength = newEnd - newStart;
//  //	  if (newLength < 1) {
//  //		return null;
//  //	  }
//  //	  if (newLength == length) {
//  //		List.of(new Insertion(newStart, newLength, user));
//  //	  }
//  //	  int leftLength = minIndex - start;
//  //	  Insertion leftInsertion = new Insertion(start, leftLength, user);
//  //	  int rightLength = length - leftLength;
//  //	  Insertion rightInsertion = new Insertion(newEnd - rightLength, rightLength, user);
//  //	  return List.of(leftInsertion, rightInsertion);
//  //	}
//  //
//  //	static Insertion mergeIfPossible(Insertion current, Insertion next)
//  //	{
//  //	  if ( !Objects.equals(current.user, next.user) || current.end() < next.start )
//  //	  {
//  //		return null;
//  //	  }
//  //	  return new Insertion(current.start, next.end() - current.start, current.user);
//  //	}
//  //
//  //	@Override
//  //	public int compareTo(Insertion o)
//  //	{
//  //	  return _start - o._start;
//  //	}
//  //  }
//  //
//  //  void updateInsertions(Insertion newInsertion)
//  //  {
//  //	_currentInsertions.add(newInsertion);
//  //	//	Set<Insertion> mergedSet = new TreeSet<>();
//  //	//	Set<Insertion> removed = new TreeSet<>();
//  //	//	//	_currentInsertions = _currentInsertions.stream().reduce(new TreeSet<>(), (set, ins) -> {});
//  //	//	_currentInsertions.iterator()
//  //	//	Insertion prevInsertion = iter.next();
//  //	//	while ( iter.hasNext() )
//  //	//	{
//  //	//	  Insertion currentInsertion
//  //	//	}
//  //  }
//  //
//  //  private void moveCursorsIfAppropriate(int minIndex, int offset)
//  //  {
//  //	if ( offset == 0 )
//  //	{
//  //	  return;
//  //	}
//  //	_userCursors.entrySet().forEach(e -> e.setValue(getNewCursorPosition(e.getValue(), minIndex, offset)));
//  //	_caretPosition = getNewCursorPosition(_caretPosition, minIndex, offset);
//  //  }
//  //
//  //  static int getNewCursorPosition(int currentIndex, int minIndex, int offset)
//  //  {
//  //	if ( currentIndex < minIndex )
//  //	{
//  //	  return currentIndex;
//  //	}
//  //	if ( currentIndex + offset < minIndex )
//  //	{
//  //	  return minIndex;
//  //	}
//  //	return currentIndex + offset;
//  //  }
//  //
//  //  @Override
//  //  public void valueChanged(Operation<StringHandler> op, boolean local, String user)
//  //  {
//  //	SwingUtilities.invokeLater(() -> updateTextFromOp(op, user));
//  //  }
//  //
//  //  @Override
//  //  public void onSubscribersReceived(List<Subscriber> subscribers)
//  //  {
//  //	_subscribers = subscribers;
//  //  }
//}
