package Milestone4.Server;

import java.util.*;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

import Milestone4.Common.Payload;
import Milestone4.Common.PayloadType;
import Milestone4.Common.RoomResultPayload;

/**
 * A server-side representation of a single client
 */
public class ServerThread extends Thread {
    private Socket client;
    private String clientName;
    private boolean isRunning = false;
    private ObjectOutputStream out;// exposed here for send()
    // private Server server;// ref to our server so we can call methods on it
    // more easily
    private Room currentRoom;
    private static Logger logger = Logger.getLogger(ServerThread.class.getName());
    private long myId;
    private List<String> muteList = new ArrayList<String>();
    private String mutePersistList;
    private Map<String, Long> lastMuteTimestamps = new HashMap<>();
    private Map<String, Long> lastUnmuteTimestamps = new HashMap<>();


    //mute/unmute feature
    //UCID: FJ28 - 4/15/24
    public boolean sendMuteUser(String name){
        Payload p = new Payload();
        p.setPayloadType(PayloadType.MUTE);
        p.setClientName(name);
        return send(p);
    }
    public boolean sendUnmuteUser(String name){
        Payload p = new Payload();
        p.setPayloadType(PayloadType.UNMUTE);
        p.setClientName(name);
        return send(p);
    }
    public boolean isMuted(String name){
        for(String i: muteList){
            if(i.equals(name)){
                return true;
            }
        }
        return false;
    }
    //The methods involve creating or updating a text file that holds muted usernames, and then utilizing this file when the server starts up.
    //FJ28
    //4-30-2024
    public void updateMuteList() {
        try(PrintWriter writer = new PrintWriter(new FileWriter(mutePersistList))) {
            for(String mutedUser : muteList) {
                writer.println(mutedUser);
            }
            writer.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void loadMuteList() {
        try(BufferedReader reader = new BufferedReader(new FileReader(mutePersistList))) {
            String line;
            while ((line = reader.readLine()) != null) {
                muteList.add(line);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void setClientId(long id) {
        myId = id;
    }

    public long getClientId() {
        return myId;
    }

    public boolean isRunning() {
        return isRunning;
    }

    private void info(String message) {
        System.out.println(String.format("Thread[%s]: %s", getId(), message));
    }

    public ServerThread(Socket myClient, Room room) {
        info("Thread created");
        // get communication channels to single client
        this.client = myClient;
        this.currentRoom = room;

    }

    protected void setClientName(String name) {
        if (name == null || name.isBlank()) {
            System.err.println("Invalid client name being set");
            return;
        }
        clientName = name;
    }

    protected String getClientName() {
        return clientName;
    }

    protected synchronized Room getCurrentRoom() {
        return currentRoom;
    }

    protected synchronized void setCurrentRoom(Room room) {
        if (room != null) {
            currentRoom = room;
        } else {
            info("Passed in room was null, this shouldn't happen");
        }
    }

    public void disconnect() {
        sendConnectionStatus(myId, getClientName(), false);
        info("Thread being disconnected by server");
        isRunning = false;
        cleanup();
    }

    // send methods
    public boolean sendRoomName(String name) {
        Payload p = new Payload();
        p.setPayloadType(PayloadType.JOIN_ROOM);
        p.setMessage(name);
        return send(p);
    }

    public boolean sendRoomsList(String[] rooms, String message) {
        RoomResultPayload payload = new RoomResultPayload();
        payload.setRooms(rooms);
        //Fixed in Module7.Part9
        if(message != null){
            payload.setMessage(message);
        }
        return send(payload);
    }

    public boolean sendExistingClient(long clientId, String clientName) {
        Payload p = new Payload();
        p.setPayloadType(PayloadType.SYNC_CLIENT);
        p.setClientId(clientId);
        p.setClientName(clientName);
        return send(p);
    }

    public boolean sendResetUserList() {
        Payload p = new Payload();
        p.setPayloadType(PayloadType.RESET_USER_LIST);
        return send(p);
    }

    public boolean sendClientId(long id) {
        Payload p = new Payload();
        p.setPayloadType(PayloadType.CLIENT_ID);
        p.setClientId(id);
        return send(p);
    }

    public boolean sendMessage(long clientId, String message) {
        Payload p = new Payload();
        p.setPayloadType(PayloadType.MESSAGE);
        p.setClientId(clientId);
        p.setMessage(message);
        return send(p);
    }

    public boolean sendConnectionStatus(long clientId, String who, boolean isConnected) {
        Payload p = new Payload();
        p.setPayloadType(isConnected ? PayloadType.CONNECT : PayloadType.DISCONNECT);
        p.setClientId(clientId);
        p.setClientName(who);
        p.setMessage(isConnected ? "connected" : "disconnected");
        return send(p);
    }

    private boolean send(Payload payload) {
        // added a boolean so we can see if the send was successful
        try {
            // TODO add logger
            logger.log(Level.FINE, "Outgoing payload: " + payload);
            out.writeObject(payload);
            logger.log(Level.INFO, "Sent payload: " + payload);
            return true;
        } catch (IOException e) {
            info("Error sending message to client (most likely disconnected)");
            // comment this out to inspect the stack trace
            // e.printStackTrace();
            cleanup();
            return false;
        } catch (NullPointerException ne) {
            info("Message was attempted to be sent before outbound stream was opened: " + payload);
            return true;// true since it's likely pending being opened
        }
    }

    // end send methods
    @Override
    public void run() {
        info("Thread starting");
        try (ObjectOutputStream out = new ObjectOutputStream(client.getOutputStream());
                ObjectInputStream in = new ObjectInputStream(client.getInputStream());) {
            this.out = out;
            isRunning = true;
            Payload fromClient;
            while (isRunning && // flag to let us easily control the loop
                    (fromClient = (Payload) in.readObject()) != null // reads an object from inputStream (null would
                                                                     // likely mean a disconnect)
            ) {

                info("Received from client: " + fromClient);
                processPayload(fromClient);

            } // close while loop
        } catch (Exception e) {
            // happens when client disconnects
            e.printStackTrace();
            info("Client disconnected");
        } finally {
            isRunning = false;
            info("Exited thread loop. Cleaning up connection");
            cleanup();
        }
    }

        //FJ28
        //4-30-2024
    void processPayload(Payload p) {
        switch (p.getPayloadType()) {
            case CONNECT:
                setClientName(p.getClientName());
                mutePersistList = "C://Users//Public//Fatima//IT114//ChatRoom" + p.getClientName() + ".txt";
                loadMuteList();
                break;
            case DISCONNECT:
                Room.disconnectClient(this, getCurrentRoom());
                break;
            case MESSAGE:
                if (currentRoom != null) {
                    currentRoom.sendMessage(this, p.getMessage());
                } else {
                    // TODO migrate to lobby
                    logger.log(Level.INFO, "Migrating to lobby on message with null room");
                    Room.joinRoom("lobby", this);
                }
                break;
            case GET_ROOMS:
                Room.getRooms(p.getMessage().trim(), this);
                break;
            case CREATE_ROOM:
                Room.createRoom(p.getMessage().trim(), this);
                break;
            case JOIN_ROOM:
                Room.joinRoom(p.getMessage().trim(), this);
                break;
            //Additional cases for the mute/unmute function
            ////FJ28
            //4-30-2024
            case MUTE:
                if (!isRedundantMute(p.getClientName())) {
                    // Execute the mute action
                    muteList.add(p.getClientName());
                    updateMuteList();
                    sendMuteUser(p.getClientName());
                    Room mroom = getCurrentRoom();
                    if (mroom != null) {
                        ServerThread mutedUser = mroom.findMute(p.getClientName());
                        mutedUser.sendMessage(p.getClientId(), "<font color=\"red\">You have been muted by " + getClientName() + "</font>");
                    }
                    lastMuteTimestamps.put(p.getClientName(), System.currentTimeMillis());
                }
                break;
            case UNMUTE:
                if (!isRedundantUnmute(p.getClientName())) {
                    // Execute the unmute action
                    muteList.remove(p.getClientName());
                    updateMuteList();
                    sendUnmuteUser(p.getClientName());
                    Room mroom = getCurrentRoom();
                    if (mroom != null) {
                        ServerThread mutedUser = mroom.findMute(p.getClientName());
                        mutedUser.sendMessage(p.getClientId(), "<font color=\"red\" >You have been unmuted by " + getClientName() + "</font>");
                         ServerThread mutingClient = mroom.findClient(getClientName());
                         lastUnmuteTimestamps.put(p.getClientName(), System.currentTimeMillis());
                         if (!p.getClientName().equals(getClientName())) {
                 }
              }
                break;
                }
            default:
                break;
        }
    }

    private boolean isRedundantMute(String username) {
        if (lastMuteTimestamps.containsKey(username)) {
            long lastMuteTime = lastMuteTimestamps.get(username);
            long currentTime = System.currentTimeMillis();
            return (currentTime - lastMuteTime) < 5000; 
        }
        return false; 
    }

    private boolean isRedundantUnmute(String username) {
        if (lastUnmuteTimestamps.containsKey(username)) {
            long lastUnmuteTime = lastUnmuteTimestamps.get(username);
            long currentTime = System.currentTimeMillis();
            return (currentTime - lastUnmuteTime) < 5000; 
        }
        return false; 
    }


    private void cleanup() {
        info("Thread cleanup() start");
        try {
            client.close();
        } catch (IOException e) {
            info("Client already closed");
        }
        info("Thread cleanup() complete");
    }
}