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
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * What the mod writes down between one run of the game and the next: the theme the collection is
 * drawn in.
 *
 * It is one file of the mod's own in the config folder of the game, {@code immersive_titles.json},
 * written by hand-readable JSON and read on the first frame that asks for a theme rather than on
 * start, so a player who never opens the collection never touches the disk. What it holds is the id
 * of a theme (see {@link CollectionTheme}) and nothing else, and the mod's own theme is what a file
 * that is missing, empty, unreadable or holding an id nobody knows reads as: a config file is a thing
 * a player can edit, and one that breaks the screen it configures is worse than one that is ignored.
 *
 * The file is written by the screen itself, when the theme button is pressed (see
 * {@link BiomeCollectionScreen}), and never at any other time: the mod has one setting, and a setting
 * that is written when nothing changed is a file the player's backup tool has to read for nothing.
 *
 * This is the mod's own file and not one of the game's: it is not registered as a Fabric config, it is
 * not synced anywhere, and it is not read by anything but this class.
 */
public final class WandererConfig
{
	/** The file of the mod's own, in the config folder of the game. */
	private static final String FILE_NAME = "immersive_titles.json";

	/** The one key it holds, and the only one it reads: the id of the theme of the collection. */
	private static final String THEME = "theme";

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/**
	 * The theme of this run, read from the file on the first ask and held from there: the screen asks
	 * for it on every frame it draws, and a file is not a thing to read sixty times a second.
	 */
	private static CollectionTheme theme;

	private WandererConfig()
	{
	}

	/** The theme the collection is drawn in, as the file last said or as the mod defaults to. */
	public static synchronized CollectionTheme theme()
	{
		if (theme == null)
		{
			theme = read();
		}

		return theme;
	}

	/**
	 * Turns the collection to {@code theme} and writes the choice down, so the next run of the game
	 * opens on it. A write that cannot happen is reported and not thrown: the theme the player asked
	 * for is the one the screen is drawn in from this frame on, whatever the disk makes of it.
	 */
	public static synchronized void setTheme(CollectionTheme theme)
	{
		WandererConfig.theme = theme;
		write(theme);
	}

	/** Where the file is, under the config folder of the game. */
	private static Path path()
	{
		return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
	}

	/**
	 * The theme written down in the file, or the mod's own default while it is missing or says
	 * something this mod does not know: the file is read as a suggestion and never as the truth.
	 */
	private static CollectionTheme read()
	{
		Path path = path();

		if (!Files.isRegularFile(path))
		{
			return CollectionTheme.DARK;
		}

		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8))
		{
			JsonObject root = GSON.fromJson(reader, JsonObject.class);

			if (root == null || !root.has(THEME))
			{
				return CollectionTheme.DARK;
			}

			String id = root.get(THEME).getAsString();
			CollectionTheme theme = CollectionTheme.of(id);

			if (!theme.id().equals(id))
			{
				WandererTitles.LOGGER.warn("{} is no theme of this mod, drawing the collection as {}",
						id, theme.id());
			}

			return theme;
		}
		catch (IOException | RuntimeException failure)
		{
			WandererTitles.LOGGER.warn("could not read {}: {}", FILE_NAME, failure.toString());
			return CollectionTheme.DARK;
		}
	}

	/** Writes the id of {@code theme} into the file, making the config folder if it is not there yet. */
	private static void write(CollectionTheme theme)
	{
		Path path = path();

		JsonObject root = new JsonObject();
		root.addProperty(THEME, theme.id());

		try
		{
			Path folder = path.getParent();

			if (folder != null)
			{
				Files.createDirectories(folder);
			}

			Files.writeString(path, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);
		}
		catch (IOException failure)
		{
			WandererTitles.LOGGER.warn("could not write {}: {}", FILE_NAME, failure.toString());
		}
	}
}
