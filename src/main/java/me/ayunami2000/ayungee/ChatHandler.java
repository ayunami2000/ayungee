package me.ayunami2000.ayungee;

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
        String args = ind == -1 ? "" : message.substring(ind + 1).trim();
        String[] argsArr = args.split(" ");
        switch (commandBase) {
            case "server":
                if (!client.authed) return false;

                if (args.isEmpty()) {
                    //usage msg
                    client.conn.send(new byte[] { 3, 0, 25, 0, (byte) 167, 0, 57, 0, 85, 0, 115, 0, 97, 0, 103, 0, 101, 0, 58, 0, 32, 0, 47, 0, 115, 0, 101, 0, 114, 0, 118, 0, 101, 0, 114, 0, 32, 0, 60, 0, 110, 0, 117, 0, 109, 0, 98, 0, 101, 0, 114, 0, 62 });
                } else {
                    try {
                        int destServer = Integer.parseInt(args);
                        client.server = Math.max(0, Math.min(Main.servers.size() - 1, destServer));
                    } catch (NumberFormatException e) {
                        //not a number
                        client.conn.send(new byte[] { 3, 0, 29, 0, (byte) 167, 0, 57, 0, 84, 0, 104, 0, 97, 0, 116, 0, 32, 0, 105, 0, 115, 0, 32, 0, 110, 0, 111, 0, 116, 0, 32, 0, 97, 0, 32, 0, 118, 0, 97, 0, 108, 0, 105, 0, 100, 0, 32, 0, 110, 0, 117, 0, 109, 0, 98, 0, 101, 0, 114, 0, 33 });
                    }
                }
                break;
            case "register":
            case "reg":
                if (!Main.useAuth) return false;
                if (client.authed) return false;
                if (argsArr[0].isEmpty()) {
                    client.conn.send(new byte[] { 3, 0, 30, 0, (byte) 167, 0, 57, 0, 89, 0, 111, 0, 117, 0, 32, 0, 109, 0, 117, 0, 115, 0, 116, 0, 32, 0, 115, 0, 112, 0, 101, 0, 99, 0, 105, 0, 102, 0, 121, 0, 32, 0, 97, 0, 32, 0, 112, 0, 97, 0, 115, 0, 115, 0, 119, 0, 111, 0, 114, 0, 100, 0, 46 });
                    return true;
                }
                if (argsArr.length == 1 || !argsArr[0].equals(argsArr[1])) {
                    client.conn.send(new byte[] { 3, 0, 31, 0, (byte) 167, 0, 57, 0, 84, 0, 104, 0, 111, 0, 115, 0, 101, 0, 32, 0, 112, 0, 97, 0, 115, 0, 115, 0, 119, 0, 111, 0, 114, 0, 100, 0, 115, 0, 32, 0, 100, 0, 111, 0, 32, 0, 110, 0, 111, 0, 116, 0, 32, 0, 109, 0, 97, 0, 116, 0, 99, 0, 104, 0, 33 });
                    return true;
                }
                if (Auth.register(client.username, argsArr[0].toCharArray(), Main.getIp(client.conn))) {
                    Main.printMsg("Player " + client + " registered successfully!");
                    client.authed = true;
                    client.server = -1;
                } else {
                    client.conn.send(new byte[] { (byte) 255, 0, 38, 0, (byte) 167, 0, 57, 0, 84, 0, 104, 0, 105, 0, 115, 0, 32, 0, 117, 0, 115, 0, 101, 0, 114, 0, 110, 0, 97, 0, 109, 0, 101, 0, 32, 0, 105, 0, 115, 0, 32, 0, 97, 0, 108, 0, 114, 0, 101, 0, 97, 0, 100, 0, 121, 0, 32, 0, 114, 0, 101, 0, 103, 0, 105, 0, 115, 0, 116, 0, 101, 0, 114, 0, 101, 0, 100, 0, 33 });
                    client.conn.close();
                }
                break;
            case "login":
            case "l":
                if (!Main.useAuth) return false;
                if (client.authed) return false;
                if (argsArr[0].isEmpty()) {
                    client.conn.send(new byte[] { 3, 0, 30, 0, (byte) 167, 0, 57, 0, 89, 0, 111, 0, 117, 0, 32, 0, 109, 0, 117, 0, 115, 0, 116, 0, 32, 0, 115, 0, 112, 0, 101, 0, 99, 0, 105, 0, 102, 0, 121, 0, 32, 0, 97, 0, 32, 0, 112, 0, 97, 0, 115, 0, 115, 0, 119, 0, 111, 0, 114, 0, 100, 0, 46 });
                    return true;
                }
                if (Auth.login(client.username, argsArr[0].toCharArray())) {
                    Main.printMsg("Player " + client + " logged in!");
                    client.authed = true;
                } else {
                    client.conn.send(new byte[] { (byte) 255, 0, 29, 0, (byte) 167, 0, 57, 0, 84, 0, 104, 0, 97, 0, 116, 0, 32, 0, 112, 0, 97, 0, 115, 0, 115, 0, 119, 0, 111, 0, 114, 0, 100, 0, 32, 0, 105, 0, 115, 0, 32, 0, 105, 0, 110, 0, 99, 0, 111, 0, 114, 0, 114, 0, 101, 0, 99, 0, 116, 0, 33 });
                    client.conn.close();
                }
                break;
            case "changepass":
            case "changepassword":
            case "changeepasswd":
            case "changepwd":
            case "changepw":
                if (!Main.useAuth) return false;
                if (!client.authed) return false;
                if (argsArr[0].isEmpty() || argsArr.length == 1) {
                    client.conn.send(new byte[] { 3, 0, 63, 0, (byte) 167, 0, 57, 0, 80, 0, 108, 0, 101, 0, 97, 0, 115, 0, 101, 0, 32, 0, 115, 0, 112, 0, 101, 0, 99, 0, 105, 0, 102, 0, 121, 0, 32, 0, 116, 0, 104, 0, 101, 0, 32, 0, 111, 0, 108, 0, 100, 0, 32, 0, 112, 0, 97, 0, 115, 0, 115, 0, 119, 0, 111, 0, 114, 0, 100, 0, 32, 0, 102, 0, 111, 0, 108, 0, 108, 0, 111, 0, 119, 0, 101, 0, 100, 0, 32, 0, 98, 0, 121, 0, 32, 0, 116, 0, 104, 0, 101, 0, 32, 0, 110, 0, 101, 0, 119, 0, 32, 0, 112, 0, 97, 0, 115, 0, 115, 0, 119, 0, 111, 0, 114, 0, 100, 0, 33 });
                    return true;
                }
                if (!Auth.isRegistered(client.username)) {
                    // register first...
                    client.conn.send(new byte[] { 3, 0, 60, 0, (byte) 167, 0, 57, 0, 89, 0, 111, 0, 117, 0, 32, 0, 109, 0, 117, 0, 115, 0, 116, 0, 32, 0, 114, 0, 101, 0, 103, 0, 105, 0, 115, 0, 116, 0, 101, 0, 114, 0, 32, 0, 97, 0, 110, 0, 32, 0, 97, 0, 99, 0, 99, 0, 111, 0, 117, 0, 110, 0, 116, 0, 32, 0, 102, 0, 105, 0, 114, 0, 115, 0, 116, 0, 32, 0, 116, 0, 111, 0, 32, 0, 99, 0, 104, 0, 97, 0, 110, 0, 103, 0, 101, 0, 32, 0, 105, 0, 116, 0, 115, 0, 32, 0, 112, 0, 97, 0, 115, 0, 115, 0, 119, 0, 111, 0, 114, 0, 100, 0, 33 });
                    return true;
                }
                if (!Auth.login(client.username, argsArr[0].toCharArray())) {
                    // invalid old password
                    client.conn.send(new byte[] { 3, 0, 29, 0, (byte) 167, 0, 57, 0, 84, 0, 104, 0, 97, 0, 116, 0, 32, 0, 112, 0, 97, 0, 115, 0, 115, 0, 119, 0, 111, 0, 114, 0, 100, 0, 32, 0, 105, 0, 115, 0, 32, 0, 105, 0, 110, 0, 99, 0, 111, 0, 114, 0, 114, 0, 101, 0, 99, 0, 116, 0, 33 });
                    return true;
                }
                if (Auth.changePass(client.username, argsArr[1].toCharArray())) {
                    // changed password!
                    Main.printMsg("Player " + client + " changed their password!");
                    client.conn.send(new byte[] { 3, 0, 33, 0, (byte) 167, 0, 57, 0, 89, 0, 111, 0, 117, 0, 114, 0, 32, 0, 112, 0, 97, 0, 115, 0, 115, 0, 119, 0, 111, 0, 114, 0, 100, 0, 32, 0, 104, 0, 97, 0, 115, 0, 32, 0, 98, 0, 101, 0, 101, 0, 110, 0, 32, 0, 99, 0, 104, 0, 97, 0, 110, 0, 103, 0, 101, 0, 100, 0, 33 });
                }
                break;
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
