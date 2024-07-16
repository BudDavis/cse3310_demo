
// This is example code provided to CSE3310 Fall 2022
// You are free to use as is, or changed, any of the code provided

// Please comply with the licensing requirements for the
// open source packages being used.

// This code is based upon, and derived from the this repository
//            https:/thub.com/TooTallNate/Java-WebSocket/tree/master/src/main/example

// http server include is a GPL licensed package from
//            http://www.freeutils.net/source/jlhttp/

/*
 * Copyright (c) 2010-2020 Nathan Rajlich
 *
 *  Permission is hereby granted, free of charge, to any person
 *  obtaining a copy of this software and associated documentation
 *  files (the "Software"), to deal in the Software without
 *  restriction, including without limitation the rights to use,
 *  copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the
 *  Software is furnished to do so, subject to the following
 *  conditions:
 *
 *  The above copyright notice and this permission notice shall be
 *  included in all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 *  EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 *  OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 *  NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 *  HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 *  WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 *  FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 *  OTHER DEALINGS IN THE SOFTWARE.
 */

package uta.cse3310;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Collections;

import org.java_websocket.WebSocket;
import org.java_websocket.drafts.Draft;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;
import java.time.Instant;
import java.time.Duration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class App extends WebSocketServer {

  public class GlobalState {
    String msgName = "GlobalState";
    Integer numConnectionsAlive;
    Integer numConnectionsTotal;
    Integer globalElapsedTime;

    GlobalState() {
      numConnectionsAlive = 0;
      numConnectionsTotal = 0;
      globalElapsedTime = 0;
    }

    void update() {
      // This function is expected to be called once a second
      globalElapsedTime++;
    }
  }

  GlobalState GS = new GlobalState();
  java.util.Timer timer = null;

  public class ConnectionState {
    String msgName = "ConnectionState";
    Integer buttonsProcessed;
  }

  public class ClientInput {
    String msgName = "ClientInput";
    Boolean ButtonPressed;

  }

  public class ConnectionID {
    String msgName = "ConnectionID";
    Integer connID;
    Integer connectionElapsedTime;

    ConnectionID(Integer ID) {
      connID = ID;
      connectionElapsedTime = 0;
    }

    void update() {
      connectionElapsedTime++;
    }
  }

  Integer conID;
  Vector<WebSocket> connectionList = new Vector<WebSocket>();

  public App(int port) {
    super(new InetSocketAddress(port));
  }

  public App(InetSocketAddress address) {
    super(address);
  }

  public App(int port, Draft_6455 draft) {
    super(new InetSocketAddress(port), Collections.<Draft>singletonList(draft));
  }

  @Override
  public void onOpen(WebSocket conn, ClientHandshake handshake) {

    System.out.println(conn.getRemoteSocketAddress().getAddress().getHostAddress() + " connected");

    ConnectionID ID = new ConnectionID(conID);
    conID++;

    conn.setAttachment(ID);

    connectionList.add(conn);

    // Tell the client it's client ID
    Gson gson = new Gson();
    String jsonString = gson.toJson(ID);
    conn.send(jsonString);

    // Update global counters
    GS.numConnectionsAlive++;
    GS.numConnectionsTotal++;

    // Send this out to all clients
    jsonString = gson.toJson(GS);
    broadcast(jsonString);

    // if this is the first client, start the one second timer
    if (GS.numConnectionsAlive == 1) {
      timer = new java.util.Timer();
      timer.scheduleAtFixedRate(new upDate(), 0, 1000);
      System.out.println("starting timer.");
    }

  }

  public class upDate extends TimerTask {

    public void run() {
      System.out.println("in run");
      GS.update();
      // Send this out to all clients
      Gson gson = new Gson();
      String jsonString = gson.toJson(GS);
      broadcast(jsonString);

      // And now for each client
      for (WebSocket C : connectionList) {
        ConnectionID CID = C.getAttachment();
        CID.update();
        jsonString = gson.toJson(CID);
        C.send(jsonString);
      }

    }
  }

  @Override
  public void onClose(WebSocket conn, int code, String reason, boolean remote) {
    System.out.println(conn + " has closed");

    ConnectionID C = conn.getAttachment();
    connectionList.removeElement(conn);

    C = null;

    // if this is the last client, stop the one second timer
    if (GS.numConnectionsAlive == 0) {
      timer.cancel();
      System.out.println("stopping timer.");
    }

  }

  @Override
  public void onMessage(WebSocket conn, String message) {
    System.out.println(conn + ": " + message);

    // Where did this message come from?
    // Ask for the class that was given to the websockets lib when the connection
    // opened.
    ConnectionID C = conn.getAttachment();
    System.out.println("the connection id is " + C.connID);

    // This received message is a json formatted string.
    // All of the defined messages have a field called 'msgName'
    if (message.contains("msgName")) {
      if (message.contains("ClientInput")) {
        GsonBuilder builder = new GsonBuilder();
        Gson gson = builder.create();
        ClientInput CI = gson.fromJson(message, ClientInput.class);

      }

    } else {
      System.out.println("Not a valid message.");
    }

  }

  @Override
  public void onMessage(WebSocket conn, ByteBuffer message) {
    System.out.println(conn + ": " + message);

  }

  @Override
  public void onError(WebSocket conn, Exception ex) {
    ex.printStackTrace();
    if (conn != null) {
      // some errors like port binding failed may not be assignable to a specific
      // websocket
    }
  }

  @Override
  public void onStart() {
    setConnectionLostTimeout(0);
    conID = 1;

  }

  public static void main(String[] args) {

    String HttpPort = System.getenv("HTTP_PORT");
    int port = 9080;
    if (HttpPort != null) {
      port = Integer.valueOf(HttpPort);
    }

    // Set up the http server

    HttpServer H = new HttpServer(port, "html/");
    H.start();
    System.out.println("http Server started on port: " + port);

    // create and start the websocket server

    port = 9180;
    String WSPort = System.getenv("WEBSOCKET_PORT");
    if (WSPort != null) {
      port = Integer.valueOf(WSPort);
    }

    App A = new App(port);
    A.setReuseAddr(true);
    A.start();
    System.out.println("websocket Server started on port: " + port);

  }
}
