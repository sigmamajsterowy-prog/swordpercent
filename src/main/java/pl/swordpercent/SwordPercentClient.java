package pl.swordpercent;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class SwordPercentClient implements ClientModInitializer {

    private static final double RANGE = 64.0;
    private static final float MIN_SCALE = 0.5f;
    private static final float MAX_SCALE = 3.0f;
    private static final float STEP = 0.1f;

    private static float scale = 1.0f;

    private static KeyBinding biggerKey;
    private static KeyBinding smallerKey;

    @Override
    public void onInitializeClient() {
        loadConfig();

        biggerKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.swordpercent.bigger", GLFW.GLFW_KEY_RIGHT_BRACKET, "category.swordpercent"));
        smallerKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.swordpercent.smaller", GLFW.GLFW_KEY_LEFT_BRACKET, "category.swordpercent"));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (biggerKey.wasPressed()) changeScale(mc, +STEP);
            while (smallerKey.wasPressed()) changeScale(mc, -STEP);
        });

        HudRenderCallback.EVENT.register(this::render);
    }

    private void changeScale(MinecraftClient mc, float delta) {
        scale = Math.round(Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale + delta)) * 10f) / 10f;
        saveConfig();
        if (mc.player != null) {
            mc.player.sendMessage(Text.literal("SwordPercent rozmiar: " + scale + "x"), true);
        }
    }

    private void render(DrawContext ctx, RenderTickCounter tick) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.currentScreen != null) return;

        PlayerEntity target = getLookedAtPlayer(mc);
        if (target == null) return;

        ItemStack held = target.getMainHandStack();
        if (held.isEmpty()) return;

        LoreComponent lore = held.get(DataComponentTypes.LORE);
        if (lore == null) return;

        TextRenderer tr = mc.textRenderer;
        MatrixStack m = ctx.getMatrices();

        m.push();
        m.translate(ctx.getScaledWindowWidth() / 2f, ctx.getScaledWindowHeight() / 2f + 15f * scale, 0f);
        m.scale(scale, scale, 1f);

        int y = 0;
        for (Text line : lore.lines()) {
            String s = line.getString().toLowerCase();
            if (s.contains("dodatkowe obra")) {
                ctx.drawCenteredTextWithShadow(tr, line, 0, y, 0xFFFFFF);
                y += 11;
            }
        }

        m.pop();
    }

    private PlayerEntity getLookedAtPlayer(MinecraftClient mc) {
        Entity cam = mc.getCameraEntity();
        if (cam == null) return null;

        Vec3d start = cam.getCameraPosVec(1.0f);
        Vec3d dir = cam.getRotationVec(1.0f);
        Vec3d end = start.add(dir.multiply(RANGE));
        Box box = cam.getBoundingBox().stretch(dir.multiply(RANGE)).expand(1.0);

        EntityHitResult hit = ProjectileUtil.raycast(
            cam, start, end, box,
            e -> e instanceof PlayerEntity && e != cam && !e.isSpectator(),
            RANGE * RANGE
        );

        return hit != null ? (PlayerEntity) hit.getEntity() : null;
    }

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("swordpercent.properties");
    }

    private static void loadConfig() {
        Path p = configPath();
        if (!Files.exists(p)) return;
        try (Reader r = Files.newBufferedReader(p)) {
            Properties props = new Properties();
            props.load(r);
            scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE,
                Float.parseFloat(props.getProperty("scale", "1.0"))));
        } catch (IOException | NumberFormatException ignored) {}
    }

    private static void saveConfig() {
        try (Writer w = Files.newBufferedWriter(configPath())) {
            Properties props = new Properties();
            props.setProperty("scale", Float.toString(scale));
            props.store(w, "SwordPercent");
        } catch (IOException ignored) {}
    }
}
