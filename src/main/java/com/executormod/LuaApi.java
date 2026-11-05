package com.executormod;

import java.util.function.Supplier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** API yang tersedia untuk script Lua: player, chat, world, inventory, print, wait. */
final class LuaApi {
    private interface Fn {
        Varargs run(Varargs a);
    }

    private LuaApi() {}

    private static LuaValue fn(Fn f) {
        return new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs a) {
                ScriptRunner.guard();
                return f.run(a);
            }
        };
    }

    private static MinecraftClient mc() {
        return MinecraftClient.getInstance();
    }

    private static void key(LuaTable t, String name, Supplier<KeyBinding> k) {
        t.set(name, fn(a -> {
            ScriptRunner.hold(k.get(), a.optboolean(1, true));
            return LuaValue.NONE;
        }));
    }

    static void install(Globals g) {
        g.set("print", fn(a -> {
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i <= a.narg(); i++) {
                if (i > 1) sb.append(' ');
                sb.append(a.arg(i).tojstring());
            }
            ScriptRunner.log(sb.toString());
            return LuaValue.NONE;
        }));

        // ---- player ----
        LuaTable player = new LuaTable();
        key(player, "forward", () -> mc().options.forwardKey);
        key(player, "back", () -> mc().options.backKey);
        key(player, "left", () -> mc().options.leftKey);
        key(player, "right", () -> mc().options.rightKey);
        key(player, "jump", () -> mc().options.jumpKey);
        key(player, "sneak", () -> mc().options.sneakKey);
        key(player, "sprint", () -> mc().options.sprintKey);
        key(player, "attack", () -> mc().options.attackKey);
        key(player, "use", () -> mc().options.useKey);

        player.set("stop", fn(a -> {
            ScriptRunner.releaseKeys();
            return LuaValue.NONE;
        }));

        player.set("look", fn(a -> {
            var pl = mc().player;
            if (pl != null) {
                pl.setYaw((float) a.checkdouble(1));
                pl.setPitch((float) MathHelper.clamp(a.checkdouble(2), -90.0, 90.0));
            }
            return LuaValue.NONE;
        }));

        player.set("lookAt", fn(a -> {
            var pl = mc().player;
            if (pl == null) return LuaValue.NONE;
            double dx = a.checkdouble(1) - pl.getX();
            double dy = a.checkdouble(2) - pl.getEyeY();
            double dz = a.checkdouble(3) - pl.getZ();
            double flat = Math.sqrt(dx * dx + dz * dz);
            pl.setYaw((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0));
            pl.setPitch((float) (-Math.toDegrees(Math.atan2(dy, flat))));
            return LuaValue.NONE;
        }));

        player.set("pos", fn(a -> {
            var pl = mc().player;
            if (pl == null) return LuaValue.NIL;
            return LuaValue.varargsOf(new LuaValue[] {
                LuaValue.valueOf(pl.getX()), LuaValue.valueOf(pl.getY()), LuaValue.valueOf(pl.getZ())
            });
        }));

        player.set("yaw", fn(a -> {
            var pl = mc().player;
            return pl == null ? LuaValue.NIL : LuaValue.valueOf((double) pl.getYaw());
        }));

        player.set("pitch", fn(a -> {
            var pl = mc().player;
            return pl == null ? LuaValue.NIL : LuaValue.valueOf((double) pl.getPitch());
        }));

        player.set("health", fn(a -> {
            var pl = mc().player;
            return pl == null ? LuaValue.NIL : LuaValue.valueOf((double) pl.getHealth());
        }));

        player.set("food", fn(a -> {
            var pl = mc().player;
            return pl == null ? LuaValue.NIL : LuaValue.valueOf((double) pl.getHungerManager().getFoodLevel());
        }));
        g.set("player", player);

        // ---- chat ----
        LuaTable chat = new LuaTable();
        chat.set("send", fn(a -> {
            String m = a.checkjstring(1);
            mc().execute(() -> {
                var nh = mc().getNetworkHandler();
                if (nh == null) return;
                if (m.startsWith("/")) nh.sendChatCommand(m.substring(1));
                else nh.sendChatMessage(m);
            });
            return LuaValue.NONE;
        }));
        g.set("chat", chat);

        // ---- world ----
        LuaTable world = new LuaTable();
        world.set("block", fn(a -> {
            var w = mc().world;
            if (w == null) return LuaValue.NIL;
            BlockPos bp = new BlockPos(
                    (int) Math.floor(a.checkdouble(1)),
                    (int) Math.floor(a.checkdouble(2)),
                    (int) Math.floor(a.checkdouble(3)));
            return LuaValue.valueOf(Registries.BLOCK.getId(w.getBlockState(bp).getBlock()).toString());
        }));
        g.set("world", world);
    }
}
