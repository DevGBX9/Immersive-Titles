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

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

import java.util.List;
import java.util.Set;

/**
 * The collection of biomes: every biome the game in front of the player has, with a check on the
 * ones that already had their reveal in this world.
 *
 * It is the list side of what the mod already does. A reveal is remembered per world (see
 * {@link WorldIdentity} and {@link DiscoveredBiomes}), and what a world remembers is the same set
 * this screen marks, so the two can never disagree about what the player has found: what is
 * checked here is exactly what no longer plays its reveal, and what is not checked is what still
 * has one waiting. The bar at the top is that same count.
 *
 * The order is the mod's own, from the biomes a player meets while walking around to the ones that
 * take a portal or a search (see {@link BiomeCatalog}), because a list that starts with the biomes
 * everyone has is a list that says something: what is left below them is what is left to find.
 *
 * Everything the screen is made of is the game's own, so it looks like the menus it sits next to
 * and follows a resource pack that redraws them:
 *
 * - every biome is a row drawn with the game's own button sprite, and a biome that was discovered
 *   is drawn with the sprite the game draws a button it will not let anyone press in, dimmed with
 *   its name and marked with a check,
 * - the panel the rows sit on is the colour of the game's own list background inside a world, which
 *   is black at 44% - measured from {@code inworld_menu_list_background.png}, the one the game's
 *   lists are drawn with,
 * - the two lines framing the list are the separators the game's own lists are framed with
 *   ({@link Screen#INWORLD_HEADER_SEPARATOR}), and
 * - the scrollbar is the game's scroller, in the same six pixels of width the game draws it in.
 *
 * The rows are drawn rather than made of widgets, because there is nothing to press on a biome:
 * a row that cannot be clicked should not be given a hover highlight, a click, a focus ring or a
 * keyboard shortcut that would suggest otherwise. Everything the player can do is scroll, jump and
 * close, and the button at the bottom is a real one of the game's.
 *
 * Scrolling is smooth: the wheel and the keys move where the list is going, not where it is drawn,
 * and the drawn list eases after it (see {@link #advanceScroll}). Rows therefore slide up and down
 * instead of jumping, by whole pixels, so the text moves without shimmering. The step is taken from
 * the clock rather than from a fixed amount per frame, so the slide takes the same time at any
 * frame rate, and it is capped so that a frame the game spent stalled cannot skip the list over
 * everything it missed.
 *
 * The screen is left at the game's own defaults wherever it matters. It does not override
 * {@code isPauseScreen}, which is true for a screen in this version of the game, so opening it
 * pauses a single player world exactly like the escape menu does, and it does not draw a background
 * of its own, so it gets the same one the escape menu gets: the world behind it, blurred and held
 * while the game waits. A title reveal that is running when the screen opens holds with it, because
 * a pause screen is exactly what a running reveal waits for.
 *
 * The key that opens it is the mod's own (see {@link TitleOverlay}), and pressing it again closes
 * the screen: the key is consumed on the client tick rather than in the screen, so the same press
 * that closes the screen cannot open it again on the way out.
 */
final class BiomeCollectionScreen extends Screen
{
	/** Height of one row, i.e. of one biome: the height of a button of the game. */
	private static final int ROW_HEIGHT = 20;

	/** Room left between two rows: the spacing the game leaves between two of its own buttons. */
	private static final int ROW_SPACING = 4;

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

	/** Where the title of the screen, and the line counting the discoveries, are drawn. */
	private static final int TITLE_Y = 8;
	private static final int PROGRESS_Y = 20;

	/** Where the list starts, under the title and the line counting the discoveries. */
	private static final int LIST_TOP = 36;

	/** How wide the panel of the list is kept away from the edges of the screen. */
	private static final int PANEL_MARGIN = 20;

	/** Room the list leaves for the hint and the button below it. */
	private static final int FOOTER_HEIGHT = 54;

	/** How high the two lines framing the list are drawn, and the texture they are cut from. */
	private static final int SEPARATOR_HEIGHT = 2;
	private static final int SEPARATOR_TEXTURE_WIDTH = 32;

	/** The strip at the right end of the list the scrollbar is drawn in, in gui pixels. */
	private static final int SCROLLBAR_WIDTH = 6;

	/** How far the rows stop before that strip, so the scrollbar is never drawn over a row. */
	private static final int SCROLLBAR_MARGIN = 4;

	/**
	 * The shortest the scrollbar thumb is allowed to be, and the room it keeps from the bottom of
	 * its track: the two ends of the game's own scroller height, which is as much of the track as
	 * the list is of its content, between a floor of its own and the bottom of the list.
	 */
	private static final int SCROLLBAR_MIN_HEIGHT = 32;
	private static final int SCROLLBAR_BOTTOM_MARGIN = 8;

	/** Where the check of a discovered biome and its name sit inside a row, from its left end. */
	private static final int CHECK_X = 9;
	private static final int NAME_X = 22;

	/** How far the text of a row is kept away from the right end of it. */
	private static final int TEXT_MARGIN = 8;

	/** The glyph a biome that was discovered is marked with, from the game's own font. */
	private static final String CHECK = "\u2714";

	/** The button of the game a row of a biome still to find is drawn with. */
	private static final Identifier BUTTON_SPRITE = Identifier.withDefaultNamespace("widget/button");

	/**
	 * The button of the game a row of a biome that was found is drawn with: the one it draws a
	 * button it will not let anyone press in, which is a flat, dimmed panel instead of a button.
	 */
	private static final Identifier DISCOVERED_BUTTON_SPRITE = Identifier.withDefaultNamespace("widget/button_disabled");

	/** The game's own scroller, and the groove it runs in. */
	private static final Identifier SCROLLER_SPRITE = Identifier.withDefaultNamespace("widget/scroller");
	private static final Identifier SCROLLER_BACKGROUND_SPRITE =
			Identifier.withDefaultNamespace("widget/scroller_background");

	/**
	 * What the list is drawn on: black at 44%, which is what the game's own list background inside a
	 * world is - {@code inworld_menu_list_background.png} is a single colour, (0, 0, 0, 112), which
	 * is the whole texture, so the game is drawing exactly this rectangle stretched over its lists.
	 */
	private static final int PANEL_COLOR = 0x70000000;

	private static final int TITLE_COLOR = 0xFFFFFFFF;
	private static final int MUTED_COLOR = 0xFFA0A0A0;
	private static final int NAME_COLOR = 0xFFFFFFFF;
	private static final int DISCOVERED_COLOR = 0xFFA0A0A0;
	private static final int CHECK_COLOR = 0xFF55FF55;
	private static final int SOURCE_COLOR = 0xFF828282;

	/** The biomes to list, in the order they are shown in. */
	private final List<BiomeCatalog.Entry> entries;

	/** The biomes of the world that already had their reveal, i.e. the ones that are checked. */
	private final Set<String> discovered;

	/** How many of {@link #entries} are in {@link #discovered}, counted once when the screen opens. */
	private final int discoveredCount;

	/** Where the list has been told to go, in rows: what the wheel and the keys move. */
	private double scrollTarget;

	/** Where the list is drawn right now, in rows: it eases towards {@link #scrollTarget}. */
	private double scrollCurrent;

	/** Wall clock time the list was drawn at last, which the step of that easing is measured from. */
	private long lastFrameMs;

	/** The box the list is drawn in, in gui pixels. Filled in by {@link #init()}. */
	private int listLeft;
	private int listTop;
	private int listRight;
	private int listBottom;

	BiomeCollectionScreen(ClientLevel level, Set<String> discovered)
	{
		super(Component.translatable("screen.immersive_titles.collection.title"));

		this.entries = BiomeCatalog.all(level);
		this.discovered = discovered;

		int found = 0;
		for (BiomeCatalog.Entry entry : entries)
		{
			if (discovered.contains(entry.id()))
			{
				found++;
			}
		}
		this.discoveredCount = found;
	}

	@Override
	protected void init()
	{
		listLeft = PANEL_MARGIN;
		listRight = width - PANEL_MARGIN;
		listTop = LIST_TOP;
		listBottom = height - FOOTER_HEIGHT;

		// a resize can leave the list scrolled past its own end
		scrollTarget = clampScroll(scrollTarget);
		scrollCurrent = clampScroll(scrollCurrent);

		// the slide is measured against the clock, so the frame it starts from is this one
		lastFrameMs = Util.getMillis();

		addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
				.bounds(width / 2 - 75, height - 28, 150, 20)
				.build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick)
	{
		advanceScroll();

		drawCentered(graphics, title, TITLE_Y, TITLE_COLOR);
		drawCentered(graphics, Component.translatable("screen.immersive_titles.collection.progress",
				discoveredCount, entries.size()), PROGRESS_Y, MUTED_COLOR);

		graphics.fill(listLeft, listTop, listRight, listBottom, PANEL_COLOR);

		drawRows(graphics);
		drawScrollBar(graphics);

		// the lines the game's own lists are framed with, one above the list and one under it
		drawSeparator(graphics, Screen.INWORLD_HEADER_SEPARATOR, listTop - SEPARATOR_HEIGHT);
		drawSeparator(graphics, Screen.INWORLD_FOOTER_SEPARATOR, listBottom);

		drawCentered(graphics, Component.translatable("screen.immersive_titles.collection.hint"),
				height - 44, MUTED_COLOR);

		// the button added in init() is one of the screen's renderables
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
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
	 * on the way.
	 */
	private void drawRows(GuiGraphicsExtractor graphics)
	{
		int slide = (int) Math.round(scrollCurrent * ROW_PITCH);
		int rowRight = listRight - SCROLLBAR_WIDTH - SCROLLBAR_MARGIN;

		// the row above the first one that is fully in the list may still be hanging into it
		int first = Math.max(0, (int) Math.floor(scrollCurrent) - 1);

		graphics.enableScissor(listLeft, listTop, listRight, listBottom);

		for (int index = first; index < entries.size(); index++)
		{
			int y = listTop + index * ROW_PITCH - slide;
			if (y >= listBottom)
			{
				break;
			}

			if (y + ROW_HEIGHT > listTop)
			{
				drawRow(graphics, entries.get(index), y, rowRight);
			}
		}

		graphics.disableScissor();
	}

	/**
	 * Draws one biome: the game's own button, its name, a check and a dimmed name once it was
	 * discovered, and the id of the mod it comes from for a biome of a mod, so a long list from a
	 * modpack stays readable.
	 */
	private void drawRow(GuiGraphicsExtractor graphics, BiomeCatalog.Entry entry, int y, int rowRight)
	{
		boolean found = discovered.contains(entry.id());
		int textY = y + (ROW_HEIGHT - font.lineHeight) / 2;

		graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
				found ? DISCOVERED_BUTTON_SPRITE : BUTTON_SPRITE,
				listLeft, y, rowRight - listLeft, ROW_HEIGHT);

		if (found)
		{
			graphics.text(font, CHECK, listLeft + CHECK_X, textY, CHECK_COLOR, true);
		}

		graphics.text(font, entry.name(), listLeft + NAME_X, textY, found ? DISCOVERED_COLOR : NAME_COLOR, true);

		String source = source(entry.id());
		if (source != null)
		{
			graphics.text(font, source, rowRight - TEXT_MARGIN - font.width(source), textY, SOURCE_COLOR, true);
		}
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
		double maxScroll = maxScroll();

		if (maxScroll <= 0.0D)
		{
			// the whole list is on the screen: the game draws no scrollbar for that either
			return;
		}

		int trackHeight = listBottom - listTop;
		int contentHeight = entries.size() * ROW_PITCH;
		int thumbHeight = Mth.clamp(trackHeight * trackHeight / contentHeight, SCROLLBAR_MIN_HEIGHT,
				Math.max(SCROLLBAR_MIN_HEIGHT, trackHeight - SCROLLBAR_BOTTOM_MARGIN));
		int thumbTop = listTop + (int) (scrollCurrent / maxScroll * Math.max(0, trackHeight - thumbHeight));
		int x = listRight - SCROLLBAR_WIDTH;

		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SCROLLER_BACKGROUND_SPRITE,
				x, listTop, SCROLLBAR_WIDTH, trackHeight);
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SCROLLER_SPRITE,
				x, thumbTop, SCROLLBAR_WIDTH, thumbHeight);
	}

	/** Draws one of the two lines a list of the game is framed with, across the list it frames. */
	private void drawSeparator(GuiGraphicsExtractor graphics, Identifier separator, int y)
	{
		graphics.blit(RenderPipelines.GUI_TEXTURED, separator, listLeft, y, 0.0F, 0.0F,
				listRight - listLeft, SEPARATOR_HEIGHT, SEPARATOR_TEXTURE_WIDTH, SEPARATOR_HEIGHT);
	}

	/** Draws {@code text} centred on the screen, at {@code y}. */
	private void drawCentered(GuiGraphicsExtractor graphics, Component text, int y, int color)
	{
		graphics.text(font, text, (width - font.width(text)) / 2, y, color, true);
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
			scrollBy(-entries.size());
			return true;
		}

		if (event.key() == InputConstants.KEY_END)
		{
			scrollBy(entries.size());
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
	 * How far the list can be scrolled, in rows: its content less the rows that fit in the box.
	 * It is a fraction of a row, so the last row of the list stops at the bottom of the box and
	 * the top of the list is allowed to be part of a row, which is what a smooth list looks like.
	 */
	private double maxScroll()
	{
		double visibleRows = (listBottom - listTop) / (double) ROW_PITCH;

		return Math.max(0.0D, entries.size() - visibleRows);
	}

	private double clampScroll(double value)
	{
		return Mth.clamp(value, 0.0D, maxScroll());
	}
}
