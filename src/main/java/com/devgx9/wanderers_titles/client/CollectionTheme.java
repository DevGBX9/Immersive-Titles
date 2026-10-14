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

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * The colours the collection is drawn in, in the two themes it can be read by: the dark one the screen
 * opens on, and the game's own.
 *
 * Every colour the screen draws itself comes from here - the panel behind the list, the frame around
 * it, the shadow it drops, the lines of text, the strip of the two tabs, the bar counting the
 * collection and the silver a button under the keyboard is drawn in - and the two themes are two
 * answers to the same list of questions. What is not here is what is not a colour: the buttons of the
 * game the rows, the tabs and the two filters are drawn with are sprites of the game in both themes,
 * so a resource pack that redraws a button still redraws this screen's buttons, and the two drawings
 * that frame the list are the mod's own in both (see {@link #frame()}).
 *
 * The game's own theme is not a second palette that has to be kept matching the game: it is the
 * colours the screen was drawn in before there was a theme at all - the flat black at 44% the game's
 * own lists sit on, the frame being one of the game's buttons, the white and the grey the screen's
 * text was already in, and none of the accents the dark theme adds. What it is there for is a player
 * who wants the collection to read as one more screen of the game, which is what the mod's other
 * theme is not: charcoal, one thin lighter edge and a shadow, gold where the dark theme accents
 * anything, and text read by brightness rather than by the game's own three colours.
 *
 * A theme is read once and held by {@link WandererConfig}, which is also what writes it down: the
 * screen asks for the theme and never for which one it is, and the two of them are named in the
 * language file of the mod, since the theme button says out loud which one is being read.
 */
public record CollectionTheme(String id, String key, Frame frame, Text text, Tabs tabs, Bar bar, int check,
		int hover, int focus)
{
	/**
	 * The theme the collection opens in: charcoal and gold. It is the default of the mod (see
	 * {@link WandererConfig}), so a player who never opens the theme button never sees the other one.
	 *
	 * The panel is charcoal rather than black, and it is two tones of it rather than one - the panel
	 * itself and a single lighter row along its top edge - which is what keeps a list of sixty-seven
	 * rows from reading as a hole cut in the world. The frame is one tone up from the panel with a
	 * thin border around it, and the block drops a light shadow: the three of them together are what
	 * gives the panel an edge the eye can find without a line of the game's own drawing around it.
	 *
	 * The gold is the one accent the theme has, and it is spent on three places: the name of a
	 * dimension, the tab of the page being read, and the row the mouse is on. The text is read by
	 * brightness instead - the title, then the count, then the name of a biome that was found, then
	 * the name of one that is still waiting - so the eye has an order to read the screen in before
	 * anything is gold.
	 */
	static final CollectionTheme DARK = new CollectionTheme("dark",
			"screen.immersive_titles.collection.theme.dark",
			new Frame(0xFF23262B, 0xFF3D444E, 0xF0181A1E, 0xFF2C3138, 0x59000000, false),
			new Text(0xFFE8E8E8, 0xFFC9C9C9, 0xFFE0B15A, 0x33FFFFFF, 0xFFF2F2F2, 0xFF787878, 0xFF6A6A6A),
			new Tabs(0x99000000, 0x40000000, 0xFFE0B15A, 0xFFFFFFFF, 0xFF8A8A8A),
			new Bar(0xFF23262B, 0xFF3D444E, 0xFF66E06A),
			0xFF66E06A, 0x99E0B15A, 0xFFC9CCD1);

	/**
	 * The game's own theme: the collection drawn in the colours of the screens around it.
	 *
	 * The frame is one of the game's buttons, drawn behind the panel the way it always was, and the
	 * panel is the black at 44% the game's own lists inside a world sit on, so nothing of the block
	 * stands out of the menus the player has just come from. None of the dark theme's three accents
	 * are drawn here - no shadow, no border, no gold under a tab, no border around the row the mouse
	 * is on - because the game draws none of those around a list of its own, and the two tabs are told
	 * apart the way they were before: the name of the page being read in white and the other one in
	 * grey, over two buttons of the game that are otherwise the same.
	 */
	static final CollectionTheme GAME = new CollectionTheme("game",
			"screen.immersive_titles.collection.theme.game",
			new Frame(0, 0, 0x70000000, 0, 0, true),
			new Text(0xFFFFFFFF, 0xFFA0A0A0, 0xFFFFAA00, 0x40FFFFFF, 0xFFFFFFFF, 0xFFA0A0A0, 0xFF828282),
			new Tabs(0, 0, 0, 0xFFFFFFFF, 0xFF909090),
			new Bar(0x70000000, 0, 0xFF55FF55),
			0xFF55FF55, 0, 0xFFA0A0A0);

	/** The theme written down under {@code id}, or the default one while nothing is. */
	public static CollectionTheme of(String id)
	{
		for (CollectionTheme theme : values())
		{
			if (theme.id.equals(id))
			{
				return theme;
			}
		}

		return DARK;
	}

	/** Both themes, in the order the theme button walks them. */
	public static CollectionTheme[] values()
	{
		return new CollectionTheme[] {DARK, GAME};
	}

	/** The theme after this one, which is what pressing the theme button goes to. */
	public CollectionTheme next()
	{
		CollectionTheme[] all = values();

		for (int index = 0; index < all.length; index++)
		{
			if (all[index] == this)
			{
				return all[(index + 1) % all.length];
			}
		}

		return DARK;
	}

	/** The name of this theme, from the language file of the mod. */
	public MutableComponent name()
	{
		return Component.translatable(key);
	}

	/**
	 * What the frame of the list is drawn as, and what the panel inside it sits on.
	 *
	 * @param fill       the colour of the frame itself, i.e. the band around the panel
	 * @param border     a thin line around the whole block, or none at all
	 * @param panel      what the list is drawn on, over the frame inside it
	 * @param edge       one row along the top of the panel, lighter than it, so it is not flat
	 * @param shadow     the shadow the block drops, or none at all
	 * @param gameButton whether the frame is one of the game's own buttons instead of {@code fill}:
	 *                   what the screen drew around the list before there was a theme, kept for the
	 *                   theme that is meant to look like the game's own screens
	 */
	public record Frame(int fill, int border, int panel, int edge, int shadow, boolean gameButton)
	{
	}

	/**
	 * The colours the text of the screen is drawn in, from the title down.
	 *
	 * @param title   the name of the screen
	 * @param count   the line counting the discoveries, and the count at the end of a section
	 * @param section the name of a dimension at the head of its section
	 * @param rule    the hairline over a section, in the room between two rows
	 * @param found   the name of a biome that already had its reveal
	 * @param waiting the name of one whose reveal is still waiting, set back from the found ones
	 * @param source  the name of the mod a biome comes from, dimmer than either
	 */
	public record Text(int title, int count, int section, int rule, int found, int waiting, int source)
	{
	}

	/**
	 * The strip of the two tabs: what is laid over the two buttons of the game under it, and what
	 * their names are written in.
	 *
	 * @param active   the wash over the tab of the page being read, or none at all
	 * @param inactive the wash over the other one, which is lighter than {@code active}
	 * @param accent   a line along the bottom of the tab being read, or none at all
	 * @param on       the name of the page being read
	 * @param off      the name of the page that is not
	 */
	public record Tabs(int active, int inactive, int accent, int on, int off)
	{
	}

	/**
	 * The bar counting the collection, drawn under the line that counts it in words.
	 *
	 * @param track  the trough the bar is read against
	 * @param border a line around the trough, or none at all
	 * @param fill   the share of the collection that is found
	 */
	public record Bar(int track, int border, int fill)
	{
	}
}
