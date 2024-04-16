package Project.Client;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.logging.Level;
import java.util.logging.Logger;

import Project.Common.Payload;
import Project.Common.PayloadType;
import Project.Common.RoomResultPayload;


public enum Client {
    INSTANCE;

    Socket server = null;
    ObjectOutputStream out = null;
    ObjectInputStream in = null;
    boolean isRunning = false;
    private Thread fromServerThread;
    private String clientName = "";
    private static Logger logger = Logger.getLogger(Client.class.getName());
    private static IClientEvents events;
    //added these variables for the mute/unmute function
    private final static String COMMAND = "`";
    private final static String MUTE = "mute";
    private final static String UNMUTE = "unmute";
    //code for mute/unmute feature
    //FJ28
    //4/15/24
    public void muteUser(String name) throws IOException{
        Payload p = new Payload();
        p.setPayloadType(PayloadType.MUTE);
        p.setClientName(name);
        out.writeObject(p);
    }
    public void unmuteUser(String name) throws IOException{
        Payload p = new Payload();
        p.setPayloadType(PayloadType.UNMUTE);
        p.setClientName(name);
        out.writeObject(p);
    }
    private boolean processMute(String command){
        boolean isCommand = false;
        if(command.startsWith(COMMAND)){
            try{
                isCommand = true;
                String check = command.substring(1).trim().split(" ")[0];
                String name = command.substring(1).trim().split(" ")[1];
                switch(check){
                    case MUTE:
                        this.muteUser(name);
                        break;
                    case UNMUTE:
                        this.unmuteUser(name);
                        break;
                    default:
                        isCommand = false;
                        break;
                }
            } catch(Exception e){
                System.out.print("Invalid format");
            }
        }
        return isCommand;
    }

    public boolean isConnected() {
        if (server == null) {
            return false;
        }
        // https://stackoverflow.com/a/10241044
        // Note: these check the client's end of the socket connect; therefore they
        // don't really help determine
        // if the server had a problem
        return server.isConnected() && !server.isClosed() && !server.isInputShutdown() && !server.isOutputShutdown();

    }

    /**
     * Takes an ip address and a port to attempt a socket connection to a server.
     * 
     * @param address
     * @param port
     * @return true if connection was successful
     */
    public boolean connect(String address, int port, String username, IClientEvents callback) {
        // TODO validate
        this.clientName = username;
        Client.events = callback;
        try {
            server = new Socket(address, port);
            // channel to send to server
            out = new ObjectOutputStream(server.getOutputStream());
            // channel to listen to server
            in = new ObjectInputStream(server.getInputStream());
            logger.log(Level.INFO, "Client connected");
            listenForServerMessage();
            sendConnect();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return isConnected();
    }

      // Send methods

    public void sendCreateRoom(String room) throws IOException, NullPointerException {
        Payload p = new Payload();
        p.setPayloadType(PayloadType.CREATE_ROOM);
        p.setMessage(room);
        out.writeObject(p);
    }

    public void sendJoinRoom(String room) throws IOException, NullPointerException {
        Payload p = new Payload();
        p.setPayloadType(PayloadType.JOIN_ROOM);
        p.setMessage(room);
        out.writeObject(p);
    }

    public void sendGetRooms(String query) throws IOException, NullPointerException {
        Payload p = new Payload();
        p.setPayloadType(PayloadType.GET_ROOMS);
        p.setMessage(query);
        out.writeObject(p);
    }

    private void sendConnect() throws IOException, NullPointerException {
        Payload p = new Payload();
        p.setPayloadType(PayloadType.CONNECT);
        p.setClientName(clientName);
        out.writeObject(p);
    }
    public void sendDisconnect() throws IOException, NullPointerException {
        Payload p = new Payload();
        p.setPayloadType(PayloadType.DISCONNECT);
        out.writeObject(p);
    }

    public void sendMessage(String message) throws IOException, NullPointerException {
        if(!processMute(message)){
        Payload p = new Payload();
        p.setPayloadType(PayloadType.MESSAGE);
        p.setMessage(message);
        p.setClientName(clientName);
        out.writeObject(p);
        }
    }

    


    // end send methods

    private void listenForServerMessage() {
        fromServerThread = new Thread() {
            @Override
            public void run() {
                try {
                    Payload fromServer;
                    isRunning = true;
                    // while we're connected, listen for objects from server
                    while (isRunning && !server.isClosed() && !server.isInputShutdown()
                            && (fromServer = (Payload) in.readObject()) != null) {

                        logger.info("Debug Info: " + fromServer);
                        processPayload(fromServer);

                    }
                    logger.info("listenForServerPayload() loop exited");
                } catch (Exception e) {
                    logger.severe("Exception in payload");
                    e.printStackTrace();
                } finally {
                    logger.info("Stopped listening to server input");
                    close();
                }
            }
        };
        fromServerThread.start();// start the thread
    }

    private void processPayload(Payload p) {
        logger.log(Level.FINE, "Received Payload: " + p);
        if (events == null) {
            logger.log(Level.FINER, "Events not initialize/set" + p);
            return;
        }
        switch (p.getPayloadType()) {
            case CONNECT:
                events.onClientConnect(p.getClientId(), p.getClientName(), p.getMessage());
                break;
            case DISCONNECT:
                events.onClientDisconnect(p.getClientId(), p.getClientName(), p.getMessage());
                break;
            case MESSAGE:
                events.onMessageReceive(p.getClientId(), p.getMessage());
                events.recentUser(p.getClientId());
                break;
            case CLIENT_ID:
                events.onReceiveClientId(p.getClientId());
                break;
            case RESET_USER_LIST:
                events.onResetUserList();
                break;
            case SYNC_CLIENT:
                events.onSyncClient(p.getClientId(), p.getClientName());
                break;
            case GET_ROOMS:
                events.onReceiveRoomList(((RoomResultPayload) p).getRooms(), p.getMessage());
                break;
            case JOIN_ROOM:
                events.onRoomJoin(p.getMessage());
                break;
            case MUTE:
                events.onMessageReceive(p.getClientId(), "<font color =\"red\">" + p.getClientName() + " has been muted</font>");
                break;
            case UNMUTE:
                events.onMessageReceive(p.getClientId(), "<font color =\"red\">" + p.getClientName() + " has been unmuted</font>");
                break;
            default:
                logger.log(Level.WARNING, "Unhandled payload type");
                break;

        }
    }

    private void close() {
        try {
            fromServerThread.interrupt();
        } catch (Exception e) {
            System.out.println("Error interrupting listener");
            e.printStackTrace();
        }
        try {
            System.out.println("Closing output stream");
            out.close();
        } catch (NullPointerException ne) {
            System.out.println("Server was never opened so this exception is ok");
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            System.out.println("Closing input stream");
            in.close();
        } catch (NullPointerException ne) {
            System.out.println("Server was never opened so this exception is ok");
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            System.out.println("Closing connection");
            server.close();
            System.out.println("Closed socket");
        } catch (IOException e) {
            e.printStackTrace();
        } catch (NullPointerException ne) {
            System.out.println("Server was never opened so this exception is ok");
        }
    }
}