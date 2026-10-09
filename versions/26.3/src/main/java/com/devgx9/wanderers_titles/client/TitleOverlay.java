/*
 * This file is part of the Wanderer's Titles project, licensed under the
 * GNU Lesser General Public License v3.0
 *
 * Copyright (C) 2026  DevGBX9 and contributors
 *
 * Wanderer's Titles is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Wanderer's Titles is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Wanderer's Titles.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.devgx9.wanderers_titles.client;

import com.devgx9.wanderers_titles.WandererTitles;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

/**
 * Plays the cinematic title reveal as a full screen overlay, triggered by a key mapping.
 *
 * The clip is stored as a numbered PNG sequence (see the {@code title} texture folder), because
 * Minecraft cannot play video. Every frame is a pre keyed image: the original footage was a bright
 * reveal on a pure black background, and the black was turned into real transparency, so the
 * overlay composites on top of the world instead of covering it with a black rectangle.
 *
 * The sequence is played back on real time ({@link Util#getMillis()}) so it runs at the same speed
 * regardless of the frame rate, and it stops by itself at the end of the clip.
 */
public final class TitleOverlay
{
	/** Source clip properties: 187 frames exported from a 30 fps export. */
	private static final int FRAME_COUNT = 187;
	private static final int FRAME_FPS = 30;
	private static final int FRAME_WIDTH = 480;
	private static final int FRAME_HEIGHT = 240;

	private static final String TEXTURE_FOLDER = "textures/title/";

	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
			Identifier.fromNamespaceAndPath(WandererTitles.MOD_ID, "title"));

	private static final KeyMapping PLAY_TITLE = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.wanderers_titles.play_title",
			InputConstants.Type.KEYBOARD,
			InputConstants.KEY_T,
			CATEGORY));

	/** Wall clock time at which the current playback started, or 0 while nothing is playing. */
	private static long startedAt = 0L;

	private TitleOverlay()
	{
	}

	public static void initialize()
	{
		// render right before the chat, so the reveal sits on top of the world and the HUD
		HudElementRegistry.attachElementBefore(
				VanillaHudElements.CHAT,
				Identifier.fromNamespaceAndPath(WandererTitles.MOD_ID, "title_overlay"),
				TitleOverlay::render);

		ClientTickEvents.END_CLIENT_TICK.register(client ->
		{
			while (PLAY_TITLE.consumeClick())
			{
				startedAt = Util.getMillis();
			}
		});
	}

	private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker)
	{
		if (startedAt == 0L)
		{
			return;
		}

		int elapsedMs = (int) (Util.getMillis() - startedAt);
		int frame = elapsedMs * FRAME_FPS / 1000;
		if (frame >= FRAME_COUNT)
		{
			startedAt = 0L;
			return;
		}

		Identifier texture = Identifier.fromNamespaceAndPath(
				WandererTitles.MOD_ID,
				String.format("%sframe_%04d.png", TEXTURE_FOLDER, frame + 1));

		Window window = Minecraft.getInstance().getWindow();
		int width = window.getGuiScaledWidth();
		int height = window.getGuiScaledHeight();

		graphics.blit(
				RenderPipelines.GUI_TEXTURED,
				texture,
				0, 0, 0, 0,
				width, height,
				FRAME_WIDTH, FRAME_HEIGHT,
				FRAME_WIDTH, FRAME_HEIGHT);
	}
}
