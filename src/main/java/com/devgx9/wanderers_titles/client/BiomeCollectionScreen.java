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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The collection of biomes: every biome the game in front of the player has, with a check on the
 * ones that already had their reveal in this world - or, when the mod list of Mod Menu is the way
 * in, every biome any of the player's worlds has found, counted across all of them at once.
 *
 * It is the list side of what the mod already does. A reveal is remembered per world (see
 * {@link WorldIdentity} and {@link DiscoveredBiomes}), and what a world remembers is the same set
 * this screen marks, so the two can never disagree about what the player has found: what is
 * checked here is exactly what no longer plays its reveal, and what is not checked is what still
 * has one waiting. The bar at the top is that same count.
 *
 * The screen is also where a discovery can be given back. A row of a discovered biome offers to
 * forget it while the mouse is on the row, and forgetting one is asked about first (see
 * {@link #askToForget}): it drops the biome from what the world remembers - on disk with
 * {@link DiscoveredBiomes#forget}, in this session with {@link TitleOverlay#forgetBiome} - so the
 * reveal plays again the next time the biome is entered. That is for a player who wants a reveal
 * back: a biome walked into by accident, a world whose file was edited by hand, a discovery that was
 * written down wrongly. It is the one thing in this screen that changes anything, which is why it is
 * the one thing that comes with a warning.
 *
 * The order is the mod's own, from the biomes a player meets while walking around to the ones that
 * take a portal or a search (see {@link BiomeCatalog}), because a list that starts with the biomes
 * everyone has is a list that says something: what is left below them is what is left to find. The
 * two buttons beside the list are for reading that list two ways: each one keeps a single end of it
 * - what was found, and what is left - so the question "what have I got left" is one press away
 * instead of a scroll through everything already found (see {@link #toggleFilter}).
 *
 * The list is divided by dimension before it is read in any other order: the biomes of the overworld
 * together at the top, then the nether's, then the end's, and last the ones that are no dimension's
 * (see {@link BiomeCatalog.Dimension}) - each of them under a line of the mod's own drawing that names
 * the dimension and counts what the collection has of it. Which dimension a biome is in is the game's
 * own answer and not the mod's: a world generates its dimensions from the biome tags it holds, and
 * those are what the list is divided by (see {@link BiomeCatalog#all}), so a biome of a mod is filed
 * under the dimension its author wrote it for, and a player can read what of each dimension is left
 * to find instead of scrolling one list of everything the game holds. A section the filter has
 * emptied is left out with the biomes it would have held rather than standing as a line over nothing.
 *
 * The collection is read as two pages, and the two tabs over it are which one: the biomes, which are
 * the list this screen is (see {@link Page}), and the structures of the world, which the mod does not
 * collect yet - that tab and its page are the interface of a collection still to come, and what the
 * page shows is the panel with a line saying that there is nothing there rather than a list of
 * nothing. The two belong to the list and not to the screen: the title, the tabs, the frame and the
 * button that closes it are the same on both pages, while the line counting the discoveries and the
 * two filters belong to the biomes and step aside on the page that has no biomes, since neither has
 * anything to count or to filter there.
 *
 * Everything the screen is made of is a sprite of the gui - the pack's, in the shape the screen draws
 * them in, so that the collection is the same dark one whatever a player runs - everything but the
 * two lines the list is framed with, which are the mod's own drawing:
 *
 * - every biome is a row drawn with the pack's button sprite, a biome that was found with its name in
 *   full white and a check beside it, and one whose reveal is still waiting with the sprite the game
 *   draws a button it will not let anyone press in, dimmed with its name - so what has been found is
 *   what stands out and what is left to find is what stays out of the way,
 * - the panel the rows sit on and the frame around it are one sprite of the pack - its own window, the
 *   frame of a list of things, drawn {@link #FRAME_PADDING} past the list on every side and nine
 *   sliced to the size the block comes to - with the block dropping a light shadow under it (see
 *   {@link #FRAME_SHADOW}), so what holds the list is one drawing, window and panel at once, with the
 *   strip of the two tabs resting on the top of it (see {@link #TABS_GAP}),
 * - the two lines framing the list are the mod's own - a needle with a thread through it, drawn once
 *   over the list and once under it - each drawn centred on the list at the size it is made at rather
 *   than stretched to a width of its own (see {@link #drawLine}), the one over it being the plate the
 *   two tabs stand under and as wide as the two of them together (see {@link #TAB_STRIP_WIDTH}),
 * - the line that opens a section of the list is the mod's own drawing too: the name of the dimension
 *   at the left end, where the names of the biomes under it are, the count of that dimension at the
 *   right end, where the names of the mods a biome comes from are, and a rule across the row over the
 *   two says that a section starts here rather than that the list has a biome with a strange name,
 * - the scrollbar is the game's scroller, in four pixels of width rather than the six the game draws
 *   it in (see {@link #SCROLLBAR_WIDTH}), and it can be taken and dragged like any other, see
 *   {@link #mouseClicked}, and
 * - the two tabs over the list, the two filters beside it and the button that closes the screen are
 *   buttons of the game, so they hover, click and speak for themselves, and what the screen draws
 *   over them is what they say and what they wear: the face of each is the pack's own button, painted
 *   over the sprite the game drew the button with (see {@link #drawButtonFaces}, {@link #drawTabs},
 *   {@link #drawFilterIcons} and {@link #drawDoneLabel}). The list is narrower than the screen and
 *   centred, which leaves the room at its sides that the two small filters stand in: each of the two
 *   is set off the frame of the list rather than off the list itself, so neither is drawn over the
 *   block it belongs to (see {@link #filterButtonX}).
 *
 * Lines of the mod's own drawing in a screen of the game's own pieces is a deliberate exception.
 * The rows have to read as the game's own rows, because they are a list of what the game holds, but
 * the frame around them is where the mod says what kind of list this is - a pair of drawn ornaments
 * over and under it reads as a collection, where the game's own two-pixel separators read as a menu -
 * and the same is asked of the one line inside it: what opens a section has to read as a heading over
 * the biomes under it, and the game draws no heading over a list of its own.
 *
 * The collection is a block of the screen rather than the screen itself, and it is set on the screen
 * the way a block of one is: it is given at most {@link #LIST_MAX_HEIGHT_PERCENT} of the screen's
 * height and {@link #LIST_MAX_WIDTH} gui pixels of its width, the rows sit a pixel closer together,
 * and the whole of it - from the top of its title to the bottom of the button that closes it - is
 * what the room left over is split around, so that the room over the title and the room under the
 * button are the same, to the pixel (see {@link #blockTop}). What that leaves is a collection that
 * reads as a block with the world around it rather than as a list stretched down the screen, and one
 * that sits in the middle of the screen instead of hanging from its top. A screen too short to hold
 * even the smallest of it keeps {@link #SCREEN_MARGIN} over and under the block and gives the list
 * everything between them.
 *
 * The rows are drawn rather than made of widgets, because there is next to nothing to press on a
 * biome, and the one thing that can be pressed is drawn where it is shown: a row of a discovered
 * biome that the mouse is on gives up the place of the name of the mod it comes from to a button of
 * the game with a cross in it - a button like the two that filter the list, in the same size and
 * with a mark of the game's own font over it - and pressing that button is what asks about forgetting
 * the biome (see {@link #askToForget}). The row the mouse is on is held in a thin line of the
 * collection's own gold, which is what says which row is being read - and with it which biome's
 * name the button at the end of the row belongs to - and the row under it stays a row that cannot
 * be clicked, hovered into a highlight or focused, which is what keeps a list of what the game
 * holds from reading as a list of things to press. Everything else the player can do is scroll,
 * filter and close.
 *
 * Scrolling is smooth: the wheel and the keys move where the list is going, not where it is drawn,
 * and the drawn list eases after it (see {@link #advanceScroll}). Rows therefore slide up and down
 * instead of jumping, by whole pixels, so the text moves without shimmering. The step is taken from
 * the clock rather than from a fixed amount per frame, so the slide takes the same time at any
 * frame rate, and it is capped so that a frame the game spent stalled cannot skip the list over
 * everything it missed. A scrollbar being dragged is the one case that is not eased: there the hand
 * on the mouse is what moves the list, so the list is put exactly where the mouse is.
 *
 * The screen is left at the game's own defaults wherever it matters. It does not override
 * {@code isPauseScreen}, which is true for a screen in this version of the game, so opening it
 * pauses a single player world exactly like the escape menu does, and a title reveal that is running
 * when it opens holds with it, because a pause screen is exactly what a running reveal waits for.
 * What it does not take from the game is the two things the game draws behind such a screen: the
 * world behind it is neither blurred nor read through the game's own gradient but through a veil of
 * the mod's own, a little lighter (see {@link #extractBackground}), because a collection read with
 * the world still a world behind it is the point of the block of the screen the collection is set in.
 * A screen opened with no world behind it at all - the mod list of Mod Menu, from the title screen -
 * keeps the game's own background, since a panorama is a thing to leave alone.
 *
 * The collection is read in one look, and it is a dark one, because the six sprites every shape in it
 * is made of are the dark gui of the resource pack Reimagined GUI (Dark): the window the list is held
 * in, the buttons its rows and its two tabs are, the experience bar under the count and the two small
 * filters beside the list are that pack's drawing, and the two of them the screen draws itself out of
 * are named out of the mod's own namespace - so the pack has been copied into the mod rather than
 * leaned on, and the collection is that drawing whether or not a player runs the pack itself. The
 * copy was made with the permission of the pack's author and is credited beside the files
 * ({@code assets/immersive_titles/textures/gui/sprites/CREDITS.md}), and the one sprite of the screen
 * that is still the game's own - the scroller - is left so on purpose: the pack redraws no scroller,
 * and a list of what the game holds is scrolled with the game's own bar. What is not a sprite at all
 * is the two lines the list is framed with (see {@link #drawLine}) and the colours of the screen,
 * which are the colours of that drawing: the gold of a dimension and of the row the mouse is on, the
 * green of a check, and the greys the text is read in by brightness, so what has been found, what is
 * left to find and what is only a name are told apart before a word of them is read (see
 * {@link #TITLE_COLOR}).
 *
 * The key that opens it is the mod's own (see {@link TitleOverlay}), and pressing it again closes
 * the screen: the key is consumed on the client tick rather than in the screen, so the same press
 * that closes the screen cannot open it again on the way out. The same screen is what the mod list
 * of Mod Menu opens, since it is the one screen this mod has (see {@link ModMenuIntegration}), and
 * that way in asks a different question than the key does: the key shows a world, while the mod list
 * shows the collection itself - every biome found in any of the player's worlds, read together out
 * of the files each world keeps its own discoveries in (see {@link DiscoveredBiomes#loadAll}), with
 * no world having to be open behind it. What is checked there is what was found anywhere, the line
 * under the title counts all of it, and nothing offers to forget a biome: a discovery is a discovery
 * of a world, and a row of that list is a biome found somewhere rather than in the world the player
 * is in, so there is no one file to take it back out of (see {@link #canForget}).
 */
final class BiomeCollectionScreen extends Screen
{
	/** Which biomes the list is showing: all of them, or only one end of them. */
	private enum Filter
	{
		/** Every biome the list has, found or not. */
		ALL,
		/** Only the biomes that already had their reveal here. */
		DISCOVERED,
		/** Only the biomes that have not had it yet, i.e. what is left to find. */
		REMAINING
	}

	/**
	 * The two pages of the collection, which are the two tabs over the list: the one the screen is, and
	 * the one that is not a list yet.
	 *
	 * The biomes are what this screen is for: every biome a world has, each of them checked or waiting
	 * (see {@link BiomeCatalog}). The structures are the shape of a collection still to come - the mod
	 * records nothing about them - so the second page is the interface of one and nothing else: the
	 * title, the tabs, the frame and the button that closes the screen are the same on both pages, and
	 * where the list of biomes would be, that page says that nothing is collected there rather than
	 * showing nothing (see {@link #rebuildShown} and {@link #drawRows}). The count line and the two
	 * filters belong to the biomes and step aside while it is shown, since neither has anything to count
	 * or to filter there.
	 */
	private enum Page
	{
		/** Every biome the world has: the page the screen opens on, and the list it is made of. */
		BIOMES("screen.immersive_titles.collection.tab.biomes"),
		/** The structures of the world, which no world writes down yet: the interface of a collection. */
		STRUCTURES("screen.immersive_titles.collection.tab.structures");

		/** The key the name of this tab is read from, in the language file of the mod. */
		private final String key;

		Page(String key)
		{
			this.key = key;
		}

		/**
		 * The name of this tab, as it is drawn in it. It is the name of the page and of the tab at once,
		 * since the two are the same word: what the strip says is which page the list below it holds.
		 */
		private MutableComponent label()
		{
			return Component.translatable(key);
		}
	}

	/**
	 * One line of the list as it is drawn: the line that opens a section of the collection, or one biome
	 * under it.
	 *
	 * The two are rows of the same height and the same width, and that is the whole of how the list
	 * works: it is drawn, scrolled and read from the mouse a row at a time (see {@link #rowTop} and
	 * {@link #rowAt}), so a section is a row of the list like any other and everything that counts rows
	 * counts it - which is what keeps a list of sections scrolling exactly like the list of biomes it
	 * was. What the two are told apart for is what is drawn in them and what can be pressed in them: a
	 * biome can be hovered and forgotten, a section is read.
	 */
	private sealed interface Row
	{
		/**
		 * The line that opens the section of one dimension: which dimension it holds, and what the
		 * collection has of it - how many of its biomes were found and how many of them there are, both
		 * counted over the whole dimension rather than over what the filter is showing, so that the line
		 * says what is left to find in that dimension and not what of it happens to be on the screen.
		 */
		record Section(BiomeCatalog.Dimension dimension, int found, int total) implements Row
		{
		}

		/** One biome of the collection, drawn under the line of the dimension it belongs to. */
		record Biome(BiomeCatalog.Entry entry) implements Row
		{
		}
	}

	/**
	 * Height of one row, i.e. of one biome: a line of the game's font with the room around it a row
	 * needs, rather than the height of a button of the game. The rows are a list to read, not buttons
	 * to press - there is nothing to press on a biome - so they are drawn as small as the text in them
	 * allows, which is what leaves the collection small enough for a screen to hold it with room around
	 * it instead of filling the screen with it.
	 *
	 * They are drawn two gui pixels shorter than they were, which is the whole of the room the game's
	 * font needs around its own line: a row is a line of nine pixels with two and a half above it and
	 * the same under it, and what those four pixels buy is two more rows of the collection on any
	 * screen - a list read for what is still missing is read by the row, so the row is what the screen
	 * spends its room on rather than the padding around it.
	 */
	private static final int ROW_HEIGHT = 14;

	/**
	 * Room left between two rows, a pixel less than the list used to leave: the collection is drawn a
	 * little smaller than it was, see {@link #LIST_MAX_HEIGHT_PERCENT}.
	 */
	private static final int ROW_SPACING = 2;

	/** How far apart two rows are drawn, i.e. a row and the room under it. */
	private static final int ROW_PITCH = ROW_HEIGHT + ROW_SPACING;

	/** How far one turn of the wheel, or one arrow key, moves the list, in rows. */
	private static final int SCROLL_STEP = 2;

	/**
	 * How long the drawn list takes to catch up with where the wheel has put it, in milliseconds.
	 * It is the time constant of the easing: after this much time a little under two thirds of the
	 * way is covered and what is left is a short glide, so a turn of the wheel reads as the rows
	 * sliding rather than as the list being somewhere else.
	 */
	private static final double SCROLL_EASE_MS = 90.0D;

	/**
	 * The longest step the slide takes from one drawn frame to the next, in milliseconds. A frame
	 * that took longer than this - the game stalling, the world being loaded in the background -
	 * counts as a tenth of a second instead of letting the list jump over everything it missed.
	 */
	private static final double MAX_SCROLL_STEP_MS = 100.0D;

	/** How close to where it is going the list has to be before it is simply put there, in rows. */
	private static final double SCROLL_SETTLED_ROWS = 0.01D;

	/**
	 * The button the scrollbar is taken with, as the game numbers the buttons of a mouse.
	 *
	 * It is the game's own {@code MOUSE_BUTTON_LEFT} rather than the zero a mouse button looks like it
	 * should be, because this version of the game numbers a mouse from one: left is one, middle is two
	 * and right is three, and everything of the game's own input agrees with that - down to a widget
	 * accepting a press of the button numbered one. A screen that took its scrollbar with the button
	 * numbered zero would never be handed one, and its scrollbar would be a picture of a scrollbar:
	 * that is exactly what this screen's was before it read the number off the game.
	 */
	private static final int LEFT_BUTTON = InputConstants.MOUSE_BUTTON_LEFT;

	/**
	 * Where the title of the screen and the line counting the discoveries are drawn, measured from the
	 * top of the block the whole collection is set in rather than from the top of the screen (see
	 * {@link #blockTop}).
	 */
	private static final int TITLE_Y = 8;
	private static final int PROGRESS_Y = 20;

	/**
	 * How much of its own size the title of the screen is set at, in hundredths of it.
	 *
	 * The name of the collection is the one line of the screen that names the screen rather than saying
	 * anything about the collection, so it is the one line drawn smaller than the game's own font: at
	 * four fifths of it, the title stands over the line counting the discoveries instead of competing
	 * with it, and what the screen is read for - how much of the world has been found - is the line the
	 * eye lands on first. The game's font is the only font here (see the class of this screen), so the
	 * size is the whole of how the two are told apart, and a hundredth of it is all the room the size
	 * has to be read in: the font is drawn at the size it is asked for and nothing is snapped.
	 */
	private static final int TITLE_SIZE_PERCENT = 80;

	/** How tall a line of the game's own font is, which every gap of the layout is measured in. */
	private static final int TEXT_HEIGHT = 9;

	/**
	 * The bar drawn under the line that counts the discoveries, in gui pixels: how wide it is, how tall
	 * it is, and how far it is kept off the line of text above it.
	 *
	 * It is the same count as that line, read at a glance instead of word by word: the trough of the
	 * game's own experience bar under the middle of the screen with the found share of it filled in (see
	 * {@link #drawProgressBar}). The size is the sprite's own - a hundred and eighty-two gui pixels wide
	 * and five tall, of which the first row is the air the sprite keeps over its own bar - and it is
	 * drawn at that size rather than scaled to a size of the mod's, because a bar drawn out of one-pixel
	 * lines is a bar that cannot be scaled: the whole of what makes it read as a bar rather than as a
	 * smear is one texture pixel to one gui pixel, and a share of it is a share of the lines themselves.
	 * It stands in the room between the count and the plate of the screen, so what it costs the block is
	 * the five pixels and the two gaps; what it buys is a screen whose first line answers the question
	 * the collection is opened with - how much is left - before a word of it is read.
	 */
	private static final int BAR_WIDTH = 182;
	private static final int BAR_HEIGHT = 5;
	private static final int BAR_GAP = 2;

	/** Where the bar is drawn, from the top of the block, and where the plate follows it. */
	private static final int BAR_Y = PROGRESS_Y + TEXT_HEIGHT + BAR_GAP;

	/**
	 * The two lines the list is framed with, one over it and one under it. They are the mod's own
	 * drawing: a needle with a thread through it, white on nothing, drawn where it is read (see
	 * {@code templates/collection_frame}), and the list is drawn between the two of them as if they were
	 * the top and the bottom of its frame.
	 */
	private static final Identifier UP_LINE = Identifier.fromNamespaceAndPath(WandererTitles.MOD_ID,
			"textures/gui/collection_up_line.png");
	private static final Identifier DOWN_LINE = Identifier.fromNamespaceAndPath(WandererTitles.MOD_ID,
			"textures/gui/collection_down_line.png");

	/**
	 * The size the two drawings are made at, which is the size they are drawn at: one texture pixel to
	 * one gui pixel, straight from {@code templates/collection_frame} (see
	 * {@code draw_ornaments.py} there).
	 *
	 * A drawing made of one-pixel threads is a drawing that cannot be scaled down. A stroke of it is
	 * the whole of the stroke, so a share of the drawing is a share of a stroke and nothing else - a
	 * stroke of a third of a pixel, which is a grey smear over whatever is behind it. That is what the
	 * two drawings these replaced had become: line art of a dense kind, cut down to the size of the
	 * screen and read as a white blur that competed with the list it framed. So this drawing is not
	 * written large and drawn at a share of itself; it is written at the size below, with threads of
	 * one pixel and a needle of six, and {@link #drawLine} draws one pixel of it as one pixel of the
	 * screen.
	 *
	 * The size is what the two cost the block, and it is the same decision twice: the line over the
	 * list is as wide as the strip of the two tabs under it (see {@link #TAB_STRIP_WIDTH}), since the
	 * plate of the screen and the bar of its two pages are one thing, and the line under the list is
	 * smaller again, so that the pair reads as the head and the foot of one block rather than as the
	 * same ornament cut twice. Both are about a quarter shorter than the drawings they replace - 30 gui
	 * pixels to 22, 26 to 18 - and both carry about a twentieth of their box in ink where the old ones
	 * carried two fifths of theirs, which is the whole of what changed about them.
	 */
	private static final int UP_LINE_WIDTH = 228;
	private static final int UP_LINE_HEIGHT = 22;
	private static final int DOWN_LINE_WIDTH = 140;
	private static final int DOWN_LINE_HEIGHT = 18;

	/**
	 * The gap kept between a line and the list it frames, and between a line and the line of text it
	 * sits under: the drawing keeps a little clear room around its own strokes, so the two are set
	 * off from each other rather than touching.
	 */
	private static final int LINE_GAP = 2;

	/**
	 * How far the block of the list stands out past the list it is drawn around, in gui pixels: eight,
 * which is the band the pack's own window keeps around what it holds.
 *
 * The block is one sprite the mod ships (see {@link #FRAME_SPRITE}), nine sliced to the size it comes to, and a sprite of
 * that kind keeps its band outside what it holds rather than over it, so this is the width of a band
 * rather than a margin the screen asks for: drawn narrower than the eight pixels the window keeps, the
 * band is cut off inside its own black line and the block reads as a panel with a thick edge.
 */
	private static final int FRAME_PADDING = 8;

	/**
	 * How far the shadow of the block is set down and to the right of it, in gui pixels: the light a
	 * screen like this is drawn in comes from over the player's left shoulder, as it does in the rest of
	 * the game's own drawing, so what is under the block is shaded on the side away from it.
	 */
	private static final int SHADOW_OFFSET = 2;

	/**
	 * The strip of tabs under the line over the list: how tall one of them is, how far the two sit apart,
	 * and how wide the two of them are together.
	 *
	 * A tab is as tall as a button of the game - twenty gui pixels, the height the game draws its own
	 * buttons at - so the strip reads as the two buttons it is, and the two of them together are as wide
	 * as the plate drawn over them (see {@link #UP_LINE_WIDTH}): the two names are the two pages of the
	 * collection, and the strip they are written in is the bar under the plate that holds both of them
	 * rather than a box fitted around each name. Each name is drawn in the middle of its own half of the
	 * strip (see {@link #drawTabs}), so the two pages are read at the two ends of one bar.
	 */
	private static final int TAB_HEIGHT = 20;
	private static final int TAB_GAP = 2;

	/** How wide one tab of the strip is: half of the strip, less the room the two of them sit apart. */
	private static final int TAB_WIDTH = (UP_LINE_WIDTH - TAB_GAP) / 2;

	/** How wide the two tabs are together: the two of them and the gap between them. */
	private static final int TAB_STRIP_WIDTH = 2 * TAB_WIDTH + TAB_GAP;

	/**
	 * Where the line over the list is drawn, and where the strip of the two tabs stands under it: the
	 * plate of the screen is set under the bar that counts the discoveries (see {@link #BAR_Y}), which is
	 * what the top of the block comes to - the title, the count, the bar, the plate, the strip of pages
	 * and the frame under it. The two pages of the collection are written in the bar under the plate
	 * rather than over it: under it, over the list the two of them are the pages of, which is the thing
	 * they say something about.
	 */
	private static final int UP_LINE_Y = BAR_Y + BAR_HEIGHT + LINE_GAP;

	/**
	 * Where the two tabs are drawn: under the plate of the screen, and over the frame of the list, with
	 * the plate set off from them and the two of them resting on the frame (see {@link #TABS_GAP}). They
	 * are set between the two on purpose - the plate names the collection, the strip says which of its two
	 * pages the list holds, and the list is what that page is made of.
	 */
	private static final int TABS_Y = UP_LINE_Y + UP_LINE_HEIGHT + LINE_GAP;

	/**
	 * How far the strip of the two tabs stands off the frame of the list it stands over, in gui pixels:
	 * not at all.
	 *
	 * The strip is the bar of the two pages of the collection, and the list under it is what the page the
	 * lit name belongs to is made of, so the two are drawn as one thing rather than as a row over a list:
	 * the strip rests on the top edge of the frame and the two edges meet, where the couple of pixels of
	 * {@link #LINE_GAP} between them would set the pages off from the list they are the pages of. What a
	 * button needs to be read as a button and pressed is room of its own, and the strip has that in its
	 * height (see {@link #TAB_HEIGHT}) rather than in the gap under it.
	 */
	private static final int TABS_GAP = 0;

	/**
	 * Where the frame of the list starts under the strip of tabs: the title, the count, the plate, the
	 * strip and the gaps between them take the top of the block, the strip rests on the frame itself (see
	 * {@link #TABS_GAP}), and the frame is the window of the pack drawn {@link #FRAME_PADDING} past the
	 * panel it is drawn around. The plate and the strip are drawn centred on the list rather than across
	 * it, both of them at the same width (see {@link #TAB_STRIP_WIDTH}); the room each is given is the
	 * room its own height takes, so the list starts at the same place under either of them.
	 */
	private static final int LIST_TOP = TABS_Y + TAB_HEIGHT + TABS_GAP + FRAME_PADDING;

	/** Where the line under the list is drawn, from the bottom of the panel the list is drawn on. */
	private static final int DOWN_LINE_OFFSET = FRAME_PADDING + LINE_GAP;

	/**
	 * How much of the screen the list takes at most: {@link #LIST_MAX_WIDTH} gui pixels of its width,
	 * and {@link #LIST_MAX_HEIGHT_PERCENT} hundredths of the height of the screen of its height.
	 *
	 * The list is narrower than the screen on purpose - the room beside it is what the two filter
	 * buttons stand in (see {@link #SIDE_SPACE}), and a row that stretches from edge to edge is read as
	 * a bar rather than as an item of a list - and it is kept short of the screen in both directions as
	 * well, so that a screen of any size has room around the collection rather than a collection drawn
	 * to its edges.
	 */
	private static final int LIST_MAX_WIDTH = 320;

	/**
	 * How much of the screen's height the list is given at most, in hundredths of it: half.
	 *
	 * The list used to be given everything the screen had between the line over it and the button
	 * under it, which on a large screen is a list drawn the whole way down the screen; at three fifths
	 * it was still the whole of the screen the eye landed on first. A collection reads as a collection
	 * when it is a block of a screen with the world around it, so the list stops at half the screen's
	 * height, and what the rest of the block leaves of the screen over is the room the block is set in
	 * (see {@link #blockTop}). A screen with less room than that still gets all of it.
	 */
	private static final int LIST_MAX_HEIGHT_PERCENT = 50;

	/**
	 * How big the two filter buttons are, and how far they sit from the frame of the list they filter.
	 *
	 * The gap is measured from the frame and not from the list, because the frame is what the button
	 * would otherwise be drawn over: it reaches {@link #FRAME_PADDING} past the list on every side, so
	 * a button set off the list itself is a button half buried in the band of the block, which is what
	 * the two of them were - drawn at the ends of the first row, inside the frame of the list, reading
	 * as two marks dropped in the panel rather than as two controls standing beside it (see
	 * {@link #filterButtonX}).
	 */
	private static final int FILTER_BUTTON_SIZE = 16;
	private static final int FILTER_BUTTON_GAP = 4;

	/**
	 * The least room the screen keeps off its own edges, and the floor the layout is built on: it is
	 * what the block of the collection is set in where the screen cannot hold it at its own size (see
	 * {@link #blockTop}), and what the two filter buttons that stand beside the list keep at the sides
	 * of the screen - so on a screen that is too narrow for the collection at its own width, what gives
	 * is the list, which is narrower, and not the room the block and its two buttons are read in.
	 */
	private static final int SCREEN_MARGIN = 8;

	/**
	 * The room one of those buttons takes at one side of the list, its own margin included: what the
	 * screen keeps clear of the list on each side is the frame of the list, the gap, the button and
	 * {@link #SCREEN_MARGIN} over it, so the block and both of its filters fit whatever the screen is.
	 *
	 * The room is the frame's own before it is anything else, since the frame is what the two buttons
	 * stand beside, and it is what the list of a screen too narrow to hold it is narrowed by (see
	 * {@link #init()}): on the smallest screen the game draws, 320 gui pixels wide, the two buttons, the
	 * frames of the list and the two margins come to 72 of them, so the list between them is 248 - which
	 * is still wider than the 228-pixel strip of the two tabs that stands over it.
	 */
	private static final int SIDE_SPACE = FRAME_PADDING + FILTER_BUTTON_GAP + FILTER_BUTTON_SIZE
			+ SCREEN_MARGIN;

	/**
	 * The size of the button that closes the screen, and the room the block of the screen keeps off its
	 * own top and bottom edges: the button is drawn under the frame of the list, which is what puts it
	 * where it is, and the room over the title and under the button is the room left over by the list,
	 * split between them (see {@link #blockTop}). This is the floor of those two margins rather than
	 * their size - it is the same {@link #SCREEN_MARGIN} the sides are kept in - and it is what a screen
	 * that cannot hold the whole block keeps around what is left of it.
	 */
	private static final int DONE_WIDTH = 110;
	private static final int DONE_HEIGHT = 16;

	/**
	 * Room the panel leaves below itself: the frame around it, the gap under it, the line that frames
	 * its bottom, the same gap again and the button that closes the screen - without the room that
	 * button keeps off the bottom of the screen, which is the block's own margin (see
	 * {@link #SCREEN_MARGIN}) rather than a gap of the layout. There is no longer a line of text
	 * between the line and the button: the screen used to spend two lines above the button, one on the
	 * line counting the discoveries and one on a line of its own telling the player to scroll, and the
	 * second one is gone.
	 */
	private static final int FOOTER_HEIGHT = DOWN_LINE_OFFSET + DOWN_LINE_HEIGHT + LINE_GAP + DONE_HEIGHT;

	/**
	 * The strip at the right end of the list the scrollbar is drawn in, in gui pixels: four, where the
	 * game draws its own scroller in six.
	 *
	 * The scroller is still the game's own sprite and the game's own groove - it can be taken and
	 * dragged as before, and a resource pack that redraws it redraws this - but it is drawn narrower
	 * than the game draws it in its own lists, because the bar of a list that is read rather than
	 * scrolled through is not a thing to spend the width of a row on. What the two pixels cost the
	 * sprite is the middle of its 9-slice, which is flat at every width (see {@link #drawScrollBar}).
	 */
	private static final int SCROLLBAR_WIDTH = 4;

	/** How far the rows stop before that strip, so the scrollbar is never drawn over a row. */
	private static final int SCROLLBAR_MARGIN = 3;

	/**
	 * The shortest the scrollbar thumb is allowed to be, and the room it keeps from the bottom of
	 * its track: the two ends of the game's own scroller height, which is as much of the track as
	 * the list is of its content, between a floor of its own and the bottom of the list.
	 */
	private static final int SCROLLBAR_MIN_HEIGHT = 24;
	private static final int SCROLLBAR_BOTTOM_MARGIN = 6;

	/** Where the check of a discovered biome and its name sit inside a row, from its left end. */
	private static final int CHECK_X = 7;
	private static final int NAME_X = 17;

	/** How far the text of a row is kept away from the right end of it. */
	private static final int TEXT_MARGIN = 6;

	/**
	 * The line under the title that counts what is found, of one world and of every world at once: the
	 * same count read against the list of a world and against the list of all of them.
	 */
	private static final String PROGRESS_KEY = "screen.immersive_titles.collection.progress";
	private static final String ALL_WORLDS_PROGRESS_KEY = "screen.immersive_titles.collection.progress_all";

	/**
	 * The names the four sections of the list are read by, one for each dimension of
	 * {@link BiomeCatalog.Dimension}: the words a player knows the three dimensions by, and the one the
	 * collection has for the biomes that are none of them (see {@link #dimensionKey}).
	 */
	private static final String SECTION_OVERWORLD_KEY = "screen.immersive_titles.collection.dimension.overworld";
	private static final String SECTION_NETHER_KEY = "screen.immersive_titles.collection.dimension.nether";
	private static final String SECTION_END_KEY = "screen.immersive_titles.collection.dimension.end";
	private static final String SECTION_UNPLACED_KEY = "screen.immersive_titles.collection.dimension.unplaced";

	/** What the button that forgets a biome says it does, and what the warning about it says. */
	private static final String FORGET_KEY = "screen.immersive_titles.collection.forget";
	private static final String FORGET_TITLE_KEY = "screen.immersive_titles.collection.forget.title";
	private static final String FORGET_MESSAGE_KEY = "screen.immersive_titles.collection.forget.message";
	private static final String FORGET_CONFIRM_KEY = "screen.immersive_titles.collection.forget.confirm";

	/**
	 * What a list with nothing in it is drawn with: the line a filter that kept no biome leaves in the
	 * panel, and the one the page of the structures is drawn with - that page collects nothing yet, and
	 * the line is what says so in the place the list of biomes would be (see {@link #drawRows}).
	 */
	private static final String EMPTY_KEY = "screen.immersive_titles.collection.empty";
	private static final String EMPTY_STRUCTURES_KEY =
			"screen.immersive_titles.collection.structures.empty";

	/** The glyph a biome that was discovered is marked with, from the game's own font. */
	private static final String CHECK = "\u2714";

	/** The glyph the filter of the biomes that are left to find is marked with. */
	private static final String QUESTION = "?";

	/**
	 * The glyph the button that forgets a biome is marked with, from the game's own font: the letter
	 * the game itself would have used, drawn over that button the way the marks of the two filters are
	 * drawn over theirs (see {@link #drawButtonMark}), with the check the list uses for a biome that
	 * was found replaced by the cross that gives it back.
	 */
	private static final String FORGET_MARK = "X";

	/**
	 * The three buttons the collection is drawn with: the flat one, the lit one a button wears under the
	 * mouse or the keyboard, and the one the game draws a button it will not let anyone press in with.
	 *
	 * They are the buttons of the resource pack Reimagined GUI (Dark), copied into the mod's own assets
	 * with the permission of the pack's author and credited there (see
	 * {@code assets/immersive_titles/textures/gui/sprites/CREDITS.md}), and they are named out of the
	 * mod's namespace and not out of the game's - which is the whole of what makes the collection dark:
	 * a sprite of the game is the player's to redraw, while these three are the drawing the screen was
	 * built for and are read whether or not the pack itself is on.
	 *
	 * What the three are used for is every block and button of the screen but the scrollbar: a row of a
	 * biome that was found, the face of each of the screen's own buttons (see {@link #drawButtonFace}),
	 * the small button that forgets a biome, and - as the sprite the game draws what cannot be pressed
	 * with - a row whose reveal is still waiting.
	 */
	private static final Identifier BUTTON_SPRITE = guiSprite("widget/button");
	private static final Identifier LIT_BUTTON_SPRITE = guiSprite("widget/button_highlighted");

	/**
	 * The third of them: the flat, dimmed panel the game draws a button it will not let anyone press in
	 * with. A biome that still has its reveal to come is the one thing in the list there is nothing to
	 * do with yet, so it is drawn the way the game draws what cannot be pressed - which is also what
	 * keeps a long list of them from drawing the eye away from the ones that were found.
	 */
	private static final Identifier WAITING_BUTTON_SPRITE = guiSprite("widget/button_disabled");

	/**
	 * The button a row of a discovered biome is given while the mouse is on that row: the same button as
	 * the rows and the tabs, at the size of one of the two filters beside the list, in the lit sprite
	 * the pack draws a button under the mouse in.
	 */
	private static final Identifier FORGET_BUTTON_SPRITE = BUTTON_SPRITE;
	private static final Identifier FORGET_HOVERED_SPRITE = LIT_BUTTON_SPRITE;
	private static final int FORGET_SIZE = 12;

	/**
	 * One of the mod's own copies of the pack's gui sprites: the file the game reads a sprite of the gui
	 * atlas out of, under {@code assets/immersive_titles/textures/gui/sprites}.
	 */
	private static Identifier guiSprite(String path)
	{
		return Identifier.fromNamespaceAndPath(WandererTitles.MOD_ID, path);
	}

	/** The game's own scroller, and the groove it runs in. */
	private static final Identifier SCROLLER_SPRITE = Identifier.withDefaultNamespace("widget/scroller");
	private static final Identifier SCROLLER_BACKGROUND_SPRITE =
			Identifier.withDefaultNamespace("widget/scroller_background");

	/**
	 * The window the block of the list is drawn as, and the two halves of the bar that counts the
	 * collection: the pack's own window - the frame of a list of things, with one flat panel inside it,
	 * drawn at the size the block comes to - and the trough and the fill of the pack's own experience
	 * bar, each of them {@link #BAR_WIDTH} x {@link #BAR_HEIGHT} gui pixels of texture.
	 *
	 * Both are copies of the pack's sprites, in the same six the buttons come from, and they are the
	 * two thirds of what makes the block of the list dark: the window is the whole of the block, frame
	 * and panel at once, and the bar under the count is the pack's own experience bar - so how much of
	 * the world has been found is read in the one shape every player already reads a share of progress
	 * in, drawn in the dark gui the rest of the screen is.
	 *
	 * The window is a sprite and is drawn as one, since the whole of it is drawn at once. The bar is
	 * not: what is drawn of the fill is a share of it - the same share the line over it counts in words
	 * - and a share of a picture is a region of that picture rather than a smaller picture of the same
	 * thing, so the two halves are named as the textures they are and drawn by the blit that takes the
	 * size on the screen and the size of the region it takes from the texture as two separate pairs (see
	 * {@link #drawProgressBar}).
	 */
	private static final Identifier FRAME_SPRITE = guiSprite("social_interactions/background");
	private static final Identifier BAR_TRACK = Identifier.fromNamespaceAndPath(WandererTitles.MOD_ID,
			"textures/gui/sprites/hud/experience_bar_background.png");
	private static final Identifier BAR_FILL = Identifier.fromNamespaceAndPath(WandererTitles.MOD_ID,
			"textures/gui/sprites/hud/experience_bar_progress.png");

	/**
	 * The shadow the block of the list drops over what is behind it: the black at 35%, set
	 * {@link #SHADOW_OFFSET} pixels down and to the right, which is what stands the block off the
	 * world it is read over. It is a shade rather than a shape - nothing of the block's own drawing
	 * is in it - so a pack that redraws the window redraws the block over this unchanged.
	 */
	private static final int FRAME_SHADOW = 0x59000000;

	/**
	 * The colours the screen draws itself, which is everything its sprites are not: the
	 * text of the title and of the line that counts the collection, the name of a dimension and the
	 * rule over it, the name of a biome that was found and the name of one that is still waiting, the
	 * name of the mod a biome comes from, the check beside a found one, the line around the row the
	 * mouse is on, and the silver around the button the keyboard is on.
	 *
	 * They are read by brightness, from the title down: the title is the brightest thing on the screen,
	 * then the count, then the name of a biome that was found, then the name of one whose reveal is
	 * still waiting, and the name of the mod a biome comes from is dimmer than either of those - so the
	 * eye has an order to read the screen in before anything is gold. The gold is the one accent the
	 * collection has, and it is spent on three places: the name of a dimension, the tab of the page
	 * being read and the row the mouse is on.
	 */
	private static final int TITLE_COLOR = 0xFFE8E8E8;
	private static final int COUNT_COLOR = 0xFFC9C9C9;
	private static final int SECTION_COLOR = 0xFFE0B15A;
	private static final int RULE_COLOR = 0x33FFFFFF;
	private static final int FOUND_COLOR = 0xFFF2F2F2;
	private static final int WAITING_COLOR = 0xFF787878;
	private static final int SOURCE_COLOR = 0xFF6A6A6A;

	/** The accent the three places above are drawn in, and the two marks that go with it. */
	private static final int ACCENT_COLOR = 0xFFE0B15A;
	private static final int CHECK_COLOR = 0xFF66E06A;
	private static final int HOVER_COLOR = 0x99E0B15A;

	/** The silver the button the keyboard is on is held in, one pixel outside its own edge. */
	private static final int FOCUS_COLOR = 0xFFC9CCD1;

	/** The white a name is written over one of the screen's own buttons in, the game's own for a label. */
	private static final int LABEL_COLOR = 0xFFFFFFFF;

	/**
	 * What is laid over the two tabs of the game under their names, and what the names are written
	 * in: the wash of the page being read, a lighter wash of the page that is not, and the line of the
	 * accent the tab being read carries along its bottom edge.
	 */
	private static final int TAB_ACTIVE_WASH = 0x99000000;
	private static final int TAB_INACTIVE_WASH = 0x40000000;
	private static final int TAB_ON_COLOR = 0xFFFFFFFF;
	private static final int TAB_OFF_COLOR = 0xFF8A8A8A;

	/**
	 * The mark of the button that forgets a biome, in the two colours it is drawn in: the grey the
	 * mark is dim in while the mouse is only on the row, and the red it turns while the mouse is on
	 * the button itself - a button that undoes something says so, and says so louder under the hand
	 * that is about to press it. The red is the one alarm the screen has, and it is not one of the
	 * colours of the collection (see {@link #CHECK_COLOR}): what it marks is the one press in the
	 * collection that takes something away.
	 */
	private static final int FORGET_COLOR = 0xFFA0A0A0;
	private static final int FORGET_HOVERED_COLOR = 0xFFFF5555;

	/**
	 * The mark of a filter that is turned off, and the colour the other one lights up in. The two are
	 * the game's own greys and white rather than the collection's: the filters are the game's own
	 * buttons and their marks are the game's own glyphs, and the check of the filter of what was found
	 * is the green of the checks in the list (see {@link #CHECK_COLOR}).
	 */
	private static final int FILTER_OFF_COLOR = 0xFF909090;
	private static final int REMAINING_COLOR = 0xFFFFFFFF;

	/**
	 * The dim the world is seen through while the collection is open, from the top of the screen down
	 * to its bottom, and the whole of what is drawn behind the block.
	 *
	 * The game draws two things behind a screen like this one - the world behind it, blurred, and then
	 * the same dark gradient the game's own menus are drawn with - and the mod draws the second of the
	 * two without the first: what is behind the collection is the world the player is in, at the light
	 * it is actually in, and the veil over it is what keeps the block of the screen readable on top of
	 * a bright sky or a dark cave alike. It is a little lighter than the game's own veil for the same
	 * reason it is not blurred: a blurred world is a world no longer read, and the room the collection is
	 * set in is worth leaving legible. What keeps the block itself readable on top of it is the block:
	 * the panel the rows sit on is the panel of the window the mod ships, drawn over the world (see
	 * {@link #drawFrame}), so the veil has nothing to do beyond setting the block off from what is
	 * behind it.
	 */
	private static final int VEIL_TOP = 0xA0121216;
	private static final int VEIL_BOTTOM = 0xB0121216;

	/** The biomes to list, in the order they are shown in. */
	private final List<BiomeCatalog.Entry> entries;

	/** The biomes of {@link #entries} the list is showing right now, i.e. the ones the filter keeps. */
	private final List<BiomeCatalog.Entry> shown = new ArrayList<>();

	/**
	 * The lines of the list as it is drawn: each section of {@link BiomeCatalog.Dimension} it has biomes
	 * of, with the biomes of that dimension under it (see {@link #rebuildRows}). What the list is read by
	 * goes through this and not through {@link #shown}: what is drawn, scrolled, hovered and counted is a
	 * row, and the line a section opens with takes a row like the biomes under it do.
	 */
	private final List<Row> rows = new ArrayList<>();

	/**
	 * The biomes that already had their reveal, i.e. the ones that are checked: the ones of the world
	 * the screen is listing, or - while it is listing every world at once - the ones found in any of
	 * them (see {@link #ofEveryWorld}). It is a copy of what the mod remembers, and it is this
	 * screen's own: a biome that is forgotten is taken out of it here, while what the mod remembers is
	 * changed through {@link TitleOverlay#forgetBiome} and nothing else.
	 */
	private final Set<String> discovered;

	/**
	 * How many of {@link #entries} are in {@link #discovered}: counted when the screen opens and held
	 * up to date from there, since a biome that is forgotten stops being one of them.
	 */
	private int discoveredCount;

	/** Which biomes the list is showing; the two buttons beside it switch this, see {@link #toggleFilter}. */
	private Filter filter = Filter.ALL;

	/** Which page of the collection is shown; the two tabs over the list switch this, see {@link #show}. */
	private Page page = Page.BIOMES;

	/**
	 * The two buttons that filter the list, held on to so that the page with nothing to filter can take
	 * them off the screen and put them back: they are built again with every {@link #init()}, and the
	 * page shown is what decides whether they are there at all (see {@link #showFilters}).
	 */
	private Button discoveredFilter;
	private Button remainingFilter;

	/**
	 * The two tabs over the list, and the button that closes the screen: held on to for what is drawn
	 * over them once the game has drawn them - the pack's own face on each of the three (see
	 * {@link #drawButtonFaces}), the name of the page in the tab (see {@link #drawTabs}), the name of
	 * the button itself (see {@link #drawDoneLabel}) and the silver border of the one the keyboard is on
	 * (see {@link #drawDoneFocus}).
	 */
	private final List<Button> tabs = new ArrayList<>();
	private Button doneButton;

	/** Where the list has been told to go, in rows: what the wheel and the keys move. */
	private double scrollTarget;

	/** Where the list is drawn right now, in rows: it eases towards {@link #scrollTarget}. */
	private double scrollCurrent;

	/** Wall clock time the list was drawn at last, which the step of that easing is measured from. */
	private long lastFrameMs;

	/** Whether the scrollbar is being dragged right now. */
	private boolean draggingScrollBar;

	/** Where on the thumb it was taken, in pixels below the top of the thumb. */
	private int scrollBarGrabOffset;

	/** The box the list is drawn in, in gui pixels. Filled in by {@link #init()}. */
	private int listLeft;
	private int listTop;
	private int listRight;
	private int listBottom;

	/**
	 * How far down the screen the block of the screen starts: the room the list leaves over is split
	 * evenly over and under that block, so that the room over the title of the screen and the room
	 * under the button that closes it are the same, to the pixel (see {@link #init()}).
	 *
	 * Everything the screen places itself is placed at an offset of this - the title, the count, the
	 * line over the list and the list itself - while the line under the list and the button after it
	 * follow the list's own bottom, which is what keeps the frame of the list around the list whatever
	 * the block comes to. It is measured in the screen rather than in the block, so a screen with room
	 * to spare and a screen without any are read the same way: the room left over is what is split,
	 * and a screen that has none leaves the block at {@link #SCREEN_MARGIN} from its edges.
	 */
	private int blockTop;

	/**
	 * Where the strip of the two tabs starts, worked out in {@link #init()}: the strip is as wide as the
	 * line drawn over the list (see {@link #TAB_STRIP_WIDTH} and {@link #UP_LINE_WIDTH}) and it is centred
	 * on the list the way that line is, so the two stand exactly one over the other.
	 */
	private int tabStripLeft;

	/**
	 * Whether the screen is listing one world or every world at once. The mod's own key opens it in a
	 * world, where the biomes are that world's registry's and the discoveries are the ones that world
	 * remembers, and the mod list of Mod Menu opens the collection of all of them, which is what this
	 * is false for: no world has to be open there at all, the biomes are the ones the mod knows
	 * together with the ones found anywhere (see {@link BiomeCatalog#known(Set, ClientLevel)}), what is
	 * checked is what was found in any world, and the line under the title counts all of that rather
	 * than one world.
	 */
	private final boolean inWorld;

	/**
	 * The screen this one was opened from, or null while it was opened from the game itself: a screen
	 * opened out of another screen - the mod list of Mod Menu - closes back into it, while the one the
	 * mod's own key opens in a world closes into the world. See {@link #onClose()}.
	 */
	private final Screen parent;

	/**
	 * The collection of the world the player is in, which is what the mod's own key opens: every biome
	 * that world has registered, and the ones that already had their reveal in it.
	 */
	BiomeCollectionScreen(ClientLevel level, Set<String> discovered, Screen parent)
	{
		this(BiomeCatalog.all(level), discovered, true, parent);
	}

	/**
	 * The collection of every world at once, which is what the mod list of Mod Menu opens.
	 *
	 * No world has to be open for it, and none is left out while one is: the discoveries of every
	 * world this client has written down are read together (see {@link DiscoveredBiomes#loadAll}), and
	 * the biomes they name are listed with the ones the mod knows (see
	 * {@link BiomeCatalog#known(Set, ClientLevel)}), so the list holds what was found in any of the
	 * player's worlds. The world that is open right now is added to that set on top of the files, since
	 * a world whose own folder cannot be reached keeps its discoveries in the session only (see
	 * {@link TitleOverlay#discoveredBiomes}), and that same world is what is read for a dimension: its
	 * tags place the biomes of the mods that are still installed, while a biome of a mod that is not -
	 * or one whose author tagged it with nothing - is listed as belonging to no dimension, which is
	 * everything the mod can say about a biome no world of the player places.
	 *
	 * What the screen is not, built this way, is a world of its own - which is why a row of it never
	 * offers to forget anything (see {@link #canForget}).
	 */
	static BiomeCollectionScreen ofEveryWorld(Minecraft client, Screen parent)
	{
		Set<String> found = DiscoveredBiomes.loadAll();
		found.addAll(TitleOverlay.discoveredBiomes());

		return new BiomeCollectionScreen(BiomeCatalog.known(found, client.level), found, false, parent);
	}

	/**
	 * Builds the screen out of what it is listing: the biomes to show, the ones of them that already
	 * had their reveal, and whether that list is one world's or every world's (see {@link #inWorld}).
	 */
	private BiomeCollectionScreen(List<BiomeCatalog.Entry> entries, Set<String> discovered,
			boolean inWorld, Screen parent)
	{
		super(Component.translatable("screen.immersive_titles.collection.title"));

		this.parent = parent;

		this.inWorld = inWorld;
		this.entries = entries;
		this.discovered = new HashSet<>(discovered);

		int found = 0;
		for (BiomeCatalog.Entry entry : entries)
		{
			if (discovered.contains(entry.id()))
			{
				found++;
			}
		}
		this.discoveredCount = found;

		rebuildShown();
	}

	@Override
	protected void init()
	{
		// the list is centred and narrower than the screen, with the room the two filter buttons need
		// left empty beside it, and it is given at most its share of the height of the screen, so that
		// the collection stays a block of the screen instead of a list down all of it
		int listWidth = Math.max(0, Math.min(LIST_MAX_WIDTH, width - 2 * SIDE_SPACE));
		int room = Math.max(0, height - LIST_TOP - FOOTER_HEIGHT - 2 * SCREEN_MARGIN);
		int listHeight = Math.min(room, height * LIST_MAX_HEIGHT_PERCENT / 100);
		int spare = height - (LIST_TOP + listHeight + FOOTER_HEIGHT);

		// what the list leaves over is what the block is set in, and it is split evenly: half of it over
		// the title and half under the button that closes the screen, so the two margins come out the
		// same. A screen of an odd width or height leaves a pixel that no halving can place, and that
		// pixel is taken off the list rather than handed to one of the two margins, so the block is
		// set in the middle of the screen exactly
		listWidth = Math.max(0, listWidth - (width - listWidth) % 2);

		if (spare > 1)
		{
			listHeight -= spare % 2;
		}

		blockTop = Math.max(SCREEN_MARGIN, (height - (LIST_TOP + listHeight + FOOTER_HEIGHT)) / 2);

		listLeft = (width - listWidth) / 2;
		listRight = listLeft + listWidth;
		listTop = blockTop + LIST_TOP;
		listBottom = listTop + listHeight;

		// the strip of tabs is as wide as the line drawn over them, and it is centred on the list the same
		// way that line is, so what holds the two pages and the plate over them stand one over the other
		tabStripLeft = listLeft + (listWidth - TAB_STRIP_WIDTH) / 2;

		// a resize can leave the list scrolled past its own end
		scrollTarget = clampScroll(scrollTarget);
		scrollCurrent = clampScroll(scrollCurrent);

		// the slide is measured against the clock, so the frame it starts from is this one
		lastFrameMs = Util.getMillis();

		addTabs();
		addFilterButtons();

		doneButton = Button.builder(Component.translatable("gui.done"), button -> onClose())
				.bounds(width / 2 - DONE_WIDTH / 2, doneY(), DONE_WIDTH, DONE_HEIGHT)
				.build();

		addRenderableWidget(doneButton);
	}

	/**
	 * Where the button that closes the screen is drawn: under the line that frames the bottom of the
	 * list, with the same gap under it that the line has over it. The button is under the frame and
	 * not pinned to the bottom of the screen, so it comes up with the list when the list stops short
	 * of the screen - and while the list is taking everything the screen has, the two are the same
	 * place.
	 */
	private int doneY()
	{
		return listBottom + DOWN_LINE_OFFSET + DOWN_LINE_HEIGHT + LINE_GAP;
	}

	/**
	 * Adds the two buttons that filter the list: the one beside the left of the list keeps the biomes
	 * that were discovered, the one beside its right keeps the ones that are left to find. Both stand
	 * outside the frame of the list, level with the first row of it, so what they filter is the list
	 * they are beside without either of them being drawn over it (see {@link #filterButtonX}).
	 *
	 * Both are buttons of the game, so they hover, click, play the game's click and answer to the
	 * narrator like any other, and each one carries the mark its filter is about as its face (see
	 * {@link #drawFilterIcons}) together with a tooltip that spells it out, because a mark alone
	 * says what a button is about rather than what pressing it does.
	 */
	private void addFilterButtons()
	{
		discoveredFilter = filterButton(true, Filter.DISCOVERED,
				"screen.immersive_titles.collection.filter_discovered");
		remainingFilter = filterButton(false, Filter.REMAINING,
				"screen.immersive_titles.collection.filter_remaining");

		addRenderableWidget(discoveredFilter);
		addRenderableWidget(remainingFilter);

		showFilters(page == Page.BIOMES);
	}

	/**
	 * Adds the two tabs over the list, the one that shows the biomes and the one that shows the
	 * structures: buttons of the game, so they hover, click and answer to the narrator like the ones
	 * that filter the list, with their names drawn over them by {@link #drawTabs} instead of by the
	 * game - the same way the two filters are drawn as blank buttons with a mark over them.
	 *
	 * The two are laid out from the start of the strip the plate of the screen is drawn over, at the width
	 * of {@link #TAB_WIDTH} each and set apart by {@link #TAB_GAP} (see {@link #TAB_STRIP_WIDTH}), so the
	 * strip is a bar of two under the plate rather than two boxes fitted around two names, and each name
	 * is drawn in the middle of its own half of it (see {@link #drawTabs}).
	 */
	private void addTabs()
	{
		int x = tabStripLeft;

		for (Page tab : Page.values())
		{
			Button button = Button.builder(Component.empty(), pressed -> show(tab))
					.size(TAB_WIDTH, TAB_HEIGHT)
					.pos(x, tabY())
					.createNarration(defaultNarration -> tab.label())
					.build();

			tabs.add(button);
			addRenderableWidget(button);

			x += TAB_WIDTH + TAB_GAP;
		}
	}

	/** Where the tabs are drawn, from the top of the block the whole collection is set in. */
	private int tabY()
	{
		return blockTop + TABS_Y;
	}

	/**
	 * Shows a page of the collection: the list of biomes, or the page that collects nothing yet.
	 *
	 * The page already shown is not shown again - nothing about it has changed - and the two filters go
	 * with it, since they filter the biomes and not the screen (see {@link #showFilters}). The list is
	 * sent back to its top, which is where a list that has just been replaced should start, and the
	 * slide is what carries it there, so a tab that is pressed reads as a page being turned rather than
	 * as one being cut to.
	 */
	private void show(Page page)
	{
		if (this.page == page)
		{
			return;
		}

		this.page = page;

		showFilters(page == Page.BIOMES);
		rebuildShown();

		scrollCurrent = clampScroll(scrollCurrent);
		scrollTarget = 0.0D;
	}

	/**
	 * Puts the two filter buttons on the screen or takes them off it: they belong to the list of biomes,
	 * so the page that lists no biomes has neither of them. They are hidden and made inert both, since
	 * a button that is only drawn flat is still a button the mouse finds.
	 */
	private void showFilters(boolean shown)
	{
		discoveredFilter.visible = shown;
		discoveredFilter.active = shown;
		remainingFilter.visible = shown;
		remainingFilter.active = shown;
	}

	/**
	 * Builds one of the two filter buttons: the one beside the left of the list, or the one beside its
	 * right, both at the height of the first row of the list and both clear of the frame of it.
	 */
	private Button filterButton(boolean left, Filter filter, String descriptionKey)
	{
		return Button.builder(Component.empty(), button -> toggleFilter(filter))
				.size(FILTER_BUTTON_SIZE, FILTER_BUTTON_SIZE)
				.pos(filterButtonX(left), listTop)
				.tooltip(Tooltip.create(Component.translatable(descriptionKey)))
				.createNarration(defaultNarration -> Component.translatable(descriptionKey))
				.build();
	}

	/**
	 * Turns a filter on, or off again while it is the one already on, in which case the list goes
	 * back to every biome. The two filters are each other's opposite, so turning one on turns the
	 * other off: what the list keeps is one of the three states of {@link Filter}, never two.
	 *
	 * The list is sent back to its top, which is where a list that has just been replaced should
	 * start, and the slide is what carries it there.
	 */
	private void toggleFilter(Filter filter)
	{
		this.filter = this.filter == filter ? Filter.ALL : filter;
		rebuildShown();

		scrollCurrent = clampScroll(scrollCurrent);
		scrollTarget = 0.0D;
	}

	/**
	 * Fills {@link #shown} with the biomes the current filter keeps, in the order of the catalog, and
	 * builds {@link #rows} out of what is left of them (see {@link #rebuildRows}).
	 *
	 * The page that collects no biomes has nothing to keep and nothing to build a row from, so both the
	 * biomes it is showing and the rows of the list are emptied: what that page draws in the panel is a
	 * line saying that there is nothing there yet (see {@link #drawRows}), which is the one thing it has
	 * to say - an empty panel is the same picture whether the list is empty or broken.
	 */
	private void rebuildShown()
	{
		shown.clear();

		if (page != Page.BIOMES)
		{
			rows.clear();
			return;
		}

		for (BiomeCatalog.Entry entry : entries)
		{
			boolean found = discovered.contains(entry.id());

			if ((filter == Filter.DISCOVERED && !found) || (filter == Filter.REMAINING && found))
			{
				continue;
			}

			shown.add(entry);
		}

		rebuildRows();
	}

	/**
	 * Builds {@link #rows} out of {@link #shown}: the biomes of one dimension under the line that opens
	 * their section, in the order of {@link BiomeCatalog.Dimension} - which is the order the catalog
	 * itself is sorted in, so the lines between the biomes are exactly the sections of the list and a
	 * biome never has another dimension's biomes over it.
	 *
	 * A dimension the filter is keeping nothing of is left out with them: a line with no biomes under it
	 * is a heading over nothing, and what the list is showing when it is showing nothing at all is said
	 * by the line in the middle of the panel (see {@link #drawRows}) rather than by four headings.
	 */
	private void rebuildRows()
	{
		rows.clear();

		for (BiomeCatalog.Dimension dimension : BiomeCatalog.Dimension.values())
		{
			if (!shows(dimension))
			{
				continue;
			}

			rows.add(new Row.Section(dimension, found(dimension), size(dimension)));

			for (BiomeCatalog.Entry entry : shown)
			{
				if (entry.dimension() == dimension)
				{
					rows.add(new Row.Biome(entry));
				}
			}
		}
	}

	/** Whether the filter is keeping any biome of {@code dimension} right now. */
	private boolean shows(BiomeCatalog.Dimension dimension)
	{
		for (BiomeCatalog.Entry entry : shown)
		{
			if (entry.dimension() == dimension)
			{
				return true;
			}
		}

		return false;
	}

	/** How many biomes of {@code dimension} the collection holds at all, found or not. */
	private int size(BiomeCatalog.Dimension dimension)
	{
		int size = 0;

		for (BiomeCatalog.Entry entry : entries)
		{
			if (entry.dimension() == dimension)
			{
				size++;
			}
		}

		return size;
	}

	/** How many biomes of {@code dimension} the world has already had the reveal of. */
	private int found(BiomeCatalog.Dimension dimension)
	{
		int found = 0;

		for (BiomeCatalog.Entry entry : entries)
		{
			if (entry.dimension() == dimension && discovered.contains(entry.id()))
			{
				found++;
			}
		}

		return found;
	}

	/**
	 * Draws the collection: the title, the count and its bar, the block the list is held in, the list
	 * itself, the two lines framing it, and - over the buttons the game has drawn - the marks and the
	 * borders the screen adds to them.
	 *
	 * The order is the order of the block, front to back: what is behind is drawn first, and everything
	 * this screen draws over a button of the game is drawn after the game has drawn it (see
	 * {@link #drawTabs}, {@link #drawFilterIcons} and {@link #drawDoneFocus}), which is what keeps a
	 * button hovering, clicking and speaking for itself while the mod's own mark sits on it.
	 */
	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick)
	{
		advanceScroll();

		drawTitle(graphics);

		// what the line under the title says: how much of the world is found, or - while the list is
		// every world at once - how much of all of them is, which is that same count read against the
		// list that holds what any of them found, and the bar under it is that count read at a glance. It
		// belongs to the biomes, so the page that collects none of them has no count to give and no bar
		// to draw, and is left with the room the line and the bar would have taken
		if (page == Page.BIOMES)
		{
			drawCentered(graphics,
					Component.translatable(inWorld ? PROGRESS_KEY : ALL_WORLDS_PROGRESS_KEY,
							discoveredCount, entries.size()),
					blockTop + PROGRESS_Y, COUNT_COLOR);

			drawProgressBar(graphics);
		}

		drawFrame(graphics);
		drawRows(graphics, mouseX, mouseY);
		drawScrollBar(graphics);

		// the two lines of the mod's own drawing the list is framed with, one over it and one under it
		drawLine(graphics, UP_LINE, UP_LINE_WIDTH, UP_LINE_HEIGHT, blockTop + UP_LINE_Y);
		drawLine(graphics, DOWN_LINE, DOWN_LINE_WIDTH, DOWN_LINE_HEIGHT, listBottom + DOWN_LINE_OFFSET);

		// the buttons added in init() are among the screen's renderables
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);

		// and what the screen draws over them, once the buttons themselves are on the screen: the face of
		// each of them in the pack's own button, the bar of the two pages with the names of both in it,
		// the marks of the two filters while the page they belong to is shown, the name of the button
		// that closes the screen and the border of it the keyboard is on
		drawButtonFaces(graphics);
		drawTabs(graphics);

		if (page == Page.BIOMES)
		{
			drawFilterIcons(graphics);
		}

		drawDoneLabel(graphics);
		drawDoneFocus(graphics);
	}

	/**
	 * Draws what is behind the block: the world the player is in, under the veil of the mod's own (see
	 * {@link #VEIL_TOP}), and the game's own background for the way in that has no world behind it.
	 *
	 * The collection is opened out of a world by the mod's own key, and out of the mod list of Mod Menu
	 * from wherever the player is - the title screen included, where what the game draws behind a screen
	 * is its own panorama. So the veil is drawn where there is a world to see through it, and everywhere
	 * else the game's own background is left alone: a screen opened from the title screen while standing
	 * in no world is not a screen with a world behind it, and the panorama says that better than a flat
	 * veil could.
	 */
	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick)
	{
		if (minecraft == null || minecraft.level == null)
		{
			super.extractBackground(graphics, mouseX, mouseY, partialTick);
			return;
		}

		graphics.fillGradient(0, 0, width, height, VEIL_TOP, VEIL_BOTTOM);
	}

	/**
	 * Draws the title of the screen, in the brightest colour of the collection and a fifth smaller
	 * than the game's own font (see {@link #TITLE_SIZE_PERCENT}): the one line of the screen drawn at
	 * a size of its own, and the reason the count under it is what the eye lands on first.
	 *
	 * The size is what the line is asked for and not what the font is built at, so the title is the same
	 * font as everything else here, drawn scaled and centred by its own scaled width - which is what keeps
	 * it in the middle of the screen at a size the game's own text has no share of.
	 */
	private void drawTitle(GuiGraphicsExtractor graphics)
	{
		float scale = TITLE_SIZE_PERCENT / 100.0F;
		Matrix3x2fStack pose = graphics.pose();

		pose.pushMatrix();
		pose.translate((width - font.width(title) * scale) / 2.0F, (float) (blockTop + TITLE_Y));
		pose.scale(scale, scale);
		graphics.text(font, title, 0, 0, TITLE_COLOR, true);
		pose.popMatrix();
	}

	/**
	 * Draws the bar counting the collection, under the line that counts it in words: the game's own
	 * experience bar - the trough of it drawn whole, and the found share of it cut out of the sprite the
	 * game fills it with - which is the one shape every player already reads a share of progress in.
	 *
	 * The share is the same count the line above says in words, taken against the whole list rather than
	 * against what the filter is showing, so the bar and the line can never disagree, and a list that is
	 * empty of everything has a bar with nothing in it rather than no bar at all. What is drawn of the
	 * fill is that share of the fill's own texture, one texture pixel to one gui pixel, which is the bar
	 * the game itself draws: the fill grows out of the left end of the trough and stops where the count
	 * stops, four pixels tall, in the colours the sprite is drawn in rather than in a colour of the mod's.
	 */
	private void drawProgressBar(GuiGraphicsExtractor graphics)
	{
		int x = (width - BAR_WIDTH) / 2;
		int y = blockTop + BAR_Y;
		int filled = entries.isEmpty() ? 0
				: (int) Math.round((double) discoveredCount / entries.size() * BAR_WIDTH);

		graphics.blit(RenderPipelines.GUI_TEXTURED, BAR_TRACK, x, y, 0.0F, 0.0F,
				BAR_WIDTH, BAR_HEIGHT, BAR_WIDTH, BAR_HEIGHT, BAR_WIDTH, BAR_HEIGHT);

		if (filled > 0)
		{
			graphics.blit(RenderPipelines.GUI_TEXTURED, BAR_FILL, x, y, 0.0F, 0.0F,
					filled, BAR_HEIGHT, filled, BAR_HEIGHT, BAR_WIDTH, BAR_HEIGHT);
		}
	}

	/**
	 * Draws the block the list is held in: the shadow it drops and the frame around it, which is the
	 * pack's own window - the frame of a list of things - with the panel of it left for the rows of the
	 * list to sit on (see {@link #FRAME_SPRITE}).
	 *
	 * The frame and the panel are one sprite, nine sliced to the size the block comes to, so the whole of
	 * the block is one drawing of the pack's, and what the screen draws itself here is what is left over:
	 * the shadow the block drops over the world behind it, which is drawn
	 * under the sprite rather than over it and reaches {@link #SHADOW_OFFSET} past it on two sides. The
	 * sprite itself is drawn from {@link #FRAME_PADDING} past the list on every side, so the band of it
	 * is outside what it holds rather than over it.
	 */
	private void drawFrame(GuiGraphicsExtractor graphics)
	{
		int left = listLeft - FRAME_PADDING;
		int top = listTop - FRAME_PADDING;
		int right = listRight + FRAME_PADDING;
		int bottom = listBottom + FRAME_PADDING;

		graphics.fill(left + SHADOW_OFFSET, top + SHADOW_OFFSET, right + SHADOW_OFFSET,
				bottom + SHADOW_OFFSET, FRAME_SHADOW);

		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, FRAME_SPRITE, left, top, right - left,
				bottom - top);
	}

	/**
	 * Draws a line one pixel wide around the box between the four numbers, which is what the collection
	 * draws the line around the row the mouse is on and the silver around the button the keyboard is on
	 * with: a line of one pixel on a screen of gui pixels is a line, and nothing about a block of the
	 * game is drawn with a thickness of its own.
	 */
	private static void drawBorder(GuiGraphicsExtractor graphics, int left, int top, int right, int bottom,
			int color)
	{
		graphics.fill(left, top, right, top + 1, color);
		graphics.fill(left, bottom - 1, right, bottom, color);
		graphics.fill(left, top + 1, left + 1, bottom - 1, color);
		graphics.fill(right - 1, top + 1, right, bottom - 1, color);
	}

	/**
	 * Draws the face of every button the screen adds to the game's own - the two tabs, the two filters
	 * and the button that closes the screen - over the button the game has just drawn.
	 *
	 * The faces come first and the names after, which is the order the two are read in: what each of
	 * these buttons says is written over the face here and in {@link #drawTabs}, {@link #drawFilterIcons}
	 * and {@link #drawDoneLabel}, and the face itself is the sprite of the pack (see
	 * {@link #drawButtonFace}).
	 */
	private void drawButtonFaces(GuiGraphicsExtractor graphics)
	{
		for (Button tab : tabs)
		{
			drawButtonFace(graphics, tab);
		}

		if (page == Page.BIOMES)
		{
			drawButtonFace(graphics, discoveredFilter);
			drawButtonFace(graphics, remainingFilter);
		}

		drawButtonFace(graphics, doneButton);
	}

	/**
	 * Draws the face of one of those buttons over the button the game has drawn: the sprite of the pack
	 * in its lit reading while the mouse is on the button or the keyboard is on it, and in its flat one
	 * otherwise - the same pair the game itself uses for a button it draws, out of the same two sprites
	 * (see {@link #BUTTON_SPRITE}).
	 *
	 * The buttons of this screen are buttons of the game: they hover, click, take the keyboard and speak
	 * for the narrator, and the game draws each of them with a sprite of its own out of its own jar -
	 * the one thing about this screen that no blit of it can name. So the face is painted over that
	 * sprite instead: a plain button is one sprite across the whole of its box, so the pack's button
	 * drawn in the place the button is covers it exactly, and what the player sees is the same button of
	 * the game in the drawing of the collection.
	 */
	private static void drawButtonFace(GuiGraphicsExtractor graphics, Button button)
	{
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
				button.isHoveredOrFocused() ? LIT_BUTTON_SPRITE : BUTTON_SPRITE,
				button.getX(), button.getY(), button.getWidth(), button.getHeight());
	}

	/**
	 * Writes the name of the button that closes the screen over the face of it: the game wrote its own
	 * label into the button before this screen painted that face over the button (see
	 * {@link #drawButtonFaces}), so the label is written here instead - in the place the game centres a
	 * label in and in the white it writes one.
	 */
	private void drawDoneLabel(GuiGraphicsExtractor graphics)
	{
		Component label = doneButton.getMessage();

		graphics.text(font, label, doneButton.getX() + (doneButton.getWidth() - font.width(label)) / 2,
				doneButton.getY() + (doneButton.getHeight() - font.lineHeight) / 2, LABEL_COLOR, true);
	}

	/**
	 * Draws a thin line around the button that closes the screen while the keyboard is on it, in the
	 * silver of the collection (see {@link #FOCUS_COLOR}).
	 *
	 * It is drawn under the button rather than over it, one pixel outside its edges, so the game's own
	 * drawing of a focused button is left as it is and what the player sees is a border around the button
	 * the keyboard has come to - which is what says where the keys would land, for a player who never
	 * touches the mouse, and what the game's own focus of a button is too faint to say on a panel as
	 * dark as this one.
	 */
	private void drawDoneFocus(GuiGraphicsExtractor graphics)
	{
		if (doneButton == null || !doneButton.isFocused())
		{
			return;
		}

		drawBorder(graphics, doneButton.getX() - 1, doneButton.getY() - 1,
				doneButton.getX() + doneButton.getWidth() + 1,
				doneButton.getY() + doneButton.getHeight() + 1, FOCUS_COLOR);
	}

	/**
	 * Closes the collection back to where it was opened from: the world, while it was opened in one,
	 * or the screen it was opened out of, which is the mod list where Mod Menu is the way in.
	 */
	@Override
	public void onClose()
	{
		if (parent == null)
		{
			super.onClose();
			return;
		}

		minecraft.setScreenAndShow(parent);
	}

	/**
	 * Moves the drawn list towards where it has been told to go by the time since the last drawn
	 * frame: no time while the two already agree, so a list nobody is scrolling costs nothing, and
	 * the step is capped so that a stalled frame cannot skip the list over what it missed. The list
	 * is put exactly where it is going once it is close enough, so it settles instead of crawling
	 * the last hundredth of a row forever.
	 */
	private void advanceScroll()
	{
		long now = Util.getMillis();
		double elapsedMs = Math.min(now - lastFrameMs, MAX_SCROLL_STEP_MS);
		lastFrameMs = now;

		if (elapsedMs <= 0.0D || scrollCurrent == scrollTarget)
		{
			return;
		}

		double approach = Math.exp(-elapsedMs / SCROLL_EASE_MS);
		double moved = scrollTarget + (scrollCurrent - scrollTarget) * approach;

		scrollCurrent = Math.abs(scrollTarget - moved) < SCROLL_SETTLED_ROWS ? scrollTarget : moved;
	}

	/**
	 * Draws the rows that are in the list right now, under a scissor of the list box: the rows slide
	 * by the same whole number of pixels, so the text moves instead of jumping and does not shimmer
	 * on the way. A filter that keeps nothing leaves the panel with a line saying so, in the middle,
	 * because an empty panel reads as a fault.
	 */
	private void drawRows(GuiGraphicsExtractor graphics, int mouseX, int mouseY)
	{
		if (rows.isEmpty())
		{
			drawCentered(graphics, Component.translatable(
					page == Page.BIOMES ? EMPTY_KEY : EMPTY_STRUCTURES_KEY),
					listTop + (listBottom - listTop - font.lineHeight) / 2, COUNT_COLOR);
			return;
		}

		int rowRight = rowRight();
		int hovered = rowAt(mouseX, mouseY);

		// the row above the first one that is fully in the list may still be hanging into it
		int first = Math.max(0, (int) Math.floor(scrollCurrent) - 1);

		graphics.enableScissor(listLeft, listTop, listRight, listBottom);

		for (int index = first; index < rows.size(); index++)
		{
			int y = rowTop(index);
			if (y >= listBottom)
			{
				break;
			}

			if (y + ROW_HEIGHT > listTop)
			{
				drawRow(graphics, rows.get(index), index, y, rowRight, hovered, mouseX, mouseY);
			}
		}

		graphics.disableScissor();
	}

	/** How far the rows are slid up from where the list is, in pixels, at this frame. */
	private int slide()
	{
		return (int) Math.round(scrollCurrent * ROW_PITCH);
	}

	/**
	 * The top of the row at {@code index} as it is drawn right now, in gui pixels. It is where the row
	 * is drawn and where it is read from the mouse alike, so a button drawn on a row is pressed where
	 * it is seen, see {@link #rowAt}.
	 */
	private int rowTop(int index)
	{
		return listTop + index * ROW_PITCH - slide();
	}

	/** The right end of a row: the room the scrollbar keeps along the right of the list is no row. */
	private int rowRight()
	{
		return listRight - SCROLLBAR_WIDTH - SCROLLBAR_MARGIN;
	}

	/**
	 * The row the point is on, or -1 while it is on none of them: the room between two rows is not part
	 * of a row, the room the scrollbar keeps is not part of one either, and a point outside the box of
	 * the list is on nothing at all.
	 */
	private int rowAt(double mouseX, double mouseY)
	{
		if (mouseX < listLeft || mouseX >= rowRight() || mouseY < listTop || mouseY >= listBottom)
		{
			return -1;
		}

		double offset = mouseY - listTop + slide();
		int index = (int) Math.floor(offset / ROW_PITCH);

		if (index < 0 || index >= rows.size() || offset - index * ROW_PITCH >= ROW_HEIGHT)
		{
			return -1;
		}

		return index;
	}

	/**
	 * Draws one row of the list, which is one of two things: a biome, or the line that opens a section
	 * of them. Which of the two a row is, is what the list itself holds and not what this decides (see
	 * {@link Row}), so the row is read for it here and handed on to what draws it.
	 */
	private void drawRow(GuiGraphicsExtractor graphics, Row row, int index, int y, int rowRight,
			int hovered, int mouseX, int mouseY)
	{
		if (row instanceof Row.Section section)
		{
			drawSection(graphics, section, y, rowRight);
		}
		else if (row instanceof Row.Biome biome)
		{
			drawBiome(graphics, biome.entry(), index, y, rowRight, hovered, mouseX, mouseY);
		}
	}

	/**
	 * Draws the line that opens a section of the list: the name of its dimension at the left end, where
	 * the names of the biomes under it are, the count of that dimension at the right end, where the
	 * names of the mods a biome comes from are, and a rule across the row over the two - which is what
	 * says that a section starts here rather than that the list has a biome with a name that is not one.
	 *
	 * The rule is drawn in the last pixel of the room above the row, which is the room the rows are set
	 * apart by, so a section is set off from the one before it without the list spending a row of its own
	 * on the space. The rule over the first section of the list falls outside the box of the list while
	 * the list is at its top, and is clipped away with everything else that hangs out of the box, so the
	 * list is never opened with a line it did not draw a section for.
	 */
	private void drawSection(GuiGraphicsExtractor graphics, Row.Section section, int y, int rowRight)
	{
		int textY = y + (ROW_HEIGHT - font.lineHeight) / 2;
		String count = section.found() + " / " + section.total();

		graphics.fill(listLeft, y - 1, rowRight, y, RULE_COLOR);
		graphics.text(font, Component.translatable(dimensionKey(section.dimension())),
				listLeft + NAME_X, textY, SECTION_COLOR, true);
		graphics.text(font, count, rowRight - TEXT_MARGIN - font.width(count), textY, COUNT_COLOR, true);
	}

	/** The name the section of {@code dimension} is read by, from the language file of the mod. */
	private static String dimensionKey(BiomeCatalog.Dimension dimension)
	{
		return switch (dimension)
		{
			case OVERWORLD -> SECTION_OVERWORLD_KEY;
			case NETHER -> SECTION_NETHER_KEY;
			case END -> SECTION_END_KEY;
			case UNPLACED -> SECTION_UNPLACED_KEY;
		};
	}

	/**
	 * Draws one biome: the pack's own button, its name, and a check on the ones that were found, with
	 * the row and the name of a biome whose reveal is still waiting dimmed - so the list reads as what
	 * has been found, with what is left to find set back from it.
	 *
	 * Beside the name sits either the id of the mod the biome comes from or - while the mouse is on a
	 * row of a biome that was found - the button that forgets it, which takes the place of that id so
	 * that a long list from a modpack stays readable and the button keeps to the end of its own row.
	 */
	private void drawBiome(GuiGraphicsExtractor graphics, BiomeCatalog.Entry entry, int index, int y,
			int rowRight, int hovered, int mouseX, int mouseY)
	{
		boolean found = discovered.contains(entry.id());
		int textY = y + (ROW_HEIGHT - font.lineHeight) / 2;

		graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
				found ? BUTTON_SPRITE : WAITING_BUTTON_SPRITE,
				listLeft, y, rowRight - listLeft, ROW_HEIGHT);

		// the row the mouse is on is held in a thin line of the collection's own gold - what says which
		// row the mouse is on, and with it which biome's name is being read - and it is drawn under the
		// text of the row rather than over it, so a name is never read through a line
		if (index == hovered)
		{
			drawBorder(graphics, listLeft, y, rowRight, y + ROW_HEIGHT, HOVER_COLOR);
		}

		if (found)
		{
			graphics.text(font, CHECK, listLeft + CHECK_X, textY, CHECK_COLOR, true);
		}

		graphics.text(font, entry.name(), listLeft + NAME_X, textY,
				found ? FOUND_COLOR : WAITING_COLOR, true);

		if (found && canForget() && index == hovered)
		{
			drawForgetButton(graphics, entry, y, rowRight, mouseX, mouseY);
			return;
		}

		String source = source(entry.id());
		if (source != null)
		{
			graphics.text(font, source, rowRight - TEXT_MARGIN - font.width(source), textY,
					SOURCE_COLOR, true);
		}
	}

	/**
	 * Whether a biome can be forgotten in this screen at all: it can while the screen is listing one
	 * world, since a discovery is a discovery of a world and the file to take it out of is that
	 * world's. The collection the mod list opens is the case where it cannot - what is listed there
	 * was found in whichever of the player's worlds it was found in, and this screen is not holding
	 * one of them (see {@link #ofEveryWorld}).
	 */
	private boolean canForget()
	{
		return inWorld;
	}

	/**
	 * Draws the button that forgets the biome of the row the mouse is on: a button of the game at the
	 * far right end of the row, where the name of the mod a biome comes from would be drawn - which is
	 * the place the button takes while it is there (see {@link #drawBiome}).
	 *
	 * It is the button the two filters are drawn as, with the mark of the game's own font over it, so
	 * the three buttons of the screen are read as the same kind of thing. It is lit while the mouse is
	 * on the button itself and flat while it is only on the row it belongs to, and its mark turns red
	 * there, since what the mouse is on decides what a press would do: a press anywhere on the row does
	 * nothing, a press on the button asks about forgetting the biome.
	 */
	private void drawForgetButton(GuiGraphicsExtractor graphics, BiomeCatalog.Entry entry, int y,
			int rowRight, int mouseX, int mouseY)
	{
		boolean onButton = isOverForgetButton(y, rowRight, mouseX, mouseY);
		int x = forgetButtonX(rowRight);
		int buttonY = forgetButtonY(y);

		graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
				onButton ? FORGET_HOVERED_SPRITE : FORGET_BUTTON_SPRITE,
				x, buttonY, FORGET_SIZE, FORGET_SIZE);

		drawButtonMark(graphics, FORGET_MARK, x, buttonY, FORGET_SIZE,
				onButton ? FORGET_HOVERED_COLOR : FORGET_COLOR);

		if (onButton)
		{
			graphics.setTooltipForNextFrame(Component.translatable(FORGET_KEY, entry.name()), mouseX, mouseY);
		}
	}

	/**
	 * Where the button that forgets a biome is drawn inside a row: its far right end, so that the row
	 * of a biome that was found ends in a button of the game the way the list itself ends in the two
	 * that filter it.
	 */
	private static int forgetButtonX(int rowRight)
	{
		return rowRight - FORGET_SIZE;
	}

	/** How far down a row the button that forgets a biome is drawn: centred in the row. */
	private static int forgetButtonY(int y)
	{
		return y + (ROW_HEIGHT - FORGET_SIZE) / 2;
	}

	/**
	 * Whether the point is on the button that forgets a biome of the row drawn at {@code y}, which is
	 * the end of the row and not the row itself: what a press does is decided by the button and not by
	 * the row around it.
	 */
	private static boolean isOverForgetButton(int y, int rowRight, double mouseX, double mouseY)
	{
		int x = forgetButtonX(rowRight);
		int buttonY = forgetButtonY(y);

		return mouseX >= x && mouseX < x + FORGET_SIZE
				&& mouseY >= buttonY && mouseY < buttonY + FORGET_SIZE;
	}

	/** The name of the mod a biome comes from, or null while the biome is one of the game's own. */
	private static String source(String id)
	{
		int separator = id.indexOf(':');
		String namespace = separator < 0 ? "" : id.substring(0, separator);

		return namespace.isEmpty() || namespace.equals("minecraft") ? null : namespace;
	}

	/** Draws where the list is, in the game's own scroller, while there is more of it than fits. */
	private void drawScrollBar(GuiGraphicsExtractor graphics)
	{
		if (maxScroll() <= 0.0D)
		{
			// the whole list is on the screen: the game draws no scrollbar for that either
			return;
		}

		int x = listRight - SCROLLBAR_WIDTH;

		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SCROLLER_BACKGROUND_SPRITE,
				x, listTop, SCROLLBAR_WIDTH, listBottom - listTop);
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SCROLLER_SPRITE,
				x, scrollBarThumbTop(), SCROLLBAR_WIDTH, scrollBarThumbHeight());
	}

	/**
	 * Draws the mark inside each of the two filter buttons, over the face of the pack's own button the
	 * two of them wear (see {@link #drawButtonFaces}): the check the list uses for a biome that was
	 * found, and a question mark for one that is still waiting.
	 *
	 * A mark is dim while its filter is off and lights up while it is on - the check in the green of
	 * the checks in the list, the question mark in white - which is what tells the player which of
	 * the two the list is being read by, and whether it is being read by either of them at all.
	 */
	private void drawFilterIcons(GuiGraphicsExtractor graphics)
	{
		drawFilterIcon(graphics, true, CHECK, filter == Filter.DISCOVERED ? CHECK_COLOR : FILTER_OFF_COLOR);
		drawFilterIcon(graphics, false, QUESTION, filter == Filter.REMAINING ? REMAINING_COLOR : FILTER_OFF_COLOR);
	}

	/** Draws {@code mark} centred in the filter button beside the left or the right of the list. */
	private void drawFilterIcon(GuiGraphicsExtractor graphics, boolean left, String mark, int color)
	{
		drawButtonMark(graphics, mark, filterButtonX(left), listTop, FILTER_BUTTON_SIZE, color);
	}

	/**
	 * Draws {@code mark} centred in a button of the game that starts at ({@code x}, {@code y}) and is
	 * {@code size} pixels on both sides: the same centring for the marks of the two filters and for the
	 * cross of the button that forgets a biome, so the three marks sit in their buttons alike.
	 */
	private void drawButtonMark(GuiGraphicsExtractor graphics, String mark, int x, int y, int size,
			int color)
	{
		graphics.text(font, mark, x + (size - font.width(mark)) / 2,
				y + (size - font.lineHeight) / 2, color, true);
	}

	/**
	 * Draws the strip of the two tabs: the two buttons of the game are already on the screen (see
	 * {@link #addTabs}) and wear the face of the pack's own button by now (see {@link #drawButtonFaces}),
	 * and what this adds is the wash over them and the name of each page in its own half.
	 *
	 * The tab of the page being read is washed in the dark of the collection and carries a line of its
	 * accent along its bottom edge; the tab that would take the player elsewhere is washed more lightly
	 * and keeps the plain button of the tab strip (see {@link #TAB_ACTIVE_WASH}). So which page the list below
	 * is holding is said twice - by the wash and the line before a word of it is read, and by the two
	 * names after - and the name being read is lit where the other one is set back, which is the same way
	 * the two filters beside the list say which of them is on (see {@link #drawFilterIcons}). What the
	 * two tabs are not is two kinds of button: the buttons under them are the game's own, and what this
	 * screen adds to them is what it washes them with and the names it writes.
	 */
	private void drawTabs(GuiGraphicsExtractor graphics)
	{
		int x = tabStripLeft;

		for (Page tab : Page.values())
		{
			boolean reading = tab == page;
			Component name = tab.label();
			int y = tabY();

			graphics.fill(x, y, x + TAB_WIDTH, y + TAB_HEIGHT,
					reading ? TAB_ACTIVE_WASH : TAB_INACTIVE_WASH);

			if (reading)
			{
				graphics.fill(x, y + TAB_HEIGHT - 1, x + TAB_WIDTH, y + TAB_HEIGHT, ACCENT_COLOR);
			}

			graphics.text(font, name, x + (TAB_WIDTH - font.width(name)) / 2,
					y + (TAB_HEIGHT - font.lineHeight) / 2,
					reading ? TAB_ON_COLOR : TAB_OFF_COLOR, true);

			x += TAB_WIDTH + TAB_GAP;
		}
	}

	/**
	 * Where the filter button of one side of the list starts, in gui pixels: just outside the frame of
	 * the list, so that the whole of the button is beside the block rather than on it.
	 *
	 * The gap is taken from the frame and not from the list (see {@link #FILTER_BUTTON_GAP}): the frame
	 * reaches {@link #FRAME_PADDING} past the list on every side, so a button set off the list itself is
	 * a button drawn over the band of the block - which is what the two of them were, at the ends of the
	 * first row of the list, reading as two marks dropped inside the panel instead of as two controls
	 * standing beside it.
	 */
	private int filterButtonX(boolean left)
	{
		int offList = FRAME_PADDING + FILTER_BUTTON_GAP;

		return left ? listLeft - offList - FILTER_BUTTON_SIZE : listRight + offList;
	}

	/**
	 * Draws one of the two lines the list is framed with, centred on the list and one texture pixel to one
	 * gui pixel: the size below is the size the drawing is made at (see {@link #UP_LINE_WIDTH}), so there
	 * is no share of it to work out and nothing of it to scale.
	 *
	 * Nothing of the drawing is stretched either, and that is the whole of how it is drawn. A drawing of a
	 * needle and a thread is a drawing of things one pixel wide, so a share of it is a share of a pixel
	 * rather than a smaller drawing, and a width of its own is a thickness of its own at every stroke of
	 * it. What it is drawn at is the one size it has, and a list of any width has the same line centred
	 * over the middle of it.
	 *
	 * The blit it uses is the one that takes a size on the screen and the size of the region it takes from
	 * the texture as two separate pairs, which is the pair that draws a picture as it is: the other blit
	 * takes one pair of sizes and means both at once, and handing it anything but the size of the texture
	 * quietly draws the corner of the drawing blown up by the gui scale.
	 */
	private void drawLine(GuiGraphicsExtractor graphics, Identifier texture, int width, int height, int y)
	{
		int x = listLeft + (listRight - listLeft - width) / 2;

		graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0.0F, 0.0F,
				width, height, width, height, width, height);
	}

	/** Draws {@code text} centred on the screen, at {@code y}. */
	private void drawCentered(GuiGraphicsExtractor graphics, Component text, int y, int color)
	{
		graphics.text(font, text, (width - font.width(text)) / 2, y, color, true);
	}

	/**
	 * Takes the scrollbar when it is pressed, and carries the list along with the mouse from there
	 * (see {@link #mouseDragged}) until the button is let go (see {@link #mouseReleased}).
	 *
	 * A press on the thumb takes it where it was taken, so the thumb does not jump under the hand
	 * that is already holding it, while a press on the groove brings the thumb under the cursor
	 * first, so that a press anywhere on the scrollbar is followed by the drag the player expects.
	 *
	 * The row area answers a press for one thing only, the button a row of a discovered biome shows
	 * while the mouse is on it (see {@link #forgetTargetAt}); anywhere else it is left to the rest of
	 * the screen, so a row of a biome is still not something that answers a click of its own.
	 */
	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick)
	{
		if (event.button() == LEFT_BUTTON && maxScroll() > 0.0D && isOverScrollBar(event.x(), event.y()))
		{
			int thumbTop = scrollBarThumbTop();
			int thumbHeight = scrollBarThumbHeight();

			scrollBarGrabOffset = event.y() >= thumbTop && event.y() < thumbTop + thumbHeight
					? (int) event.y() - thumbTop
					: thumbHeight / 2;
			draggingScrollBar = true;
			dragScrollBarTo(event.y());

			return true;
		}

		if (event.button() == LEFT_BUTTON)
		{
			BiomeCatalog.Entry forget = forgetTargetAt(event.x(), event.y());

			if (forget != null)
			{
				askToForget(forget);
				return true;
			}
		}

		return super.mouseClicked(event, doubleClick);
	}

	/**
	 * The biome whose button a press at the point would land on, or null while it lands on none: a row
	 * shows its button only while the mouse is on that row and only for a biome that was discovered
	 * (see {@link #drawBiome}), and the point has to be on the button itself, so a press can never reach
	 * a button that is not drawn.
	 */
	private BiomeCatalog.Entry forgetTargetAt(double mouseX, double mouseY)
	{
		if (!canForget())
		{
			return null;
		}

		int index = rowAt(mouseX, mouseY);

		if (index < 0)
		{
			return null;
		}

		BiomeCatalog.Entry entry = biomeAt(index);

		if (entry == null || !discovered.contains(entry.id()))
		{
			return null;
		}

		return isOverForgetButton(rowTop(index), rowRight(), mouseX, mouseY) ? entry : null;
	}

	/**
	 * The biome of the row at {@code index}, or null while that row is the line of a section: a section
	 * is not a biome, so there is nothing under a heading for a press to reach.
	 */
	private BiomeCatalog.Entry biomeAt(int index)
	{
		Row row = rows.get(index);

		return row instanceof Row.Biome biome ? biome.entry() : null;
	}

	/**
	 * Asks about forgetting a biome before anything is forgotten: the game's own confirmation screen,
	 * carrying the name of the biome and what forgetting it does - the reveal plays again the next
	 * time it is entered - because a button at the end of a row is a small thing to press by mistake,
	 * and a reveal that has already played is the one thing a collection is made of.
	 *
	 * The collection is what both answers come back to, so a press that was a mistake costs a press of
	 * cancel and nothing else, and closing the confirmation with escape is cancel as well, since the
	 * game's own confirmation screen answers its own close with no.
	 */
	private void askToForget(BiomeCatalog.Entry entry)
	{
		minecraft.setScreenAndShow(new ConfirmScreen(
				confirmed ->
				{
					if (confirmed)
					{
						forget(entry);
					}

					minecraft.setScreenAndShow(this);
				},
				Component.translatable(FORGET_TITLE_KEY),
				Component.translatable(FORGET_MESSAGE_KEY, entry.name()),
				Component.translatable(FORGET_CONFIRM_KEY),
				Component.translatable("gui.cancel")));
	}

	/**
	 * Forgets the biome of a row: the reveal is taken back out of what the world remembers, on disk as
	 * well as in this session (see {@link TitleOverlay#forgetBiome}), and the list is built again with
	 * that row as a biome that still has its reveal waiting, which is what it is now.
	 *
	 * The count over the list comes down with it, and the list is held where it is: a row that leaves
	 * a filtered list is one row out of a list the player is reading, not a reason to throw them back
	 * to the top.
	 */
	private void forget(BiomeCatalog.Entry entry)
	{
		if (!discovered.remove(entry.id()))
		{
			return;
		}

		TitleOverlay.forgetBiome(entry.id());
		discoveredCount--;

		rebuildShown();

		scrollCurrent = clampScroll(scrollCurrent);
		scrollTarget = clampScroll(scrollTarget);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY)
	{
		if (!draggingScrollBar)
		{
			return super.mouseDragged(event, dragX, dragY);
		}

		dragScrollBarTo(event.y());

		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event)
	{
		draggingScrollBar = false;

		return super.mouseReleased(event);
	}

	/**
	 * Puts the point of the thumb the player is holding under {@code mouseY}, and the list with it.
	 *
	 * Nothing is eased here: the list is put exactly where the mouse is, because the hand on the
	 * mouse is what is moving it, and a list that slid after a hand that has already stopped would
	 * read as the scrollbar coming loose from the list it is scrolling.
	 */
	private void dragScrollBarTo(double mouseY)
	{
		int thumbHeight = scrollBarThumbHeight();
		double travel = Math.max(0, listBottom - listTop - thumbHeight);

		if (travel <= 0.0D)
		{
			return;
		}

		double thumbTop = Mth.clamp(mouseY - scrollBarGrabOffset, listTop, listTop + travel);
		double position = clampScroll((thumbTop - listTop) / travel * maxScroll());

		scrollTarget = position;
		scrollCurrent = position;
	}

	/** Whether ({@code x}, {@code y}) is on the scrollbar, the way the game's own lists read it. */
	private boolean isOverScrollBar(double x, double y)
	{
		return x >= listRight - SCROLLBAR_WIDTH && x <= listRight && y >= listTop && y <= listBottom;
	}

	/** How tall the list is, in pixels, which is what the scrollbar thumb is sized against. */
	private int contentHeight()
	{
		return Math.max(1, rows.size() * ROW_PITCH);
	}

	/** The scrollbar thumb, as tall as the game draws it: as much of the track as the list is of it. */
	private int scrollBarThumbHeight()
	{
		int trackHeight = listBottom - listTop;

		return Mth.clamp(trackHeight * trackHeight / contentHeight(), SCROLLBAR_MIN_HEIGHT,
				Math.max(SCROLLBAR_MIN_HEIGHT, trackHeight - SCROLLBAR_BOTTOM_MARGIN));
	}

	/** How far down the thumb is drawn, which is where the list is inside the whole list. */
	private int scrollBarThumbTop()
	{
		double travel = Math.max(0, listBottom - listTop - scrollBarThumbHeight());
		double maxScroll = maxScroll();

		return listTop + (int) (maxScroll <= 0.0D ? 0.0D : scrollCurrent / maxScroll * travel);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY)
	{
		scrollBy(-Math.signum(scrollY) * SCROLL_STEP);

		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event)
	{
		if (event.key() == InputConstants.KEY_DOWN)
		{
			scrollBy(1);
			return true;
		}

		if (event.key() == InputConstants.KEY_UP)
		{
			scrollBy(-1);
			return true;
		}

		if (event.key() == InputConstants.KEY_PAGEDOWN)
		{
			scrollBy(pageRows());
			return true;
		}

		if (event.key() == InputConstants.KEY_PAGEUP)
		{
			scrollBy(-pageRows());
			return true;
		}

		if (event.key() == InputConstants.KEY_HOME)
		{
			scrollBy(-rows.size());
			return true;
		}

		if (event.key() == InputConstants.KEY_END)
		{
			scrollBy(rows.size());
			return true;
		}

		// escape is the game's own business, and the key that opens the screen is left to the key
		// mapping: it is consumed on the client tick, which closes the screen from there
		return super.keyPressed(event);
	}

	/** Moves the list {@code rows} rows further down, letting the slide catch up with it. */
	private void scrollBy(double rows)
	{
		scrollTarget = clampScroll(scrollTarget + rows);
	}

	/** How many rows fit into the list, which is how far a page moves it. */
	private int pageRows()
	{
		return Math.max(1, (listBottom - listTop) / ROW_PITCH);
	}

	/**
	 * How far the list can be scrolled, in rows: the rows it is showing less the rows that fit in
	 * the box. It is a fraction of a row, so the last row of the list stops at the bottom of the box
	 * and the top of the list is allowed to be part of a row, which is what a smooth list looks
	 * like.
	 */
	private double maxScroll()
	{
		double visibleRows = (listBottom - listTop) / (double) ROW_PITCH;

		return Math.max(0.0D, rows.size() - visibleRows);
	}

	private double clampScroll(double value)
	{
		return Mth.clamp(value, 0.0D, maxScroll());
	}
}
