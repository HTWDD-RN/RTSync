package de.dmos.rtsync.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.eclipse.collections.impl.factory.Sets;

import de.dmos.rtsync.customotter.CustomModel;
import de.dmos.rtsync.customotter.CustomSharedList;
import de.dmos.rtsync.customotter.CustomSharedMap;
import se.l4.otter.model.SharedList;
import se.l4.otter.model.SharedMap;
import se.l4.otter.model.SharedObject;
import se.l4.otter.model.SharedString;

/**
 * This contains several functions for converting {@link SharedObject}s and finding differences in model objects. The
 * functions for finding differences and for checking equality try to ignore the different classes of model objects to a
 * certain degree, so that they can check if an object equals its serialized and then deserialized counterpart.
 *
 * @author <a href="mailto:michael.danzig@dmos2002.de">Michael Danzig</a>
 * @version $Rev$
 *
 */
public class SharedObjectHelper
{
  private SharedObjectHelper()
  {
  }

  private static final String PATH_SEPARATOR = "/";

  /**
   * Creates a new {@link List} from the current state of the given {@link SharedList} without locking it.
   *
   * @implNote This runs in n choose 2 -> which means in O(n²), due to SharedListImpl.values being private and not
   *           offering a method which returns all values at once or an iterator.
   */
  private static <T> List<T> toList(SharedList<T> sharedList)
  {
	if ( sharedList == null )
	{
	  return null;
	}
	if ( sharedList instanceof CustomSharedList<T> customList )
	{
	  return customList.toList();
	}

	int length = sharedList.length();
	List<T> copiedList = new ArrayList<>(length);
	for ( int i = 0; i < length; i++ )
	{
	  copiedList.add(sharedList.get(i));
	}
	return copiedList;
  }

  /**
   * Creates a new array from the current state of the given {@link SharedList} without locking it.
   *
   * @implNote This runs in n choose 2 -> which means in O(n²), due to SharedListImpl.values being private and not
   *           offering a method which returns all values at once or an iterator.
   */
  @SuppressWarnings("unchecked")
  public static <T> T[] toArray(SharedList<T> sharedList)
  {
	List<T> list = toList(sharedList);
	return list != null ? (T[]) list.toArray() : null;
  }

  public static boolean sharedListEquals(SharedList<?> sharedList, List<?> expectedList)
  {
	return listEquals(SharedObjectHelper.toList(sharedList), expectedList);
  }

  public static boolean listEquals(List<?> l1, List<?> l2)
  {
	return getListDifferences(l1, l2, "").isEmpty();
  }

  public static boolean arrayEquals(Object[] a1, Object[] a2)
  {
	return getArrayDifferences(a1, a2, "").isEmpty();
  }

  public static List<String> getArrayDifferences(Object[] a1, Object[] a2, String path)
  {
	if ( a1.length != a2.length )
	{
	  return List.of(path);
	}
	List<String> differences = new ArrayList<>();
	String subPath = path + PATH_SEPARATOR;
	for ( int i = 0; i < a1.length; i++ )
	{
	  getObjectDifferences(a1[i], a2[i], subPath + i).forEach(differences::add);
	}
	return differences;
  }

  public static boolean sharedMapEquals(SharedMap sharedMap, SharedMap otherMap)
  {
	return mapEquals(toMap(sharedMap), toMap(otherMap));
  }

  public static boolean sharedMapEquals(SharedMap sharedMap, Map<String, ?> expectedMap)
  {
	return mapEquals(toMap(sharedMap), expectedMap);
  }

  private static boolean mapEquals(Map<String, ?> m1, Map<String, ?> m2)
  {
	return getMapDifferences(m1, m2, "").isEmpty();
  }

  public static List<String> getMapDifferences(Map<String, ?> m1, Map<String, ?> m2, String path)
  {
	List<String> differences = new ArrayList<>();
	if ( m1 == null || m2 == null )
	{
	  differences.add(path);
	  return differences;
	}

	Set<String> keys1 = m1.keySet();
	Set<String> keys2 = m2.keySet();
	String subPath = path.isEmpty() ? path : path + PATH_SEPARATOR;
	if ( keys1 != null && keys2 != null )
	{
	  Sets.symmetricDifference(keys1, keys2).forEach(d -> differences.add(subPath + d));
	}
	Set<String> keysToCheck = keys1 != null ? keys1 : keys2;
	if ( keysToCheck != null )
	{
	  keysToCheck
	  .stream()
	  .filter(k -> !differences.contains(k))
	  .flatMap(k -> getObjectDifferences(m1.get(k), m2.get(k), subPath + k).stream())
	  .forEach(differences::add);
	}
	return differences;
  }

  public static List<String> getListDifferences(List<?> l1, List<?> l2, String path)
  {
	if ( l1 == null || l2 == null || l1.size() != l2.size() )
	{
	  return List.of(path);
	}
	return getArrayDifferences(l1.toArray(), l2.toArray(), path);
  }

  @SuppressWarnings("all")
  public static Map<String, Object> toMap(Object obj)
  {
	if ( obj instanceof CustomModel model )
	{
	  return model.getRoot().toMap();
	}
	else if ( obj instanceof Map map )
	{
	  return map;
	}
	else if ( obj instanceof CustomSharedMap customSharedMap )
	{
	  return customSharedMap.toMap();
	}

	return null;
  }

  public static boolean isMapLike(Object obj)
  {
	return obj instanceof SharedMap || obj instanceof Map;
  }

  public static boolean isList(Object obj)
  {
	return obj instanceof List || obj instanceof SharedList;
  }

  @SuppressWarnings("all")
  public static List<?> toList(Object obj)
  {
	if ( obj instanceof List list )
	{
	  return list;
	}
	else if ( obj instanceof SharedList sl )
	{
	  return toList(sl);
	}
	return null;
  }

  public static boolean sharedObjectEquals(Object obj1, Object obj2)
  {
	return getObjectDifferences(obj1, obj2, "").isEmpty();
  }

  private static List<String> getObjectDifferences(Object obj1, Object obj2, String path)
  {
	if ( obj1 instanceof SharedString ss1 && obj2 instanceof SharedString ss2 )
	{
	  return getSingleDifference(Objects.equals(ss1.get(), ss2.get()), path);
	}
	else if ( isList(obj1) && isList(obj2) )
	{
	  return getListDifferences(toList(obj1), toList(obj2), path);
	}
	else if ( isMapLike(obj1) || isMapLike(obj2) )
	{
	  return getMapDifferences(toMap(obj1), toMap(obj2), path);
	}
	else if ( obj1 instanceof Number n1 && obj2 instanceof Number n2 )
	{
	  // Locally created float values are stored less precisely than their transmitted double values. Therefore, we must either
	  // tolerate some error in this equality check or prohibit floats as model objects.
	  return getSingleDifference((float) n1.doubleValue() == (float) n2.doubleValue(), path);
	}
	return getSingleDifference(Objects.equals(obj1, obj2), path);
  }

  public static boolean sharedListEquals(SharedList<?> sl1, SharedList<?> sl2)
  {
	return listEquals(toList(sl1), toList(sl2));
  }

  public static List<String> getDifferences(CustomModel model1, CustomModel model2)
  {
	return getMapDifferences(model1.getRoot().toMap(), model2.getRoot().toMap(), "");
  }

  public static List<String> getDifferences(SharedMap m1, SharedMap m2)
  {
	return getMapDifferences(toMap(m1), toMap(m2), "");
  }

  private static List<String> getSingleDifference(boolean isEqual, String path)
  {
	return isEqual ? Collections.emptyList() : List.of(path);
  }

  public static Object getObjectAtPath(CustomModel model, String path)
  {
	String[] parts = path.split(PATH_SEPARATOR);
	return getObjectAtPath(model, parts);
  }

  public static Object getObjectAtPath(CustomModel model, String[] pathParts)
  {
	Object obj = model.getRoot();
	for ( int i = 0; i < pathParts.length && obj != null; i++ )
	{
	  obj = getObjectAtPath(obj, pathParts[i]);
	}
	return obj;
  }

  public static Object getObjectAtPath(Object obj, String pathPart)
  {
	if ( isMapLike(obj) )
	{
	  Map<String, ?> map = toMap(obj);
	  return map == null ? null : map.get(pathPart);
	}
	if ( isList(obj) )
	{
	  int listIndex;
	  try
	  {
		listIndex = Integer.parseInt(pathPart);
	  }
	  catch (NumberFormatException nfe)
	  {
		return null;
	  }
	  List<?> list = toList(obj);
	  return list == null || list.size() <= listIndex ? null : list.get(listIndex);
	}
	return null;
  }
}
