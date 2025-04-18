package de.dmos.rtsync.listeners;

import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.dmos.rtsync.client.SubscriptionSupplier;
import de.dmos.rtsync.message.Subscriber;
import de.dmos.rtsync.swingui.Selection;

/**
 * This serves the purpose to keep a map of each user's id and color updated.
 *
 * @author <a href="mailto:michael.danzig@dmos2002.de">Michael Danzig</a>
 * @version $Rev$
 *
 */
public class UserIdAndColorListener implements SelfSubscriberListener, SubscriberListener
{
  private final Map<String, Color>		 _userColors = new HashMap<>();
  private final Map<Long, String>		 _userIds	 = new HashMap<>();
  private Subscriber					 _selfSubscriber;
  private SubscriptionSupplier	   _subscriptionSupplier;

  public UserIdAndColorListener(SelfSubscriptionSupplier selfSubscriptionSupplier)
  {
	this(selfSubscriptionSupplier, null);
  }

  public UserIdAndColorListener(
	SelfSubscriptionSupplier selfSubscriptionSupplier,
	SubscriptionSupplier subscriptionSupplier)
  {
	if ( selfSubscriptionSupplier != null )
	{
	  selfSubscriptionSupplier.addSelfSubscriberListener(this);
	  onOwnNameOrColorChanged(selfSubscriptionSupplier.getSelfSubscriber());
	}
	setSubscriptionSupplier(subscriptionSupplier);
  }

  public void setSubscriptionSupplier(SubscriptionSupplier subscriptionSupplier)
  {
	if ( _subscriptionSupplier != null )
	{
	  _subscriptionSupplier.removeWeakSubscriberListener(this);
	}
	_subscriptionSupplier = subscriptionSupplier;
	if ( subscriptionSupplier != null )
	{
	  subscriptionSupplier.addWeakSubscriberListener(this);
	  onSubscribersReceived(subscriptionSupplier.getSubscribers());
	}
  }

  public boolean isSelfSubscriber(String user)
  {
	return user == null || (_selfSubscriber != null && _selfSubscriber.getName().equals(user));
  }

  public Subscriber getSelfSubscriber()
  {
	return _selfSubscriber;
  }

  public Color getOrDefaultColor(String user, Color defaultColor)
  {
	return _userColors.getOrDefault(user, defaultColor);
  }

  public Color getColor(String user)
  {
	return _userColors.get(user);
  }

  public String getUser(Long userId)
  {
	return _userIds.get(userId);
  }

  @Override
  public void onSubscribersReceived(List<Subscriber> subscribers)
  {
	if ( subscribers != null )
	{
	  subscribers.stream().forEach(this::updateColorAndId);
	}
  }

  private void updateColorAndId(Subscriber subscriber)
  {
	_userColors.put(subscriber.getName(), subscriber.getColor());
	_userIds.put(subscriber.getId(), subscriber.getName());
  }

  @Override
  public void onOwnNameOrColorChanged(Subscriber subscriber)
  {
	_selfSubscriber = subscriber;
	if ( subscriber != null )
	{
	  updateColorAndId(subscriber);
	}
  }

  public boolean updateSelectionColor(Selection sel, Color defaultColor)
  {
	return sel.setColor(getOrDefaultColor(sel.getUser(), defaultColor));
  }
}
