package me.ayunami2000.ayungee;

public class ServerItem {
    public String host;
    public int port = 25565;

    public ServerItem(String h, int p) {
        host = h;
        port = p;
    }

    public ServerItem(String hp) {
        String[] pieces = hp.split(":");
        if (pieces.length > 1) port = Integer.parseInt(pieces[1]);
        host = pieces[0];
    }
}
