package me.ayunami2000.ayungee;

import org.java_websocket.WebSocket;

import java.io.*;
import java.util.*;

public class Voice {
    private static final Map<String, WebSocket> voicePlayers = new HashMap<>();
    private static final Map<String, ExpiringSet<String>> voiceRequests = new HashMap<>();
    private static final Set<String[]> voicePairs = new HashSet<>();

    private static final int VOICE_SIGNAL_ALLOWED = 0;
    private static final int VOICE_SIGNAL_REQUEST = 0;
    private static final int VOICE_SIGNAL_CONNECT = 1;
    private static final int VOICE_SIGNAL_DISCONNECT = 2;
    private static final int VOICE_SIGNAL_ICE = 3;
    private static final int VOICE_SIGNAL_DESC = 4;
    private static final int VOICE_SIGNAL_GLOBAL = 5;

    public static boolean handleVoice(String user, WebSocket connection, String tag, byte[] msg) {
        synchronized (voicePlayers) {
            if (!Main.voiceEnabled) return false;
            if (!("EAG|Voice".equals(tag))) return false;
            try {
                if (msg.length == 0) return true;
                DataInputStream streamIn = new DataInputStream(new ByteArrayInputStream(msg));
                int sig = streamIn.read();
                switch (sig) {
                    case VOICE_SIGNAL_CONNECT:
                        if (voicePlayers.containsKey(user)) return true; // user is already using voice chat
                        // send out packet for player joined voice
                        // notice: everyone on the server can see this packet!! however, it doesn't do anything but let clients know that the player has turned on voice chat
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        DataOutputStream dos = new DataOutputStream(baos);
                        dos.write(VOICE_SIGNAL_CONNECT);
                        dos.writeUTF(user);
                        byte[] out = baos.toByteArray();
                        for (WebSocket conn : voicePlayers.values()) sendVoicePacket(conn, out);
                        voicePlayers.put(user, connection);
                        for (String username : voicePlayers.keySet()) sendVoicePlayers(username);
                        break;
                    case VOICE_SIGNAL_DISCONNECT:
                        if (!voicePlayers.containsKey(user)) return true; // user is not using voice chat
                        try {
                            String user2 = streamIn.readUTF();
                            if (!voicePlayers.containsKey(user2)) return true;
                            if (voicePairs.removeIf(pair -> (pair[0].equals(user) && pair[1].equals(user2)) || (pair[0].equals(user2) && pair[1].equals(user)))) {
                                ByteArrayOutputStream baos2 = new ByteArrayOutputStream();
                                DataOutputStream dos2 = new DataOutputStream(baos2);
                                dos2.write(VOICE_SIGNAL_DISCONNECT);
                                dos2.writeUTF(user);
                                sendVoicePacket(voicePlayers.get(user2), baos2.toByteArray());
                                baos2 = new ByteArrayOutputStream();
                                dos2 = new DataOutputStream(baos2);
                                dos2.write(VOICE_SIGNAL_DISCONNECT);
                                dos2.writeUTF(user2);
                                sendVoicePacket(connection, baos2.toByteArray());
                            }
                        } catch (EOFException e) {
                            removeUser(user);
                        }
                        break;
                    case VOICE_SIGNAL_REQUEST:
                        if (!voicePlayers.containsKey(user)) return true; // user is not using voice chat
                        String targetUser = streamIn.readUTF();
                        if (user.equals(targetUser)) return true; // prevent duplicates
                        if (checkVoicePair(user, targetUser)) return true; // already paired
                        if (!voicePlayers.containsKey(targetUser)) return true; // target user is not using voice chat
                        if (!voiceRequests.containsKey(user)) voiceRequests.put(user, new ExpiringSet<>(2000));
                        if (voiceRequests.get(user).contains(targetUser)) return true;
                        voiceRequests.get(user).add(targetUser);

                        // check if other has requested earlier
                        if (voiceRequests.containsKey(targetUser) && voiceRequests.get(targetUser).contains(user)) {
                            if (voiceRequests.containsKey(targetUser)) {
                                voiceRequests.get(targetUser).remove(user);
                                if (voiceRequests.get(targetUser).isEmpty()) voiceRequests.remove(targetUser);
                            }
                            if (voiceRequests.containsKey(user)) {
                                voiceRequests.get(user).remove(targetUser);
                                if (voiceRequests.get(user).isEmpty()) voiceRequests.remove(user);
                            }
                            // send each other add data
                            voicePairs.add(new String[]{user, targetUser});
                            ByteArrayOutputStream baos2 = new ByteArrayOutputStream();
                            DataOutputStream dos2 = new DataOutputStream(baos2);
                            dos2.write(VOICE_SIGNAL_CONNECT);
                            dos2.writeUTF(user);
                            dos2.writeBoolean(false);
                            sendVoicePacket(voicePlayers.get(targetUser), baos2.toByteArray());
                            baos2 = new ByteArrayOutputStream();
                            dos2 = new DataOutputStream(baos2);
                            dos2.write(VOICE_SIGNAL_CONNECT);
                            dos2.writeUTF(targetUser);
                            dos2.writeBoolean(true);
                            sendVoicePacket(connection, baos2.toByteArray());
                        }
                        break;
                    case VOICE_SIGNAL_ICE:
                    case VOICE_SIGNAL_DESC:
                        if (!voicePlayers.containsKey(user)) return true; // user is not using voice chat
                        String targetUser2 = streamIn.readUTF();
                        if (checkVoicePair(user, targetUser2)) {
                            String data = streamIn.readUTF();
                            ByteArrayOutputStream baos2 = new ByteArrayOutputStream();
                            DataOutputStream dos2 = new DataOutputStream(baos2);
                            dos2.write(sig);
                            dos2.writeUTF(user);
                            dos2.writeUTF(data);
                            sendVoicePacket(voicePlayers.get(targetUser2), baos2.toByteArray());
                        }
                        break;
                    default:
                        break;
                }
            } catch (Throwable t) {
                // hacker
                // t.printStackTrace(); // todo: remove in production
                removeUser(user);
            }
        }
        return true;
    }

    public static void onLogin(String username, WebSocket conn) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            dos.write(VOICE_SIGNAL_ALLOWED);
            dos.writeBoolean(Main.voiceEnabled);
            dos.write(Main.voiceICE.size());
            for(String str : Main.voiceICE) {
                dos.writeUTF(str);
            }
            sendVoicePacket(conn, baos.toByteArray());
            sendVoicePlayers(username);
        } catch (IOException ignored) {  }
    }

    public static void onQuit(String username) {
        removeUser(username);
    }

    private static void sendVoicePlayers(String name) {
        synchronized (voicePlayers) {
            if (!Main.voiceEnabled) return;
            if (!voicePlayers.containsKey(name)) return;
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                DataOutputStream dos = new DataOutputStream(baos);
                dos.write(VOICE_SIGNAL_GLOBAL);
                Set<String> mostlyGlobalPlayers = new HashSet<>();
                for (String username : voicePlayers.keySet()) {
                    if (username.equals(name)) continue;
                    if (voicePairs.stream().anyMatch(pair -> (pair[0].equals(name) && pair[1].equals(username)) || (pair[0].equals(username) && pair[1].equals(name))))
                        continue;
                    mostlyGlobalPlayers.add(username);
                }
                if (mostlyGlobalPlayers.size() > 0) {
                    dos.writeInt(mostlyGlobalPlayers.size());
                    for (String username : mostlyGlobalPlayers) dos.writeUTF(username);
                    sendVoicePacket(voicePlayers.get(name), baos.toByteArray());
                }
            } catch (IOException ignored) {
            }
        }
    }

    private static void removeUser(String name) {
        synchronized (voicePlayers) {
            voicePlayers.remove(name);
            for (String username : voicePlayers.keySet()) {
                if (!name.equals(username)) sendVoicePlayers(username);
            }
            for (String[] voicePair : voicePairs) {
                String target = null;
                if (voicePair[0].equals(name)) {
                    target = voicePair[1];
                } else if (voicePair[1].equals(name)) {
                    target = voicePair[0];
                }
                if (target != null && voicePlayers.containsKey(target)) {
                    try {
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        DataOutputStream dos = new DataOutputStream(baos);
                        dos.write(VOICE_SIGNAL_DISCONNECT);
                        dos.writeUTF(name);
                        sendVoicePacket(voicePlayers.get(target), baos.toByteArray());
                    } catch (IOException ignored) {
                    }
                }
            }
            voicePairs.removeIf(pair -> pair[0].equals(name) || pair[1].equals(name));
        }
    }

    private static boolean checkVoicePair(String user1, String user2) {
        return voicePairs.stream().anyMatch(pair -> (pair[0].equals(user1) && pair[1].equals(user2)) || (pair[0].equals(user2) && pair[1].equals(user1)));
    }

    private static void sendVoicePacket(WebSocket conn, byte[] msg) {
        byte[] packetPrefix = new byte[] { (byte) 250, 0, 9, 0, 69, 0, 65, 0, 71, 0, 124, 0, 86, 0, 111, 0, 105, 0, 99, 0, 101, (byte) ((msg.length >>> 8) & 0xFF), (byte) (msg.length & 0xFF) };
        byte[] fullPacket = new byte[packetPrefix.length + msg.length];
        System.arraycopy(packetPrefix, 0, fullPacket, 0, packetPrefix.length);
        System.arraycopy(msg, 0, fullPacket, packetPrefix.length, msg.length);
        conn.send(fullPacket);
    }
}