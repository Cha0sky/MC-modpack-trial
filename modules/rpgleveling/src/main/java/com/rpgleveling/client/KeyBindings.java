package com.rpgleveling.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/** The single key binding of the mod (default {@code K}). */
public final class KeyBindings {

    public static final String CATEGORY = "key.categories.rpgleveling";

    public static final KeyMapping OPEN_RPG = new KeyMapping(
            "key.rpgleveling.open",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            CATEGORY);

    private KeyBindings() {}
}
