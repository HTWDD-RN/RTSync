package de.dmos.rtsync.message;

import java.util.Arrays;
import java.util.Objects;

import se.l4.otter.engine.TaggedOperation;
import se.l4.otter.operations.Operation;
import se.l4.otter.operations.combined.CombinedHandler;

/**
 * A {@link TaggedOperation<Operation<CombinedHandler>>} that has a user field so that a user's version of data
 * modifications can be transmitted over the network.
 *
 * @author <a href="mailto:michael.danzig@dmos2002.de">Michael Danzig</a>
 * @version $Rev$
 *
 */
public class TaggedUserOperation extends TaggedOperation<Operation<CombinedHandler>> //implements HasIdAndPreviousIds
{
  private final String _user;
  private final String[] _mergedIds;

  public TaggedUserOperation(TaggedOperation<Operation<CombinedHandler>> op, String user)
  {
	this(op.getHistoryId(), op.getToken(), op.getOperation(), user, null);
  }

  public TaggedUserOperation(TaggedOperation<Operation<CombinedHandler>> op, String user, String[] mergedIds)
  {
	this(op.getHistoryId(), op.getToken(), op.getOperation(), user, mergedIds);
  }

  public static <T extends Operation<CombinedHandler>> TaggedUserOperation toTaggedUserOperation(
	TaggedOperation<T> op)
  {
	return op instanceof TaggedUserOperation userOp
		? userOp
			: new TaggedUserOperation(op.getHistoryId(), op.getToken(), op.getOperation(), null, null);
  }

  public TaggedUserOperation(long historyId, String token, Operation<CombinedHandler> operation, String user)
  {
	this(historyId, token, operation, user, null);
  }

  public TaggedUserOperation(
	long historyId,
	String token,
	Operation<CombinedHandler> operation,
	String user,
	String[] mergedIds)
  {
	super(historyId, token, operation);
	_user = user;
	_mergedIds = mergedIds;
  }

  public boolean includes(String token) {
	if (getToken().equals(token)) {
	  return true;
	}
	if (_mergedIds != null) {
	  for (int i = 0; i < _mergedIds.length; i++) {
		if ( token.equals(_mergedIds[i]) )
		{
		  return true;
		}
	  }
	}
	return false;
  }

  public String getUser()
  {
	return _user;
  }

  public String[] getMergedIds()
  {
	return _mergedIds;
  }

  @Override
  public boolean equals(Object obj)
  {
	if ( !super.equals(obj) )
	{
	  return false;
	}
	TaggedUserOperation op2 = (TaggedUserOperation) obj;
	return Objects.equals(_user, op2._user) && Arrays.equals(_mergedIds, op2._mergedIds);
  }

  @Override
  public int hashCode()
  {
	int hash = 43 * super.hashCode() + ((_user == null) ? 0 : _user.hashCode());
	return 43 * hash + ((_mergedIds == null) ? 0 : Arrays.hashCode(_mergedIds));
  }

  @Override
  public String toString()
  {
	String userString = _user != null ? _user : "null";
	return getClass().getSimpleName()
		+ "[historyId="
		+ getHistoryId()
		+ ", token="
		+ getToken()
		+ ", operation="
		+ getOperation()
		+ ", user="
		+ userString
		+ ", mergedIds="
		+ getMergedIds()
		+ "]";
  }

  // This should actually be better placed in TaggedOperation but since that is in another project we define it here.
  public boolean equalsHistoryIdAndToken(TaggedOperation<Operation<CombinedHandler>> other)
  {
	return getHistoryId() == other.getHistoryId() && getToken().equals(other.getToken());
  }

  // This should actually be better placed in TaggedOperation but since that is in another project we define it here.
  public static boolean equalsHistoryIdAndToken(TaggedOperation<?> op1, TaggedOperation<?> op2)
  {
	return op1.getHistoryId() == op2.getHistoryId() && op1.getToken().equals(op2.getToken());
  }
}
