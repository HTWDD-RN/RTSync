package de.dmos.rtsync.customotter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.dmos.rtsync.test.RTSyncTestHelper;
import se.l4.otter.engine.Editor;
import se.l4.otter.engine.LocalOperationSync;
import se.l4.otter.lock.CloseableLock;
import se.l4.otter.operations.DefaultCompoundOperation;
import se.l4.otter.operations.Operation;
import se.l4.otter.operations.combined.CombinedDelta;
import se.l4.otter.operations.combined.CombinedHandler;
import se.l4.otter.operations.internal.combined.Update;
import se.l4.otter.operations.string.StringDelta;
import se.l4.otter.operations.string.StringHandler;

class CustomEditorTest
{
  private static final String                             ID   = "id1";

  private CustomEditorControl<Operation<CombinedHandler>> _control;
  private LocalOperationSync<Operation<CombinedHandler>>  _sync;

  @BeforeEach
  void setupEditor()
  {
    _control = new CustomEditorControl<>(RTSyncTestHelper.createCustomHistory());
    _control
    .store(
      1,
      "abcd",
      CombinedDelta.builder().update(ID, "string", StringDelta.builder().insert("Hello World").done()).done());

    _sync = new LocalOperationSync<>(_control);
  }

  @AfterEach
  void closeSync()
  {
    _sync.close();
  }

  private Editor<Operation<CombinedHandler>> editor()
  {
    return new CustomEditor<>(_sync);
  }

  private void applyToEditor(Editor<Operation<CombinedHandler>> editor, Operation<StringHandler> stringOp)
  {
    editor.apply(CombinedDelta.builder().update(ID, "string", stringOp).done());
  }

  @Test
  void test1()
  {
    Editor<Operation<CombinedHandler>> editor = editor();

    applyToEditor(editor, StringDelta.builder().retain(6).delete("World").insert("Cookies").done());

    _sync.waitForEmpty();

    assertString(editor, StringDelta.builder().insert("Hello Cookies").done());
  }

  @Test
  void testMultiple1()
  {
    Editor<Operation<CombinedHandler>> e1 = editor();
    Editor<Operation<CombinedHandler>> e2 = editor();

    applyToEditor(e1, StringDelta.builder().retain(6).delete("World").insert("Cookies").done());

    _sync.waitForEmpty();

    assertString(e2, StringDelta.builder().insert("Hello Cookies").done());
  }

  @Test
  void testMultiple2()
  {
    Editor<Operation<CombinedHandler>> e1 = editor();
    Editor<Operation<CombinedHandler>> e2 = editor();

    _sync.suspend();
    applyToEditor(e1, StringDelta.builder().retain(6).delete("World").insert("Cookies").done());

    applyToEditor(e2, StringDelta.builder().retain(11).insert("!").done());
    _sync.resume();

    _sync.waitForEmpty();

    assertString(e1, StringDelta.builder().insert("Hello Cookies!").done());

    assertString(e2, StringDelta.builder().insert("Hello Cookies!").done());

  }

  @Test
  void testMultiple3()
  {
    Editor<Operation<CombinedHandler>> e1 = editor();
    Editor<Operation<CombinedHandler>> e2 = editor();

    _sync.suspend();
    applyToEditor(e1, StringDelta.builder().retain(6).delete("World").insert("Cookies").done());

    applyToEditor(e2, StringDelta.builder().retain(11).insert("!").done());
    applyToEditor(e2, StringDelta.builder().retain(11).insert("!").retain(1).done());
    _sync.resume();

    _sync.waitForEmpty();

    assertString(e1, StringDelta.builder().insert("Hello Cookies!!").done());

    assertString(e2, StringDelta.builder().insert("Hello Cookies!!").done());
  }

  @Test
  void testMultiple4()
  {
    Editor<Operation<CombinedHandler>> e1 = editor();
    Editor<Operation<CombinedHandler>> e2 = editor();

    _sync.suspend();
    applyToEditor(e1, StringDelta.builder().retain(6).delete("World").insert("Cookies").done());

    applyToEditor(e2, StringDelta.builder().retain(11).insert("!").done());

    applyToEditor(e1, StringDelta.builder().retain(12).delete("s").done());

    applyToEditor(e2, StringDelta.builder().retain(11).insert("!").retain(1).done());
    _sync.resume();

    _sync.waitForEmpty();

    assertString(e1, StringDelta.builder().insert("Hello Cookie!!").done());

    assertString(e2, StringDelta.builder().insert("Hello Cookie!!").done());
  }

  @Test
  void testMultiple5()
  {
    Editor<Operation<CombinedHandler>> e1 = editor();
    Editor<Operation<CombinedHandler>> e2 = editor();

    _sync.suspend();
    applyToEditor(e1, StringDelta.builder().insert("a").retain(11).done());

    applyToEditor(e2, StringDelta.builder().insert("b").retain(11).done());

    _sync.resume();

    _sync.waitForEmpty();

    assertString(e1, StringDelta.builder().insert("abHello World").done());

    assertString(e2, StringDelta.builder().insert("abHello World").done());
  }

  @Test
  void testMultiple6()
  {
    Editor<Operation<CombinedHandler>> e1 = editor();
    Editor<Operation<CombinedHandler>> e2 = editor();

    _sync.suspend();
    applyToEditor(e1, StringDelta.builder().insert("a").retain(11).done());

    applyToEditor(e2, StringDelta.builder().insert("b").retain(11).done());

    applyToEditor(e2, StringDelta.builder().retain(1).insert("c").retain(11).done());

    _sync.resume();

    _sync.waitForEmpty();

    assertString(e1, StringDelta.builder().insert("abcHello World").done());

    assertString(e2, StringDelta.builder().insert("abcHello World").done());
  }

  @Test
  void testMultiple7()
  {
    Editor<Operation<CombinedHandler>> e1 = editor();
    Editor<Operation<CombinedHandler>> e2 = editor();

    _sync.suspend();
    applyToEditor(e1, StringDelta.builder().insert("a").retain(11).done());

    applyToEditor(e2, StringDelta.builder().insert("b").retain(11).done());

    applyToEditor(e2, StringDelta.builder().retain(1).insert("c").retain(11).done());

    applyToEditor(e1, StringDelta.builder().retain(1).insert("d").retain(11).done());

    _sync.resume();

    _sync.waitForEmpty();

    assertString(e1, StringDelta.builder().insert("abdcHello World").done());

    assertString(e2, StringDelta.builder().insert("abdcHello World").done());
  }

  @Test
  void testLock1()
  {
    Editor<Operation<CombinedHandler>> e1 = editor();
    Editor<Operation<CombinedHandler>> e2 = editor();

    try (CloseableLock lock = e1.lock())
    {
      applyToEditor(e1, StringDelta.builder().retain(6).delete("World").insert("Cookies").done());

      // This should lock everything if e1 would actually send the event
      _sync.waitForEmpty();

      assertString(e2, StringDelta.builder().insert("Hello World").done());
    }

    _sync.waitForEmpty();

    assertString(e2, StringDelta.builder().insert("Hello Cookies").done());
  }

  private void assertString(Editor<Operation<CombinedHandler>> editor, Operation<StringHandler> stringOp)
  {
    Operation<CombinedHandler> cOp = editor.getCurrent();
    assertTrue(cOp instanceof DefaultCompoundOperation);
    @SuppressWarnings("rawtypes")
    DefaultCompoundOperation compoundOp = (DefaultCompoundOperation) cOp;
    Object firstOp = compoundOp.getOperations().get(0);
    assertTrue(firstOp instanceof Update);
    Update stringUpdate = (Update) firstOp;
    assertEquals(stringOp, stringUpdate.getOperation());
  }
}
