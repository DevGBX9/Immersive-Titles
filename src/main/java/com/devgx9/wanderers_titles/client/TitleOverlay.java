/*
 * This file is part of the Immersive Titles project, licensed under the
 * MIT License
 *
 * Copyright (C) 2026  DevGBX9 and contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.devgx9.wanderers_titles.client;

import com.devgx9.wanderers_titles.WandererTitles;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
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
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
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
 * first visits to hand out. A world is named by an id it carries itself rather than by anything
 * read from its name, and its list of discoveries lives with it (see {@link WorldIdentity}), so no
 * two worlds can end up sharing one and a world that is deleted takes its list along with it. While a world is being joined the titles wait until the player actually
 * moves, so the first title is about the biome the player ends up standing in once they are playing
 * instead of about whatever the camera saw while the world was still loading.
 *
 * What is followed is the place the player is in rather than the bare biome the game reports for
 * them: below the surface that report is the biome of the column they are under, so a walk through
 * a cave hands out the cave biomes it passes through and none of the surface biomes it passes under,
 * see {@link #checkBiome} and {@link BiomeNames#place}.
 *
 * The structures are the second thing the titles follow, and they are read the other way round: what
 * the game recognizes a player entering is worked out on the server while a world is generated - the
 * client is sent the blocks a structure is made of and told nothing about the structure itself - so
 * the mod asks the server of a world the player hosts themselves and keeps the answer beside the
 * client (see {@link StructurePresence}). What is titled there is the same two titles out of the same
 * kind of memory: the reveal the first time the player is inside one in a world, the corner title on
 * every visit after that (see {@link #checkStructure}), and the structures that can be titled are the
 * ones the game can recognize a player inside, which is every structure it ships (see
 * {@link StructureCatalog}). A world on a server is the one place
 * this cannot reach, because its server is another machine and this mod is not in it, so the titles of
 * such a world name biomes and no structures.
 *
 * The big reveal draws the clip stored as a numbered PNG sequence at its 1440x720 resolution (see
 * the {@code title} texture folder) minus the two strips of rows {@link #FRAME_CROP} describes,
 * because Minecraft cannot play video. Every frame is a pre
 * keyed image: the footage was a bright reveal on pure black, so the frames carry the brightness of
 * the original as their alpha channel and pure white as their color, and every pixel the render left
 * black - 88% of them - is fully transparent. Drawn over the world that reproduces the original
 * picture and lets the reveal glow over any background, instead of covering the world with a black
 * rectangle. The sequence is played back on the time between two drawn frames, which is real time
 * as long as frames keep coming, so it runs at the same speed regardless of the frame rate, and it
 * stops by itself at the end of the clip. A full resolution frame is about 4 MiB in video memory
 * and there are 187 of them, so only the frames that are still needed are kept loaded and the ones
 * that have been drawn are released again.
 *
 * The biome name is not part of the clip: the plate leaves an empty box in its middle, and the name
 * is drawn into it with the game font, scaled to the box and faded in and out in step with the clip
 * (see {@link #nameAlpha}). So the reveal works for any biome name, at any length.
 *
 * The corner title is purely programmatic, i.e. it uses no texture at all: the text is drawn with
 * the game font in bold and it fades in, holds and fades out in the lower left corner of the screen.
 * It is a title of that corner rather than one over the game's own HUD, so it is drawn as low as the
 * corner goes - and where the corner is not clear of the HUD any more, i.e. on a screen narrow enough
 * for the name to reach the bar the HUD is drawn along the foot of it, it is put back over the HUD as
 * it used to be drawn rather than through it (see {@link #CORNER_BOTTOM_MARGIN} and
 * {@link #CORNER_HUD_ROOM}).
 *
 * It is the small title of the two, and it is drawn in the shape of the small area title of Hollow
 * Knight: Silksong as the picture the owner sent has it: the last word of the name is the name
 * itself, at the size a title is read at, and every word before it stands over it as one small word,
 * spaced letter by letter and centred on the name (see {@link #CORNER_KICKER_SCALE} and
 * {@link #drawCornerWord}). A name of one word has nothing over it and is drawn as it always was.
 * The share between the two sizes and the two rooms of that shape are measured off the picture rather
 * than chosen ({@code _inspect/measure_small_title.py}), with the game's own font in the place of
 * the picture's: the shape is what was asked for, and this font is the one the mod draws it in.
 *
 * The mod's own key opens the collection (see {@link BiomeCollectionScreen}): the biomes of the world
 * and the structures the game recognizes a player entering, each of the two a page of it, with the
 * ones that already had their reveal checked - so that what the reveals have been remembering per
 * world is something the player can look at - and the mod list of Mod Menu opens the same collection
 * over every world the player has played at once, rather than over the one they are in (see
 * {@link ModMenuIntegration}).
 *
 * The big reveal opens with a short sound of the plate arriving, see {@link #REVEAL_SOUND}. Both
 * titles stand still while the pause menu is open over the world, and only then: the menu the player
 * opens with escape is what a reveal has to survive, so the reveal waits on the frame it was showing
 * behind that menu and carries on from the same frame once it is closed, see {@link #render}. The
 * inventory, a chest, the chat and the other screens hold nothing - the game does not pause for them
 * either - so a reveal keeps running behind them and is further along, or already over, when they
 * are closed.
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
	 * Where the middle of the empty box of the plate is, in frame pixels: the plate is one ornament
	 * over the name and one under it, and what the name is centred on is the middle of the room they
	 * leave between them.
	 *
	 * Both ornaments are drawn about one column, and it is the middle of the picture the plate is
	 * drawn into: the middle stroke of the upper one measures 720.00 down the frames and of the lower
	 * one 720.39, against a picture that is 1440 wide. The room between them is rows 197 to 342, which
	 * no frame of the plate has any ink in, so its middle is row 269.5.
	 *
	 * Both numbers are measured on the frames rather than written down by hand, because the drawing
	 * is what decides where the middle of it is: the box this used to be centred on, column 727.5 and
	 * row 258, drew every name 7.5 pixels right of the middle of the plate and 11.5 pixels above the
	 * middle of the room it sits in.
	 */
	private static final float NAME_CENTER_X = 720.0F;
	private static final float NAME_CENTER_Y = 269.5F;

	/**
	 * The room the name may fill, in frame pixels: what the name is sized against, and not where it is
	 * put, since where it is put is the middle of the plate (see {@link #NAME_CENTER_X}).
	 *
	 * It is not all of the room the name has: {@link #NAME_MAX_HEIGHT} and {@link #NAME_MAX_WIDTH}
	 * are the shares of it a name may take, so the largest name keeps clear of the ornaments over and
	 * under it instead of touching them.
	 */
	private static final int NAME_ROOM_WIDTH = 807;
	private static final int NAME_ROOM_HEIGHT = 120;

	/** How much of the room the name may fill, so a long biome name still fits inside the plate. */
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
	// The sound of the reveal
	// ---------------------------------------------------------------------------------------------

	/**
	 * The sound the big reveal opens with: the impact of the plate arriving. The file is
	 * {@code title_reveal.ogg} in the {@code sounds} folder of the mod's assets, and this is the name
	 * the mod's own {@code sounds.json} gives it.
	 *
	 * That entry has to spell the name out with the mod's namespace, i.e.
	 * {@code immersive_titles:title_reveal}, because a name without a namespace is read as one of the
	 * game's own sounds: written as a bare {@code title_reveal} the game looks for
	 * {@code minecraft:sounds/title_reveal.ogg}, finds nothing and drops the sound with
	 * "File minecraft:sounds/title_reveal.ogg does not exist" - the reveal then plays in silence,
	 * because the event it plays exists but has no sound left in it. Vanilla's own entries can leave
	 * the namespace out since they are all under {@code minecraft}.
	 */
	private static final SoundEvent REVEAL_SOUND = SoundEvent.createVariableRangeEvent(
			Identifier.fromNamespaceAndPath(WandererTitles.MOD_ID, "title_reveal"));

	// ---------------------------------------------------------------------------------------------
	// When a title plays
	// ---------------------------------------------------------------------------------------------

	/**
	 * How many ticks between two checks of the place the player is in, which is what the automatic
	 * titles follow: the biome they are in and the structure the game says they are inside (see
	 * {@link #checkPlace}). The title is a reveal for entering one, so half a second of latency is fine,
	 * and it keeps the per tick cost of the two checks irrelevant - the structures of a level are read
	 * at this same interval (see {@link StructurePresence}).
	 */
	private static final int CHECK_INTERVAL_TICKS = 10;

	/**
	 * How far the player has to move after joining a world before the titles start, in blocks
	 * squared: one block is enough to tell "the world is loaded and the player is playing" from the
	 * first frames of a join.
	 */
	private static final double MOVED_DISTANCE_SQR = 1.0D;

	// ---------------------------------------------------------------------------------------------
	// The corner title
	// ---------------------------------------------------------------------------------------------

	/**
	 * The size of the two lines of the corner title, in gui pixels per pixel of the game font: the
	 * small word over the name, and the name itself.
	 *
	 * The share between the two is the shape of the title, and it comes from the picture the owner
	 * sent: its own small word measures 18 rows against a name of 43, while the game font at 1 and 2.5
	 * is a cap height of 7 against 17.5. That is 0.40 of it against 0.42, as close as whole sizes of
	 * this font get: half a pixel per pixel is not a size the game font is drawn at.
	 */
	private static final float CORNER_KICKER_SCALE = 1.0F;
	private static final float CORNER_NAME_SCALE = 2.5F;

	/**
	 * The room the letters of the small word keep between them, in gui pixels at that size: the word
	 * stands over the name as a label rather than as a name, and letters set apart are what makes it
	 * read as one.
	 *
	 * A letter of the game font is five pixels of ink that the pen moves seven past, and bold draws one
	 * pixel of ink more, so the letters of the small word stand one apart by themselves; the picture the
	 * owner sent measures letters of eleven to fourteen rows with eight between them, which is 0.6 to
	 * 0.7 of a letter. Three gui pixels of room stands the letters of the game's font four apart, 0.67
	 * of its own six - and it is the room the letters are read at rather than counted at, so the room
	 * is what is set and the letters are whatever the font draws.
	 */
	private static final int CORNER_KICKER_SPACING = 3;

	/**
	 * The room between the two lines of the corner title, in gui pixels: what the picture the owner
	 * sent measures between the foot of the small word and the top of the name is 15 rows against a
	 * name of 43, 0.35 of it, which at the 17.5 gui pixels the name is drawn at comes to six.
	 */
	private static final int CORNER_LINE_GAP = 6;

	/**
	 * The timing of the corner title's fade in, hold and fade out. The hold is the three seconds the
	 * owner asked the title to stay for: it stands fully drawn for those three seconds, and the fade
	 * out after them is the going rather than the stay.
	 */
	private static final long CORNER_FADE_IN_MS = 400L;
	private static final long CORNER_HOLD_MS = 3000L;
	private static final long CORNER_FADE_OUT_MS = 900L;
	private static final long CORNER_ANIMATION_MS = CORNER_FADE_IN_MS + CORNER_HOLD_MS + CORNER_FADE_OUT_MS;

	/** Distance between the corner title and the left edge of the screen, in gui pixels. */
	private static final int CORNER_MARGIN = 20;

	/**
	 * Where the corner title is drawn, in gui pixels: the foot of the name this far over the foot of
	 * the screen, with the small word over it and the whole of it at {@link #CORNER_MARGIN}.
	 *
	 * The place is the one the owner marked on a picture of their own screen: the foot of the name
	 * measures row 717 of a window whose game area is 736 rows tall at a gui scale of 2, which is 9.5
	 * gui pixels over the foot of it. Nine gui pixels puts the foot of the name at row 358 of a 368
	 * pixel gui, i.e. rows 684 to 717 of their window - the row they drew their box round, to the
	 * pixel.
	 *
	 * What is at the foot of the screen there is the game's own HUD, but not at that column: the HUD
	 * is a bar 182 gui pixels wide and centred, so the left of it is empty world - empty of the
	 * hotbar, of the row of hearts over it and of the bar of experience over that - on any screen wide
	 * enough for the name to stay left of it. Where it does not stay left of it the title is put back
	 * over the HUD (see {@link #cornerNameRow}).
	 */
	private static final int CORNER_BOTTOM_MARGIN = 9;

	/**
	 * Half the width of the game's own HUD along the foot of the screen, in gui pixels: the hotbar is
	 * 182 pixels wide and centred, and the row of hearts, armor and hunger and the bar of experience
	 * are drawn within those same columns.
	 */
	private static final int CORNER_HUD_BAR_HALF = 91;

	/**
	 * What the corner title keeps over the bottom of the screen where it cannot be drawn in the corner
	 * of it, in gui pixels. What is under it there is not the edge of the screen but the game's own
	 * HUD: the hotbar is 22 pixels tall and is drawn from the bottom edge up, and the row of hearts,
	 * armor and hunger sits 17 pixels over that and is 9 pixels tall, so the bottom 39 pixels of the
	 * screen belong to the game.
	 *
	 * A title drawn across them reads as one more thing of the inventory rather than as a title over
	 * the world - and a name long enough to reach the columns the HUD takes is a name that would be
	 * drawn through it, so that is the one case the title is kept over the HUD for. The numbers are the
	 * game's own HUD read sprite by sprite, not a guess at it.
	 */
	private static final int CORNER_HUD_ROOM = 39;

	/**
	 * The room the corner title keeps over that, so that it still reads as a title of the corner:
	 * what is above the HUD is the world, and the title sits just over it.
	 */
	private static final int CORNER_HUD_GAP = 4;

	/** The color of the titles: pure white, only its alpha is animated by the fades. */
	private static final int TITLE_TEXT_RGB = 0xFFFFFF;

	/** Used when there is no biome to name, e.g. while a title is triggered from the main menu. */
	private static final Component FALLBACK_NAME = Component.literal("PALE GARDEN").withStyle(ChatFormatting.BOLD);

	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
			Identifier.fromNamespaceAndPath(WandererTitles.MOD_ID, "title"));

	private static final KeyMapping PLAY_TITLE = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.immersive_titles.play_title",
			InputConstants.Type.KEYBOARD,
			InputConstants.KEY_T,
			CATEGORY));

	/**
	 * The collection of biomes, which is what the mod's own key opens: the list is the half of the
	 * feature a player looks at, so it gets the key they are told about.
	 */
	private static final KeyMapping OPEN_COLLECTION = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.immersive_titles.open_collection",
			InputConstants.Type.KEYBOARD,
			InputConstants.KEY_K,
			CATEGORY));

	/**
	 * The corner title, on a key of its own because K now opens the collection. Both titles have a
	 * key of their own so that either can be seen without walking into the right biome for it.
	 */
	private static final KeyMapping SHOW_CORNER_TITLE = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.immersive_titles.show_corner_title",
			InputConstants.Type.KEYBOARD,
			InputConstants.KEY_J,
			CATEGORY));

	/** Whether the cinematic reveal is playing right now. */
	private static boolean revealPlaying = false;

	/**
	 * How much of the clip has been shown so far, in milliseconds. The frame of the reveal is read
	 * from this instead of from the clock: time is only ever added to it while the reveal is running,
	 * which is what lets the pause menu hold the reveal on the exact frame it was showing and lets
	 * closing the menu carry on from that frame instead of starting the clip again.
	 */
	private static long revealMs = 0L;

	/** Whether the corner title animation is playing right now. */
	private static boolean cornerPlaying = false;

	/**
	 * How much of the corner title animation has been played, in milliseconds. It holds with the
	 * reveal while the pause menu is open.
	 */
	private static long cornerMs = 0L;

	/**
	 * Wall clock time the overlay was drawn at last, which the time between two drawn frames is
	 * measured from.
	 */
	private static long lastFrameMs = 0L;

	/**
	 * The longest step a title takes from one drawn frame to the next, in milliseconds. A frame that
	 * took longer than this - the game stalling, the HUD being hidden and shown again - counts as a
	 * tenth of a second instead of letting the clip jump ahead over everything it missed.
	 */
	private static final long MAX_FRAME_STEP_MS = 100L;

	/** The name the running reveal writes into the plate. */
	private static Component revealName = null;

	/**
	 * The name the corner title shows: the last word of the title it was started with, drawn at
	 * {@link #CORNER_NAME_SCALE}. The words before it are the small word over it, or nothing while the
	 * name is a single word (see {@link #CORNER_KICKER_SCALE}).
	 */
	private static Component cornerName = null;

	/**
	 * The small word the corner title shows over its name, or null while the title has only one word
	 * to show. It keeps the style of the name it was cut out of, so a name a resource pack has
	 * coloured is still coloured as one word over one word.
	 */
	private static Component cornerKicker = null;

	/** The biomes of the current world that already had their reveal, so entering them again only
	 * gets the corner title. Loaded when the world is joined and written to disk as a biome is
	 * discovered, see {@link DiscoveredBiomes}. */
	private static final Set<String> VISITED_BIOMES = new HashSet<>();

	/** The structures of the current world that already had their reveal, read exactly like
	 * {@link #VISITED_BIOMES}: loaded when the world is joined, and written to disk as a structure is
	 * discovered, see {@link DiscoveredStructures}. */
	private static final Set<String> VISITED_STRUCTURES = new HashSet<>();

	/** The world {@link #VISITED_BIOMES} belongs to, or null while there is no world. */
	private static WorldIdentity world = null;

	/**
	 * The place the player was in when it was last checked, to notice a change, i.e. the biome
	 * {@link BiomeNames#place} reports and not the biome the game reports for them: a player who
	 * went into the ground under a biome never entered it, see {@link #checkBiome}.
	 */
	private static String lastBiomeId = null;

	/**
	 * The structure the player was inside when they were last checked, to notice entering one: it is
	 * read where the game keeps it (see {@link StructurePresence}), and it is null while they are inside
	 * none - so leaving a structure and coming back to it is a visit and a visit after it, which is what
	 * decides between the reveal and the corner title.
	 */
	private static String lastStructureId = null;

	/** Where the player stood when the world was joined, until they start moving. */
	private static Vec3 joinPosition = null;

	/**
	 * Whether the level the titles are following is loaded. The game loads a level both when a
	 * world is entered and when the level of the world the titles are already following is
	 * replaced (a respawn, a portal), so this is what tells the two apart: entering a world is the
	 * case that has to start over, see {@link #levelChanged}.
	 */
	private static boolean inLevel = false;

	/** Ticks left until the place the player is in is checked again. */
	private static int ticksUntilCheck = 0;

	/** The frame textures that are currently loaded, oldest first. */
	private static final Deque<Identifier> LOADED_FRAMES = new ArrayDeque<>();

	private TitleOverlay()
	{
	}

	public static void initialize()
	{
		// Render right before the chat, so the titles sit on top of the world and the HUD. Being a
		// HUD element is also what keeps the titles behind every screen the player opens: the game
		// extracts the HUD before it extracts a screen, so the inventory and the pause menu are
		// drawn over the titles instead of the titles being drawn over them.
		HudElementRegistry.attachElementBefore(
				VanillaHudElements.CHAT,
				Identifier.fromNamespaceAndPath(WandererTitles.MOD_ID, "title_overlay"),
				TitleOverlay::render);

		// A world is prepared the moment the game loads its level, and everything after that is
		// watched from the tick, see levelChanged and tick.
		ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register(TitleOverlay::levelChanged);
		ClientTickEvents.END_CLIENT_TICK.register(TitleOverlay::tick);

		// The structures of a world are known to the server side of a game and to nothing else, so
		// they are read where they are - which is the server of a world the player hosts themselves,
		// running in this same game - and the answers are kept here, see StructurePresence.
		StructurePresence.initialize();
	}

	/**
	 * Called by the game once it has loaded a level, i.e. when a world is entered.
	 *
	 * This is where a world is prepared: the identity it is remembered by together with the biomes
	 * that already had their reveal in it (see {@link WorldIdentity} and {@link DiscoveredBiomes}),
	 * and the state the titles start a world from - no biome seen yet, and no position the player
	 * can have moved on from. The game also loads a level when it replaces one, i.e. on a respawn
	 * or through a portal, so those walk through here too: they leave the world the titles are
	 * following alone, and the biome the player arrives in is titled right away, while a world that
	 * is actually being entered is the one case that starts over.
	 *
	 * The level is not used: what happens here belongs to the world, not to one of its levels.
	 */
	private static void levelChanged(Minecraft client, ClientLevel level)
	{
		if (inLevel)
		{
			return;
		}

		inLevel = true;
		joinPosition = null;
		lastBiomeId = null;
		lastStructureId = null;
		world = WorldIdentity.of(client).orElse(null);
		VISITED_BIOMES.clear();
		VISITED_BIOMES.addAll(DiscoveredBiomes.load(world));
		VISITED_STRUCTURES.clear();
		VISITED_STRUCTURES.addAll(DiscoveredStructures.load(world));

		// what the game last saw the player inside belongs to the world that was left: a structure of it
		// is not one of this one, so the titles wait for the game to say what the player is in now
		// instead of reading an answer about another world
		StructurePresence.clear();
		logWorld();
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

		while (OPEN_COLLECTION.consumeClick())
		{
			toggleCollection(client);
		}

		if (client.level == null)
		{
			// back in a menu: the discoveries of the world that was left are on disk, so the next
			// world is prepared from its own file when its level arrives, see #levelChanged
			inLevel = false;
			joinPosition = null;
			return;
		}

		if (client.player == null)
		{
			return;
		}

		if (joinPosition == null)
		{
			// the world has just been joined: the titles wait until the player actually moves, so
			// the first title is about the biome the player ends up standing in instead of about
			// whatever the camera saw while the world was still loading
			joinPosition = client.player.position();
			return;
		}

		if (client.player.position().distanceToSqr(joinPosition) >= MOVED_DISTANCE_SQR)
		{
			checkPlace(client);
		}
	}

	/**
	 * Watches the place the player is in, half a second at a time, and plays the titles of what changed:
	 * the biome they are in (see {@link #checkBiome}) and the structure the game says they are inside
	 * (see {@link #checkStructure}).
	 *
	 * The two are checked together because they are one question - where is the player - asked of the two
	 * sides that can answer it: the biome is read where the titles stand, out of the world the client was
	 * sent, and the structure is read where the game worked it out, on the server (see
	 * {@link StructurePresence}). What each of the two plays is the same: the big reveal for the first
	 * visit of that biome or structure in this world and the corner title for every visit after it, both
	 * out of the discoveries the world has written down.
	 *
	 * A check that finds both changed at once - a player who walks into a trial chamber in the same half
	 * second they cross the border of a biome - starts the two titles one after the other, and the
	 * second of them, the structure, is the one that stays: it is the rarer of the two and the one the
	 * player is standing inside.
	 */
	private static void checkPlace(Minecraft client)
	{
		if (ticksUntilCheck > 0)
		{
			ticksUntilCheck--;
			return;
		}

		ticksUntilCheck = CHECK_INTERVAL_TICKS;

		checkBiome(client);
		checkStructure(client);
	}

	/**
	 * Watches the biome the player is in and plays the matching title when it changes: the reveal on a
	 * first visit, the corner title on every visit after that.
	 *
	 * What is watched is {@link BiomeNames#place}, which is the biome the game reports for the player
	 * except where the game is reporting ground rather than a place: under the biome that covers the
	 * sky over their head, and in any biome the surface of the ground around them wears. That is what
	 * keeps a player in a cave from being told about the biome over the cave - down there the game
	 * reports the climate of the player's depth, which is the ground of the column they are under, or
	 * a biome whose region reaches under the ground of a neighbour at that depth - so a walk through
	 * a cave under one surface biome after another is not a walk into biomes, nothing is titled and
	 * nothing is written down as discovered, and the titles pick up again once the player is out in
	 * the open. A biome of its own is reported there just as it is anywhere else, so the cave biomes
	 * - a dripstone cave, a lush cave, the deep dark - are titled like any other biome, which is what
	 * the titles follow while a player is down there instead of the rock over their head.
	 */
	private static void checkBiome(Minecraft client)
	{
		String biomeId = BiomeNames.place(client).orElse(null);
		if (biomeId == null || biomeId.equals(lastBiomeId))
		{
			return;
		}

		lastBiomeId = biomeId;
		Component name = BiomeNames.of(client).orElse(FALLBACK_NAME);

		if (VISITED_BIOMES.add(biomeId))
		{
			DiscoveredBiomes.remember(world, biomeId);
			startReveal(name);
		}
		else
		{
			startCornerTitle(name);
		}
	}

	/**
	 * Watches the structure the game says the player is inside and plays the matching title when they
	 * enter one: the reveal on their first visit to it in this world, the corner title on every visit
	 * after that - exactly what {@link #checkBiome} does for a biome, out of a second set of discoveries
	 * that is remembered per world the same way (see {@link DiscoveredStructures}).
	 *
	 * What it watches is {@link StructurePresence}, which is the game's own answer. A structure of a
	 * world is not something the client is sent: the world is generated with it on the server, the game
	 * keeps its own knowledge of who is inside one there, and that is where the mod asks for it - which
	 * in a world the player hosts themselves is this same game (see {@link StructurePresence}). A world
	 * on a server is the case this cannot reach, and it is this method where that shows: nothing is ever
	 * inside a structure, the structure stays null, no title of one is played and nothing is written
	 * down, while the biomes of that world are titled as they always were.
	 */
	private static void checkStructure(Minecraft client)
	{
		String structureId = StructurePresence.of(client).orElse(null);
		if (structureId == null || structureId.equals(lastStructureId))
		{
			return;
		}

		lastStructureId = structureId;
		Component name = StructureNames.displayName(structureId);

		if (VISITED_STRUCTURES.add(structureId))
		{
			DiscoveredStructures.remember(world, structureId);
			startReveal(name);
		}
		else
		{
			startCornerTitle(name);
		}
	}

	/**
	 * Opens the collection, or closes it again while it is open, so that one key both
	 * shows the list and puts it away.
	 *
	 * A screen the player opened themselves is left alone: the collection only opens on a client that
	 * has nothing on screen at the moment and is in a world, because the biomes it lists are the ones
	 * that world registered. Pressing the key while the list is open is what closes it: the key
	 * mapping is consumed here, on the client tick, and not by the screen, so the press that closes
	 * the screen cannot be seen a second time and open it again.
	 */
	private static void toggleCollection(Minecraft client)
	{
		Screen screen = client.gui.screen();

		if (screen instanceof BiomeCollectionScreen collection)
		{
			collection.onClose();
			return;
		}

		if (screen != null || client.level == null)
		{
			return;
		}

		// opened from the game rather than out of another screen, so it closes back into the world
		client.setScreenAndShow(new BiomeCollectionScreen(client.level, discoveredBiomes(),
				discoveredStructures(), null));
	}

	/**
	 * The biomes of the world the player is in that already had their reveal, i.e. the ones the
	 * collection marks as found. A copy, so that nothing the screen does with its own list changes
	 * what the reveals know: a discovery it takes back is taken back through {@link #forgetBiome},
	 * and an empty set while there is no world.
	 */
	static Set<String> discoveredBiomes()
	{
		return Set.copyOf(VISITED_BIOMES);
	}

	/**
	 * The structures of the world the player is in that already had their reveal, i.e. the ones the
	 * collection marks as found on its other page: a copy for the same reason {@link #discoveredBiomes}
	 * is one, and an empty set while there is no world.
	 */
	static Set<String> discoveredStructures()
	{
		return Set.copyOf(VISITED_STRUCTURES);
	}

	/**
	 * Forgets that the biome {@code biomeId} had its reveal in the world the player is in, so that
	 * entering it plays the reveal again: the biome is dropped from what this session remembers,
	 * which is what the titles read on every check, and its line is taken out of the world's own file,
	 * which is what a later session loads (see {@link DiscoveredBiomes#forget}).
	 *
	 * The collection screen is what asks for this, after warning the player about it (see
	 * {@link BiomeCollectionScreen}); a biome that was never discovered changes nothing, and so does
	 * a world whose discoveries were never written down.
	 */
	static void forgetBiome(String biomeId)
	{
		if (!VISITED_BIOMES.remove(biomeId))
		{
			return;
		}

		DiscoveredBiomes.forget(world, biomeId);
	}

	/**
	 * Forgets that the structure {@code structureId} had its reveal in the world the player is in, so
	 * that entering it plays the reveal again: it is dropped from what this session remembers, which is
	 * what the titles read on every check, and its line is taken out of the world's own file, which is
	 * what a later session loads (see {@link DiscoveredStructures#forget}).
	 *
	 * It is the counterpart of {@link #forgetBiome} for the other kind of discovery, and the collection
	 * screen is what asks for it, after warning the player about it (see {@link BiomeCollectionScreen}).
	 */
	static void forgetStructure(String structureId)
	{
		if (!VISITED_STRUCTURES.remove(structureId))
		{
			return;
		}

		DiscoveredStructures.forget(world, structureId);
	}

	/**
	 * Says once per world which world the titles are being kept for and how much of it is already
	 * discovered, so a world whose own folder cannot be reached shows up in the log instead of
	 * quietly losing its discoveries.
	 */
	private static void logWorld()
	{
		if (world == null)
		{
			WandererTitles.LOGGER.warn("This world cannot be identified; the biome and structure"
					+ " reveals of this session will be remembered in this session only");
		}
		else
		{
			WandererTitles.LOGGER.info("Reveals of world {}: {} biomes and {} structures already"
					+ " discovered", world.id(), VISITED_BIOMES.size(), VISITED_STRUCTURES.size());
		}
	}

	/**
	 * Starts the cinematic reveal from its first frame, writing {@code name} into the plate for the
	 * length of the clip and playing the sound that goes with the plate arriving.
	 */
	private static void startReveal(Component name)
	{
		revealName = name;
		revealMs = 0L;
		revealPlaying = true;

		// the big reveal takes over from the corner title
		cornerName = null;
		cornerKicker = null;
		cornerMs = 0L;
		cornerPlaying = false;

		playRevealSound();
	}

	/**
	 * Starts the corner title, which fades in, holds and fades out on its own.
	 *
	 * The name is cut into the small word over it and the name itself here, once, rather than on every
	 * drawn frame: the title is the last word of the name, e.g. the GARDEN of PALE GARDEN, and every
	 * word before it is the small word that stands over it. A name of one word is a name without a
	 * word over it, and the small title of a one-word name is the name alone.
	 */
	private static void startCornerTitle(Component name)
	{
		String text = name.getString();
		int lastSpace = text.lastIndexOf(' ');

		if (lastSpace < 0)
		{
			cornerKicker = null;
			cornerName = name;
		}
		else
		{
			cornerKicker = Component.literal(text.substring(0, lastSpace)).withStyle(name.getStyle());
			cornerName = Component.literal(text.substring(lastSpace + 1)).withStyle(name.getStyle());
		}

		cornerMs = 0L;
		cornerPlaying = true;
	}

	/**
	 * Plays the sound of the plate arriving, once, as the big reveal starts. It goes to the
	 * {@code MASTER} channel - the one the game pauses along with itself - so that pressing escape in
	 * a single player world holds the sound exactly like it holds the picture and both pick up where
	 * they stopped; a UI sound would keep playing through the pause, and the reveal would come back
	 * to a sound that has already finished.
	 */
	private static void playRevealSound()
	{
		Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(
				REVEAL_SOUND.location(),
				SoundSource.MASTER,
				1.0F,
				1.0F,
				SoundInstance.createUnseededRandom(),
				false,
				0,
				SoundInstance.Attenuation.NONE,
				0.0D,
				0.0D,
				0.0D,
				true));
	}

	/**
	 * Draws the titles and moves them on by the time since the last drawn frame.
	 *
	 * That step is what holds a title still while the pause menu is open: no time is added while it is
	 * there, so the reveal keeps standing on the exact frame it was showing - on screen, behind the
	 * menu the player opened - and when the menu is closed the reveal carries on from that same frame
	 * to the end of the clip instead of playing the clip from its start again. The small corner title
	 * holds the same way.
	 */
	private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker)
	{
		long now = Util.getMillis();
		long elapsedMs = held() ? 0L : Math.min(now - lastFrameMs, MAX_FRAME_STEP_MS);
		lastFrameMs = now;

		revealMs += elapsedMs;
		cornerMs += elapsedMs;

		renderSequence(graphics);
		renderCornerTitle(graphics);
	}

	/**
	 * Whether the pause menu is open over the world and is holding the titles still.
	 *
	 * It is the menu that escape opens, together with the screens opened from it - what the game
	 * itself counts as a pause screen - and nothing else. The inventory, a chest, the chat and the
	 * other screens do not pause the game and are no reason to stop the titles, so a reveal keeps
	 * running behind them and is further along, or already over, when they are closed, instead of
	 * standing still for as long as a container was open.
	 *
	 * The pause menu is what a reveal has to survive instead, because escape is what a player presses
	 * to step out of the game: the frame a reveal holds behind the menu is the frame it carries on
	 * from once the menu is closed, see {@link #render}.
	 */
	private static boolean held()
	{
		Minecraft client = Minecraft.getInstance();
		Screen screen = client.gui.screen();

		if (screen != null && screen.isPauseScreen())
		{
			return true;
		}

		Overlay overlay = client.gui.overlay();
		return overlay != null && overlay.isPausing();
	}

	/** Draws the current frame of the cinematic reveal, as long as a playback is running. */
	private static void renderSequence(GuiGraphicsExtractor graphics)
	{
		if (!revealPlaying)
		{
			return;
		}

		int frame = (int) (revealMs * FRAME_FPS / 1000L);
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
	 * drawn in so the two stay in step at any screen size and gui scale, and centred on the middle of
	 * that box as the frames measure it (see {@link #NAME_CENTER_X}).
	 */
	private static void renderName(GuiGraphicsExtractor graphics, int plateX, int plateY, float scale, int frame)
	{
		int alpha = nameAlpha(frame);
		if (alpha <= 0 || revealName == null)
		{
			return;
		}

		Font font = Minecraft.getInstance().font;
		float textScale = Math.min(
				NAME_ROOM_HEIGHT * scale * NAME_MAX_HEIGHT / FONT_CAP_HEIGHT,
				NAME_ROOM_WIDTH * scale * NAME_MAX_WIDTH / Math.max(1, font.width(revealName)));
		float textWidth = font.width(revealName) * textScale;
		float textHeight = FONT_CAP_HEIGHT * textScale;

		// the middle of the plate, put where the plate itself is on this screen: down is measured from
		// the row of the picture the plate is drawn from, since the rows above it are cropped away
		float centerX = plateX + NAME_CENTER_X * scale;
		float centerY = plateY + (NAME_CENTER_Y - FRAME_CROP) * scale;

		// what is centred is the cap height of the name and not its whole line: the game draws the
		// capitals a title is set in from the top of the cell down this far, so that band is the line
		// as a reader sees it, and the empty room the line keeps under its descenders is not part of
		// the block that has to sit in the middle
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(centerX - textWidth / 2.0F, centerY - textHeight / 2.0F);
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

	/**
	 * Draws the small corner title of a place the player has already seen: the name, and the small
	 * word it is read under.
	 *
	 * The name is drawn at the corner of the screen (see {@link #cornerNameRow}), at
	 * {@link #CORNER_MARGIN} from the left edge of it, and the small word is placed against the name:
	 * centred on it, and set {@link #CORNER_LINE_GAP} over the top of its capitals.
	 */
	private static void renderCornerTitle(GuiGraphicsExtractor graphics)
	{
		if (!cornerPlaying || cornerName == null)
		{
			return;
		}

		if (cornerMs >= CORNER_ANIMATION_MS)
		{
			cornerPlaying = false;
			cornerMs = 0L;
			cornerName = null;
			cornerKicker = null;
			return;
		}

		Minecraft client = Minecraft.getInstance();
		Font font = client.font;
		int x = CORNER_MARGIN;
		int y = cornerNameRow(client, font);
		int color = (cornerAlpha(cornerMs) << 24) | TITLE_TEXT_RGB;

		drawCornerWord(graphics, font, x, y, color);

		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(CORNER_NAME_SCALE, CORNER_NAME_SCALE);
		graphics.text(font, cornerName, 0, 0, color, true);
		pose.popMatrix();
	}

	/**
	 * The row of the screen the name of the corner title is drawn at, which is the row its capitals
	 * start on: the game font draws a capital from the row it is drawn at.
	 *
	 * The title is drawn in the corner of the screen, the foot of its name
	 * {@link #CORNER_BOTTOM_MARGIN} gui pixels over the foot of the screen, because the left of the
	 * game's own HUD is empty world: its bar is 182 gui pixels wide and centred, so what is left of
	 * {@link #CORNER_HUD_BAR_HALF} of the middle of the screen belongs to the world and not to the HUD.
	 *
	 * That holds while the name stays left of that bar. A name of a mod's own, or a screen narrow
	 * enough, can reach it - and a title drawn over the columns of the hotbar is a title drawn through
	 * the game's own inventory, which is the one thing this title is not allowed to be. So that one
	 * case is read here and the title is put back over the HUD on it (see {@link #CORNER_HUD_ROOM}),
	 * which is where the title was drawn before the owner asked for it in the corner.
	 */
	private static int cornerNameRow(Minecraft client, Font font)
	{
		Window window = client.getWindow();
		float nameRoom = CORNER_MARGIN + font.width(cornerName) * CORNER_NAME_SCALE;

		if (nameRoom > window.getGuiScaledWidth() / 2.0F - CORNER_HUD_BAR_HALF)
		{
			return window.getGuiScaledHeight() - CORNER_HUD_ROOM - CORNER_HUD_GAP
					- (int) (font.lineHeight * CORNER_NAME_SCALE);
		}

		return window.getGuiScaledHeight() - CORNER_BOTTOM_MARGIN
				- (int) (FONT_CAP_HEIGHT * CORNER_NAME_SCALE);
	}

	/**
	 * Draws the small word of the corner title over the name, centred on it, as long as it has one.
	 *
	 * The word is drawn one letter at a time, which is the only way the game font can be made to keep
	 * room between its letters: what the pen moves by is the advance of the letter plus
	 * {@link #CORNER_KICKER_SPACING}, which is what the letters are drawn at.
	 *
	 * It is centred on the name rather than on the screen (see {@link #CORNER_MARGIN}): the two lines
	 * are one title, and the picture the owner sent has the small word of its own standing over the
	 * middle of the name - the middle of its name measures column 208.0 and of its small word 206.0.
	 * The word is put over the name by the ink of the name's capitals, which is exactly what
	 * {@link #FONT_CAP_HEIGHT} measures: the game font draws a capital from the row it is drawn at, so
	 * the top of the name is the row the name is drawn at, whichever word of the two is longer.
	 */
	private static void drawCornerWord(GuiGraphicsExtractor graphics, Font font, int x, int y, int color)
	{
		if (cornerKicker == null)
		{
			return;
		}

		String word = cornerKicker.getString();
		float wordWidth = (font.width(cornerKicker) + CORNER_KICKER_SPACING * (word.length() - 1))
				* CORNER_KICKER_SCALE;
		float nameWidth = font.width(cornerName) * CORNER_NAME_SCALE;
		int wordX = x + Math.round((nameWidth - wordWidth) / 2.0F);
		int wordY = y - CORNER_LINE_GAP - (int) (FONT_CAP_HEIGHT * CORNER_KICKER_SCALE);

		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(wordX, wordY);
		pose.scale(CORNER_KICKER_SCALE, CORNER_KICKER_SCALE);

		int pen = 0;

		for (int letter = 0; letter < word.length(); letter++)
		{
			Component one = Component.literal(word.substring(letter, letter + 1))
					.withStyle(cornerKicker.getStyle());
			graphics.text(font, one, pen, 0, color, true);
			pen += font.width(one) + CORNER_KICKER_SPACING;
		}

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
		revealPlaying = false;
		revealMs = 0L;
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
