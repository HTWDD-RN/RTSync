package de.dmos.rtsync.server.project;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.websocket.servlet.WebSocketMessagingAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

@SpringBootApplication
@ComponentScan(excludeFilters = {
  @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = WebSocketMessagingAutoConfiguration.class)})
public class RTSyncProjectServer
{
  public static void main(String[] args)
  {
	SpringApplication.run(RTSyncProjectServer.class, args);
  }
}