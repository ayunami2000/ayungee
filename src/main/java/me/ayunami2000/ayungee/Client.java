package me.ayunami2000.ayungee;

import org.java_websocket.WebSocket;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class Client {
    public Socket socket = null;
    public OutputStream socketOut = null;
    public InputStream socketIn = null;

    public List<byte[]> msgCache =new ArrayList<>();

    public WebSocket conn;

    public String username;

    public int server = 0;

    public byte[] handshake = null;

    public boolean firstTime = true;

    public boolean hasLoginHappened = false;

    public boolean authed = !Main.useAuth;

    public int clientEntityId;
    public int serverEntityId;

    public List<byte[]> packetCache = new ArrayList<>();
    public byte[] positionPacket = null;

    public void setSocket(Socket sock) throws IOException {
        socket = sock;
        socketOut = sock.getOutputStream();
        socketIn = sock.getInputStream();
    }

    public Client(WebSocket c, String uname) {
        conn = c;
        username = uname;
    }

    public String toString() {
        return username + " (" + Main.getIp(conn) + ")";
    }
}
