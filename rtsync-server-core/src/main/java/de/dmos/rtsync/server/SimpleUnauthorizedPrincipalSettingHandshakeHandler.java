package de.dmos.rtsync.server;

import java.security.Principal;
import java.util.Map;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

public class SimpleUnauthorizedPrincipalSettingHandshakeHandler extends DefaultHandshakeHandler
{
  @Override
  protected Principal determineUser(
    ServerHttpRequest request,
    WebSocketHandler wsHandler,
    Map<String, Object> attributes)
  {
    Principal superPrincipal = super.determineUser(request, wsHandler, attributes);
    return superPrincipal != null ? superPrincipal : new SimpleUnauthorizedPrincipal(request.getHeaders());
  }
}
