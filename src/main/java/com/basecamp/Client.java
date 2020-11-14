package com.basecamp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.*;

public class Client {
    private static InetSocketAddress serverAddress;
    private static SocketChannel clientChannel;
    private static String myId;
    private static boolean readyToClose;
    private static HashMap<String, int[]> idToKeyMap = new HashMap<>();

    public static void main(String[] args) {

        try {
            serverAddress = new InetSocketAddress("localhost", 8090);
            clientChannel = SocketChannel.open(serverAddress);

            System.out.println("Connected to server..." + "\n");

            setInitInfo();

        } catch (IOException e) {
            System.out.println("!You cannot get initial information from server!");
            return;
        }

        new Thread(() -> {

            System.out.println("Input string format: '123!Hello'(friendId!message) or 'Close'(disconnect)" + "\n"); //hint

            try {
                input();

            } catch (IOException | InterruptedException e) {
                System.out.println("!You cannot send this message!");

            }

        }).start();

        new Thread(() -> {

            try {
                getMessage();

            } catch (IOException | InterruptedException e) {

                try {
                    clientChannel.close();
                } catch (IOException e1) {
                    System.out.println("!Cannot close connection with server!");
                }

                System.out.println("!Connection with server has been broken!");

            }

        }).start();

    }

    private static void setInitInfo() throws IOException {

        ByteBuffer buffer = ByteBuffer.allocate(200);
        StringBuilder messageWithIds = new StringBuilder();

        clientChannel.read(buffer);
        buffer.flip();

        while (buffer.hasRemaining()) {
            messageWithIds.append((char) buffer.get());
        }

        int index = messageWithIds.lastIndexOf("!");
        myId = messageWithIds.substring(++index);

        System.out.println("My id: " + myId);

        if (index == 1) {
            System.out.println("Ids of my online friends: [no friends online]" + "\n");
        } else {
            System.out.println("Ids of my online friends: " + messageWithIds.substring(0, index) + "\n");
        }
    }

    private static void input() throws IOException, InterruptedException {

        while (clientChannel.isConnected()) {

            System.out.print("New message: ");

            Scanner scanner = new Scanner(System.in).useDelimiter("!");

            String inputId = scanner.next();

            //Check if input has valid format
            if (inputId.matches("^[0-9]+$") && !(inputId.equals(myId)) && scanner.hasNext()) {

                String inputMessage = scanner.nextLine().substring(1); //To remove '!'
                workWithPhases(inputId, 1, inputMessage);

            } else if ("Close".equals(inputId)) {
                ByteBuffer buffer = ByteBuffer.wrap("readyToClose".getBytes());
                clientChannel.write(buffer);
                break; //To exit from input thread
            } else {
                System.out.println("Wrong input format");
            }
        }
    }

    private static void getMessage() throws IOException, InterruptedException {

        while (!readyToClose) {

            ByteBuffer buffer = ByteBuffer.allocate(5000);
            StringBuilder message = new StringBuilder();

            clientChannel.read(buffer);
            buffer.flip(); //for read

            while (buffer.hasRemaining()) {
                char symbol = (char) buffer.get();

                if (symbol == ']') {

                    if ("Close".equals(message.toString())) {
                        clientChannel.close();
                        readyToClose = true;
                        break;
                    } else {
                        parseMessage(message.toString());
                        message.setLength(0);
                    }
                } else {
                    message.append(symbol);
                }
            }
            buffer.clear();
        }
    }

    private static void parseMessage(String message) throws IOException, InterruptedException {

        StringTokenizer stringTokenizer = new StringTokenizer(message, "!");

        String senderId = stringTokenizer.nextToken();
        String receiverId = stringTokenizer.nextToken(); //used to skip token
        int phase = Integer.parseInt(stringTokenizer.nextToken());
        String receivedMessage = stringTokenizer.nextToken();

        workWithPhases(senderId, phase, receivedMessage);
    }

    private static void workWithPhases(String id, int phase, String message) throws IOException, InterruptedException {
        if (phase == 1 || phase == 2) {
            int[] encryptionKey = generateKey(message.chars().toArray().length);

            idToKeyMap.put(id, encryptionKey);

            String encryptedMessage = new String(encrypt(message.chars().toArray(), encryptionKey));
            sendMessage(id, ++phase, encryptedMessage);
        } else if (phase == 3) {
            int[] encryptionKey = idToKeyMap.get(id);

            String encryptedMessage = new String(encrypt(message.chars().toArray(), encryptionKey));
            sendMessage(id, ++phase, encryptedMessage);
        } else if (phase == 4) {
            int[] encryptionKey = idToKeyMap.get(id);

            String decryptedMessage = new String(encrypt(message.chars().toArray(), encryptionKey));

            System.out.println("\n" + "Received message from " + id + ": " + decryptedMessage);
        }
    }

    private static int[] generateKey(int lengthOfKey) {
        int[] key = new int[lengthOfKey];

        for (int i : key) {
            i = new Random().nextInt(10);
        }

        return key;
    }

    private static char[] encrypt(int[] message, int[] encryptionKey) {
        char[] encryptedMessage = new char[message.length];

        for (int i = 0; i < encryptedMessage.length; i++) {
            encryptedMessage[i] = (char) (message[i] ^ encryptionKey[i]);
        }

        return encryptedMessage;
    }

    private static void sendMessage(String receiverId, int phase, String message) throws IOException, InterruptedException {

        String messageForWrite = myId + "!" + receiverId + "!" + phase + "!" + message + "]";

        ByteBuffer buffer = ByteBuffer.wrap(messageForWrite.getBytes());

        clientChannel.write(buffer);

        Thread.sleep(1); //To prevent sending many answers in one message
    }
}
