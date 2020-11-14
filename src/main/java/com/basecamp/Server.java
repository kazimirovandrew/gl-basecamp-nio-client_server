package com.basecamp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.*;

public class Server {
    private static Selector selector;
    private static ServerSocketChannel serverSocketChannel;
    private static InetSocketAddress serverAddress;
    private static LinkedHashMap<SocketAddress, String> addressToIdMap = new LinkedHashMap<>();
    private static HashMap<String, ArrayList<String>> idToMessagesMap = new HashMap<>();
    private static int randomId = 0;
    private static ByteBuffer buffer;

    public static void main(String[] args) throws IOException, InterruptedException {
        selector = Selector.open();

        serverSocketChannel = ServerSocketChannel.open();
        serverAddress = new InetSocketAddress("localhost", 8090);

        serverSocketChannel.bind(serverAddress);

        serverSocketChannel.configureBlocking(false);

        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

        System.out.println("Server started..." + "\n");

        while (true) {
            selector.select();

            Set<SelectionKey> selectionKeys = selector.selectedKeys();
            Iterator<SelectionKey> iterator = selectionKeys.iterator();

            while (iterator.hasNext()) {
                SelectionKey nextKey = iterator.next();

                if (nextKey.isAcceptable()) {

                    SocketChannel clientChannel = serverSocketChannel.accept();

                    clientChannel.configureBlocking(false);

                    clientChannel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);


                    SocketAddress remoteAddress = clientChannel.getRemoteAddress();

                    addressToIdMap.put(remoteAddress, ++randomId + "");

                    //Send info for client init
                    String clientsIds = "";

                    for (SocketAddress address : addressToIdMap.keySet()) {
                        clientsIds += ("!" + addressToIdMap.get(address));
                    }

                    ByteBuffer buffer = ByteBuffer.wrap(clientsIds.getBytes());

                    clientChannel.write(buffer);
                    Thread.sleep(1); //To prevent sending ids and messages at the same time

                    System.out.println("Connection with " + remoteAddress + "(id:" + randomId + ") accepted" + "\n");
                } else if (nextKey.isReadable()) {

                    SocketChannel senderChannel = (SocketChannel) nextKey.channel();

                    buffer = ByteBuffer.allocate(50);
                    int bytesRead = senderChannel.read(buffer);

                    StringBuilder receivedMessage = new StringBuilder();

                    while (bytesRead > 0) {
                        buffer.flip(); //for read

                        while (buffer.hasRemaining()) {
                            receivedMessage.append((char) buffer.get());
                        }

                        buffer.clear(); //for write
                        bytesRead = senderChannel.read(buffer);
                    }

                    if ("readyToClose".equals(receivedMessage.toString())) {
                        buffer = ByteBuffer.wrap("Close]".getBytes());
                        senderChannel.write(buffer);

                        SocketAddress remoteAddress = senderChannel.getRemoteAddress();
                        String remoteId = addressToIdMap.get(remoteAddress);

                        addressToIdMap.remove(remoteAddress);
                        senderChannel.close();

                        System.out.println("Connection with " + remoteAddress + "(id:" + remoteId + ") closed");
                    }
                    else {
                        System.out.println("Received message: " + receivedMessage.toString());

                        int from = receivedMessage.indexOf("!");
                        int to = receivedMessage.indexOf("!", from + 1);
                        String receiverId = receivedMessage.substring(++from, to);

                        if (idToMessagesMap.containsKey(receiverId)) {
                            ArrayList<String> listOfMessages = idToMessagesMap.get(receiverId);
                            listOfMessages.add(receivedMessage.toString());
                        }
                        else {
                            ArrayList<String> listOfMessages = new ArrayList<>();
                            listOfMessages.add(receivedMessage.toString());
                            idToMessagesMap.put(receiverId, listOfMessages);
                        }
                    }
                } else if (nextKey.isWritable()) {

                    SocketChannel receiverChannel = (SocketChannel) nextKey.channel();

                    SocketAddress receiverAddress = receiverChannel.getRemoteAddress();

                    String receiverId = addressToIdMap.get(receiverAddress);

                    if (idToMessagesMap.containsKey(receiverId)) {

                        for (String message : idToMessagesMap.get(receiverId)) {

                            ByteBuffer buffer = ByteBuffer.wrap(message.getBytes());

                            receiverChannel.write(buffer);

                            System.out.println("Sent message: " + new String(buffer.array()) + "\n");

                        }

                        idToMessagesMap.remove(receiverId);
                    }
                }

                iterator.remove(); //to prevent the same key
            }
        }
    }
}
