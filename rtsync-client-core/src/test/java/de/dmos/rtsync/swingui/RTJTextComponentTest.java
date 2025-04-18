package de.dmos.rtsync.swingui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.InvocationTargetException;

import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;

import de.dmos.rtsync.customotter.CustomEditor;
import de.dmos.rtsync.customotter.CustomModelBuilder;
import de.dmos.rtsync.message.TaggedUserOperation;
import de.dmos.rtsync.test.RTSyncTestHelper;
import se.l4.otter.model.Model;
import se.l4.otter.model.SharedString;
import se.l4.otter.operations.Operation;
import se.l4.otter.operations.combined.CombinedDelta;
import se.l4.otter.operations.combined.CombinedHandler;
import se.l4.otter.operations.string.StringDelta;
import se.l4.otter.operations.string.StringHandler;

/**
 * A base class to test RTJTextField and RTJTextPane. The used JTextComponent is the main difference in these tests.
 *
 * @author <a href="mailto:michael.danzig@dmos2002.de">Michael Danzig</a>
 * @version $Rev$
 *
 */
abstract class RTJTextComponentTest
{
  private final String testText1 = "Hi";
  private final String testText2 = "Bye!";
  private final String testText3 = "";

  abstract JTextComponent createRTJTextComponent(SharedString sharedString);

  void updateSharedStringFromTextComponentChange() throws InvocationTargetException, InterruptedException
  {
	Model model = RTSyncTestHelper.createLocalSyncedInMemoryModel();
	SharedString sharedString = model.newString();
	JTextComponent rtTextComponent = createRTJTextComponent(sharedString);

	rtTextComponent.setText(testText1);
	assertEquals(testText1, rtTextComponent.getText());
	waitForGUIUpdate();
	assertEquals(testText1, sharedString.get());
	rtTextComponent.setText(testText2);
	assertEquals(testText2, rtTextComponent.getText());
	waitForGUIUpdate();
	assertEquals(testText2, sharedString.get());
	rtTextComponent.setText(testText3);
	assertEquals(testText3, rtTextComponent.getText());
	waitForGUIUpdate();
	assertEquals(testText3, sharedString.get());
  }

  void updateTextComponentFromSharedStringChange()
	  throws InvocationTargetException, InterruptedException
  {
	Model model = RTSyncTestHelper.createLocalSyncedInMemoryModel();
	SharedString sharedString = model.newString();
	JTextComponent rtTextComponent = createRTJTextComponent(sharedString);

	sharedString.set(testText1);
	waitForGUIUpdate();
	assertEquals(testText1, rtTextComponent.getText());
	sharedString.set(testText2);
	waitForGUIUpdate();
	assertEquals(testText2, rtTextComponent.getText());
	sharedString.set(testText3);
	waitForGUIUpdate();
	assertEquals(testText3, rtTextComponent.getText());
  }

  void changeTwoStringSharingTextComponentsFields()
	  throws InvocationTargetException, InterruptedException
  {
	Model model = RTSyncTestHelper.createLocalSyncedInMemoryModel();
	SharedString sharedString = model.newString();
	JTextComponent rtTextComponent1 = createRTJTextComponent(sharedString);
	JTextComponent rtTextComponent2 = createRTJTextComponent(sharedString);

	rtTextComponent1.setText(testText1);
	waitForGUIUpdate();
	assertEquals(testText1, rtTextComponent2.getText());
	sharedString.set(testText2);
	waitForGUIUpdate();
	assertEquals(testText2, rtTextComponent1.getText());
	assertEquals(testText2, rtTextComponent2.getText());
	rtTextComponent2.setText(testText3);
	waitForGUIUpdate();
	assertEquals(testText3, rtTextComponent1.getText());
  }

  void remoteChange()
	  throws InvocationTargetException, InterruptedException
  {
	CustomEditor<Operation<CombinedHandler>> editor =
		RTSyncTestHelper.createLocalSyncedInMemoryEditor();
	Model model = new CustomModelBuilder(editor).build();
	SharedString ss = model.newString();
	model.set("s1", ss);
	JTextComponent rtTextComponent1 = createRTJTextComponent(ss);

	ss.set(testText1);
	waitForGUIUpdate();
	assertEquals(testText1, rtTextComponent1.getText());

	Operation<StringHandler> stringOp = StringDelta.builder().retain(testText1.length()).insert(testText2).done();
	Operation<CombinedHandler> cOp = CombinedDelta.builder().update(ss.getObjectId(), "string", stringOp).done();
	TaggedUserOperation userOp2 = new TaggedUserOperation(3, "t2", cOp, null, null);
	editor.onTaggedUserOperationReceived(userOp2, false);

	String expected = testText1 + testText2;
	assertEquals(expected, ss.get());
	waitForGUIUpdate();
	assertEquals(expected, rtTextComponent1.getText());
  }

  void waitForGUIUpdate() throws InvocationTargetException, InterruptedException
  {
	SwingUtilities.invokeAndWait(() -> {
	});
  }
}
