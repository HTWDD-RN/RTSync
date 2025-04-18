package de.dmos.rtsync.network;

public class EndpointPaths
{
  private EndpointPaths()
  {
  }

  public static final String DELIMITER					 = "/";

  // Top level paths
  public static final String WEB_SOCKET					 = DELIMITER + "socket";
  public static final String TOPIC						 = DELIMITER + "topic";
  public static final String APP						 = DELIMITER + "app";
  public static final String QUEUE						 = DELIMITER + "queue";
  public static final String USER						 = DELIMITER + "user";

  // Subscribable topics
  public static final String OPERATIONS					 = "operations";
  public static final String SUBSCRIBERS				 = "subscribers";
  public static final String CURSORS					 = "cursors";
  public static final String PROJECTS					 = "projects";
  public static final String ALL_TOPICS					 = "*";

  public static final String PATH_OPERATIONS			 = DELIMITER + OPERATIONS;
  public static final String PATH_SUBSCRIBERS			 = DELIMITER + SUBSCRIBERS;
  public static final String PATH_CURSORS				 = DELIMITER + CURSORS;
  public static final String PATH_PROJECTS				 = DELIMITER + PROJECTS;
  public static final String PATH_ALL_TOPICS			 = DELIMITER + ALL_TOPICS;

  // User specific response topics
  public static final String LATEST_OPERATION			 = "latestOperation";
  public static final String EXCEPTION					 = "error";

  public static final String PATH_LATEST_OPERATION		 = DELIMITER + LATEST_OPERATION;
  public static final String PATH_EXCEPTION				 = DELIMITER + EXCEPTION;

  // Client update paths. Note that the prefix "/topic" is automatically removed from the destinations in the controller's mappings.
  public static final String TOPIC_OPERATIONS            = TOPIC + PATH_OPERATIONS;
  public static final String TOPIC_SUBSCRIBERS           = TOPIC + PATH_SUBSCRIBERS;
  public static final String TOPIC_CURSORS				 = TOPIC + PATH_CURSORS;
  public static final String TOPIC_PROJECTS				 = TOPIC + PATH_PROJECTS;

  // The server's client specific messages
  public static final String QUEUE_LATEST_OPERATION		 = QUEUE + PATH_LATEST_OPERATION;
  public static final String QUEUE_EXCEPTION			 = QUEUE + PATH_EXCEPTION;
  public static final String QUEUE_OPERATIONS            = QUEUE + PATH_OPERATIONS;

  // The client subscriptions for the server's client specific messages.
  public static final String USER_QUEUE_LATEST_OPERATION = USER + QUEUE_LATEST_OPERATION;
  public static final String USER_QUEUE_EXCEPTION        = USER + QUEUE_EXCEPTION;
  public static final String USER_QUEUE_OPERATIONS       = USER + QUEUE_OPERATIONS;

  // App functions registered by the server.
  public static final String SEND_OPERATION				 = "sendOperation";
  public static final String SET_OWN_NAME				 = "setName";
  public static final String SET_OWN_COLOR				 = "setColor";
  public static final String GET_SUBSCRIBERS			 = "getSubscribers";
  public static final String UPDATE_OWN_CURSORS			 = "setOwnCursors";

  public static final String PATH_SEND_OPERATION		 = DELIMITER + SEND_OPERATION;
  public static final String PATH_SET_OWN_NAME			 = DELIMITER + SET_OWN_NAME;
  public static final String PATH_SET_OWN_COLOR			 = DELIMITER + SET_OWN_COLOR;
  public static final String PATH_GET_SUBSCRIBERS		 = DELIMITER + GET_SUBSCRIBERS;
  public static final String PATH_UPDATE_OWN_CURSORS	 = DELIMITER + UPDATE_OWN_CURSORS;

  // App functions registered by the server which are intended to be subscribed to for a one time response.
  public static final String GET_LATEST_OPERATION		 = "getLatestOperation";
  public static final String INIT_CLIENT				 = "initClient";
  // projects can also be queried but they are listed as topic.

  public static final String PATH_GET_LATEST_OPERATION	 = DELIMITER + GET_LATEST_OPERATION;
  public static final String PATH_INIT_CLIENT			 = DELIMITER + INIT_CLIENT;

  // Endpoints for app functions to be used by the clients. The server can find the corresponding app functions without the prefix "/app".
  public static final String APP_SEND_OPERATION          = APP + PATH_SEND_OPERATION;
  public static final String APP_SET_OWN_NAME            = APP + PATH_SET_OWN_NAME;
  public static final String APP_SET_OWN_COLOR           = APP + PATH_SET_OWN_COLOR;
  public static final String APP_INIT_CLIENT			 = APP + PATH_INIT_CLIENT;

  // Intended for subscribe messages to get a one time response
  public static final String APP_GET_LATEST_OPERATION    = APP + PATH_GET_LATEST_OPERATION;
  public static final String APP_UPDATE_OWN_CURSORS		 = APP + PATH_UPDATE_OWN_CURSORS;
  public static final String APP_QUERY_PROJECTS			 = APP + PATH_PROJECTS;
}
