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
 * Every colour the screen draws itself comes from here - what is laid over the panel the list sits on,
 * the lines of text, what is washed over the tab of the page being read and the silver a button under
 * the keyboard is drawn in - and the two themes are two answers to the same list of questions. What is
 * not here is what is not a colour: the block the list is held in, the rows, the tabs and the two
 * filters are sprites of the game in both themes, so is the bar counting the collection in the theme
 * the collection opens on, and one window, one button and one experience bar out of the game's own set
 * of gui sprites are what this screen is made of - a resource pack that redraws any of them redraws
 * this screen with them. The two drawings that frame the list are the mod's own in both themes (see
 * {@link #frame()} and {@link #bar()}).
 *
 * The game's own theme is not a second palette that has to be kept matching the game: it is the
 * colours the screen was drawn in before there was a theme at all - the flat black at 44% the game's
 * own lists sit on, the frame being one of the game's buttons, the white and the grey the screen's
 * text was already in, and none of the accents the dark theme adds. What it is there for is a player
 * who wants the collection to read as one more screen of the game, which is what the mod's other theme
 * is not: the game's own window in the game's own three greys, gold where the dark theme accents
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
	 * The theme the collection opens in: the game's own window, in the game's own greys, and its own
	 * experience bar. It is the default of the mod (see {@link WandererConfig}), so a player who never
	 * opens the theme button never sees the other one.
	 *
	 * The block the list is held in is the game's own window: a band of grey with a black line around
	 * it, a lit edge over a darker one and a single flat grey inside, drawn at the size of the block -
	 * which is what keeps a list of sixty-seven rows from reading as a hole cut in the world - and it
	 * drops a light shadow, so the block stands off the world behind it. What the list sits on is that
	 * flat inside, and the rows are the game's own buttons, one tone apart from the grey they sit on: a
	 * biome that was found is the lit button and one whose reveal is still waiting is the flat, dim one
	 * the game draws what cannot be pressed with, so the order of the list is the order of the game's
	 * own greys rather than anything the mod paints over it. The bar counting the collection is the
	 * game's own experience bar, trough and fill both, so how much of the world has been found is read
	 * in the one shape every player already reads a share of progress in.
	 *
	 * The gold is the one accent the theme has, and it is spent on three places: the name of a
	 * dimension, the tab of the page being read, and the row the mouse is on. The text is read by
	 * brightness instead - the title, then the count, then the name of a biome that was found, then
	 * the name of one that is still waiting - so the eye has an order to read the screen in before
	 * anything is gold.
	 */
	static final CollectionTheme DARK = new CollectionTheme("dark",
			"screen.immersive_titles.collection.theme.dark",
			new Frame(true, 0, 0x59000000),
			new Text(0xFFE8E8E8, 0xFFC9C9C9, 0xFFE0B15A, 0x33FFFFFF, 0xFFF2F2F2, 0xFF787878, 0xFF6A6A6A),
			new Tabs(0x99000000, 0x40000000, 0xFFE0B15A, 0xFFFFFFFF, 0xFF8A8A8A),
			new Bar(true, 0, 0),
			0xFF66E06A, 0x99E0B15A, 0xFFC9CCD1);

	/**
	 * The game's own theme: the collection drawn in the colours of the screens around it.
	 *
	 * The frame is one of the game's buttons, drawn behind the panel the way it always was, and the
	 * panel is the black at 44% the game's own lists inside a world sit on, so nothing of the block
	 * stands out of the menus the player has just come from. None of the dark theme's three accents
	 * are drawn here - no shadow, no gold under a tab, no border around the row the mouse is on -
	 * because the game draws none of those around a list of its own, and the two tabs are told
	 * apart the way they were before: the name of the page being read in white and the other one in
	 * grey, over two buttons of the game that are otherwise the same.
	 *
	 * The bar counting the collection is the flat one it was, rather than the game's own experience
	 * bar: this theme is the screen as it was before there was a theme at all, and what it counts is
	 * still counted in a trough of the game's own black and the green of the game's own check.
	 */
	static final CollectionTheme GAME = new CollectionTheme("game",
			"screen.immersive_titles.collection.theme.game",
			new Frame(false, 0x70000000, 0),
			new Text(0xFFFFFFFF, 0xFFA0A0A0, 0xFFFFAA00, 0x40FFFFFF, 0xFFFFFFFF, 0xFFA0A0A0, 0xFF828282),
			new Tabs(0, 0, 0, 0xFFFFFFFF, 0xFF909090),
			new Bar(false, 0x70000000, 0xFF55FF55),
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
	 * The block of the list: what the frame around it and the panel under it are, and what is laid over
	 * that panel.
	 *
	 * A window and a button are the same drawing at two sizes in the game - a sprite whose band is the
	 * frame, whose middle is the panel and which is nine sliced so that it fits whatever box it is drawn
	 * in - so which of the two a theme names is a question of how large the block is read as rather than
	 * of what it is made of, and the answer is a sprite of the game either way: one that a resource pack
	 * redraws like the rows, the tabs and the two filters beside it.
	 *
	 * @param window whether the block is the game's own window rather than one of its buttons: the
	 *               window is the frame of a list of things, and the button is the frame the screen was
	 *               drawn with before there was a theme
	 * @param panel  what is drawn over the panel the sprite leaves inside the frame - the black at 44%
	 *               the game's own lists sit on in the game's own theme - or nothing at all, while the
	 *               panel the sprite draws itself is the one the list is read on
	 * @param shadow the shadow the block drops, or none at all
	 */
	public record Frame(boolean window, int panel, int shadow)
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
	 * @param gameBar whether the trough and the found share of it are the game's own experience bar -
	 *                the sprite the game draws a share of progress with, read twice: once whole for the
	 *                trough and once as the region the share covers - rather than the two colours below,
	 *                which are the flat bar the game's own theme is drawn with
	 * @param track   the trough the bar is read against, where it is not the game's own
	 * @param fill    the share of the collection that is found, where it is not the game's own
	 */
	public record Bar(boolean gameBar, int track, int fill)
	{
	}
}
