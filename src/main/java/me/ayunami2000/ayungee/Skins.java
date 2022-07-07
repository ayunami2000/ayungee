package me.ayunami2000.ayungee;

import org.java_websocket.WebSocket;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

public class Skins {
    private static final HashMap<String,byte[]> skinCollection = new HashMap();
    private static final HashMap<String,byte[]> capeCollection = new HashMap();
    private static final HashMap<String,Long> lastSkinLayerUpdate = new HashMap();

    private static final int[] SKIN_DATA_SIZE = new int[] { 64*32*4, 64*64*4, -9, -9, 1, 64*64*4, -9 }; // 128 pixel skins crash clients
    private static final int[] CAPE_DATA_SIZE = new int[] { 32*32*4, -9, 1 };

    public static boolean setSkin(String user, WebSocket conn, String tag, byte[] msg) {
        if(msg.length >= 3) {
            try {
                if("EAG|MySkin".equals(tag)) {
                    if(!skinCollection.containsKey(user)) {
                        int t = (int)msg[0] & 0xFF;
                        if(t >= 0 && t < SKIN_DATA_SIZE.length && msg.length == (SKIN_DATA_SIZE[t] + 1)) {
                            skinCollection.put(user, msg);
                        }
                    }
                }else if("EAG|MyCape".equals(tag)) {
                    if(!capeCollection.containsKey(user)) {
                        int t = (int)msg[0] & 0xFF;
                        if(t >= 0 && t < CAPE_DATA_SIZE.length && msg.length == (CAPE_DATA_SIZE[t] + 2)) {
                            capeCollection.put(user, msg);
                        }
                    }
                }else if("EAG|FetchSkin".equals(tag)) {
                    if(msg.length > 2) {
                        String fetch = new String(msg, 2, msg.length - 2, StandardCharsets.UTF_8);
                        byte[] data;
                        if((data = skinCollection.get(fetch)) != null) {
                            byte[] conc = new byte[data.length + 2];
                            conc[0] = msg[0]; conc[1] = msg[1]; //synchronization cookie
                            System.arraycopy(data, 0, conc, 2, data.length);
                            if((data = capeCollection.get(fetch)) != null) {
                                byte[] conc2 = new byte[conc.length + data.length];
                                System.arraycopy(conc, 0, conc2, 0, conc.length);
                                System.arraycopy(data, 0, conc2, conc.length, data.length);
                                conc = conc2;
                            }
                            byte[] packetPrefix = new byte[] { (byte) 250, 0, 12, 0, 69, 0, 65, 0, 71, 0, 124, 0, 85, 0, 115, 0, 101, 0, 114, 0, 83, 0, 107, 0, 105, 0, 110, (byte) ((conc.length >>> 8) & 0xFF), (byte) (conc.length & 0xFF) };
                            byte[] fullPacket = new byte[packetPrefix.length + conc.length];
                            System.arraycopy(packetPrefix, 0, fullPacket, 0, packetPrefix.length);
                            System.arraycopy(conc, 0, fullPacket, packetPrefix.length, conc.length);
                            conn.send(fullPacket);
                        }
                    }
                }else if("EAG|SkinLayers".equals(tag)) {
                    long millis = System.currentTimeMillis();
                    Long lsu = lastSkinLayerUpdate.get(user);
                    if(lsu != null && millis - lsu.longValue() < 700l) { // DoS protection
                        return false;
                    }
                    lastSkinLayerUpdate.put(user, millis);
                    byte[] data;
                    if((data = capeCollection.get(user)) != null) {
                        data[1] = msg[0];
                    }else {
                        data = new byte[] { (byte)2, msg[0], (byte)0 };
                        capeCollection.put(user, data);
                    }
                    ByteArrayOutputStream bao = new ByteArrayOutputStream();
                    DataOutputStream dd = new DataOutputStream(bao);
                    dd.write(msg[0]);
                    dd.writeUTF(user);
                    byte[] bpacket = bao.toByteArray();
                    byte[] packetPrefix = new byte[] { (byte) 250, 0, 14, 0, 69, 0, 65, 0, 71, 0, 124, 0, 83, 0, 107, 0, 105, 0, 110, 0, 76, 0, 97, 0, 121, 0, 101, 0, 114, 0, 115, (byte) ((bpacket.length >>> 8) & 0xFF), (byte) (bpacket.length & 0xFF) };
                    int off = bpacket.length == 0 ? 2 : 0;
                    byte[] fullPacket = new byte[(packetPrefix.length - off) + bpacket.length];
                    System.arraycopy(packetPrefix, 0, fullPacket, 0, packetPrefix.length - off);
                    if (bpacket.length != 0) System.arraycopy(bpacket, 0, fullPacket, packetPrefix.length, bpacket.length);
                    for (WebSocket pl : Main.clients.keySet()) {
                        if (pl.equals(conn)) continue;
                        if (pl.isOpen()) pl.send(fullPacket);
                    }
                } else {
                    return false;
                }
            }catch(Throwable t) {
                // hacker
            }
        } else {
            return false;
        }

        return true;
    }

    public static void removeSkin(String username) {
        skinCollection.remove(username);
        capeCollection.remove(username);
        lastSkinLayerUpdate.remove(username);
    }
}