package me.ayunami2000.ayungee;

import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class Auth {
    private static class AuthData {
        public String passHash;
        public Set<String> ips;

        public AuthData(String p, Set<String> i) {
            passHash = p;
            ips = i;
        }
    }

    private static final Argon2 argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id);

    private static Map<String, AuthData> database = new HashMap<>();

    public static boolean register(String username, char[] password, String ip) {
        AuthData authData = database.get(username);
        if (authData != null) return false;
        if (isIpAtTheLimit(ip)) return false;
        String hash = argon2.hash(10, 65536, 1, password);
        Set<String> initIps = new HashSet<>();
        initIps.add(ip);
        database.put(username, new AuthData(hash, initIps));
        writeDatabase();
        return true;
        // todo: registering & packet cancellation
    }

    public static boolean isRegistered(String username) {
        return database.containsKey(username);
    }

    public static boolean changePass(String username, char[] password) {
        AuthData authData = database.get(username);
        authData.passHash = argon2.hash(10, 65536, 1, password);
        writeDatabase();
        return true;
    }

    public static boolean login(String username, char[] password) {
        AuthData authData = database.get(username);
        if (authData == null) return false;
        return argon2.verify(authData.passHash, password);
    }

    private static boolean isIpAtTheLimit(String ip) {
        if (Main.authIpLimit <= 0) return false;
        Map<String, AuthData> cache = new HashMap<>(database);
        int num = 0;
        for (AuthData authData : cache.values()) {
            if (authData.ips.contains(ip)) num++;
            if (num >= Main.authIpLimit) {
                cache.clear();
                return true;
            }
        }
        cache.clear();
        return false;
    }

    // only use once, on load
    public static void readDatabase() {
        try {
            File authFile = new File("auth.uwu");
            if (!authFile.exists()) authFile.createNewFile();

            Map<String, AuthData> cache = new HashMap<>();

            String[] lines = new String(Files.readAllBytes(authFile.toPath())).trim().split("\n");
            if (lines.length == 1 && lines[0].isEmpty()) return;
            for (String line : lines) {
                String[] pieces = line.split("\u0000");
                cache.put(pieces[0], new AuthData(pieces[2], new HashSet<>(Arrays.asList(pieces[1].split("§")))));
            }

            database.clear();
            database.putAll(cache);
            cache.clear();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void writeDatabase() {
        StringBuilder out = new StringBuilder();

        Map<String, AuthData> cache = new HashMap<>(database);

        for (String username : cache.keySet()) {
            AuthData entry = cache.get(username);
            out.append(username);
            out.append("\u0000");
            out.append(String.join("§", entry.ips));
            out.append("\u0000");
            out.append(entry.passHash);
            out.append("\n");
        }

        cache.clear();

        try {
            Files.write(Paths.get("auth.uwu"), out.toString().getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
