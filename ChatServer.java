

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class ChatServer {
    private static long nextId;
    private List<ClientThread> clientthreads;
    private SimpleDateFormat df;
    private int port;
    private boolean online;
    private String notificationHighlight = " *** ";

    private String[] pinguFacts = {"Penguins do not only live in the Antarctic. Penguins can be found also in South America, Australia, New Zealand, South Africa and on the Galapagos Islands. Altogether, 18 species have been identified.", 
            "The name Penguin is derived from \"pen gewyn\". This means white head. But also the latin word \"penguis\" which means fat, plays a role for the naming.",
            "The smallest penguin species is the dwarf penguin, whose height rarely exceeds 30cm. The emporer penguin, the largest species, on the other hand, may even reach one meter in height."};
    Random r = new Random();

    public ChatServer(int port) {
        this.port = port;
        df = new SimpleDateFormat("HH:mm:ss");
        clientthreads = Collections.synchronizedList(new ArrayList<>());
    }

    public void start() {
        online = true;

        try {
            ServerSocket serverSocket = new ServerSocket(port);

            while(online) {
                display("Server is waiting on port " + port + ".");
                Socket socket = serverSocket.accept();

                if (!online) {
                    break;
                }
                ClientThread ct = new ClientThread(socket);
                clientthreads.add(ct);

                ct.start();
            }

            // stopping the server
            serverSocket.close();
            for (int i = clientthreads.size() - 1; i >= 0; i--) {
                ClientThread ct = clientthreads.get(i);
                try {
                    ct.oOutput.close();
                    ct.oInput.close();
                    ct.socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void display(String s) {
        System.out.println(LocalTime.now().toString() + ": " + s);
    }

    private synchronized boolean broadcast(String msg) {
        boolean isDM = false;
        String[] spliited = msg.split(" ");
        // check whether someone slid into the DMs
        if (spliited[1].charAt(0) == '@') {
            isDM = true;
        }

        boolean targetFound = false;
        if (isDM) {
            String target = spliited[1].substring(1);

            for (int i = clientthreads.size() - 1; i >= 0 ; i--) {
                ClientThread ct = clientthreads.get(i);
                String userName = ct.getUserName();
                if (userName.equalsIgnoreCase(target)) {
                    if (!ct.writeMsg(LocalTime.now().toString() + msg)) {
                        clientthreads.remove(i);
                        display("Client " + userName + " seems to have disconnected, removed from the list of clients.");
                    }
                    targetFound = true;
                    break;
                }
            }

            if (!targetFound) {
                return false;
            }
        } else {
            msg = LocalTime.now().toString() + " " + msg + "\n";
            System.out.print(msg);

            for (int i = clientthreads.size() - 1; i >= 0; i--) {
                ClientThread ct = clientthreads.get(i);
                if (!ct.writeMsg(msg)) {
                    clientthreads.remove(i);
                    display("Client " + ct.userName + " seems to have disconnected, removed from the list of clients.");
                }
            }
        }
        return true;
    }

    synchronized public void remove(long id) {
        String disconnectedClient = "";
        for(int i = 0; i < clientthreads.size(); ++i) {
            ClientThread ct = clientthreads.get(i);
            if(ct.id == id) {
                disconnectedClient = ct.getUserName();
                clientthreads.remove(i);
                break;
            }
        }
        broadcast(notificationHighlight + disconnectedClient + " has left the chat room." + notificationHighlight);
    }

    public static void main(String[] args) {
        int portNr = 3000;
        switch (args.length) {
            case 1:
                try {
                    portNr = Integer.parseInt(args[0]);
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                }
            case 0:
                break;
            default:
                System.out.println("Usage is: > java ChatServer [portNumber]");
        }
        ChatServer cs = new ChatServer(portNr);
        cs.start();
    }

    class ClientThread extends Thread {
        Socket socket;
        ObjectOutputStream oOutput;
        ObjectInputStream oInput;
        long id;
        String userName;
        ChatMessage cm;
        String date;

        public ClientThread(Socket socket) {
            this.socket = socket;
            id = nextId++;

            try {
                oOutput = new ObjectOutputStream(socket.getOutputStream());
                oInput = new ObjectInputStream(socket.getInputStream());
                userName = (String) oInput.readObject();
                broadcast(notificationHighlight + userName + " has joined the chat room." + notificationHighlight);
            } catch (IOException e) {
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
            date = LocalTime.now().toString();
        }

        public String getUserName() {
            return userName;
        }

        @Override
        public void run() {
            boolean online = true;
            while (online) {
                try {
                    cm = (ChatMessage) oInput.readObject();
                }
                catch (IOException e) {
                    display(userName + " Exception reading Streams: " + e);
                    break;
                }
                catch(ClassNotFoundException e2) {
                    break;
                }
                // get the message from the ChatMessage object received
                String message = cm.getMessage();

                // different actions based on type message
                switch(cm.getType()) {

                    case MESSAGE:
                        boolean confirmation =  broadcast(userName + ": " + message);
                        if(!confirmation){
                            String msg = notificationHighlight + "Sorry. No such user exists." + notificationHighlight;
                            writeMsg(msg);
                        }
                        break;
                    case PINGUFACT:
                        for (int i = clientthreads.size() - 1; i >= 0; i--) {
                            ClientThread ct = clientthreads.get(i);
                            if (!ct.writeMsg("PinguFacts: " + pinguFacts[r.nextInt(3)])) {
                                clientthreads.remove(i);
                                display("Client " + ct.userName + " seems to have disconnected, removed from the list of clients.");
                            }
                        }
                        break;
                    case LOGOUT:
                        display(userName + " disconnected with a LOGOUT message.");
                        online = false;
                        break;
                    case WHOIS:
                        writeMsg("List of the users connected at " + LocalTime.now().toString() + "\n");
                        // send list of active clients
                        for(int i = 0; i < clientthreads.size(); ++i) {
                            ClientThread ct = clientthreads.get(i);
                            writeMsg((i+1) + ") " + ct.userName + " since " + ct.date);
                        }
                        break;
                }
            }
            // if out of the loop then disconnected and remove from client list
            remove(id);
            disconnect();
        }

        private void disconnect() {
            if (oInput != null) {
                try {
                    oInput.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (oOutput != null) {
                try {
                    oOutput.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private boolean writeMsg(String msg) {
            if(!socket.isConnected()) {
                disconnect();
                return false;
            }
            try {
                oOutput.writeObject(msg);
            }
            catch(IOException e) {
                display(notificationHighlight + "Error sending message to " + userName + notificationHighlight);
                display(e.toString());
            }
            return true;
        }
    }
}
