package me.ayunami2000.ayungee;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

public class WebSocketProxy extends WebSocketServer {
    private static final int maxBuffSize = 33000; // 4096;

    public WebSocketProxy(int port) {
        super(new InetSocketAddress(port));
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        // anti-concurrentmodificationexception
        Set<Pattern> snapshotOriginBlacklist = new HashSet<>(Main.originBlacklist);
        if (!snapshotOriginBlacklist.isEmpty() || !Main.originWhitelist.isEmpty()) {
            String origin = handshake.getFieldValue("Origin");
            if (origin == null) {
                conn.close();
                return;
            }
            if (origin.contains("://")) origin = origin.substring(origin.indexOf("://") + 3);
            if (!Main.originWhitelist.isEmpty() && !Main.originWhitelist.contains(origin)) {
                conn.close();
                return;
            }
            for (Pattern pattern : snapshotOriginBlacklist) {
                if (pattern.matcher(origin).matches()) {
                    conn.close();
                    return;
                }
            }
        }
        if (Main.forwarded) {
            String forwardedIp = handshake.getFieldValue("X-Real-IP");
            if (forwardedIp == null) {
                conn.close();
                return;
            }
            conn.setAttachment(forwardedIp);
        } else {
            conn.setAttachment(conn.getRemoteSocketAddress().getAddress().toString().split("/")[1]);
        }
        new Thread(() -> {
            try {
                Thread.sleep(10000);
            } catch (InterruptedException ignored) {}
            if (conn.isOpen() && (Main.bans.contains(Main.getIp(conn)) || !Main.clients.containsKey(conn) || !Main.clients.get(conn).authed)) conn.close();
        }).start();
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        Client selfClient = Main.clients.remove(conn);
        if (selfClient != null) {
            Main.printMsg("Player " + selfClient + " left!");
            Skins.removeSkin(selfClient.username);
            Voice.onQuit(selfClient.username);
            if (selfClient.socket.isClosed()) {
                try {
                    selfClient.socket.close();
                } catch (IOException e) {
                    //e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void onMessage(WebSocket conn, String s) {
        if (!conn.isOpen()) return;
        if (s.equals("Accept: MOTD")) {
            if (Main.bans.contains(Main.getIp(conn))) {
                conn.send("{\"data\":{\"motd\":[\"§cYour IP is §4banned §cfrom this server.\"],\"cache\":true,\"max\":0,\"icon\":false,\"online\":0,\"players\":[\"§4Banned...\"]},\"vers\":\"0.2.0\",\"name\":\"Your IP is banned from this server.\",\"time\":" + Instant.now().toEpochMilli() + ",\"type\":\"motd\",\"brand\":\"Eagtek\",\"uuid\":\"" + UUID.randomUUID().toString() + "\",\"cracked\":true}");
            } else {
                conn.send(Main.motdJson);
                conn.send(Main.serverIcon);
            }
        }
        conn.close();
    }

    @Override
    public void onMessage(WebSocket conn, ByteBuffer message) { // todo: make use of the fact that it's already a bytebuffer dumbass
        if (Main.bans.contains(Main.getIp(conn))) {
            conn.close();
            return;
        }
        if (!conn.isOpen()) return;
        byte[] msg = message.array();
        if (!Main.clients.containsKey(conn)) {
            if (msg.length > 3 && msg[0] == 2 && msg[1] == 69) {
                message.get();
                message.get();
                short unameLen = message.getShort();
                if (unameLen < 3 || unameLen > 16) {
                    conn.close();
                    return;
                }
                if (msg.length < 5 + unameLen * 2) {
                    conn.close();
                    return;
                }
                StringBuilder unameBuilder = new StringBuilder();
                for (int i = 0; i < unameLen; i++) unameBuilder.append(message.getChar());
                String username = unameBuilder.toString();
                if (!username.equals(username.replaceAll("[^A-Za-z0-9_-]", "_"))) {
                    conn.close();
                    return;
                }
                if (Main.clients.values().stream().anyMatch(client -> client.username.equals(username) || client.conn == conn)) {
                    conn.close();
                    return;
                }
                Client selfClient = new Client(conn, username);
                Main.clients.put(conn, selfClient);
                new Thread(() -> {
                    try {
                        while (conn.isOpen()) {
                            int currServer = selfClient.server;
                            if (currServer == -1 && selfClient.authed) currServer = selfClient.server = 0;
                            selfClient.hasLoginHappened = false;
                            if (!selfClient.firstTime) Main.printMsg("Player " + selfClient + " joined server " + currServer + "!");
                            ServerItem chosenServer = Main.servers.get(currServer);
                            /*
                            if (chosenServer.host.equals(Main.authKey)) {
                                // todo: custom server here
                                return;
                            }
                            */
                            Socket selfSocket = new Socket();
                            try {
                                // todo: pregenerate InetSocketAddresses
                                selfSocket.connect(new InetSocketAddress(chosenServer.host, chosenServer.port), 10000);
                            } catch (SocketTimeoutException e) {
                                conn.close();
                                return;
                            }
                            selfClient.setSocket(selfSocket);
                            if (!selfClient.firstTime) sendToServer(selfClient.handshake, selfClient);
                            if (selfClient.firstTime) {
                                while (selfClient.msgCache.size() > 0)
                                    sendToServer(selfClient.msgCache.remove(0), selfClient);
                            }
                            while (conn.isOpen() && !selfSocket.isInputShutdown() && selfClient.server == currServer) {
                                byte[] dataa = new byte[maxBuffSize];
                                int read = selfClient.socketIn.read(dataa, 0, maxBuffSize);
                                byte[] data;
                                if (read == maxBuffSize) {
                                    data = dataa;
                                } else if (read > 0) {
                                    data = new byte[read];
                                    System.arraycopy(dataa, 0, data, 0, read);
                                } else {
                                    continue;
                                }
                                if (ChatHandler.serverChatMessage(selfClient, data)) continue;
                                if (PluginMessages.serverPluginMessage(selfClient, data)) continue;
                                if (!selfClient.authed && data[0] == 13) selfClient.positionPacket = data;
                                boolean loginPacket = data[0] == 1;
                                if (loginPacket && !selfClient.hasLoginHappened) selfClient.hasLoginHappened = true;
                                if (selfClient.firstTime && loginPacket) {
                                    selfClient.clientEntityId = selfClient.serverEntityId = EntityMap.readInt(data, 1);
                                    Voice.onLogin(username, conn);
                                }
                                if (!selfClient.firstTime && loginPacket) {
                                    selfClient.serverEntityId = EntityMap.readInt(data, 1);
                                    // assume server is giving valid data; we don't have to validate it because it isn't a potentially malicious client
                                    short worldByteLen = (short) ((data[5] << 8) + data[6] & 0xff);
                                    byte[] worldByte = new byte[worldByteLen * 2 + 2];
                                    System.arraycopy(data, 5, worldByte, 0, worldByte.length);
                                    byte gamemode = data[worldByte.length + 5];
                                    byte dimension = data[worldByte.length + 6];
                                    byte difficulty = data[worldByte.length + 7];
                                    Arrays.fill(data, (byte) 0);
                                    data[0] = 9;
                                    EntityMap.setInt(data, 1, dimension);
                                    data[5] = difficulty;
                                    data[6] = gamemode;
                                    data[7] = (byte)(256 & 0xff);
                                    data[8] = (byte)((256 >> 8) & 0xff);
                                    System.arraycopy(worldByte, 0, data, 9, worldByte.length);
                                    read = 9 + worldByte.length;
                                    byte[] trimData = new byte[read];
                                    System.arraycopy(data, 0, trimData, 0, read);
                                    data = trimData;
                                    if (conn.isOpen()) conn.send(new byte[] { 9, 0, 0, 0, 1, 0, 0, 1, 0, 0, 7, 0, 100, 0, 101, 0, 102, 0, 97, 0, 117, 0, 108, 0, 116 });
                                    if (conn.isOpen()) conn.send(new byte[] { 9, 0, 0, 0, -1, 0, 0, 1, 0, 0, 7, 0, 100, 0, 101, 0, 102, 0, 97, 0, 117, 0, 108, 0, 116 });
                                }
                                EntityMap.rewrite(data, selfClient.serverEntityId, selfClient.clientEntityId);
                                if (selfClient.authed || ((loginPacket || data[0] == 51 || data[0] == 13 || data[0] == 6) || !selfClient.hasLoginHappened)) {
                                    if (selfClient.authed) {
                                        while (selfClient.packetCache.size() > 0) {
                                            if (conn.isOpen()) conn.send(selfClient.packetCache.remove(0));
                                        }
                                    }
                                    if (conn.isOpen()) conn.send(data);
                                } else {
                                    // cache data
                                    selfClient.packetCache.add(data);
                                }
                                if (loginPacket) sendToServer(new byte[] { (byte) 250, 0, 8, 0, 82, 0, 69, 0, 71, 0, 73, 0, 83, 0, 84, 0, 69, 0, 82, 0, 10, 66, 117, 110, 103, 101, 101, 67, 111, 114, 100 }, selfClient);
                            }
                            if (conn.isOpen() && selfClient.server == currServer) conn.close();
                            if (!selfSocket.isClosed()) selfSocket.close();
                            selfClient.socketOut = null;
                            selfClient.firstTime = false;
                        }
                    } catch (IOException ex) {
                        conn.close();
                    }
                }).start();
                msg[1] = (byte) 61;
                selfClient.handshake = msg;
                Main.printMsg("Player " + selfClient + " joined!");
            } else {
                conn.close();
                return;
            }
        }
        Client currClient = Main.clients.get(conn);
        if (ChatHandler.clientChatMessage(currClient, msg)) return;
        if (PluginMessages.clientPluginMessage(currClient, msg)) return;
        if (!currClient.firstTime && !currClient.hasLoginHappened && !(msg[0] == 1 || msg[0] == 2)) return;
        if (currClient.socketOut == null || currClient.socket.isOutputShutdown()) {
            currClient.msgCache.add(msg);
        } else {
            try {
                sendToServer(msg, currClient);
            } catch (IOException ignored) {}
        }
    }

    public void sendToServer(byte[] orig, Client client) throws IOException {
        if (!client.authed) {
            if (orig.length > 0) {
                if (orig[0] == 2 || orig[0] == 3) client.conn.send(new byte[] { 3, 0, 114, 0, (byte) 167, 0, 57, 0, 80, 0, 108, 0, 101, 0, 97, 0, 115, 0, 101, 0, 32, 0, 114, 0, 101, 0, 103, 0, 105, 0, 115, 0, 116, 0, 101, 0, 114, 0, 32, 0, 111, 0, 114, 0, 32, 0, 108, 0, 111, 0, 103, 0, 105, 0, 110, 0, 32, 0, 116, 0, 111, 0, 32, 0, 99, 0, 111, 0, 110, 0, 116, 0, 105, 0, 110, 0, 117, 0, 101, 0, 32, 0, 116, 0, 111, 0, 32, 0, 116, 0, 104, 0, 105, 0, 115, 0, 32, 0, 115, 0, 101, 0, 114, 0, 118, 0, 101, 0, 114, 0, 33, 0, 32, 0, 47, 0, 114, 0, 101, 0, 103, 0, 105, 0, 115, 0, 116, 0, 101, 0, 114, 0, 32, 0, 60, 0, 112, 0, 97, 0, 115, 0, 115, 0, 119, 0, 111, 0, 114, 0, 100, 0, 62, 0, 32, 0, 60, 0, 99, 0, 111, 0, 110, 0, 102, 0, 105, 0, 114, 0, 109, 0, 80, 0, 97, 0, 115, 0, 115, 0, 119, 0, 111, 0, 114, 0, 100, 0, 62, 0, 32, 0, 111, 0, 114, 0, 32, 0, 47, 0, 108, 0, 111, 0, 103, 0, 105, 0, 110, 0, 32, 0, 60, 0, 112, 0, 97, 0, 115, 0, 115, 0, 119, 0, 111, 0, 114, 0, 100, 0, 62 });
                if (orig[0] == 10 || orig[0] == 11 || orig[0] == 12 || orig[0] == 13 || orig[0] == 14 || orig[0] == 15) client.conn.send(client.positionPacket);
            }
            if (client.hasLoginHappened) return; // drop client packets :trol:
        }
        byte[] data = orig.clone();
        EntityMap.rewrite(data, client.clientEntityId, client.serverEntityId);
        client.socketOut.write(data);
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        //
    }

    @Override
    public void onStart() {
        setConnectionLostTimeout(0);
    }
}