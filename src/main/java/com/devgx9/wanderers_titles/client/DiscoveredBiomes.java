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
 * One file per world, and where that file lives is what keeps a world's discoveries its own: for a
 * world the player hosts themselves it sits in the world's own folder (see {@link WorldIdentity}),
 * so it is part of the world like its {@code level.dat} - a deleted world takes its discoveries
 * with it, and a copied one carries them along - while the discoveries of a world on a server, whose
 * folder a client cannot write to, are kept in {@code config/immersive_titles/servers} under the
 * address it was joined with. Either way the file holds one biome id per line, and the format is
 * deliberately plain: the file stays readable, a user can delete one line to get a single reveal
 * again, or delete the whole file to start over in that world.
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
	private DiscoveredBiomes()
	{
	}

	/**
	 * The biomes of {@code world} that already had their reveal, or an empty set while there is no
	 * world, nothing was written down in it yet, or the file cannot be read.
	 */
	static Set<String> load(WorldIdentity world)
	{
		return world == null ? new HashSet<>() : read(world.file());
	}

	/**
	 * Every biome that already had its reveal in any world this client knows of, read from all the
	 * files the mod has written down (see {@link WorldIdentity#discoveredFiles}).
	 *
	 * It is the union of what every world found, which is what the collection is built from when it is
	 * opened from the mod list rather than from a world (see {@link BiomeCollectionScreen}): a biome
	 * that had its reveal once, in any world the player has played, is a biome they have seen, and
	 * which world it was found in is no part of the question here - that is what the file of one world
	 * answers, and it is the only thing that can, since a discovery belongs to the world it was made
	 * in.
	 *
	 * Reading is best effort for each file on its own (see {@link #read}), so one file this client
	 * cannot read is one world missing from the list and not an empty list.
	 */
	static Set<String> loadAll()
	{
		Set<String> discovered = new HashSet<>();

		for (Path file : WorldIdentity.discoveredFiles())
		{
			discovered.addAll(read(file));
		}

		return discovered;
	}

	/**
	 * The biomes written down in {@code file}, or an empty set while it is not there or cannot be
	 * read: a mod has no business interrupting play over a file it cannot parse, so a failure here is
	 * a line in the log and the discoveries of that one file stay unknown.
	 */
	private static Set<String> read(Path file)
	{
		Set<String> discovered = new HashSet<>();

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
			WandererTitles.LOGGER.warn("Could not read the biomes discovered in {}", file, e);
		}

		return discovered;
	}

	/**
	 * Takes {@code biomeId} back out of what is written down for {@code world}, so that its reveal
	 * plays again the next time it is entered. The file is rewritten without that line and with every
	 * other line as it was - a comment the player wrote stays, and the line ends are the mod's own,
	 * the same on every system (see {@link WorldIdentity#LINE_SEPARATOR}) - and a world that has
	 * nothing written down about that biome is left alone.
	 *
	 * This is the counterpart of {@link #remember}, and it is best effort in the same way: a failure
	 * is logged and the discovery stays where it is, which is the side to fail on, since the reveal
	 * the player asked to have back is then only still had when they ask for it again.
	 */
	static void forget(WorldIdentity world, String biomeId)
	{
		if (world == null)
		{
			return;
		}

		Path file = world.file();

		if (!Files.isRegularFile(file))
		{
			return;
		}

		try
		{
			StringBuilder kept = new StringBuilder();
			boolean writtenDown = false;

			for (String line : Files.readAllLines(file, StandardCharsets.UTF_8))
			{
				if (line.trim().equals(biomeId))
				{
					writtenDown = true;
					continue;
				}

				kept.append(line).append(WorldIdentity.LINE_SEPARATOR);
			}

			if (writtenDown)
			{
				Files.writeString(file, kept, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
						StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
			}
		}
		catch (IOException | RuntimeException e)
		{
			WandererTitles.LOGGER.warn("Could not take the discovery of {} out of {}", biomeId, file, e);
		}
	}

	/**
	 * Writes down that the biome {@code biomeId} had its reveal in {@code world}, so the next visit
	 * only gets the corner title. Nothing happens while there is no world.
	 */
	static void remember(WorldIdentity world, String biomeId)
	{
		if (world == null)
		{
			return;
		}

		Path file = world.file();

		try
		{
			Files.createDirectories(file.getParent());
			Files.writeString(file, biomeId + WorldIdentity.LINE_SEPARATOR, StandardCharsets.UTF_8,
					StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
		}
		catch (IOException | RuntimeException e)
		{
			WandererTitles.LOGGER.warn("Could not write down the discovery of {} in {} to {}",
					biomeId, world.id(), file, e);
		}
	}
}
