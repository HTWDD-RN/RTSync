package de.dmos.rtsync.customotter;

import java.util.HashMap;
import java.util.Map;

import se.l4.otter.model.ModelBuilder;
import se.l4.otter.model.SharedMap;
import se.l4.otter.model.SharedObject;
import se.l4.otter.model.internal.DefaultModelBuilder;
import se.l4.otter.model.spi.SharedObjectFactory;
import se.l4.otter.operations.Operation;
import se.l4.otter.operations.combined.CombinedHandler;

/**
 * An upgraded version of the {@link DefaultModelBuilder} that uses {@link CustomSharedMap}s for {@link SharedMap}s. It
 * had to be copied from {@link DefaultModelBuilder} because of private fields.
 */
public class CustomModelBuilder implements ModelBuilder
{
  protected final CustomEditor<Operation<CombinedHandler>> _editor;
  protected final Map<String, SharedObjectFactory<?, ?>> _types;
  protected boolean										 _resetOnOperationException	= true;

  @SuppressWarnings({"rawtypes", "unchecked"})
  public CustomModelBuilder(CustomEditor<? extends Operation<CombinedHandler>> editor)
  {
	_editor = (CustomEditor<Operation<CombinedHandler>>) editor;
	_types = new HashMap<>();
	addType("map", e -> new CustomSharedMap((CustomSharedObjectEditor) e));
	addType("string", e -> new CustomSharedString((CustomSharedObjectEditor) e));
	addType("list", e -> new CustomSharedList<Object>((CustomSharedObjectEditor) e));
  }

  @Override
  public <T extends SharedObject, O extends Operation<?>> ModelBuilder addType(
	String id,
	SharedObjectFactory<T, O> factory)
  {
	_types.put(id, factory);
	return this;
  }

  public CustomModelBuilder setResetOnOperationException(boolean reset)
  {
	_resetOnOperationException = reset;
	return this;
  }

  @Override
  public CustomModel build()
  {
	return new CustomModel(_editor, _types, _resetOnOperationException);
  }
}