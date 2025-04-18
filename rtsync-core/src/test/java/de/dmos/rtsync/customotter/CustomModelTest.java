package de.dmos.rtsync.customotter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.dmos.rtsync.test.RTSyncTestHelper;
import se.l4.otter.engine.LocalOperationSync;
import se.l4.otter.model.Model;
import se.l4.otter.model.SharedMap;
import se.l4.otter.model.SharedString;
import se.l4.otter.operations.Operation;
import se.l4.otter.operations.combined.CombinedHandler;

class CustomModelTest
{
  private CustomEditorControl<Operation<CombinedHandler>> _control;
  private LocalOperationSync<Operation<CombinedHandler>>  _sync;

  @BeforeEach
  void setupEditor()
  {
	_control = new CustomEditorControl<>(RTSyncTestHelper.createCustomHistory());
	_sync = new LocalOperationSync<>(_control);
  }

  @AfterEach
  void close() throws IOException
  {
	_sync.close();
  }

  private CustomEditor<Operation<CombinedHandler>> editor()
  {
	return new CustomEditor<>(_sync);
  }

  private Model model()
  {
	return new CustomModelBuilder(editor()).build();
  }

  @Test
  void testRootMap()
  {
	Model m1 = model();

	_sync.suspend();
	m1.set("key", "value");

	assertEquals("value", m1.get("key"));

	_sync.resume();
	_sync.waitForEmpty();

	assertEquals("value", m1.get("key"));

	Model m2 = model();
	assertEquals("value", m2.get("key"));
  }

  @Test
  void testRootMapEvents()
  {
	Model m1 = model();
	Model m2 = model();

	AtomicReference<Object> b = new AtomicReference<>();
	m2.addChangeListener(new SharedMap.Listener() {
	  @Override
	  public void valueRemoved(String key, Object oldValue)
	  {
	  }

	  @Override
	  public void valueChanged(String key, Object oldValue, Object newValue)
	  {
		b.set(newValue);
	  }
	});

	m1.set("key", "value");
	_sync.waitForEmpty();

	assertEquals("value", m2.get("key"));
	assertEquals("value", b.get());
  }

  @Test
  void testNewString()
  {
	Model m1 = model();

	SharedString s1 = m1.newString();
	s1.set("Hello World");
	m1.set("string", s1);

	_sync.waitForEmpty();

	Model m2 = model();
	SharedString s2 = m2.get("string");
	assertEquals("Hello World", s2.get());

	s1.set("Hello Cookies");
	_sync.waitForEmpty();
	assertEquals("Hello Cookies", s2.get());
  }
}
