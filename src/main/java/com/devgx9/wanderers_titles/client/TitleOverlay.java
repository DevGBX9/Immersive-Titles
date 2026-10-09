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
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.joml.Matrix3x2fStack;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * The title reveals of the mod, each one bound to its own key mapping.
 *
 * The first one plays the cinematic title reveal as a full screen overlay. The clip is stored as a
 * numbered PNG sequence at its full 1920x960 resolution (see the {@code title} texture folder),
 * because Minecraft cannot play video. Every frame is a pre keyed image: the original footage was a
 * bright reveal on a pure black background, so the frames carry the brightness of the original as
 * their alpha channel and pure white as their color. Drawn over the world that reproduces the
 * original picture exactly on a black background and lets the reveal glow over any other one,
 * instead of covering the world with a black rectangle. The sequence is played back on real time
 * ({@link Util#getMillis()}) so it runs at the same speed regardless of the frame rate, and it stops
 * by itself at the end of the clip. A full resolution frame is about 7 MiB in video memory, so only
 * the frames that are still needed are kept loaded and the ones that have been drawn are released
 * again.
 *
 * The second one is purely programmatic, i.e. it uses no texture at all: the text is drawn with the
 * game font in bold, like the cinematic reveal, and it fades in, holds and fades out in the lower
 * left corner.
 */
public final class TitleOverlay
{
	/** Source clip properties: 187 full resolution frames exported from a 30 fps export. */
	private static final int FRAME_COUNT = 187;
	private static final int FRAME_FPS = 30;
	private static final int FRAME_WIDTH = 1920;
	private static final int FRAME_HEIGHT = 960;

	private static final String TEXTURE_FOLDER = "textures/title/";

	/**
	 * How many frame textures stay loaded at the same time. A full resolution frame costs about 7 MiB
	 * of video memory, and the whole clip would cost more than 1 GiB, so the frames that have already
	 * been drawn are released again. The window is kept at a few frames because a queued blit only
	 * runs later in the same frame.
	 */
	private static final int FRAMES_KEPT_LOADED = 4;

	/** Corner title properties: the text, its size, and the timing of its fade in, hold and fade out. */
	private static final String CORNER_TITLE_TEXT = "PALE GARDEN";
	private static final float CORNER_TEXT_SCALE = 3.0F;
	private static final long CORNER_FADE_IN_MS = 400L;
	private static final long CORNER_HOLD_MS = 1600L;
	private static final long CORNER_FADE_OUT_MS = 900L;
	private static final long CORNER_ANIMATION_MS = CORNER_FADE_IN_MS + CORNER_HOLD_MS + CORNER_FADE_OUT_MS;

	/** Distance between the corner title and the lower left corner of the screen, in gui pixels. */
	private static final int CORNER_MARGIN = 20;

	/** The color of the corner title: pure white, only its alpha is animated by the fade. */
	private static final int CORNER_TEXT_RGB = 0xFFFFFF;

	/** The corner title text itself, bold like the cinematic reveal, drawn by the game font. */
	private static final Component CORNER_TITLE = Component.literal(CORNER_TITLE_TEXT).withStyle(ChatFormatting.BOLD);

	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
			Identifier.fromNamespaceAndPath(WandererTitles.MOD_ID, "title"));

	private static final KeyMapping PLAY_TITLE = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.wanderers_titles.play_title",
			InputConstants.Type.KEYBOARD,
			InputConstants.KEY_T,
			CATEGORY));

	private static final KeyMapping SHOW_CORNER_TITLE = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.wanderers_titles.show_pale_garden",
			InputConstants.Type.KEYBOARD,
			InputConstants.KEY_K,
			CATEGORY));

	/** Wall clock time at which the current playback started, or 0 while nothing is playing. */
	private static long startedAt = 0L;

	/** Wall clock time at which the corner title animation started, or 0 while it is not playing. */
	private static long cornerStartedAt = 0L;

	/** The frame textures that are currently loaded, oldest first. */
	private static final Deque<Identifier> LOADED_FRAMES = new ArrayDeque<>();

	private TitleOverlay()
	{
	}

	public static void initialize()
	{
		// render right before the chat, so the titles sit on top of the world and the HUD
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

			while (SHOW_CORNER_TITLE.consumeClick())
			{
				cornerStartedAt = Util.getMillis();
			}
		});
	}

	private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker)
	{
		renderSequence(graphics);
		renderCornerTitle(graphics);
	}

	/** Draws the current frame of the cinematic reveal, as long as a playback is running. */
	private static void renderSequence(GuiGraphicsExtractor graphics)
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
			releaseFrameTextures();
			return;
		}

		Identifier texture = Identifier.fromNamespaceAndPath(
				WandererTitles.MOD_ID,
				String.format("%sframe_%04d.png", TEXTURE_FOLDER, frame + 1));
		keepFrameTexture(texture);

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

	/** Draws the programmatic corner title, as long as its fade in, hold and fade out are running. */
	private static void renderCornerTitle(GuiGraphicsExtractor graphics)
	{
		if (cornerStartedAt == 0L)
		{
			return;
		}

		long elapsedMs = Util.getMillis() - cornerStartedAt;
		if (elapsedMs >= CORNER_ANIMATION_MS)
		{
			cornerStartedAt = 0L;
			return;
		}

		Minecraft client = Minecraft.getInstance();
		Font font = client.font;
		int x = CORNER_MARGIN;
		int y = client.getWindow().getGuiScaledHeight() - CORNER_MARGIN - (int) (font.lineHeight * CORNER_TEXT_SCALE);

		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(CORNER_TEXT_SCALE, CORNER_TEXT_SCALE);
		graphics.text(font, CORNER_TITLE, 0, 0, (cornerAlpha(elapsedMs) << 24) | CORNER_TEXT_RGB, true);
		pose.popMatrix();
	}

	/** The alpha of the corner title at {@code elapsedMs} of its animation: fade in, hold, fade out. */
	private static int cornerAlpha(long elapsedMs)
	{
		if (elapsedMs < CORNER_FADE_IN_MS)
		{
			return (int) (255L * elapsedMs / CORNER_FADE_IN_MS);
		}

		long fadeOutElapsedMs = elapsedMs - CORNER_FADE_IN_MS - CORNER_HOLD_MS;
		if (fadeOutElapsedMs <= 0L)
		{
			return 255;
		}

		long remainingMs = CORNER_FADE_OUT_MS - fadeOutElapsedMs;
		return remainingMs <= 0L ? 0 : (int) (255L * remainingMs / CORNER_FADE_OUT_MS);
	}

	/**
	 * Marks the frame texture as in use and releases the textures of the frames that have already
	 * been drawn, so playing the full resolution sequence does not fill the video memory of the game.
	 */
	private static void keepFrameTexture(Identifier texture)
	{
		if (LOADED_FRAMES.contains(texture))
		{
			return;
		}

		LOADED_FRAMES.addLast(texture);
		TextureManager textureManager = Minecraft.getInstance().getTextureManager();

		while (LOADED_FRAMES.size() > FRAMES_KEPT_LOADED)
		{
			textureManager.release(LOADED_FRAMES.removeFirst());
		}
	}

	/** Drops the frame textures of a finished playback. */
	private static void releaseFrameTextures()
	{
		TextureManager textureManager = Minecraft.getInstance().getTextureManager();

		while (!LOADED_FRAMES.isEmpty())
		{
			textureManager.release(LOADED_FRAMES.removeFirst());
		}
	}
}
