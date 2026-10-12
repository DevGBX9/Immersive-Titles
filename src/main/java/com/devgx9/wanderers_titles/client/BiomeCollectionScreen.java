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
import net.minecraft.network.chat.Component;

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
	/** Height of one row, i.e. of one biome, in gui pixels. */
	private static final int ROW_HEIGHT = 20;

	/** How far one turn of the wheel, or one arrow key, moves the list, in rows. */
	private static final int SCROLL_STEP = 2;

	/** How wide the panel of the list is kept away from the edges of the screen. */
	private static final int PANEL_MARGIN = 20;

	/** Room the list leaves for the hint and the button below it. */
	private static final int FOOTER_HEIGHT = 54;

	/** The glyph a biome that was discovered is marked with, from the game's own font. */
	private static final String CHECK = "\u2714";

	private static final int PANEL_COLOR = 0xC0101010;
	private static final int ROW_STRIPE_COLOR = 0x18FFFFFF;
	private static final int ROW_HOVER_COLOR = 0x38FFFFFF;
	private static final int TITLE_COLOR = 0xFFFFFFFF;
	private static final int MUTED_COLOR = 0xFFA0A0A0;
	private static final int NAME_COLOR = 0xFFFFFFFF;
	private static final int DISCOVERED_COLOR = 0xFF8C8C8C;
	private static final int CHECK_COLOR = 0xFF55FF55;
	private static final int SOURCE_COLOR = 0xFF6E6E6E;
	private static final int SCROLL_TRACK_COLOR = 0x40000000;
	private static final int SCROLL_THUMB_COLOR = 0xFF909090;

	/** The biomes to list, in the order they are shown in. */
	private final List<BiomeCatalog.Entry> entries;

	/** The biomes of the world that already had their reveal, i.e. the ones that are checked. */
	private final Set<String> discovered;

	/** How many of {@link #entries} are in {@link #discovered}, counted once when the screen opens. */
	private final int discoveredCount;

	/** How many rows the list is scrolled down by, in rows. */
	private int scroll;

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
		listTop = 44;
		listBottom = height - FOOTER_HEIGHT;

		// a resize can leave the list scrolled past its own end
		scroll = clampScroll(scroll);

		addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
				.bounds(width / 2 - 75, height - 28, 150, 20)
				.build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick)
	{
		drawCentered(graphics, title, 12, TITLE_COLOR);
		drawCentered(graphics, Component.translatable("screen.immersive_titles.collection.progress",
				discoveredCount, entries.size()), 24, MUTED_COLOR);

		graphics.fill(listLeft - 4, listTop - 4, listRight + 4, listBottom + 4, PANEL_COLOR);

		drawRows(graphics, mouseX, mouseY);
		drawScrollBar(graphics);

		drawCentered(graphics, Component.translatable("screen.immersive_titles.collection.hint"),
				height - 44, MUTED_COLOR);

		// the button added in init() is one of the screen's renderables
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	/** Draws the rows that fit into the list, marked and hovered, under a scissor of the list box. */
	private void drawRows(GuiGraphicsExtractor graphics, int mouseX, int mouseY)
	{
		int rows = visibleRows();

		graphics.enableScissor(listLeft, listTop, listRight, listBottom);

		for (int row = 0; row < rows; row++)
		{
			int index = scroll + row;
			if (index >= entries.size())
			{
				break;
			}

			drawRow(graphics, entries.get(index), row, listTop + row * ROW_HEIGHT, mouseX, mouseY);
		}

		graphics.disableScissor();
	}

	/**
	 * Draws one biome: its name, a check and a dimmed name once it was discovered, and the id of the
	 * mod it comes from for a biome of a mod, so a long list from a modpack stays readable.
	 */
	private void drawRow(GuiGraphicsExtractor graphics, BiomeCatalog.Entry entry, int row, int y,
			int mouseX, int mouseY)
	{
		boolean found = discovered.contains(entry.id());
		boolean hovered = mouseX >= listLeft && mouseX <= listRight && mouseY >= y && mouseY < y + ROW_HEIGHT;

		if (hovered)
		{
			graphics.fill(listLeft, y, listRight, y + ROW_HEIGHT, ROW_HOVER_COLOR);
		}
		else if (row % 2 == 0)
		{
			graphics.fill(listLeft, y, listRight, y + ROW_HEIGHT, ROW_STRIPE_COLOR);
		}

		int textY = y + (ROW_HEIGHT - font.lineHeight) / 2;
		int nameX = listLeft + 22;

		if (found)
		{
			graphics.text(font, CHECK, listLeft + 6, textY, CHECK_COLOR, false);
		}

		graphics.text(font, entry.name(), nameX, textY, found ? DISCOVERED_COLOR : NAME_COLOR, false);

		String source = source(entry.id());
		if (source != null)
		{
			graphics.text(font, source, listRight - 8 - font.width(source), textY, SOURCE_COLOR, false);
		}
	}

	/** The name of the mod a biome comes from, or null while the biome is one of the game's own. */
	private static String source(String id)
	{
		int separator = id.indexOf(':');
		String namespace = separator < 0 ? "" : id.substring(0, separator);

		return namespace.isEmpty() || namespace.equals("minecraft") ? null : namespace;
	}

	/** Draws where the list is: nothing at all while every biome fits on the screen. */
	private void drawScrollBar(GuiGraphicsExtractor graphics)
	{
		int rows = visibleRows();
		int total = entries.size();

		if (total <= rows)
		{
			return;
		}

		int trackTop = listTop;
		int trackHeight = listBottom - listTop;
		int thumbHeight = Math.max(ROW_HEIGHT, trackHeight * rows / total);
		int thumbTop = trackTop + (trackHeight - thumbHeight) * scroll / Math.max(1, total - rows);
		int x = listRight + 1;

		graphics.fill(x, trackTop, x + 3, listBottom, SCROLL_TRACK_COLOR);
		graphics.fill(x, thumbTop, x + 3, thumbTop + thumbHeight, SCROLL_THUMB_COLOR);
	}

	/** Draws {@code text} centred on the screen, at {@code y}. */
	private void drawCentered(GuiGraphicsExtractor graphics, Component text, int y, int color)
	{
		graphics.text(font, text, (width - font.width(text)) / 2, y, color, false);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY)
	{
		setScroll(scroll - (int) Math.signum(scrollY) * SCROLL_STEP);

		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event)
	{
		if (event.key() == InputConstants.KEY_DOWN)
		{
			setScroll(scroll + 1);
			return true;
		}

		if (event.key() == InputConstants.KEY_UP)
		{
			setScroll(scroll - 1);
			return true;
		}

		if (event.key() == InputConstants.KEY_PAGEDOWN)
		{
			setScroll(scroll + visibleRows());
			return true;
		}

		if (event.key() == InputConstants.KEY_PAGEUP)
		{
			setScroll(scroll - visibleRows());
			return true;
		}

		if (event.key() == InputConstants.KEY_HOME)
		{
			setScroll(0);
			return true;
		}

		if (event.key() == InputConstants.KEY_END)
		{
			setScroll(entries.size());
			return true;
		}

		// escape is the game's own business, and the key that opens the screen is left to the key
		// mapping: it is consumed on the client tick, which closes the screen from there
		return super.keyPressed(event);
	}

	/** How many rows of the list fit into the box {@link #init()} laid out. */
	private int visibleRows()
	{
		return Math.max(1, (listBottom - listTop) / ROW_HEIGHT);
	}

	private void setScroll(int value)
	{
		scroll = clampScroll(value);
	}

	private int clampScroll(int value)
	{
		return Math.max(0, Math.min(value, Math.max(0, entries.size() - visibleRows())));
	}
}
