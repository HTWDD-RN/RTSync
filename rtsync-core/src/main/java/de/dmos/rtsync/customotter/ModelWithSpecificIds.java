package de.dmos.rtsync.customotter;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.l4.otter.engine.Editor;
import se.l4.otter.model.DefaultModel;
import se.l4.otter.model.SharedObject;
import se.l4.otter.model.spi.SharedObjectFactory;
import se.l4.otter.operations.Operation;
import se.l4.otter.operations.combined.CombinedHandler;

/**
 * An upgraded {@link DefaultModel} which makes it easier to create {@link SharedObject}s with given ids by which the
 * objects can be merged.
 *
 * @author <a href="mailto:michael.danzig@dmos2002.de">Michael Danzig</a>
 * @version $Rev$
 *
 */
public class ModelWithSpecificIds extends DefaultModel
{
  private static final Logger LOG = LoggerFactory.getLogger(ModelWithSpecificIds.class);

  public ModelWithSpecificIds(Editor<Operation<CombinedHandler>> editor, Map<String, SharedObjectFactory<?, ?>> types)
  {
    super(editor, types);
  }

  //  public SharedString getOrCreateString(String id)
  //  {
  //    return (SharedString) get(id, () -> getOrCreateObject(id, "string"));
  //  }
  //
  //  public SharedMap getOrCreateMap(String id)
  //  {
  //    return (SharedMap) get(id, () -> getOrCreateObject(id, "map"));
  //  }
  //
  //  @SuppressWarnings("unchecked")
  //  public <T> SharedList<T> getOrCreateList(String id)
  //  {
  //    return (SharedList<T>) get(id, () -> getOrCreateObject(id, "list"));
  //  }
  //
  //  private SharedObject getOrCreateObject(String id, String type)
  //  {
  //    // Does this need locking for our purposes?
  //    SharedObject obj = get(id, () -> getObject(id, type));
  //    if (!obj.getObjectType().equals(type)) {
  //      LOG.error("The shared object with the id {} was expected to be a {} but was actually a {}.", id, type, obj.getObjectType() );
  //    }
  //    return obj;
  //  }
}