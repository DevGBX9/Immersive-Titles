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

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;
import java.util.Optional;

/**
 * The identity of the world the player is in, as the remembered biome discoveries need it: a name
 * that is the same every time the same world is joined again and that no other world shares.
 *
 * A singleplayer world is identified by the name of its save folder, e.g. {@code smp_test}. That
 * name is unique inside {@code saves} and it stays the same when the world's display name is
 * edited, which the display name itself does not. A server is identified by the address the player
 * joined it with.
 *
 * The result is a usable file name: the kind of world is put in front of the name, everything a
 * file name cannot contain becomes an underscore, the name is cut to a sane length, and the hash of
 * the untouched name is appended. That last part keeps two worlds apart whose names differ only in
 * the characters that were replaced or past the length limit.
 *
 * The path the game hands out for a level ends in the {@code "."} that {@code LevelResource.ROOT} is
 * defined as, e.g. {@code saves/New World/.}. The name is read from the normalised path, otherwise
 * every singleplayer world would be called {@code "."} and all of them would share one file.
 */
final class WorldIdentity
{
	/** How many characters of the name are kept in front of the hash. */
	private static final int MAX_NAME_LENGTH = 64;

	private WorldIdentity()
	{
	}

	/**
	 * The key of the world the client is in, or an empty optional while there is no world yet or it
	 * cannot be told apart from another one.
	 */
	static Optional<String> of(Minecraft client)
	{
		IntegratedServer integratedServer = client.getSingleplayerServer();

		if (integratedServer != null)
		{
			Optional<String> name = saveName(integratedServer.getWorldPath(LevelResource.ROOT));

			if (name.isEmpty())
			{
				// the save folder cannot be read, so the name from the world list is the last thing
				// left; renaming a world then starts a new round of discoveries in it
				name = nameOf(integratedServer.getWorldData().getLevelName());
			}

			if (name.isPresent())
			{
				return Optional.of(key("sp_", name.get()));
			}
		}

		ServerData server = client.getCurrentServer();

		if (server != null)
		{
			Optional<String> address = nameOf(server.ip);

			if (address.isPresent())
			{
				return Optional.of(key("mp_", address.get()));
			}
		}

		return Optional.empty();
	}

	/**
	 * The name of the save folder a level root path points at, e.g. {@code New World} for the path
	 * {@code saves/New World} the game builds its root out of. Normalising first removes the trailing
	 * {@code "."} of that path, and a name that is still empty or a dot is refused instead of becoming
	 * a key that every world would share.
	 */
	static Optional<String> saveName(Path levelRoot)
	{
		if (levelRoot == null)
		{
			return Optional.empty();
		}

		Path name = levelRoot.normalize().getFileName();
		return name == null ? Optional.empty() : nameOf(name.toString());
	}

	/** A name that can tell a world from another one: not null, not blank and not a bare dot. */
	private static Optional<String> nameOf(String name)
	{
		if (name == null)
		{
			return Optional.empty();
		}

		String trimmed = name.trim();
		return trimmed.isEmpty() || trimmed.equals(".") ? Optional.empty() : Optional.of(trimmed);
	}

	private static String key(String prefix, String name)
	{
		StringBuilder safe = new StringBuilder(Math.min(name.length(), MAX_NAME_LENGTH));

		for (int i = 0; i < name.length() && safe.length() < MAX_NAME_LENGTH; i++)
		{
			char c = name.charAt(i);
			safe.append(isFileNameCharacter(c) ? c : '_');
		}

		return prefix + safe + '-' + Integer.toHexString(name.hashCode());
	}

	private static boolean isFileNameCharacter(char c)
	{
		return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9'
				|| c == '.' || c == '_' || c == '-';
	}
}
