package com.basecamp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.*;

public class Client {
    private static final Logger LOG = LoggerFactory.getLogger(Client.class);

    private static final String HOST = "localhost";
    private static final int PORT = 8090;

    private static SocketChannel channel;
    private static String myId;
    private static boolean readyToClose;
    private static HashMap<String, int[]> idToKeyMap = new HashMap<>();

    public static void main(String[] args) {

        try {
            InetSocketAddress serverAddress = new InetSocketAddress(HOST, PORT);
            channel = SocketChannel.open(serverAddress);

            LOG.info("Connected to server...");

            setInitInfo();

        } catch (IOException e) {
            LOG.error("!You cannot get initial information from server!");
            return;
        }

        new Thread(() -> {

            LOG.info("Input string format: '123!Hello'(friendId!message) or 'Close'(disconnect)");

            try {
                input();

            } catch (IOException | InterruptedException e) {
                LOG.error("!You cannot send this message!");
            }

        }).start();

        new Thread(() -> {

            try {
                getMessage();

            } catch (IOException | InterruptedException e) {

                try {
                    channel.close();
                } catch (IOException e1) {
                    LOG.error("!Cannot close connection with server!");
                }

                LOG.error("!Connection with server has been broken!");
            }

        }).start();

    }

    private static void setInitInfo() throws IOException {

        ByteBuffer buffer = ByteBuffer.allocate(200);
        StringBuilder ids = new StringBuilder();

        channel.read(buffer);
        buffer.flip();

        while (buffer.hasRemaining()) {
            ids.append((char) buffer.get());
        }

        int index = ids.lastIndexOf("!");
        myId = ids.substring(++index);

        LOG.info("My id: {}", myId);

        if (index == 1) {
            LOG.info("Ids of my online friends: [no friends online]");

        } else {
            LOG.info("Ids of my online friends: {}", ids.substring(0, index));
        }
    }

    private static void input() throws IOException, InterruptedException {

        while (channel.isConnected()) {

            LOG.info("New message: ");

            Scanner scanner = new Scanner(System.in).useDelimiter("!");

            String inputId = scanner.next();

            //Check if input has valid format
            if (inputId.matches("^[0-9]+$") &&
                    !(inputId.equals(myId)) && scanner.hasNext()) {

                String inputMessage = scanner.nextLine().substring(1); //To remove '!'
                workWithPhases(inputId, 1, inputMessage);

            } else if ("Close".equals(inputId)) {
                ByteBuffer buffer = ByteBuffer.wrap("readyToClose".getBytes());
                channel.write(buffer);
                break; //To exit from input thread

            } else {
                LOG.error("Wrong input format");
            }
        }
    }

    private static void getMessage() throws IOException, InterruptedException {

        while (!readyToClose) {

            ByteBuffer buffer = ByteBuffer.allocate(5000);
            StringBuilder message = new StringBuilder();

            channel.read(buffer);
            buffer.flip(); //for read

            while (buffer.hasRemaining()) {
                char symbol = (char) buffer.get();

                if (symbol == ']') {

                    if ("Close".equals(message.toString())) {
                        channel.close();
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

    private static void parseMessage(String message)
            throws IOException, InterruptedException {

        StringTokenizer stringTokenizer = new StringTokenizer(message, "!");

        String senderId = stringTokenizer.nextToken();
        String receiverId = stringTokenizer.nextToken(); //used to skip token
        int phase = Integer.parseInt(stringTokenizer.nextToken());
        String receivedMessage = stringTokenizer.nextToken();

        workWithPhases(senderId, phase, receivedMessage);
    }

    private static void workWithPhases(String id, int phase, String message)
            throws IOException, InterruptedException {
        if (phase == 1 || phase == 2) {
            int[] encryptionKey = generateKey(message.chars().toArray().length);

            idToKeyMap.put(id, encryptionKey);

            String encryptedMessage = new String(
                    encrypt(message.chars().toArray(), encryptionKey));
            sendMessage(id, ++phase, encryptedMessage);

        } else if (phase == 3) {
            int[] encryptionKey = idToKeyMap.get(id);

            String encryptedMessage = new String(
                    encrypt(message.chars().toArray(), encryptionKey));
            sendMessage(id, ++phase, encryptedMessage);

        } else if (phase == 4) {
            int[] encryptionKey = idToKeyMap.get(id);

            String decryptedMessage = new String(
                    encrypt(message.chars().toArray(), encryptionKey));

            LOG.info("Received message from {}: {}", id, decryptedMessage);
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

    private static void sendMessage(String receiverId, int phase, String message)
            throws IOException, InterruptedException {

        String messageForWrite =
                myId + "!" + receiverId + "!" + phase + "!" + message + "]";

        ByteBuffer buffer = ByteBuffer.wrap(messageForWrite.getBytes());

        channel.write(buffer);

        Thread.sleep(1); //To prevent sending many answers in one message
    }
}
