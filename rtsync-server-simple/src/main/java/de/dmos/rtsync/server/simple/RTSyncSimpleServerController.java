package de.dmos.rtsync.server.simple;

import java.security.Principal;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Controller;

import de.dmos.rtsync.message.CursorPosition;
import de.dmos.rtsync.message.RTState;
import de.dmos.rtsync.message.SimpleStateMessage;
import de.dmos.rtsync.message.TaggedUserOperation;
import de.dmos.rtsync.message.UserCursors;
import de.dmos.rtsync.network.EndpointPaths;
import de.dmos.rtsync.network.RTSyncSimpleNetworkNode;
import de.dmos.rtsync.server.AbstractRTSyncServerController;
import de.dmos.rtsync.server.ServerNetworkHandler;
import se.l4.otter.engine.TaggedOperation;
import se.l4.otter.operations.Operation;
import se.l4.otter.operations.combined.CombinedHandler;

@Controller
public class RTSyncSimpleServerController extends AbstractRTSyncServerController
implements
ServerNetworkHandler
{
  private static final Logger                 LOG           = LoggerFactory.getLogger(RTSyncSimpleServerController.class);

  private final RTSimpleServerOperationSync	_serverSync;
  private final RTSyncSimpleNetworkNode	_serverNode;

  @Autowired
  public RTSyncSimpleServerController(SimpUserRegistry simpUserRegistry, SimpMessagingTemplate simpMessagingTemplate)
  {
	super(simpUserRegistry, simpMessagingTemplate);
	_serverNode = new RTSyncSimpleServerNode(control -> new RTSimpleServerOperationSync(control, this));
	_serverSync = (RTSimpleServerOperationSync) _serverNode.getSync();
  }

  @MessageMapping(EndpointPaths.PATH_UPDATE_OWN_CURSORS)
  @SendTo(EndpointPaths.TOPIC_CURSORS)
  public UserCursors setCursor(@Payload CursorPosition cursorPosition, Principal principal)
  {
	// TODO: Store this information somewhere and attach it to StateMessages.
	return new UserCursors(getSubscriber(principal).getId(), List.of(cursorPosition));
	// It would be more professionell to broadcast it only to others like this:
	//	broadcastToOthers(EndpointPaths.TOPIC_CURSORS, toBroadcast, principal);
  }

  @MessageMapping(EndpointPaths.PATH_SEND_OPERATION)
  public void sendOperation(@Payload TaggedOperation<Operation<CombinedHandler>> taggedOp, Principal principal)
  {
	long currentHistoryId = _serverSync.getLatestVersion();
	if ( _rejectOldHistoryIds && taggedOp.getHistoryId() < currentHistoryId )
	{
	  LOG
	  .info(
		"{} called sendOperation({}) with an older historyId than {}",
		principal.getName(),
		taggedOp,
		currentHistoryId);
	  String oldIdMessage = getOldHistoryIdMessage(taggedOp, currentHistoryId);
	  _simpMessagingTemplate
	  .convertAndSendToUser(principal.getName(), EndpointPaths.QUEUE_EXCEPTION, oldIdMessage);
	  return;
	}
	// Maybe we should send the user's id instead of the their principal's name.
	TaggedUserOperation userOp = new TaggedUserOperation(taggedOp, principal.getName());
	LOG.debug("sendOperation({})", userOp);
	_serverSync.send(userOp);
  }

  @SubscribeMapping(EndpointPaths.PATH_GET_LATEST_OPERATION)
  public TaggedUserOperation getLatestOperation()
  {
	LOG.debug("getLatestOperation called");
	return _serverSync.getLatestUserOperation();
  }

  @SubscribeMapping(EndpointPaths.PATH_INIT_CLIENT)
  public SimpleStateMessage initClient(Principal principal)
  {
	LOG.debug("{} called initClient", principal.getName());
	broadCastAllSubscribers();

	RTState state = new RTState(null, _serverSync.getLatestUserOperation());
	return new SimpleStateMessage(getSubscriber(principal), state);
  }

  @Override
  public void brodcastTaggedOperation(TaggedUserOperation taggedOperation)
  {
	LOG.debug("broadcastTaggedOperation({})", taggedOperation);
	_simpMessagingTemplate.convertAndSend(EndpointPaths.TOPIC_OPERATIONS, taggedOperation);
  }
}

