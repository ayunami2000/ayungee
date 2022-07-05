package me.ayunami2000.ayungee;

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

    public String username;

    public void setSocket(Socket sock) throws IOException {
        socket = sock;
        socketOut = sock.getOutputStream();
        socketIn = socket.getInputStream();
    }

    public Client(String uname) {
        username = uname;
    }
}
