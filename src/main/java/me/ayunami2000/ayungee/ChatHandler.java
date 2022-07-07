package me.ayunami2000.ayungee;

import java.io.IOException;
import java.nio.ByteBuffer;

public class ChatHandler {
    // return true to cancel being sent to server/client

    public static boolean fromServer(Client client, String message) {
        return false;
    }

    public static boolean fromClient(Client client, String message) {
        if (!message.startsWith("/")) return false;
        int ind = message.indexOf(' ');
        String commandBase = message.substring(1, ind != -1 ? ind : message.length()).toLowerCase();
        String args = ind != -1 ? message.substring(ind + 1) : "";
        switch (commandBase) {
            case "server":
                if (args.isEmpty()) {
                    //usage msg
                    client.conn.send(new byte[] { 3, 0, 25, 0, (byte) 167, 0, 57, 0, 85, 0, 115, 0, 97, 0, 103, 0, 101, 0, 58, 0, 32, 0, 47, 0, 115, 0, 101, 0, 114, 0, 118, 0, 101, 0, 114, 0, 32, 0, 60, 0, 110, 0, 117, 0, 109, 0, 98, 0, 101, 0, 114, 0, 62 });
                } else {
                    try {
                        int destServer = Integer.parseInt(args);
                        client.server = Math.max(0, Math.min(Main.servers.size() - 1, destServer));
                        try {
                            client.socket.close();
                        } catch (IOException ignored) {}
                    } catch (NumberFormatException e) {
                        //not a number
                        client.conn.send(new byte[] { 3, 0, 29, 0, (byte) 167, 0, 57, 0, 84, 0, 104, 0, 97, 0, 116, 0, 32, 0, 105, 0, 115, 0, 32, 0, 110, 0, 111, 0, 116, 0, 32, 0, 97, 0, 32, 0, 118, 0, 97, 0, 108, 0, 105, 0, 100, 0, 32, 0, 110, 0, 117, 0, 109, 0, 98, 0, 101, 0, 114, 0, 33 });
                    }
                }
                break;
                /*
            case "register":

                break;
            case "login":

                break;
                */
            default:
                return false;
        }
        return true;
    }

    public static boolean serverChatMessage(Client client, byte[] packet) {
        return fireChatMessage(client, true, packet);
    }

    public static boolean clientChatMessage(Client client, byte[] packet) {
        return fireChatMessage(client, false, packet);
    }

    private static boolean fireChatMessage(Client client, boolean fromServer, byte[] packet) {
        if (packet.length >= 3 && packet[0] == 3) {
            ByteBuffer bb = ByteBuffer.wrap(packet);
            bb.get();
            int msgLen = bb.getShort(); //(short) ((msg[1] << 8) + msg[2] & 0xff);
            if (msgLen >= 0) {
                if (packet.length >= 3 + msgLen * 2) {
                    //byte[] chatBytes = new byte[msgLen];
                    //for (int i = 0; i < chatBytes.length; i++) chatBytes[i] = msg[4 + i * 2];
                    //String chatMsg = new String(chatBytes);
                    StringBuilder chatBuilder = new StringBuilder();
                    for (int i = 0; i < msgLen; i++) chatBuilder.append(bb.getChar());
                    String chatMsg = chatBuilder.toString();
                    if (fromServer) {
                        return fromServer(client, chatMsg);
                    } else {
                        return fromClient(client, chatMsg);
                    }
                }
            }
        }
        return false;
    }
}
