package Project.Server;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

import Project.Common.Constants;

public class Room implements AutoCloseable {
    private static String flip;
    private static String roll;
    // protected static Server server;// used to refer to accessible server
    // functions
    private String name;
    private List<ServerThread> clients = new ArrayList<ServerThread>();

    private boolean isRunning = false;
    // Commands
    private final static String COMMAND_TRIGGER = "/";
    private static final String FLIP = flip;
    private static final String ROLL = roll;
    // private final static String CREATE_ROOM = "createroom";
    // private final static String JOIN_ROOM = "joinroom";
    // private final static String DISCONNECT = "disconnect";
    // private final static String LOGOUT = "logout";
    // private final static String LOGOFF = "logoff";
    private Logger logger = Logger.getLogger(Room.class.getName());

    public Room(String name) {
        this.name = name;
        isRunning = true;
    }

    private void info(String message) {
        logger.info(String.format("Room[%s]: %s", name, message));
    }

    public String getName() {
        return name;
    }

    protected synchronized void addClient(ServerThread client) {
        if (!isRunning) {
            return;
        }
        client.setCurrentRoom(this);
        client.sendJoinRoom(getName());// clear first
        if (clients.indexOf(client) > -1) {
            info("Attempting to add a client that already exists");
        } else {
            clients.add(client);
            // connect status second
            sendConnectionStatus(client, true);
            syncClientList(client);
        }


    }

    protected synchronized void removeClient(ServerThread client) {
        if (!isRunning) {
            return;
        }
        clients.remove(client);
        // we don't need to broadcast it to the server
        // only to our own Room
        if (clients.size() > 0) {
            // sendMessage(client, "left the room");
            sendConnectionStatus(client, false);
        }
        checkClients();
    }

    /***
     * Checks the number of clients.
     * If zero, begins the cleanup process to dispose of the room
     */
    private void checkClients() {
        // Cleanup if room is empty and not lobby
        if (!name.equalsIgnoreCase(Constants.LOBBY) && clients.size() == 0) {
            close();
        }
    }

    /***
     * Helper function to process messages to trigger different functionality.
     * 
     * @param message The original message being sent
     * @param client  The sender of the message (since they'll be the ones
     *                triggering the actions)
     */
    private boolean processCommands(String message, ServerThread client) {
        boolean wasCommand = false;
        try {
            if (message.startsWith(COMMAND_TRIGGER)) {
                String[] comm = message.split(COMMAND_TRIGGER);
                String part1 = comm[1];
                String[] comm2 = part1.split(" ");
                String command = comm2[0];
                // String roomName;
                wasCommand = true;
                switch (command) {
                    /*
                     * case CREATE_ROOM:
                     * roomName = comm2[1];
                     * Room.createRoom(roomName, client);
                     * break;
                     * case JOIN_ROOM:
                     * roomName = comm2[1];
                     * Room.joinRoom(roomName, client);
                     * break;
                     */
                    /*
                     * case DISCONNECT:
                     * case LOGOUT:
                     * case LOGOFF:
                     * Room.disconnectClient(client, this);
                     * break;
                     */
                    
                // ucid: fj28 
                // date: 4/1/24     
                    case "FLIP":
                    int coin = (int) (Math.random() * 2); 
                    if (coin == 0) {
                    sendMessage(null, String.format("<b>%s did a coin flip and landed on Heads</b>", client.getClientName()));
                     } else if (coin == 1) {
                    sendMessage(null, String.format("<b>%s did a coin flip and landed on Tails</b>", client.getClientName()));
                     }
                    break;

                    case "ROLL":
                    int total = 0;
                    try {
                    String[] rollParts = message.trim().split(" ");
                    if (rollParts.length >= 2 && rollParts[1].contains("d")) {
                    String[] rollParams = rollParts[1].split("d");
                    int numberOfDice = Integer.parseInt(rollParams[0]);
                    int sidesOfDice = Integer.parseInt(rollParams[1]);
                    for (int i = 0; i < numberOfDice; i++) {
                    int rollDice = (int) (Math.random() * sidesOfDice) + 1;
                    total += rollDice;
            }
                    sendMessage(null, String.format("<b>%s did a roll of <u>%sd%s</u> and got a total roll of >%s<</b>", client.getClientName(), numberOfDice, sidesOfDice, total));
                    } else if (rollParts.length >= 2) {
                    int value = Integer.parseInt(rollParts[1]);
                    int singleDiceRoll = (int) (Math.random() * value) + 1;
                    sendMessage(null, String.format("<b>%s did a >roll< with a range of <u>1-%s</u> and the result is \">%s</b>", client.getClientName(), value, singleDiceRoll));
                    } else {
                    client.sendMessage(-1, "<b><font color=\"red\">invalid input</font></b>");
                    }
                    } catch (NumberFormatException e) {
                    client.sendMessage(-1, "<b><font color=\"red\">invalid input</font></b>");
                    }
                    break;


                     
                    default:
                        wasCommand = false;
                        break;
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return wasCommand;
    }

    // Command helper methods
    private synchronized void syncClientList(ServerThread joiner) {
        Iterator<ServerThread> iter = clients.iterator();
        while (iter.hasNext()) {
            ServerThread st = iter.next();
            if (st.getClientId() != joiner.getClientId()) {
                joiner.sendClientMapping(st.getClientId(), st.getClientName());
            }
        }
    }
    protected static void createRoom(String roomName, ServerThread client) {
        if (Server.INSTANCE.createNewRoom(roomName)) {
            // server.joinRoom(roomName, client);
            Room.joinRoom(roomName, client);
        } else {
            client.sendMessage(Constants.DEFAULT_CLIENT_ID, String.format("Room %s already exists", roomName));
        }
    }

    protected static void joinRoom(String roomName, ServerThread client) {
        if (!Server.INSTANCE.joinRoom(roomName, client)) {
            client.sendMessage(Constants.DEFAULT_CLIENT_ID, String.format("Room %s doesn't exist", roomName));
        }
    }

    protected static List<String> listRooms(String searchString, int limit) {
        return Server.INSTANCE.listRooms(searchString, limit);
    }

    protected static void disconnectClient(ServerThread client, Room room) {
        client.setCurrentRoom(null);
        client.disconnect();
        room.removeClient(client);
    }
    // end command helper methods

    /***
     * Takes a sender and a message and broadcasts the message to all clients in
     * this room. Client is mostly passed for command purposes but we can also use
     * it to extract other client info.
     * 
     * @param sender  The client sending the message
     * @param message The message to broadcast inside the room
     */
    protected synchronized void sendMessage(ServerThread sender, String message) {
        if (!isRunning) {
            return;
        }

        //FJ28 4.1.24
        //Altered message output
        String startTag = "";
        String endTag = "";
        int startIndex = -1;
        int endIndex = -1;
        boolean processed = true;
        //FJ28 4.1.24
        while(processed){
            processed = false;
            startIndex = message.indexOf("*b");
            endIndex = message.indexOf("b*");
            //bold
            if(startIndex > -1 && endIndex > -1 && endIndex > startIndex+2){
                processed = true;
                startTag = "<b>";
                endTag = "</b>";
                message = message.substring(0, startIndex) + startTag 
                + message.substring(startIndex+2, endIndex) + endTag 
                + message.substring(endIndex+2); 
            }
            //italic
            startIndex = message.indexOf("*i");
            endIndex = message.indexOf("i*");
            if(startIndex > -1 && endIndex > -1 && endIndex > startIndex+2){
                processed = true;
                startTag = "<i>";
                endTag = "</i>";
                message = message.substring(0, startIndex) + startTag 
                + message.substring(startIndex+2, endIndex) + endTag 
                + message.substring(endIndex+2);
            }
            //underline
            startIndex = message.indexOf("*u");
            endIndex = message.indexOf("u*");
            if(startIndex > -1 && endIndex > -1 && endIndex > startIndex+2){
                processed = true;
                startTag = "<u>";
                endTag = "</u>";
                message = message.substring(0, startIndex) + startTag 
                + message.substring(startIndex+2, endIndex) + endTag 
                + message.substring(endIndex+2);
            }
            //red
            startIndex = message.indexOf("#r");
            endIndex = message.indexOf("r#");
            if(startIndex > -1 && endIndex > -1 && endIndex > startIndex+2){
                processed = true;
                startTag = "<font color=\"red\">";
                endTag = "</font>";
                message = message.substring(0, startIndex) + startTag 
                + message.substring(startIndex+2, endIndex) + endTag 
                + message.substring(endIndex+2);
            }
            //green
            startIndex = message.indexOf("#g");
            endIndex = message.indexOf("g#");
            if(startIndex > -1 && endIndex > -1 && endIndex > startIndex+2){
                processed = true;
                startTag = "<font color=\"green\">";
                endTag = "</font>";
                message = message.substring(0, startIndex) + startTag 
                + message.substring(startIndex+2, endIndex) + endTag 
                + message.substring(endIndex+2);
            }
            //blue
            startIndex = message.indexOf("#b");
            endIndex = message.indexOf("b#");
            if(startIndex > -1 && endIndex > -1 && endIndex > startIndex+2){
                processed = true;
                startTag = "<font color=\"blue\">";
                endTag = "</font>";
                message = message.substring(0, startIndex) + startTag 
                + message.substring(startIndex+2, endIndex) + endTag 
                + message.substring(endIndex+2);
            }
            
            //Whisper/Private message
            startIndex = message.indexOf("@");
            if (startIndex > -1) {
                endIndex = message.indexOf(" ", startIndex);
                if (endIndex == -1) {
                    endIndex = message.length();
                }
                String clientName = message.substring(startIndex + 1, endIndex);
                for (ServerThread client : clients) {
                    if (client != sender && client.getClientName().equals(clientName)) {
                        client.sendMessage(sender.getClientId(), message);
                        sender.sendMessage(sender.getClientId(), message);
                        return;
                    }
                }
            }


        } 
        System.out.println(message);
    
        //End

        info("Sending message to " + clients.size() + " clients");
        if (sender != null && processCommands(message, sender)) {
            // it was a command, don't broadcast
            return;
        }

        /// String from = (sender == null ? "Room" : sender.getClientName());
        long from = (sender == null) ? Constants.DEFAULT_CLIENT_ID : sender.getClientId();
        Iterator<ServerThread> iter = clients.iterator();
        while (iter.hasNext()) {
            ServerThread client = iter.next();
            boolean messageSent = client.sendMessage(from, message);
            if (!messageSent) {
                handleDisconnect(iter, client);
            }
        }
    }
	

    protected synchronized void sendConnectionStatus(ServerThread sender, boolean isConnected) {
        Iterator<ServerThread> iter = clients.iterator();
        while (iter.hasNext()) {
            ServerThread client = iter.next();
            boolean messageSent = client.sendConnectionStatus(sender.getClientId(), sender.getClientName(),
                    isConnected);
            if (!messageSent) {
                handleDisconnect(iter, client);
            }
        }
    }

    private void handleDisconnect(Iterator<ServerThread> iter, ServerThread client) {
        iter.remove();
        info("Removed client " + client.getClientName());
        checkClients();
        sendMessage(null, client.getClientName() + " disconnected");
    }

    public void close() {
        Server.INSTANCE.removeRoom(this);
        // server = null;
        isRunning = false;
        clients = null;
    }
}
