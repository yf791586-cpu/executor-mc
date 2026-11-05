package com.executormod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class ExecutorMod implements ClientModInitializer {
    public static KeyBinding openKey;
    public static KeyBinding stopKey;

    @Override
    public void onInitializeClient() {
        // Sejak 1.21.9 keybind memakai KeyBinding.Category (Meteor juga begitu)
        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of("executormod", "main"));

        openKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.executormod.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_H, category));
        stopKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.executormod.stop", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, category));

        ScriptRunner.init();

        // Script dijalankan di awal tick, sebelum game memproses input
        ClientTickEvents.START_CLIENT_TICK.register(ScriptRunner::tick);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new ExecutorScreen());
                }
            }
            while (stopKey.wasPressed()) {
                ScriptRunner.stop("Script dihentikan (tombol K)");
            }
        });
    }
}
