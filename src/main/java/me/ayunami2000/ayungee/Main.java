package me.ayunami2000.ayungee;

import org.java_websocket.WebSocket;
import org.java_websocket.server.WebSocketServer;
import org.json.simple.JSONObject;
import org.yaml.snakeyaml.Yaml;

import javax.imageio.ImageIO;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Main {
    public static String hostname = "localhost";
    public static int port = 25569;
    public static int webPort = 25565;

    public static String motdJson = "";
    public static byte[] serverIcon = null;

    public static boolean forwarded = false;

    public static boolean eaglerPackets = false;

    public static WebSocketServer webSocketServer = null;

    public static Map<WebSocket, Client> clients = new HashMap<>();

    public static Set<String> bans = new HashSet<>();
    public static Set<Pattern> originBlacklist = new HashSet<>();
    public static Set<String> originWhitelist = new HashSet<>();

    public static void main(String[] args) throws IOException, InterruptedException {
        Yaml yaml = new Yaml();

        Map<String, Object> config;

        try {
            config = yaml.load(new FileReader("config.yml"));
        } catch (FileNotFoundException e) {
            Files.copy(Main.class.getResourceAsStream("/config.yml"), Paths.get("config.yml"), StandardCopyOption.REPLACE_EXISTING);
            config = yaml.load(new FileReader("config.yml"));
        }

        File iconFile = new File("icon.png");
        if (!iconFile.exists()) Files.copy(Main.class.getResourceAsStream("/icon.png"), Paths.get("icon.png"), StandardCopyOption.REPLACE_EXISTING);
        int[] serverIconInt = ServerIcon.createServerIcon(ImageIO.read(iconFile));
        byte[] iconPixels = new byte[16384];
        for(int i = 0; i < 4096; ++i) {
            iconPixels[i * 4] = (byte)((serverIconInt[i] >> 16) & 0xFF);
            iconPixels[i * 4 + 1] = (byte)((serverIconInt[i] >> 8) & 0xFF);
            iconPixels[i * 4 + 2] = (byte)(serverIconInt[i] & 0xFF);
            iconPixels[i * 4 + 3] = (byte)((serverIconInt[i] >> 24) & 0xFF);
        }
        serverIcon = iconPixels;


        try (BufferedReader br = new BufferedReader(new FileReader("bans.txt"))) {
            String line;
            while ((line = br.readLine()) != null) bans.add(line);
        } catch (FileNotFoundException e) {
            saveBans();
        }

        originWhitelist = new HashSet<>((List<String>) config.getOrDefault("origins", new ArrayList<>()));

        String originBlacklistUrl = (String) config.getOrDefault("origin_blacklist", "https://g.eags.us/eaglercraft/origin_blacklist.txt");
        if (originBlacklistUrl.isEmpty()) {
            new Thread(() -> {
                while (true) {
                    readUrlBlacklist();
                    try {
                        Thread.sleep(300000);
                    } catch (InterruptedException ignored) {}
                }
            }).start();
        } else {
            URL blacklistUrl = new URL(originBlacklistUrl);
            new Thread(() -> {
                while (true) {
                    try {
                        URLConnection cc = blacklistUrl.openConnection();
                        if (cc instanceof HttpURLConnection) {
                            HttpURLConnection ccc = (HttpURLConnection)cc;
                            ccc.setRequestProperty("Accept", "text/plain,text/html,application/xhtml+xml,application/xml");
                            ccc.setRequestProperty("User-Agent", "Mozilla/5.0 ayungee");
                        }
                        cc.connect();
                        Files.copy(cc.getInputStream(), Paths.get("origin_blacklist.txt"), StandardCopyOption.REPLACE_EXISTING);
                        readUrlBlacklist();
                    } catch (IOException e) {
                        System.out.println("An error occurred attempting to update the origin blacklist!");
                    }
                    try {
                        Thread.sleep(300000);
                    } catch (InterruptedException ignored) {
                    }
                }
            }).start();
        }


        hostname = (String) config.getOrDefault("hostname", "localhost");
        port = (int) config.getOrDefault("port", 25569);
        webPort = (int) config.getOrDefault("web_port", 25565);
        forwarded = (boolean) config.getOrDefault("forwarded", false);
        eaglerPackets = (boolean) config.getOrDefault("eag_packets", false);

        List<String> defaultMotd = new ArrayList<>();

        defaultMotd.add("Welcome to my");
        defaultMotd.add("ayungee-powered server!");

        List<String> defaultPlayersMotd = new ArrayList<>();

        defaultPlayersMotd.add("whar?");

        Map<String, Object> defaultConfigMotd = new HashMap<>();

        defaultConfigMotd.put("lines", defaultMotd);
        defaultConfigMotd.put("max", 20);
        defaultConfigMotd.put("online", 4);
        defaultConfigMotd.put("name", "An ayungee-powered Eaglercraft server");
        defaultConfigMotd.put("players", defaultPlayersMotd);

        Map<String, Object> configMotd = (LinkedHashMap<String, Object>) config.getOrDefault("motd", defaultConfigMotd);

        JSONObject motdObj = new JSONObject();
        JSONObject motdObjObj = new JSONObject();

        motdObjObj.put("motd", configMotd.getOrDefault("lines", defaultMotd));
        motdObjObj.put("cache", true);
        motdObjObj.put("max", configMotd.get("max"));
        motdObjObj.put("icon", true);
        motdObjObj.put("online", configMotd.get("online"));
        motdObjObj.put("players", configMotd.getOrDefault("players", defaultPlayersMotd));

        motdObj.put("data", motdObjObj);
        motdObj.put("vers", "0.2.0");
        motdObj.put("name", configMotd.get("name"));
        motdObj.put("time", Instant.now().toEpochMilli());
        motdObj.put("type", "motd");
        motdObj.put("brand", "Eagtek");
        motdObj.put("uuid", UUID.randomUUID().toString());
        motdObj.put("cracked", true);

        motdJson = motdObj.toJSONString();

        webSocketServer = new WebSocketProxy(webPort);
        webSocketServer.start();

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        boolean running = true;
        System.out.println("ayungee started!");
        while (running) {
            //System.out.print("> ");
            String cmd = reader.readLine();
            String[] pieces = cmd.split(" ");
            pieces[0] = pieces[0].toLowerCase();
            switch (pieces[0]) {
                case "help":
                case "?":
                    System.out.println("help ; unban <ip> ; banip <ip> ; ban <username> ; stop");
                    break;
                case "unban":
                case "pardon":
                case "unban-ip":
                case "unbanip":
                case "pardon-ip":
                case "pardonip":
                    if (pieces.length == 1) {
                        System.out.println("Usage: " + pieces[0] + " <ip>");
                        break;
                    }
                    if (bans.remove(pieces[1])) {
                        System.out.println("Successfully unbanned IP " + pieces[1]);
                        saveBans();
                    } else {
                        System.out.println("IP " + pieces[1] + " is not banned!");
                    }
                    break;
                case "ban":
                    if (pieces.length == 1) {
                        System.out.println("Usage: " + pieces[0] + " <username>");
                        break;
                    }
                    //there should NEVER be duplicate usernames...
                    Client[] targetClients = clients.values().stream().filter(client -> client.username.equals(pieces[1])).toArray(Client[]::new);
                    if (targetClients.length == 0) targetClients = clients.values().stream().filter(client -> client.username.equalsIgnoreCase(pieces[1])).toArray(Client[]::new);
                    if (targetClients.length == 0) {
                        System.out.println("Unable to find any user with that username! (note: they must be online)");
                        break;
                    }
                    for (Client targetClient : targetClients) {
                        WebSocket targetWebSocket = getKeysByValue(clients, targetClient).stream().findFirst().orElse(null);
                        if (targetWebSocket == null) {
                            System.out.println("An internal error occurred which should never happen! Oops...");
                            return;
                        }
                        String ipToBan = getIp(targetWebSocket);
                        if (bans.add(ipToBan)) {
                            System.out.println("Successfully banned user " + targetClient.username + " with IP " + ipToBan);
                            try {
                                saveBans();
                            } catch (IOException ignored) {}
                        } else {
                            System.out.println("IP " + ipToBan + " is already banned!");
                        }
                    }
                    break;
                case "ban-ip":
                case "banip":
                    if (pieces.length == 1) {
                        System.out.println("Usage: " + pieces[0] + " <ip>");
                        break;
                    }
                    if (bans.add(pieces[1])) {
                        System.out.println("Successfully banned IP " + pieces[1]);
                        saveBans();
                    } else {
                        System.out.println("IP " + pieces[1] + " is already banned!");
                    }
                    break;
                case "stop":
                case "end":
                case "exit":
                case "quit":
                    System.out.println("Stopping!");
                    running = false;
                    webSocketServer.stop(10);
                    System.exit(0);
                    break;
                default:
                    System.out.println("Command not found!");
            }
        }
    }

    public static String getIp(WebSocket conn) {
        return conn.getAttachment();
    }

    // https://stackoverflow.com/a/2904266/6917520

    public static <T, E> Set<T> getKeysByValue(Map<T, E> map, E value) {
        return map.entrySet()
                .stream()
                .filter(entry -> Objects.equals(entry.getValue(), value))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    private static void saveBans() throws IOException {
        BufferedWriter writer = new BufferedWriter(new FileWriter("bans.txt"));
        writer.write(String.join("\n", bans));
        writer.close();
    }

    private static void readUrlBlacklist() {
        try {
            try (BufferedReader br = new BufferedReader(new FileReader("origin_blacklist.txt"))) {
                originBlacklist.clear();
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.startsWith("#")) continue;
                    if (line.trim().isEmpty()) continue;
                    originBlacklist.add(Pattern.compile(line, Pattern.CASE_INSENSITIVE));
                }
            } catch (FileNotFoundException e) {
                new File("origin_blacklist.txt").createNewFile();
            }
        } catch (IOException ignored) {}
    }
}
