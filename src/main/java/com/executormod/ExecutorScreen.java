package com.executormod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/**
 * UI executor: daftar script (kiri), preview script (tengah),
 * kotak script 1 baris + tombol (bawah).
 */
public class ExecutorScreen extends Screen {
    private static String lastLine = "player.forward(true); wait(3); player.stop()";
    private static Path selected;

    private TextFieldWidget lineBox;
    private final List<String> preview = new ArrayList<>();

    public ExecutorScreen() {
        super(Text.literal("Executor"));
    }

    @Override
    protected void init() {
        if (lineBox != null) lastLine = lineBox.getText();

        int px = 120;
        int py = 28;
        int pw = Math.max(120, width - 130);
        int ph = Math.max(40, height - 108);

        lineBox = new TextFieldWidget(textRenderer, px, py + ph + 4, pw, 20, Text.literal("Script"));
        lineBox.setMaxLength(100000);
        lineBox.setPlaceholder(Text.literal("Script 1 baris, pisahkan perintah dengan ;"));
        lineBox.setText(lastLine);
        addDrawableChild(lineBox);

        int by = py + ph + 28;
        int bw = 60;
        addDrawableChild(ButtonWidget.builder(Text.literal("Run File"), b -> runFile())
                .dimensions(px, by, bw, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Run Line"), b -> runLine())
                .dimensions(px + (bw + 4), by, bw, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Stop"), b -> ScriptRunner.stop("Script dihentikan"))
                .dimensions(px + (bw + 4) * 2, by, bw, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Reload"), b -> reloadScripts())
                .dimensions(px + (bw + 4) * 3, by, bw, 20).build());

        List<Path> files = ScriptRunner.listScripts();
        if (selected == null && !files.isEmpty()) selected = files.get(0);
        for (int i = 0; i < Math.min(files.size(), 9); i++) {
            Path p = files.get(i);
            String name = textRenderer.trimToWidth(p.getFileName().toString(), 90);
            addDrawableChild(ButtonWidget.builder(Text.literal(name), b -> select(p))
                    .dimensions(10, 28 + i * 22, 100, 20).build());
        }
        loadPreview();
    }

    private void select(Path p) {
        selected = p;
        loadPreview();
    }

    private void loadPreview() {
        preview.clear();
        if (selected == null) {
            preview.add("(belum ada script di folder config/executormod/scripts)");
            return;
        }
        try {
            for (String line : Files.readAllLines(selected)) {
                preview.add(line.replace("\t", "  "));
            }
        } catch (IOException e) {
            preview.add("Gagal membuka " + selected.getFileName());
        }
    }

    private void runFile() {
        if (selected == null) return;
        try {
            String code = Files.readString(selected);
            client.setScreen(null);
            ScriptRunner.start(code);
        } catch (IOException e) {
            ScriptRunner.log("Gagal membuka " + selected.getFileName());
        }
    }

    private void runLine() {
        lastLine = lineBox.getText();
        client.setScreen(null);
        ScriptRunner.start(lastLine);
    }

    private void reloadScripts() {
        clearChildren();
        init();
    }

    @Override
    public void removed() {
        if (lineBox != null) lastLine = lineBox.getText();
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);

        int px = 120;
        int py = 28;
        int pw = Math.max(120, width - 130);
        int ph = Math.max(40, height - 108);

        ctx.fill(px, py, px + pw, py + ph, 0xAA000000);
        int maxLines = Math.max(1, (ph - 6) / 10);
        for (int i = 0; i < Math.min(preview.size(), maxLines); i++) {
            ctx.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(preview.get(i), pw - 8),
                    px + 4, py + 4 + i * 10, 0xFFDDDDDD);
        }

        ctx.drawTextWithShadow(textRenderer, "Executor", 10, 10, 0xFFFFFFFF);
        boolean run = ScriptRunner.isRunning();
        ctx.drawTextWithShadow(textRenderer, run ? "Berjalan" : "Idle", 120, 10, run ? 0xFF55FF55 : 0xFFAAAAAA);
        ctx.drawTextWithShadow(textRenderer, "K = stop script", 180, 10, 0xFFAAAAAA);

        List<String> tail = ScriptRunner.logTail(2);
        int y = height - 26;
        for (String line : tail) {
            ctx.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(line, width - 20), 10, y, 0xFFDDDDDD);
            y += 12;
        }
    }
}
