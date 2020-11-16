package com.basecamp;

import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final Logger LOG = LoggerFactory.getLogger(Server.class);

    private static final String HOST = "localhost";
    private static final int PORT = 8090;
    private static final int ID_LENGTH = 3;
    private static final int BUFFER_CAPACITY = 50;

    private static LinkedHashMap<SocketAddress, String> addressToIdMap = new LinkedHashMap<>();
    private static HashMap<String, ArrayList<String>> idToMessagesMap = new HashMap<>();

    public static void main(String[] args) throws IOException, InterruptedException {

        Selector selector = Selector.open();

        ServerSocketChannel serverSocketChannel = createServerChannel(selector);

        LOG.info("Server started...");

        while (true) {
            selector.select();

            Set<SelectionKey> selectionKeys = selector.selectedKeys();
            Iterator<SelectionKey> iterator = selectionKeys.iterator();

            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();

                if (key.isAcceptable()) {

                    SocketChannel clientChannel =
                            registerNewClientChannel(selector, serverSocketChannel);
                    sendInitInfo(clientChannel);

                } else if (key.isReadable()) {

                    SocketChannel senderChannel = (SocketChannel) key.channel();
                    readFrom(senderChannel);

                } else if (key.isWritable()) {

                    SocketChannel receiverChannel = (SocketChannel) key.channel();
                    writeTo(receiverChannel);
                }

                iterator.remove(); //to prevent the same key
            }
        }
    }

    private static ServerSocketChannel createServerChannel(
            Selector selector) throws IOException {

        InetSocketAddress serverAddress = new InetSocketAddress(HOST, PORT);

        ServerSocketChannel channel = ServerSocketChannel.open();
        channel.bind(serverAddress);
        channel.configureBlocking(false);
        channel.register(selector, SelectionKey.OP_ACCEPT);

        return channel;
    }


    private static SocketChannel registerNewClientChannel(
            Selector selector, ServerSocketChannel serverChannel) throws IOException {

        SocketChannel clientChannel = serverChannel.accept();
        clientChannel.configureBlocking(false);
        clientChannel.register(selector,
                SelectionKey.OP_READ | SelectionKey.OP_WRITE);

        SocketAddress clientAddress = clientChannel.getRemoteAddress();
        String clientId = RandomStringUtils.randomNumeric(ID_LENGTH);

        addressToIdMap.put(clientAddress, clientId);

        LOG.info("Connection with {}(id:{}) accepted", clientAddress, clientId);

        return clientChannel;
    }

    private static void sendInitInfo(SocketChannel clientChannel)
            throws IOException, InterruptedException {

        StringBuilder ids = new StringBuilder();
        addressToIdMap.values().forEach(id -> ids.append("!").append(id));

        ByteBuffer buffer = ByteBuffer.wrap(ids.toString().getBytes());
        clientChannel.write(buffer);

        Thread.sleep(1); //To prevent sending ids and messages at the same time
    }

    private static void readFrom(SocketChannel channel) throws IOException {

        String receivedMessage = getMessage(channel);

        LOG.info("Received message: {}", receivedMessage);

        if ("readyToClose".equals(receivedMessage)) {
            closeChannel(channel);

        } else {
            saveMessage(receivedMessage);
        }
    }

    private static String getMessage(SocketChannel channel) throws IOException {

        StringBuilder message = new StringBuilder();

        ByteBuffer buffer = ByteBuffer.allocate(BUFFER_CAPACITY);
        int bytesCount = channel.read(buffer);

        while (bytesCount > 0) {
            buffer.flip(); //for read

            while (buffer.hasRemaining()) {
                message.append((char) buffer.get());
            }

            buffer.clear(); //for write
            bytesCount = channel.read(buffer);
        }

        return message.toString();
    }

    private static void closeChannel(SocketChannel channel) throws IOException {

        ByteBuffer buffer = ByteBuffer.wrap("Close]".getBytes());
        channel.write(buffer);

        SocketAddress address = channel.getRemoteAddress();
        String id = addressToIdMap.get(address);

        addressToIdMap.remove(address);
        channel.close();

        LOG.info("Connection with {}(id:{}) closed", address, id);
    }

    private static void saveMessage(String massage) {

        int from = massage.indexOf('!');
        int to = massage.indexOf('!', from + 1);
        String receiverId = massage.substring(++from, to);

        if (idToMessagesMap.containsKey(receiverId)) {
            ArrayList<String> messages = idToMessagesMap.get(receiverId);
            messages.add(massage);

        } else {
            ArrayList<String> messages = new ArrayList<>();
            messages.add(massage);
            idToMessagesMap.put(receiverId, messages);
        }
    }

    private static void writeTo(SocketChannel channel) throws IOException {

        SocketAddress address = channel.getRemoteAddress();
        String id = addressToIdMap.get(address);

        if (idToMessagesMap.containsKey(id)) {

            for (String message : idToMessagesMap.get(id)) {

                ByteBuffer buffer = ByteBuffer.wrap(message.getBytes());
                channel.write(buffer);

                LOG.info("Sent message: {}", message);
            }

            idToMessagesMap.remove(id);
        }
    }
}
