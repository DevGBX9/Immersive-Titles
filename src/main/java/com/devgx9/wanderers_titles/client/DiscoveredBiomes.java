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
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The biomes of a world that already had their big reveal, written down on the client so that
 * leaving a world and joining it again does not make every biome a first discovery once more.
 *
 * There is one file per world in {@code config/wanderers_titles/discovered}, named after the world
 * (see {@link WorldIdentity}), and it holds one biome id per line. The format is deliberately plain:
 * the file stays readable, a user can delete one line to get a single reveal again, or delete the
 * whole folder to start over.
 *
 * A discovery is appended as it happens rather than the whole set being written when the world is
 * left, because a game that crashed or was killed would otherwise lose everything found in that
 * session. A window between two biome checks is all a discovery can be lost to now.
 *
 * Both reading and writing are best effort. A mod has no business interrupting play over a file it
 * cannot parse, so a failure is logged and the titles fall back to remembering the world for as
 * long as the player stays in it.
 */
final class DiscoveredBiomes
{
	/** The folder below the config directory, one file per world inside it. */
	private static final String FOLDER = "wanderers_titles/discovered";

	private static final String SUFFIX = ".txt";

	private DiscoveredBiomes()
	{
	}

	/**
	 * The biomes of the world {@code worldKey} that already had their reveal, or an empty set while
	 * the key is unknown, nothing was written down yet, or the file cannot be read.
	 */
	static Set<String> load(String worldKey)
	{
		Set<String> discovered = new HashSet<>();

		if (worldKey == null)
		{
			return discovered;
		}

		Path file = file(worldKey);

		if (!Files.isRegularFile(file))
		{
			return discovered;
		}

		try
		{
			List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);

			for (String line : lines)
			{
				String biomeId = line.trim();

				// the id of a biome is never empty and never starts with a hash, so the rest of a
				// line is a comment a user wrote into the file
				if (!biomeId.isEmpty() && !biomeId.startsWith("#"))
				{
					discovered.add(biomeId);
				}
			}
		}
		catch (IOException | RuntimeException e)
		{
			WandererTitles.LOGGER.warn("Could not read the biomes discovered in {} from {}",
					worldKey, file, e);
		}

		return discovered;
	}

	/**
	 * Writes down that the biome {@code biomeId} had its reveal in the world {@code worldKey}, so
	 * the next visit only gets the corner title. Nothing happens while the key is unknown.
	 */
	static void remember(String worldKey, String biomeId)
	{
		if (worldKey == null)
		{
			return;
		}

		Path file = file(worldKey);

		try
		{
			Files.createDirectories(file.getParent());
			Files.writeString(file, biomeId + System.lineSeparator(), StandardCharsets.UTF_8,
					StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
		}
		catch (IOException | RuntimeException e)
		{
			WandererTitles.LOGGER.warn("Could not write down the discovery of {} in {} to {}",
					biomeId, worldKey, file, e);
		}
	}

	private static Path file(String worldKey)
	{
		return FabricLoader.getInstance().getConfigDir().resolve(FOLDER).resolve(worldKey + SUFFIX);
	}
}
