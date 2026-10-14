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
 * The structures of a world that already had their big reveal, written down beside the biomes of it.
 *
 * It is the structure half of what {@link DiscoveredBiomes} does, and the two do share the one file a
 * world keeps its discoveries in (see {@link WorldIdentity}): a discovery is a reveal a world has
 * already handed out, and the file is the list of those, whatever kind of thing each of them is. What
 * tells the two kinds apart in there is {@link WorldIdentity#STRUCTURE_PREFIX}, which a line naming a
 * structure begins with - so an id of one kind is never read as an id of the other, a file written
 * before structures were collected is a file of biomes still, and a player reading the file, or
 * editing it to get a single reveal back, reads one line per reveal either way.
 *
 * So the reading and the writing here are the reading and the writing there, of the lines that carry
 * the marker: one file per world, and where that file lives is what keeps a world's revelations its
 * own (see {@link WorldIdentity}). A structure is appended as it is discovered rather than the whole
 * set being written when the world is left, so a game that crashed keeps everything found in that
 * session, and both reading and writing are best effort: a mod has no business interrupting play over
 * a file it cannot parse, so a failure is logged and the titles fall back to remembering the world for
 * as long as the player stays in it.
 *
 * The two classes are written apart rather than as one with a kind because the two are read apart:
 * the collection lists its two pages one at a time, and what the titles ask on every check is which
 * structures were seen, not which reveals of any kind were. What they would share is a file name and a
 * loop over lines, and what each keeps is what it is the reader of: a set of ids that belong to its
 * own page.
 */
final class DiscoveredStructures
{
	private DiscoveredStructures()
	{
	}

	/**
	 * The structures of {@code world} that already had their reveal, or an empty set while there is no
	 * world, nothing was written down in it yet, or the file cannot be read.
	 */
	static Set<String> load(WorldIdentity world)
	{
		return world == null ? new HashSet<>() : read(world.file());
	}

	/**
	 * Every structure that already had its reveal in any world this client knows of, read from all the
	 * files the mod has written down (see {@link WorldIdentity#discoveredFiles}).
	 *
	 * It is the union of what every world found, which is what the structures page of the collection is
	 * built from when it is opened from the mod list rather than from a world (see
	 * {@link BiomeCollectionScreen}): a structure that had its reveal once, in any world the player has
	 * played, is a structure they have seen. Reading is best effort for each file on its own (see
	 * {@link #read}), so one file this client cannot read is one world missing from the list.
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
	 * The structures written down in {@code file}, i.e. the lines of it that carry
	 * {@link WorldIdentity#STRUCTURE_PREFIX}, or an empty set while the file is not there or cannot be
	 * read: a mod has no business interrupting play over a file it cannot parse, so a failure here is
	 * a line in the log and the structures of that one file stay unknown.
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
				String marked = line.trim();

				// the rest of the line after the marker is the id of the structure, and the same
				// reading of a line a biome is written down with: what is not there names nothing
				if (marked.startsWith(WorldIdentity.STRUCTURE_PREFIX))
				{
					String structureId = marked.substring(WorldIdentity.STRUCTURE_PREFIX.length()).trim();

					if (!structureId.isEmpty() && !structureId.startsWith("#"))
					{
						discovered.add(structureId);
					}
				}
			}
		}
		catch (IOException | RuntimeException e)
		{
			WandererTitles.LOGGER.warn("Could not read the structures discovered in {}", file, e);
		}

		return discovered;
	}

	/**
	 * Takes {@code structureId} back out of what is written down for {@code world}, so that its reveal
	 * plays again the next time it is entered. The file is rewritten with every line but that one as it
	 * was - the biomes and the structures of the world both, and a comment the player wrote stays - and
	 * a world that has nothing written down about that structure is left alone.
	 *
	 * This is the counterpart of {@link #remember}, and it is best effort in the same way: a failure is
	 * logged and the discovery stays where it is, which is the side to fail on, since the reveal the
	 * player asked to have back is then only still had when they ask for it again.
	 */
	static void forget(WorldIdentity world, String structureId)
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
			String line = WorldIdentity.STRUCTURE_PREFIX + structureId;
			StringBuilder kept = new StringBuilder();
			boolean writtenDown = false;

			for (String written : Files.readAllLines(file, StandardCharsets.UTF_8))
			{
				if (written.trim().equals(line))
				{
					writtenDown = true;
					continue;
				}

				kept.append(written).append(WorldIdentity.LINE_SEPARATOR);
			}

			if (writtenDown)
			{
				Files.writeString(file, kept, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
						StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
			}
		}
		catch (IOException | RuntimeException e)
		{
			WandererTitles.LOGGER.warn("Could not take the discovery of {} out of {}", structureId, file, e);
		}
	}

	/**
	 * Writes down that the structure {@code structureId} had its reveal in {@code world}, so the next
	 * visit only gets the corner title. Nothing happens while there is no world.
	 */
	static void remember(WorldIdentity world, String structureId)
	{
		if (world == null)
		{
			return;
		}

		Path file = world.file();

		try
		{
			Files.createDirectories(file.getParent());
			Files.writeString(file, WorldIdentity.STRUCTURE_PREFIX + structureId
							+ WorldIdentity.LINE_SEPARATOR, StandardCharsets.UTF_8,
					StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
		}
		catch (IOException | RuntimeException e)
		{
			WandererTitles.LOGGER.warn("Could not write down the discovery of {} in {} to {}",
					structureId, world.id(), file, e);
		}
	}
}
