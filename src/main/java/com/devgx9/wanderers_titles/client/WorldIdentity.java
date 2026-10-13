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
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The world the player is in, as the remembered biome discoveries need it: the world's own id and
 * the file its discoveries belong in.
 *
 * A world the player hosts themselves is kept at home, in the world's own folder next to its
 * {@code level.dat}. The mod writes an id there once - a random UUID, which no other world has had
 * or will have - and keeps the discoveries of that world in the same folder, see
 * {@link DiscoveredBiomes}. Two things follow from that, and both of them are the point:
 *
 * - The id is a value the world carries around with it, and nothing is read from its name. Two
 *   worlds both called {@code New World} cannot end up sharing anything, renaming a world for
 *   whatever reason changes nothing about it, and a world that is copied brings its own discoveries
 *   along with the copy, since they are part of what was copied. Nothing is guessed from a name, so
 *   nothing can be guessed wrong.
 * - The discoveries of a world sit in the world's own folder, so deleting a world takes them with it
 *   and leaves nothing behind. No list of worlds has to be kept, no file can go stale, and a world
 *   that is deleted and made again starts over - the new folder simply has no id and no
 *   discoveries of its own yet.
 *
 * A world on a server is not the client's to write to, and the client is not told anything about one
 * that no other server could also have: the game never sends a world id, and the seed only exists
 * locally. That leaves the address the player joined with as the one thing to tell two servers
 * apart, so the discoveries of a server world are kept in the config folder under that address.
 */
final class WorldIdentity
{
	/** The folder a hosted world keeps its own data in, inside the world's own folder. */
	private static final String WORLD_FOLDER = "immersive_titles";

	/** The folder of the game directory the worlds a player hosts themselves are kept in. */
	private static final String SAVES_FOLDER = "saves";

	/** Below the config directory: where the discoveries of a world on a server are kept. */
	private static final String SERVERS_FOLDER = "immersive_titles/servers";

	/** The id of the world, inside {@link #WORLD_FOLDER} of a hosted world. */
	private static final String ID_FILE = "identity.txt";

	/** The name of the file the discoveries of a world are written to. */
	static final String DISCOVERED_FILE = "discovered.txt";

	/** The file ends every line the same way on every system, so it stays comparable and editable. */
	static final String LINE_SEPARATOR = "\n";

	/** How many characters of a server address are kept in front of its hash. */
	private static final int MAX_ADDRESS_LENGTH = 64;

	/** The world's own id: the UUID of a hosted world, the address of a world on a server. */
	private final String id;

	/** The file the biomes discovered in this world are written to. */
	private final Path file;

	private WorldIdentity(String id, Path file)
	{
		this.id = id;
		this.file = file;
	}

	/** The id of the world, as log lines name it. Never empty, never shared with another world. */
	String id()
	{
		return id;
	}

	/** The file the biomes discovered in this world are written to. */
	Path file()
	{
		return file;
	}

	/**
	 * Every file the discoveries of a world have been written to that is on this client: one per
	 * world the player hosts themselves, in the folder the game keeps its saves in, and one per
	 * address they have joined a server with, under {@link #SERVERS_FOLDER}.
	 *
	 * It is everything the mod has ever written down about what was found, which is what a collection
	 * of every world at once is read from (see {@link DiscoveredBiomes#loadAll}). Nothing here says
	 * which world a file belongs to: a caller is after the union of them, and which world found what
	 * stays in the file of that world, where the id of the world is the name of its folder.
	 *
	 * Only the files that are actually there, and a folder that is not there is not a fault: a client
	 * that has never joined a server has no folder of server worlds to list, and a client that has
	 * never played a world of its own has no saves. A folder that is there and cannot be listed is
	 * worth a line in the log, since that is a client whose collection is quietly short.
	 */
	static List<Path> discoveredFiles()
	{
		List<Path> files = new ArrayList<>();

		Path saves = FabricLoader.getInstance().getGameDir().resolve(SAVES_FOLDER);

		if (Files.isDirectory(saves))
		{
			try (DirectoryStream<Path> worlds = Files.newDirectoryStream(saves))
			{
				for (Path world : worlds)
				{
					Path discovered = world.resolve(WORLD_FOLDER).resolve(DISCOVERED_FILE);

					if (Files.isRegularFile(discovered))
					{
						files.add(discovered);
					}
				}
			}
			catch (IOException | RuntimeException e)
			{
				WandererTitles.LOGGER.warn("Could not list the discoveries of the worlds in {}", saves, e);
			}
		}

		// the discoveries of a world on a server are one file per address, all of them in one folder,
		// and every file in there is one of them: the folder holds nothing else
		Path servers = FabricLoader.getInstance().getConfigDir().resolve(SERVERS_FOLDER);

		if (Files.isDirectory(servers))
		{
			try (DirectoryStream<Path> addresses = Files.newDirectoryStream(servers, "*.txt"))
			{
				for (Path discovered : addresses)
				{
					files.add(discovered);
				}
			}
			catch (IOException | RuntimeException e)
			{
				WandererTitles.LOGGER.warn("Could not list the discoveries of the servers in {}", servers, e);
			}
		}

		return files;
	}

	/**
	 * The world the client is in, or an empty optional while there is no world yet or the data of the
	 * world cannot be reached.
	 */
	static Optional<WorldIdentity> of(Minecraft client)
	{
		Optional<WorldIdentity> hosted = hosted(client);

		return hosted.isPresent() ? hosted : server(client);
	}

	/**
	 * The world this client hosts itself, as the folder the game keeps it in. The id is written into
	 * that folder the first time the world is joined: it is made by the world, it stays in it, and it
	 * is the same one every time that folder is played again.
	 */
	private static Optional<WorldIdentity> hosted(Minecraft client)
	{
		IntegratedServer server = client.getSingleplayerServer();

		if (server == null)
		{
			return Optional.empty();
		}

		Path root = server.getWorldPath(LevelResource.ROOT);

		if (root == null)
		{
			return Optional.empty();
		}

		// the level root the game hands out ends in the "." that LevelResource.ROOT is defined as,
		// so normalising it first is what turns it into the folder of the world itself
		Path folder = root.normalize().resolve(WORLD_FOLDER);

		return identity(folder, server.getWorldData().getLevelName())
				.map(id -> new WorldIdentity(id, folder.resolve(DISCOVERED_FILE)));
	}

	/**
	 * The id of the world in {@code folder}: the one written there before, or a fresh random UUID.
	 *
	 * {@code levelName} is a hint for whoever reads the log, nothing else - the id of a world is the
	 * UUID, never its name - and the id is kept beside the discoveries so that a world carries both
	 * of them wherever its folder is copied to.
	 */
	private static Optional<String> identity(Path folder, String levelName)
	{
		Path identity = folder.resolve(ID_FILE);

		try
		{
			if (Files.isRegularFile(identity))
			{
				String stored = Files.readString(identity, StandardCharsets.UTF_8).trim();

				if (!stored.isEmpty())
				{
					return Optional.of(describe(stored, levelName));
				}
			}

			String id = UUID.randomUUID().toString();
			Files.createDirectories(folder);
			Files.writeString(identity, id + LINE_SEPARATOR, StandardCharsets.UTF_8,
					StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
					StandardOpenOption.WRITE);

			return Optional.of(describe(id, levelName));
		}
		catch (IOException | RuntimeException e)
		{
			WandererTitles.LOGGER.warn("Could not read or write the id of the world in {}", identity, e);
			return Optional.empty();
		}
	}

	/**
	 * The world of a server, told apart by the address the player joined it with. The world behind
	 * that address is not the client's to write to, and the game tells a client nothing about a
	 * server world that no other server world could also have, so the address is as far as an
	 * identity can go here.
	 */
	private static Optional<WorldIdentity> server(Minecraft client)
	{
		ServerData server = client.getCurrentServer();

		if (server == null)
		{
			return Optional.empty();
		}

		String address = server.ip == null ? "" : server.ip.trim();

		if (address.isEmpty())
		{
			return Optional.empty();
		}

		Path folder = FabricLoader.getInstance().getConfigDir().resolve(SERVERS_FOLDER);

		return Optional.of(new WorldIdentity(address, folder.resolve(fileName(address) + ".txt")));
	}

	/** {@code id} with the world's name behind it, as a hint for whoever reads the log. */
	private static String describe(String id, String levelName)
	{
		String name = levelName == null ? "" : levelName.trim();

		return name.isEmpty() ? id : id + " (" + name + ")";
	}

	/**
	 * The file name an address gets: a usable file name with everything a file name cannot contain
	 * replaced, cut to a sane length, and the hash of the untouched address appended, so that two
	 * addresses that differ only in the characters that were replaced, or past the length limit, still
	 * end up in two files.
	 */
	private static String fileName(String address)
	{
		StringBuilder safe = new StringBuilder(Math.min(address.length(), MAX_ADDRESS_LENGTH));

		for (int i = 0; i < address.length() && safe.length() < MAX_ADDRESS_LENGTH; i++)
		{
			char c = address.charAt(i);
			safe.append(isFileNameCharacter(c) ? c : '_');
		}

		return safe + "-" + Integer.toHexString(address.hashCode());
	}

	private static boolean isFileNameCharacter(char c)
	{
		return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9'
				|| c == '.' || c == '_' || c == '-';
	}
}
