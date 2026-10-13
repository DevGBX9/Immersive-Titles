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
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

import java.util.ArrayList;
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
 * everyone has is a list that says something: what is left below them is what is left to find. The
 * two buttons beside the list are for reading that list two ways: each one keeps a single end of it
 * - what was found, and what is left - so the question "what have I got left" is one press away
 * instead of a scroll through everything already found (see {@link #toggleFilter}).
 *
 * Everything the screen is made of is the game's own, so it looks like the menus it sits next to
 * and follows a resource pack that redraws them - everything but the two lines the list is framed
 * with, which are the mod's own drawing:
 *
 * - every biome is a row drawn with the game's own button sprite, and a biome that was discovered
 *   is drawn with the sprite the game draws a button it will not let anyone press in, dimmed with
 *   its name and marked with a check,
 * - the panel the rows sit on is the colour of the game's own list background inside a world, which
 *   is black at 44% - measured from {@code inworld_menu_list_background.png}, the one the game's
 *   lists are drawn with,
 * - the two lines framing the list are the mod's own, one over the list and one under it, drawn
 *   across the width of the list (see {@link #UP_LINE}),
 * - the scrollbar is the game's scroller, in the same six pixels of width the game draws it in, and
 *   it can be taken and dragged like any other, see {@link #mouseClicked}, and
 * - the three buttons - the two filters and the one that closes the screen - are buttons of the
 *   game, so they hover, click and speak for themselves. The list is narrower than the screen and
 *   centred, which leaves the room at its sides that those two small buttons sit in.
 *
 * Two lines of the mod's own drawing in a screen of the game's own pieces is a deliberate exception.
 * The rows have to read as the game's own rows, because they are a list of what the game holds, but
 * the frame around them is where the mod says what kind of list this is - a pair of drawn ornaments
 * over and under it reads as a collection, where the game's own two-pixel separators read as a menu.
 *
 * The rows are drawn rather than made of widgets, because there is nothing to press on a biome:
 * a row that cannot be clicked should not be given a hover highlight, a click, a focus ring or a
 * keyboard shortcut that would suggest otherwise. Everything the player can do is scroll, filter and
 * close.
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
	/** Which biomes the list is showing: all of them, or only one end of them. */
	private enum Filter
	{
		/** Every biome of the world, discovered or not. */
		ALL,
		/** Only the biomes that already had their reveal here. */
		DISCOVERED,
		/** Only the biomes that have not had it yet, i.e. what is left to find. */
		REMAINING
	}

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

	/** The mouse button the scrollbar is taken with, as the game numbers them. */
	private static final int LEFT_BUTTON = 0;

	/** Where the title of the screen, and the line counting the discoveries, are drawn. */
	private static final int TITLE_Y = 8;
	private static final int PROGRESS_Y = 20;

	/** How tall a line of the game's own font is, which every gap of the layout is measured in. */
	private static final int TEXT_HEIGHT = 9;

	/**
	 * The two lines the list is framed with, one over it and one under it. They are the mod's own
	 * drawing: two white lines on a flat dark sheet, cut out of it so that only the lines are left
	 * (see {@code templates/collection_frame}), and the list is drawn between them as if they were
	 * the top and the bottom of its frame.
	 */
	private static final Identifier UP_LINE = Identifier.fromNamespaceAndPath(WandererTitles.MOD_ID,
			"textures/gui/collection_up_line.png");
	private static final Identifier DOWN_LINE = Identifier.fromNamespaceAndPath(WandererTitles.MOD_ID,
			"textures/gui/collection_down_line.png");

	/**
	 * The width both of those textures are written at, and the height of each of them there. They are
	 * written at twice the height they are drawn in: a game at gui scale 2 draws them one to one, and
	 * a game at gui scale 1 reads them through the area filter the scaling down is, instead of
	 * dropping every other pixel of a line that is one pixel thin.
	 */
	private static final int LINE_TEXTURE_WIDTH = 880;
	private static final int UP_LINE_TEXTURE_HEIGHT = 44;
	private static final int DOWN_LINE_TEXTURE_HEIGHT = 52;

	/** How tall each of the two lines is drawn on the screen, in gui pixels. */
	private static final int UP_LINE_HEIGHT = UP_LINE_TEXTURE_HEIGHT / 2;
	private static final int DOWN_LINE_HEIGHT = DOWN_LINE_TEXTURE_HEIGHT / 2;

	/**
	 * The gap kept between a line and the list it frames, and between a line and the line of text it
	 * sits under: the drawing keeps a little clear room around its own strokes, so the two are set
	 * off from each other rather than touching.
	 */
	private static final int LINE_GAP = 2;

	/**
	 * Where the line over the list is drawn, under the line counting the discoveries, and where the
	 * list starts under it: the title, the count and that line take the top of the screen, each with
	 * the same small gap under it.
	 */
	private static final int UP_LINE_Y = PROGRESS_Y + TEXT_HEIGHT + LINE_GAP;
	private static final int LIST_TOP = UP_LINE_Y + UP_LINE_HEIGHT + LINE_GAP;

	/** How wide the panel of the list is kept away from the edges of the screen. */
	private static final int PANEL_MARGIN = 20;

	/**
	 * How much of the screen the list takes at most, in gui pixels. The list is narrower than the
	 * screen on purpose: the room beside it is what the two filter buttons sit in, and a row that
	 * stretches from edge to edge is read as a bar rather than as an item of a list.
	 */
	private static final int LIST_MAX_WIDTH = 440;

	/** How big the two filter buttons are, and how far they sit from the list they filter. */
	private static final int FILTER_BUTTON_SIZE = 20;
	private static final int FILTER_BUTTON_GAP = 6;

	/** The room one of those buttons takes at one side of the list, margin included. */
	private static final int SIDE_SPACE = PANEL_MARGIN + FILTER_BUTTON_GAP + FILTER_BUTTON_SIZE;

	/** How far the hint above the button is drawn from the bottom of the screen. */
	private static final int HINT_Y_FROM_BOTTOM = 44;

	/**
	 * Room the list leaves below itself, in gui pixels: the line that frames its bottom (2 + 26), the
	 * hint under that line (9), the button that closes the screen (20) and the room under the button
	 * (8), with a small gap between each of them. The hint and the button keep the places they have
	 * always had, so the line above them is what this room is larger than it was by.
	 */
	private static final int FOOTER_HEIGHT = 74;

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

	/** The glyph the filter of the biomes that are left to find is marked with. */
	private static final String QUESTION = "?";

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

	/** The mark of a filter that is turned off, and the colour the other one lights up in. */
	private static final int FILTER_OFF_COLOR = 0xFF909090;
	private static final int REMAINING_COLOR = 0xFFFFFFFF;

	/** The biomes to list, in the order they are shown in. */
	private final List<BiomeCatalog.Entry> entries;

	/** The biomes of {@link #entries} the list is showing right now, i.e. the ones the filter keeps. */
	private final List<BiomeCatalog.Entry> shown = new ArrayList<>();

	/** The biomes of the world that already had their reveal, i.e. the ones that are checked. */
	private final Set<String> discovered;

	/** How many of {@link #entries} are in {@link #discovered}, counted once when the screen opens. */
	private final int discoveredCount;

	/** Which biomes the list is showing; the two buttons beside it switch this, see {@link #toggleFilter}. */
	private Filter filter = Filter.ALL;

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

		rebuildShown();
	}

	@Override
	protected void init()
	{
		// the list is centred and narrower than the screen, with the room the two filter buttons need
		// left empty beside it
		int listWidth = Math.max(0, Math.min(LIST_MAX_WIDTH, width - 2 * SIDE_SPACE));
		listLeft = (width - listWidth) / 2;
		listRight = listLeft + listWidth;
		listTop = LIST_TOP;
		listBottom = height - FOOTER_HEIGHT;

		// a resize can leave the list scrolled past its own end
		scrollTarget = clampScroll(scrollTarget);
		scrollCurrent = clampScroll(scrollCurrent);

		// the slide is measured against the clock, so the frame it starts from is this one
		lastFrameMs = Util.getMillis();

		addFilterButtons();

		addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
				.bounds(width / 2 - 75, height - 28, 150, 20)
				.build());
	}

	/**
	 * Adds the two buttons that filter the list: the one at the left end of the first row keeps the
	 * biomes that were discovered, the one at the right end keeps the ones that are left to find.
	 *
	 * Both are buttons of the game, so they hover, click, play the game's click and answer to the
	 * narrator like any other, and each one carries the mark its filter is about as its face (see
	 * {@link #drawFilterIcons}) together with a tooltip that spells it out, because a mark alone
	 * says what a button is about rather than what pressing it does.
	 */
	private void addFilterButtons()
	{
		addRenderableWidget(filterButton(true, Filter.DISCOVERED,
				"screen.immersive_titles.collection.filter_discovered"));
		addRenderableWidget(filterButton(false, Filter.REMAINING,
				"screen.immersive_titles.collection.filter_remaining"));
	}

	/** Builds one of the two filter buttons, at the left or the right end of the first row. */
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

	/** Fills {@link #shown} with the biomes the current filter keeps, in the order of the catalog. */
	private void rebuildShown()
	{
		shown.clear();

		for (BiomeCatalog.Entry entry : entries)
		{
			boolean found = discovered.contains(entry.id());

			if ((filter == Filter.DISCOVERED && !found) || (filter == Filter.REMAINING && found))
			{
				continue;
			}

			shown.add(entry);
		}
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

		// the two lines of the mod's own drawing the list is framed with, one over it and one under
		// it, drawn before the text below so that the hint and the button stay on top of everything
		drawLine(graphics, UP_LINE, UP_LINE_HEIGHT, UP_LINE_TEXTURE_HEIGHT, UP_LINE_Y);
		drawLine(graphics, DOWN_LINE, DOWN_LINE_HEIGHT, DOWN_LINE_TEXTURE_HEIGHT,
				listBottom + LINE_GAP);

		drawCentered(graphics, Component.translatable("screen.immersive_titles.collection.hint"),
				height - HINT_Y_FROM_BOTTOM, MUTED_COLOR);

		// the buttons added in init() are among the screen's renderables
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);

		// and their marks are drawn over them, once the buttons themselves are on the screen
		drawFilterIcons(graphics);
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
	private void drawRows(GuiGraphicsExtractor graphics)
	{
		if (shown.isEmpty())
		{
			drawCentered(graphics, Component.translatable("screen.immersive_titles.collection.empty"),
					listTop + (listBottom - listTop - font.lineHeight) / 2, MUTED_COLOR);
			return;
		}

		int slide = (int) Math.round(scrollCurrent * ROW_PITCH);
		int rowRight = listRight - SCROLLBAR_WIDTH - SCROLLBAR_MARGIN;

		// the row above the first one that is fully in the list may still be hanging into it
		int first = Math.max(0, (int) Math.floor(scrollCurrent) - 1);

		graphics.enableScissor(listLeft, listTop, listRight, listBottom);

		for (int index = first; index < shown.size(); index++)
		{
			int y = listTop + index * ROW_PITCH - slide;
			if (y >= listBottom)
			{
				break;
			}

			if (y + ROW_HEIGHT > listTop)
			{
				drawRow(graphics, shown.get(index), y, rowRight);
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
	 * Draws the mark inside each of the two filter buttons: the check the list uses for a biome that
	 * was found, and a question mark for one that is still waiting.
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

	/** Draws {@code mark} centred in the filter button at the left or the right end of the list. */
	private void drawFilterIcon(GuiGraphicsExtractor graphics, boolean left, String mark, int color)
	{
		graphics.text(font, mark,
				filterButtonX(left) + (FILTER_BUTTON_SIZE - font.width(mark)) / 2,
				listTop + (FILTER_BUTTON_SIZE - font.lineHeight) / 2,
				color, true);
	}

	/** Where the filter button of one side of the list starts, in gui pixels. */
	private int filterButtonX(boolean left)
	{
		return left ? listLeft - FILTER_BUTTON_GAP - FILTER_BUTTON_SIZE : listRight + FILTER_BUTTON_GAP;
	}

	/**
	 * Draws one of the two lines the list is framed with, across the whole width of the list: the
	 * drawing is stretched to it, which is what a line of a frame is for - its two ends are the ends
	 * of the list, and the room it is drawn across is the list it frames, at any screen size.
	 *
	 * The height it is drawn at is half the height its texture was written at (see
	 * {@link #UP_LINE_HEIGHT}), so the line is the same drawing at any screen size, its shape coming
	 * from the drawing rather than from the room it happens to be given.
	 */
	private void drawLine(GuiGraphicsExtractor graphics, Identifier texture, int drawnHeight,
			int textureHeight, int y)
	{
		graphics.blit(RenderPipelines.GUI_TEXTURED, texture, listLeft, y, 0.0F, 0.0F,
				listRight - listLeft, drawnHeight, LINE_TEXTURE_WIDTH, textureHeight);
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
	 * The row area is not a target of anything: a press there is left to the rest of the screen.
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

		return super.mouseClicked(event, doubleClick);
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
		return Math.max(1, shown.size() * ROW_PITCH);
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
			scrollBy(-shown.size());
			return true;
		}

		if (event.key() == InputConstants.KEY_END)
		{
			scrollBy(shown.size());
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

		return Math.max(0.0D, shown.size() - visibleRows);
	}

	private double clampScroll(double value)
	{
		return Mth.clamp(value, 0.0D, maxScroll());
	}
}
