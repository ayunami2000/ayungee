package me.ayunami2000.ayungee;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class PluginMessages {
    // return true to cancel being sent to server/client

    public static boolean fromServer(Client client, String name, byte[] data) {
        if (name.equals("BungeeCord")) { // eat all bungeecord messages
            DataInputStream dataIn = new DataInputStream(new ByteArrayInputStream(data));
            try {
                String bungeeTag = dataIn.readUTF();
                if (bungeeTag.equals("Connect")) { // actually send current player to server :D
                    if (!client.authed) return true;
                    String destServer = dataIn.readUTF();
                    try {
                        int destServerInt = Integer.parseInt(destServer);
                        client.server = Math.max(0, Math.min(Main.servers.size() - 1, destServerInt));
                    } catch (NumberFormatException ignored) {}
                }
            } catch (IOException e) {
                // broken packet
            }
            return true;
        }
        return false;
    }

    public static boolean fromClient(Client client, String name, byte[] data) {
        return Skins.setSkin(client.username, client.conn, name, data) || Voice.handleVoice(client.username, client.conn, name, data);
    }

    public static boolean serverPluginMessage(Client client, byte[] packet) {
        return firePluginMessage(client, true, packet);
    }

    public static boolean clientPluginMessage(Client client, byte[] packet) {
        return firePluginMessage(client, false, packet);
    }

    private static boolean firePluginMessage(Client client, boolean fromServer, byte[] packet) {
        if(packet.length >= 3 && packet[0] == (byte) 250) {
            ByteBuffer bb = ByteBuffer.wrap(packet);
            bb.get();
            int tagLen = bb.getShort();
            if (tagLen < 0 || tagLen * 2 > packet.length - 3) return false;
            StringBuilder tagBuilder = new StringBuilder();
            for (int i = 0; i < tagLen; i++) tagBuilder.append(bb.getChar());
            //int dataLen = bb.getShort();
            int dataLen = bb.remaining() - 2; // - 2 for the 2 bytes specifying data length
            String tag = tagBuilder.toString();
            int offset = 3 + tagLen * 2 + 2; // - 2 for the 2 bytes specifying data length
            byte[] msg = new byte[dataLen];
            System.arraycopy(packet, offset, msg, 0, dataLen);
            if (fromServer) {
                return fromServer(client, tag, msg);
            } else {
                return fromClient(client, tag, msg);
            }
        }
        return false;
    }
}
