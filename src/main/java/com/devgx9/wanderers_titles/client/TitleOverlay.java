/*
 * This file is part of the Immersive Titles project, licensed under the
 * GNU Lesser General Public License v3.0
 *
 * Copyright (C) 2026  DevGBX9 and contributors
 *
 * Immersive Titles is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Immersive Titles is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Immersive Titles.  If not, see <https://www.gnu.org/licenses/>.
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
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3x2fStack;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * The two title reveals of the mod and when each of them plays.
 *
 * A biome gets its big cinematic reveal the first time the player enters it, and only the small
 * corner title on every later entry. Which biomes those are is remembered per world and written to
 * disk (see {@link DiscoveredBiomes}), so the reveal is a discovery of that world: leaving it and
 * joining it again later only brings the corner title back, while another world still has its own
 * first visits to hand out. While a world is being joined the titles wait until the player actually
 * moves, so the first title is about the biome the player ends up standing in once they are playing
 * instead of about whatever the camera saw while the world was still loading.
 *
 * The big reveal draws the clip stored as a numbered PNG sequence at its 1440x720 resolution (see
 * the {@code title} texture folder) minus the two strips of rows {@link #FRAME_CROP} describes,
 * because Minecraft cannot play video. Every frame is a pre
 * keyed image: the footage was a bright reveal on pure black, so the frames carry the brightness of
 * the original as their alpha channel and pure white as their color, and every pixel the render left
 * black - 88% of them - is fully transparent. Drawn over the world that reproduces the original
 * picture and lets the reveal glow over any background, instead of covering the world with a black
 * rectangle. The sequence is played back on real time ({@link Util#getMillis()}) so it runs at the
 * same speed regardless of the frame rate, and it stops by itself at the end of the clip. A full
 * resolution frame is about 4 MiB in video memory and there are 187 of them, so only the frames that
 * are still needed are kept loaded and the ones that have been drawn are released again.
 *
 * The biome name is not part of the clip: the plate leaves an empty box in its middle, and the name
 * is drawn into it with the game font, scaled to the box and faded in and out in step with the clip
 * (see {@link #nameAlpha}). So the reveal works for any biome name, at any length.
 *
 * The corner title is purely programmatic, i.e. it uses no texture at all: the text is drawn with
 * the game font in bold and it fades in, holds and fades out in the lower left corner.
 */
public final class TitleOverlay
{
	/** Clip properties: 187 full resolution frames exported from a 30 fps render. */
	private static final int FRAME_COUNT = 187;
	private static final int FRAME_FPS = 30;
	private static final int FRAME_WIDTH = 1440;
	private static final int FRAME_HEIGHT = 720;

	private static final String TEXTURE_FOLDER = "textures/title/";

	/**
	 * How many frame textures stay loaded at the same time. A full resolution frame costs about
	 * 4 MiB of video memory and the whole clip would cost about 750 MiB, so the frames that have
	 * already been drawn are released again. The window is kept at a few frames because a queued
	 * blit only runs later in the same frame.
	 */
	private static final int FRAMES_KEPT_LOADED = 4;

	// ---------------------------------------------------------------------------------------------
	// The biome name inside the plate
	// ---------------------------------------------------------------------------------------------

	/**
	 * The empty box of the plate, in frame pixels. The plate is symmetrical and leaves its middle
	 * free for the name: the ornaments occupy rows 147 to 196 and 342 to 430, and the box of the
	 * original render maps onto this one exactly.
	 */
	private static final int NAME_BOX_X = 324;
	private static final int NAME_BOX_Y = 198;
	private static final int NAME_BOX_WIDTH = 807;
	private static final int NAME_BOX_HEIGHT = 120;

	/** How much of the box the name may fill, so a long biome name still fits inside the plate. */
	private static final float NAME_MAX_HEIGHT = 0.70F;
	private static final float NAME_MAX_WIDTH = 0.94F;

	/** Cap height of the game font at scale 1, used to size and centre the name. */
	private static final float FONT_CAP_HEIGHT = 7.0F;

	/**
	 * The name is invisible while the opening flash is still covering the plate, and appears exactly
	 * as the flash clears, which is what makes the reveal read as "the flash reveals the name".
	 *
	 * The table is the brightness of the empty box measured in the clip itself, 0..255, sampled
	 * every second frame starting at the fifth frame, which is the peak of the flash. The alpha of
	 * the name is the complement of it, so the name is drawn fully opaque exactly where the flash
	 * has already cleared.
	 */
	private static final int VEIL_FIRST_FRAME = 4;
	private static final int[] VEIL = {254, 240, 199, 144, 83, 48, 28, 15, 12, 8, 7, 4, 3, 1};
	private static final int VEIL_LAST_FRAME = VEIL_FIRST_FRAME + (VEIL.length - 1) * 2;

	/** The plate dissolves here, so the name goes with it. */
	private static final int NAME_FADE_OUT_FROM = 140;
	private static final int NAME_FADE_OUT_TO = 152;

	// ---------------------------------------------------------------------------------------------
	// The part of the clip that is drawn, and the flash beyond it
	// ---------------------------------------------------------------------------------------------

	/**
	 * The clip carries a one pixel bright rind along the top and the bottom of its picture: the
	 * opening flash was cut off there, so the outermost row of the picture is up to fifty levels
	 * brighter than the rows right inside it, measured on the frames themselves. Drawn as it is, that
	 * rind reads as a hard white line across the screen along the top and the bottom of the picture.
	 *
	 * The first and the last {@code FRAME_CROP} rows are therefore left out of the picture and the
	 * remaining {@link #CONTENT_WIDTH} x {@link #CONTENT_HEIGHT} pixels are what the overlay draws.
	 * Away from the rind the glow is smooth, so nothing of the picture is lost by this, and the
	 * transparent margins the clip keeps at its top and its bottom go with it.
	 */
	private static final int FRAME_CROP = 9;
	private static final int CONTENT_WIDTH = FRAME_WIDTH;
	private static final int CONTENT_HEIGHT = FRAME_HEIGHT - 2 * FRAME_CROP;

	// ---------------------------------------------------------------------------------------------
	// When a title plays
	// ---------------------------------------------------------------------------------------------

	/**
	 * How many ticks between two biome checks. The title is a reveal for entering a biome, so half a
	 * second of latency is fine and it keeps the per tick cost of the check irrelevant.
	 */
	private static final int BIOME_CHECK_INTERVAL_TICKS = 10;

	/**
	 * How far the player has to move after joining a world before the titles start, in blocks
	 * squared: one block is enough to tell "the world is loaded and the player is playing" from the
	 * first frames of a join.
	 */
	private static final double MOVED_DISTANCE_SQR = 1.0D;

	// ---------------------------------------------------------------------------------------------
	// The corner title
	// ---------------------------------------------------------------------------------------------

	/** Corner title properties: its size, and the timing of its fade in, hold and fade out. */
	private static final float CORNER_TEXT_SCALE = 2.5F;
	private static final long CORNER_FADE_IN_MS = 400L;
	private static final long CORNER_HOLD_MS = 1600L;
	private static final long CORNER_FADE_OUT_MS = 900L;
	private static final long CORNER_ANIMATION_MS = CORNER_FADE_IN_MS + CORNER_HOLD_MS + CORNER_FADE_OUT_MS;

	/** Distance between the corner title and the lower left corner of the screen, in gui pixels. */
	private static final int CORNER_MARGIN = 20;

	/** The color of the titles: pure white, only its alpha is animated by the fades. */
	private static final int TITLE_TEXT_RGB = 0xFFFFFF;

	/** Used when there is no biome to name, e.g. while a title is triggered from the main menu. */
	private static final Component FALLBACK_NAME = Component.literal("PALE GARDEN").withStyle(ChatFormatting.BOLD);

	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
			Identifier.fromNamespaceAndPath(WandererTitles.MOD_ID, "title"));

	private static final KeyMapping PLAY_TITLE = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.wanderers_titles.play_title",
			InputConstants.Type.KEYBOARD,
			InputConstants.KEY_T,
			CATEGORY));

	private static final KeyMapping SHOW_CORNER_TITLE = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.wanderers_titles.show_corner_title",
			InputConstants.Type.KEYBOARD,
			InputConstants.KEY_K,
			CATEGORY));

	/** Wall clock time at which the current playback started, or 0 while nothing is playing. */
	private static long startedAt = 0L;

	/** Wall clock time at which the corner title animation started, or 0 while it is not playing. */
	private static long cornerStartedAt = 0L;

	/** The name the running reveal writes into the plate. */
	private static Component revealName = null;

	/** The name the corner title shows. */
	private static Component cornerName = null;

	/** The biomes of the current world that already had their reveal, so entering them again only
	 * gets the corner title. Loaded when the world is joined and written to disk as a biome is
	 * discovered, see {@link DiscoveredBiomes}. */
	private static final Set<String> VISITED_BIOMES = new HashSet<>();

	/** The world {@link #VISITED_BIOMES} belongs to, or null while there is no world. */
	private static String worldKey = null;

	/** The biome the player was in when it was last checked, to notice a change. */
	private static String lastBiomeId = null;

	/** Where the player stood when the world was joined, until they start moving. */
	private static Vec3 joinPosition = null;

	/** Whether the player is currently in a world. */
	private static boolean inWorld = false;

	/** Ticks left until the biome is checked again. */
	private static int ticksUntilBiomeCheck = 0;

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

		ClientTickEvents.END_CLIENT_TICK.register(TitleOverlay::tick);
	}

	private static void tick(Minecraft client)
	{
		while (PLAY_TITLE.consumeClick())
		{
			startReveal(BiomeNames.of(client).orElse(FALLBACK_NAME));
		}

		while (SHOW_CORNER_TITLE.consumeClick())
		{
			startCornerTitle(BiomeNames.of(client).orElse(FALLBACK_NAME));
		}

		checkBiome(client);
	}

	/**
	 * Watches the biome the player is in and plays the matching title when it changes: the reveal on
	 * a first visit, the corner title on every visit after that.
	 */
	private static void checkBiome(Minecraft client)
	{
		if (ticksUntilBiomeCheck > 0)
		{
			ticksUntilBiomeCheck--;
			return;
		}

		ticksUntilBiomeCheck = BIOME_CHECK_INTERVAL_TICKS;

		if (client.level == null || client.player == null)
		{
			// back in a menu: the discoveries of the world that was left are on disk, so the next
			// world loads its own
			inWorld = false;
			joinPosition = null;
			lastBiomeId = null;
			worldKey = null;
			VISITED_BIOMES.clear();
			return;
		}

		if (!inWorld)
		{
			// the world has just been joined: pick up the discoveries of this world - a biome that
			// had its reveal in an earlier session is not a discovery again - and wait for the
			// player to actually start moving
			inWorld = true;
			joinPosition = client.player.position();
			worldKey = WorldIdentity.of(client).orElse(null);
			VISITED_BIOMES.clear();
			VISITED_BIOMES.addAll(DiscoveredBiomes.load(worldKey));
			logWorld();
			return;
		}

		if (joinPosition != null && client.player.position().distanceToSqr(joinPosition) < MOVED_DISTANCE_SQR)
		{
			return;
		}

		String biomeId = BiomeNames.id(client).orElse(null);
		if (biomeId == null || biomeId.equals(lastBiomeId))
		{
			return;
		}

		lastBiomeId = biomeId;
		Component name = BiomeNames.of(client).orElse(FALLBACK_NAME);

		if (VISITED_BIOMES.add(biomeId))
		{
			DiscoveredBiomes.remember(worldKey, biomeId);
			startReveal(name);
		}
		else
		{
			startCornerTitle(name);
		}
	}

	/**
	 * Says once per world which world the titles are being kept for and how much of it is already
	 * discovered, so a world that cannot be told apart from another one shows up in the log instead
	 * of quietly sharing, or losing, its discoveries.
	 */
	private static void logWorld()
	{
		if (worldKey == null)
		{
			WandererTitles.LOGGER.warn("This world cannot be identified; the biome reveals of this"
					+ " session will be remembered in this session only");
		}
		else
		{
			WandererTitles.LOGGER.info("Biome reveals of world {}: {} already discovered",
					worldKey, VISITED_BIOMES.size());
		}
	}

	/** Starts the cinematic reveal, writing {@code name} into the plate for the length of the clip. */
	private static void startReveal(Component name)
	{
		revealName = name;
		startedAt = Util.getMillis();

		// the big reveal takes over from the corner title
		cornerName = null;
		cornerStartedAt = 0L;
	}

	/** Starts the corner title, which fades in, holds and fades out on its own. */
	private static void startCornerTitle(Component name)
	{
		cornerName = name;
		cornerStartedAt = Util.getMillis();
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

		int frame = (int) ((Util.getMillis() - startedAt) * FRAME_FPS / 1000L);
		if (frame >= FRAME_COUNT)
		{
			stopPlayback();
			return;
		}

		Identifier texture = Identifier.fromNamespaceAndPath(
				WandererTitles.MOD_ID,
				String.format("%sframe_%04d.png", TEXTURE_FOLDER, frame + 1));
		keepFrameTexture(texture);

		Window window = Minecraft.getInstance().getWindow();
		int screenWidth = window.getGuiScaledWidth();
		int screenHeight = window.getGuiScaledHeight();

		// the clip is 2:1 and a screen is not, so fit it into the screen instead of stretching it to
		// the screen shape
		float scale = Math.min(screenWidth / (float) CONTENT_WIDTH, screenHeight / (float) CONTENT_HEIGHT);
		int width = Math.max(1, Math.round(CONTENT_WIDTH * scale));
		int height = Math.max(1, Math.round(CONTENT_HEIGHT * scale));
		int x = (screenWidth - width) / 2;
		int y = (screenHeight - height) / 2;

		renderClipSeam(graphics, texture, x, y, width, height, screenWidth, screenHeight);

		graphics.blit(
				RenderPipelines.GUI_TEXTURED,
				texture,
				x, y, 0, FRAME_CROP,
				width, height,
				CONTENT_WIDTH, CONTENT_HEIGHT,
				FRAME_WIDTH, FRAME_HEIGHT);

		renderName(graphics, x, y, scale, frame);
	}

	/**
	 * Covers the part of the screen the clip does not reach - the clip is 2:1 and a screen is not -
	 * with the row or the column of the clip right inside the crop, stretched across the strip.
	 *
	 * The clip's own pixels are used instead of a solid colour because its picture is not uniform
	 * along its edge: the flash is brighter in its middle than at its sides, by up to fifty levels,
	 * so any single colour would step against the clip somewhere along the line. Sampling the clip
	 * keeps the strip and the picture it continues within a few levels of each other at every column,
	 * on every screen shape, which is the difference between a seam and no seam.
	 *
	 * At a frame whose edge sample is transparent - every frame outside the opening flash - nothing is
	 * drawn, so the world is untouched by all of this at any other time.
	 */
	private static void renderClipSeam(GuiGraphicsExtractor graphics, Identifier texture, int plateX,
			int plateY, int width, int height, int screenWidth, int screenHeight)
	{
		int right = plateX + width;
		int bottom = plateY + height;

		// the strips above and below the clip: one row of the clip, stretched sideways
		if (plateY > 0)
		{
			blitBand(graphics, texture, 0, 0, screenWidth, plateY, 0.0F, FRAME_CROP, FRAME_WIDTH, 1);
		}

		if (bottom < screenHeight)
		{
			blitBand(graphics, texture, 0, bottom, screenWidth, screenHeight,
					0.0F, FRAME_HEIGHT - FRAME_CROP - 1, FRAME_WIDTH, 1);
		}

		// the strips left and right of the clip: one column of the clip, stretched downwards, and
		// only over the clip's own rows so they leave the two strips above alone
		if (plateX > 0)
		{
			blitBand(graphics, texture, 0, plateY, plateX, bottom, 0.0F, FRAME_CROP, 1, CONTENT_HEIGHT);
		}

		if (right < screenWidth)
		{
			blitBand(graphics, texture, right, plateY, screenWidth, bottom,
					FRAME_WIDTH - 1.0F, FRAME_CROP, 1, CONTENT_HEIGHT);
		}
	}

	/**
	 * Draws the {@code bandWidth} x {@code bandHeight} pixels of the clip at ({@code u}, {@code v})
	 * into the screen rectangle from ({@code x0}, {@code y0}) to ({@code x1}, {@code y1}).
	 */
	private static void blitBand(GuiGraphicsExtractor graphics, Identifier texture, int x0, int y0,
			int x1, int y1, float u, float v, int bandWidth, int bandHeight)
	{
		graphics.blit(
				RenderPipelines.GUI_TEXTURED,
				texture,
				x0, y0, u, v,
				x1 - x0, y1 - y0,
				bandWidth, bandHeight,
				FRAME_WIDTH, FRAME_HEIGHT);
	}

	/**
	 * Draws the biome name into the empty box of the plate, in the same coordinates the plate was
	 * drawn in so the two stay in step at any screen size and gui scale.
	 */
	private static void renderName(GuiGraphicsExtractor graphics, int plateX, int plateY, float scale, int frame)
	{
		int alpha = nameAlpha(frame);
		if (alpha <= 0 || revealName == null)
		{
			return;
		}

		Font font = Minecraft.getInstance().font;
		float boxX = plateX + NAME_BOX_X * scale;
		float boxY = plateY + (NAME_BOX_Y - FRAME_CROP) * scale;
		float boxWidth = NAME_BOX_WIDTH * scale;
		float boxHeight = NAME_BOX_HEIGHT * scale;

		float textScale = Math.min(
				boxHeight * NAME_MAX_HEIGHT / FONT_CAP_HEIGHT,
				boxWidth * NAME_MAX_WIDTH / Math.max(1, font.width(revealName)));
		float textWidth = font.width(revealName) * textScale;
		float textHeight = FONT_CAP_HEIGHT * textScale;

		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(boxX + (boxWidth - textWidth) / 2.0F, boxY + (boxHeight - textHeight) / 2.0F);
		pose.scale(textScale, textScale);
		graphics.text(font, revealName, 0, 0, (alpha << 24) | TITLE_TEXT_RGB, false);
		pose.popMatrix();
	}

	/**
	 * The alpha of the name at {@code frame} of the clip: hidden while the opening flash covers the
	 * plate, revealed as the flash clears, held while the plate holds, and faded out with it.
	 */
	private static int nameAlpha(int frame)
	{
		if (frame <= VEIL_FIRST_FRAME)
		{
			return 0;
		}

		if (frame < VEIL_LAST_FRAME)
		{
			int index = (frame - VEIL_FIRST_FRAME) / 2;
			int offset = (frame - VEIL_FIRST_FRAME) % 2;
			int veil = VEIL[index] + (VEIL[index + 1] - VEIL[index]) * offset / 2;
			return 256 - veil;
		}

		if (frame <= NAME_FADE_OUT_FROM)
		{
			return 255;
		}

		if (frame >= NAME_FADE_OUT_TO)
		{
			return 0;
		}

		return 255 * (NAME_FADE_OUT_TO - frame) / (NAME_FADE_OUT_TO - NAME_FADE_OUT_FROM);
	}

	/** Draws the small corner title of a biome the player has already seen. */
	private static void renderCornerTitle(GuiGraphicsExtractor graphics)
	{
		if (cornerStartedAt == 0L || cornerName == null)
		{
			return;
		}

		long elapsedMs = Util.getMillis() - cornerStartedAt;
		if (elapsedMs >= CORNER_ANIMATION_MS)
		{
			cornerStartedAt = 0L;
			cornerName = null;
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
		graphics.text(font, cornerName, 0, 0, (cornerAlpha(elapsedMs) << 24) | TITLE_TEXT_RGB, true);
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

	/** Ends a playback and drops the frame textures it was still holding. */
	private static void stopPlayback()
	{
		startedAt = 0L;
		revealName = null;
		releaseFrameTextures();
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
