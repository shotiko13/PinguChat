

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.time.LocalTime;
import java.util.Scanner;

public class ChatClient {
    private ObjectInputStream oInput;
    private ObjectOutputStream oOutput;
    private Socket socket;

    private String server, username;
    private int port;
    private String notificationHighlight = " ***** ";

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public ChatClient(String server, String username, int port) {
        this.server = server;
        this.username = username;
        this.port = port;
    }

    public boolean start() {
        try {
            socket = new Socket(server, port);
        } catch (Exception e) {
            display("Opening Socket not possible: " + e);
            return false;
        }

        display("Connection accepted " + socket.getInetAddress() + ":" + socket.getPort());

        try {
            oInput = new ObjectInputStream(socket.getInputStream());
            oOutput = new ObjectOutputStream(socket.getOutputStream());
        } catch (IOException e) {
            display("Exception creating I/O-Streams: " + e);
            return false;
        }

        new ListenOnServer().start();
        // sent the username first as a normal string message
        try {
            oOutput.writeObject(username);
        } catch (IOException e) {
            e.printStackTrace();
            disconnect();
            return false;
        }
        return true;
    }

    private void display(String s) {
        System.out.println(LocalTime.now().toString() + ": " + s);
    }

    public void sendMessage(ChatMessage msg) {
        try {
            oOutput.writeObject(msg);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // close all open streams/sockets
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

    public static void main(String[] args) {
        int portNumber = 3000;
        String serverAddress = "localhost";
        String userName = "Noone";
        Scanner scan = new Scanner(System.in);

        System.out.println("Enter the username: ");
        userName = scan.nextLine();

        switch (args.length) {
            case 2:
                serverAddress = args[1];
                portNumber = Integer.parseInt(args[0]);
            case 0:
                break;
            default:
                System.out.println("Usage is: > java ChatClient [portNumber] [serverAddress]");
        }
        ChatClient client = new ChatClient(serverAddress, userName, portNumber);

        if (!client.start()) {
            return;
        }

        System.out.println("\nHello.! Welcome to the chatroom.");
        System.out.println("Instructions:");
        System.out.println("1. Simply type the message to send broadcast to all active clients");
        System.out.println("2. Type '@username<space>yourmessage' without quotes to send message to desired client");
        System.out.println("3. Type 'WHOIS' without quotes to see list of active clients");
        System.out.println("4. Type 'LOGOUT' without quotes to logoff from server");
        System.out.println("5. Type 'PINGU' without quotes to request a random penguin fact");

        while (true) {
            System.out.println("> ");
            String msg = scan.nextLine();
            if (msg.equalsIgnoreCase("LOGOUT")) {
                client.sendMessage(new ChatMessage(ChatMessageType.LOGOUT, ""));
                break;
            } else if (msg.equalsIgnoreCase("WHOIS")) {
                client.sendMessage(new ChatMessage(ChatMessageType.WHOIS, ""));
            } else if (msg.equalsIgnoreCase("PINGU")) {
                client.sendMessage(new ChatMessage(ChatMessageType.PINGUFACT, ""));
            } else {
                client.sendMessage(new ChatMessage(ChatMessageType.MESSAGE, msg));
            }
        }
        scan.close();
        client.disconnect();
    }

    class ListenOnServer extends Thread {

        public void run() {
            while (true) {
                try {
                    // read the message form the input datastream
                    String msg = (String) oInput.readObject();
                    // print the message
                    System.out.println(msg);
                    System.out.print("> ");
                } catch (IOException e) {
                    display(notificationHighlight + " Server has closed the connection: " + e + notificationHighlight);
                    break;
                } catch (ClassNotFoundException e2) {
                }
            }
        }
    }
}
