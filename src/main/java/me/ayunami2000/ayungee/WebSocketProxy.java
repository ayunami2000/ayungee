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
    public void onMessage(WebSocket conn, ByteBuffer message) {
        if (Main.bans.contains(Main.getIp(conn))) {
            conn.close();
            return;
        }
        if (!conn.isOpen()) return;
        byte[] msg = message.array();
        if (!Main.clients.containsKey(conn)) {
            if (msg.length > 3 && msg[1] == (byte) 69) {
                if (msg[3] < 3 || msg[3] > 16) {
                    conn.close();
                    return;
                }
                byte[] uname = new byte[msg[3]];
                if (msg.length < 5 + msg[3] * 2) {
                    conn.close();
                    return;
                }
                for (int i = 0; i < uname.length; i++) uname[i] = msg[5 + i * 2];
                String username = new String(uname);
                Main.clients.put(conn, new Client(username));
                new Thread(() -> {
                    try {
                        Socket selfSocket = new Socket(Main.hostname, Main.port);
                        Client selfClient = Main.clients.get(conn);
                        selfClient.setSocket(selfSocket);
                        while (selfClient.msgCache.size() > 0) selfClient.socketOut.write(selfClient.msgCache.remove(0));
                        while (conn.isOpen() && !selfSocket.isInputShutdown()) {
                            byte[] data = new byte[maxBuffSize];
                            int read = selfClient.socketIn.read(data, 0, maxBuffSize);
                            if (read == maxBuffSize) {
                                if (conn.isOpen()) conn.send(data);
                            } else if (read > 0) {
                                byte[] trueData = new byte[read];
                                System.arraycopy(data, 0, trueData, 0, read);
                                if (conn.isOpen()) conn.send(trueData);
                            }
                        }
                        if (conn.isOpen()) conn.close();
                        if (!selfSocket.isClosed()) selfSocket.close();
                    } catch (IOException ex) {
                        conn.close();
                    }
                }).start();
                msg[1] = (byte) 61;
                System.out.println("Player " + username + " (" + Main.getIp(conn) + ") joined!");
            } else {
                conn.close();
                return;
            }
        }
        byte[] packet = message.array();
        if (!Main.eaglerPackets && packet.length >= 11 && packet[0] == -6 && packet[2] >= 4 && packet[4] == 69 && packet[6] == 65 && packet[8] == 71 && packet[10] == 124) return; // EAG|
        Client currClient = Main.clients.get(conn);
        if (currClient.socketOut == null) {
            currClient.msgCache.add(packet);
        } else if (!currClient.socket.isOutputShutdown()) {
            try {
                currClient.socketOut.write(packet);
            } catch (IOException ignored) {}
        }
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