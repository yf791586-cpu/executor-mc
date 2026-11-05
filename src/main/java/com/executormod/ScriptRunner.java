package com.executormod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.jse.JsePlatform;

/** Menjalankan script Lua sebagai coroutine, satu langkah per tick client. */
public final class ScriptRunner {
    public static final String EXAMPLE_MOVE = """
            -- Contoh: jalan maju 3 detik, lompat, lalu berhenti
            print("Mulai jalan otomatis")
            player.forward(true)
            wait(3)
            player.jump(true)
            wait(0.2)
            player.jump(false)
            wait(2)
            player.stop()
            print("Selesai")
            """;

    private static final String EXAMPLE_SPIN = """
            -- Contoh: putar kamera 360 derajat pelan-pelan
            local yaw = player.yaw()
            for i = 1, 36 do
              player.look(yaw + i * 10, 0)
              wait(0.1)
            end
            print("Selesai muter")
            """;

    private static final String EXAMPLE_CHAT = """
            -- Contoh: kirim chat tiap 5 detik, 3 kali
            for i = 1, 3 do
              chat.send("halo dari executor " .. i)
              wait(5)
            end
            """;

    private static final String PRELUDE = """
            local co, sleep = nil, 0
            function __run(fn) co = coroutine.create(fn); sleep = 0 end
            function __stop() co = nil end
            function __tick()
              if co == nil then return false end
              if sleep > 0 then sleep = sleep - 1; return true end
              local ok, res = coroutine.resume(co)
              if not ok then co = nil; return false, tostring(res) end
              if coroutine.status(co) == "dead" then co = nil; return false end
              if type(res) == "number" and res > 1 then sleep = math.ceil(res) - 1 end
              return true
            end
            function waitTicks(n) coroutine.yield(n or 1) end
            function wait(sec) coroutine.yield(math.ceil((sec or 0.05) * 20)) end
            """;

    private static Globals globals;
    private static LuaValue tickFn;
    private static boolean running;
    private static int apiCalls;
    private static final Map<KeyBinding, Boolean> held = new LinkedHashMap<>();
    private static final ArrayDeque<String> logLines = new ArrayDeque<>();

    private ScriptRunner() {}

    public static Path scriptsDir() {
        return FabricLoader.getInstance().getConfigDir().resolve("executormod").resolve("scripts");
    }

    static void init() {
        try {
            Files.createDirectories(scriptsDir());
            writeIfMissing("jalan.lua", EXAMPLE_MOVE);
            writeIfMissing("muter.lua", EXAMPLE_SPIN);
            writeIfMissing("kirim_chat.lua", EXAMPLE_CHAT);
        } catch (IOException ignored) {
        }
    }

    private static void writeIfMissing(String name, String content) throws IOException {
        Path p = scriptsDir().resolve(name);
        if (!Files.exists(p)) Files.writeString(p, content);
    }

    public static List<Path> listScripts() {
        try (var s = Files.list(scriptsDir())) {
            return s.filter(p -> p.toString().endsWith(".lua")).sorted().toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    public static void save(String text) {
        try {
            Files.createDirectories(scriptsDir());
            Files.writeString(scriptsDir().resolve("saved.lua"), text);
            log("Tersimpan ke saved.lua");
        } catch (IOException e) {
            log("Gagal menyimpan: " + e.getMessage());
        }
    }

    public static boolean isRunning() {
        return running;
    }

    public static synchronized void start(String code) {
        stop(null);
        try {
            globals = JsePlatform.standardGlobals();
            for (String n : new String[] {"os", "io", "luajava", "debug", "require", "package", "dofile", "loadfile"}) {
                globals.set(n, LuaValue.NIL);
            }
            LuaApi.install(globals);
            globals.load(PRELUDE, "prelude").call();
            tickFn = globals.get("__tick");
            LuaValue chunk = globals.load(code, "script");
            globals.get("__run").call(chunk);
            apiCalls = 0;
            running = true;
            log("Script berjalan...");
        } catch (LuaError e) {
            running = false;
            log("Error: " + clean(e.getMessage()));
        }
    }

    public static synchronized void stop(String reason) {
        if (running) {
            running = false;
            if (reason != null) log(reason);
        }
        releaseKeys();
    }

    static void hold(KeyBinding key, boolean down) {
        held.put(key, down);
        key.setPressed(down);
    }

    static void releaseKeys() {
        for (KeyBinding k : held.keySet()) {
            k.setPressed(false);
        }
        held.clear();
    }

    /** Dipanggil tiap API; mencegah loop tanpa wait() membekukan game. */
    static void guard() {
        if (++apiCalls > 3000) {
            throw new LuaError("terlalu banyak perintah tanpa wait(); tambahkan wait() di dalam loop");
        }
    }

    static void tick(MinecraftClient client) {
        if (!running) return;
        if (client.player == null || client.world == null) {
            stop("Keluar dari dunia, script berhenti");
            return;
        }
        apiCalls = 0;
        try {
            Varargs r = tickFn.invoke(LuaValue.NONE);
            if (!r.arg1().toboolean()) {
                running = false;
                LuaValue err = r.arg(2);
                if (!err.isnil()) log("Error: " + clean(err.tojstring()));
                else log("Script selesai");
                releaseKeys();
                return;
            }
            for (Map.Entry<KeyBinding, Boolean> e : held.entrySet()) {
                e.getKey().setPressed(e.getValue());
            }
        } catch (Exception e) {
            stop("Error: " + clean(String.valueOf(e.getMessage())));
        }
    }

    private static String clean(String s) {
        return s == null ? "" : s.replace('\n', ' ');
    }

    public static void log(String line) {
        synchronized (logLines) {
            logLines.addLast(line);
            while (logLines.size() > 50) logLines.removeFirst();
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.execute(() -> {
            if (mc.player != null) {
                mc.player.sendMessage(Text.literal("\u00a77[Executor] \u00a7f" + line), false);
            }
        });
    }

    public static List<String> logTail(int n) {
        synchronized (logLines) {
            List<String> all = new ArrayList<>(logLines);
            return all.subList(Math.max(0, all.size() - n), all.size());
        }
    }
}
