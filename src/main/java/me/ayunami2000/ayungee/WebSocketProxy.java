package me.ayunami2000.ayungee;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
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
                Thread.sleep(5000);
            } catch (InterruptedException ignored) {}
            if (conn.isOpen() && (Main.bans.contains(Main.getIp(conn)) || !Main.clients.containsKey(conn))) conn.close();
        }).start();
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        Client selfClient = Main.clients.remove(conn);
        if (selfClient != null) {
            System.out.println("Player " + selfClient.username + " (" + Main.getIp(conn) + ") left!");
            Skins.removeSkin(selfClient.username);
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
            if (msg.length > 3 && msg[1] == (byte) 69) {
                // todo: it uses shorts dumbass, get with the system
                short unameLen = (short) ((msg[2] << 8) + msg[3] & 0xff);
                if (unameLen < 3 || unameLen > 16) {
                    conn.close();
                    return;
                }
                if (msg.length < 5 + msg[3] * 2) {
                    conn.close();
                    return;
                }
                byte[] uname = new byte[unameLen];
                for (int i = 0; i < uname.length; i++) uname[i] = msg[5 + i * 2];
                String username = new String(uname);
                Client selfClient = new Client(username);
                Main.clients.put(conn, selfClient);
                new Thread(() -> {
                    try {
                        while (conn.isOpen()) {
                            int currServer = selfClient.server;
                            selfClient.hasLoginHappened = false;
                            ServerItem chosenServer = Main.servers.get(currServer);
                            Socket selfSocket = new Socket(chosenServer.host, chosenServer.port);
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
                                if (data[0] == 1 && !selfClient.hasLoginHappened) selfClient.hasLoginHappened = true;
                                if (selfClient.firstTime && data[0] == 1) selfClient.clientEntityId = selfClient.serverEntityId = EntityMap.readInt(data, 1);
                                if (!selfClient.firstTime && data[0] == 1) {
                                    selfClient.serverEntityId = EntityMap.readInt(data, 1);
                                    // assume server is giving valid data; we don't have to validate it because it isn't a potentially malicious client
                                    byte[] worldByte = new byte[data[6] * 2 + 2];
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
                                if (conn.isOpen()) conn.send(data);
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
                System.out.println("Player " + username + " (" + Main.getIp(conn) + ") joined!");
            } else {
                conn.close();
                return;
            }
        }
        Client currClient = Main.clients.get(conn);
        if (msg.length >= 3 && msg[0] == 3) {
            int msgLen = (short) ((msg[1] << 8) + msg[2] & 0xff);
            if (msgLen != 0) {
                if (msg.length >= 3 + msgLen * 2) {
                    byte[] chatBytes = new byte[msgLen];
                    for (int i = 0; i < chatBytes.length; i++) chatBytes[i] = msg[4 + i * 2];
                    String chatMsg = new String(chatBytes);
                    if (chatMsg.toLowerCase().startsWith("/server")) {
                        String msgArgs = chatMsg.substring(7 + (chatMsg.contains(" ") ? 1 : 0));
                        if (msgArgs.isEmpty()) {
                            //usage msg
                            conn.send(new byte[] { 3, 0, 25, 0, (byte) 167, 0, 57, 0, 85, 0, 115, 0, 97, 0, 103, 0, 101, 0, 58, 0, 32, 0, 47, 0, 115, 0, 101, 0, 114, 0, 118, 0, 101, 0, 114, 0, 32, 0, 60, 0, 110, 0, 117, 0, 109, 0, 98, 0, 101, 0, 114, 0, 62 });
                        } else {
                            try {
                                int destServer = Integer.parseInt(msgArgs);
                                currClient.server = Math.max(0, Math.min(Main.servers.size() - 1, destServer));
                            } catch (NumberFormatException e) {
                                //not a number
                                conn.send(new byte[] { 3, 0, 29, 0, (byte) 167, 0, 57, 0, 84, 0, 104, 0, 97, 0, 116, 0, 32, 0, 105, 0, 115, 0, 32, 0, 110, 0, 111, 0, 116, 0, 32, 0, 97, 0, 32, 0, 118, 0, 97, 0, 108, 0, 105, 0, 100, 0, 32, 0, 110, 0, 117, 0, 109, 0, 98, 0, 101, 0, 114, 0, 33 });
                            }
                        }
                        return; // don't send to underlying server
                    }
                }
            }
        }
        if (msg.length > 0 && msg[0] == (byte) 250) {
            if (Skins.setSkin(currClient.username, conn, msg)) return;
        }
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