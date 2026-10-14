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

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The structure of {@link StructureCatalog} the player is inside right now, as the game knows it.
 *
 * A biome is read where the titles stand: the client is sent the biomes of a world like it is sent its
 * blocks, so {@link BiomeNames} answers by looking, and there is nothing in between. A structure is
 * not like that. The structures of a world are worked out on the server while the world is generated,
 * the client is sent the blocks a structure is made of and told nothing about the structure itself,
 * and the game keeps its own knowledge of who is standing in one on the server - the structures of a
 * chunk are worked out there, and it is there that a player entering a fortress is recognized at all
 * (see {@link StructureCatalog}). So this is where the mod asks for the same answer,
 * and this is the one thing about the titles that is read on the other side of a world.
 *
 * What is read is the game's own reading and not a copy of it: {@link StructureCatalog#at} is handed
 * a level and a position, and what it does is what the game's own advancement criterion does with
 * them. What this class adds is when to ask and where to keep the answer.
 *
 * The answer is kept here, in the memory of the game that asked, and that is the whole of how the two
 * sides are put together:
 *
 * - A world the player hosts themselves is one game running both sides - the client in front of them
 *   and the server behind it - so the mod asking the server is the mod asking itself, and the answer
 *   is in the client's hands the moment it is put down. This is the case this class serves, and the one
 *   the owner of the world is the player in: the titles of a hosted world name the structures of it
 *   like they name its biomes.
 * - A world on a server is another machine. The mod is a client mod - nothing of it is loaded on a
 *   dedicated server at all - so there is nothing out there to answer, and nothing this class can do
 *   about it: the level tick below never runs, no structure is ever written down here, and the titles
 *   of such a world name biomes and no structures. A player hosted world that other players have
 *   joined is the same thing from the other side: the host's game knows the structures of everyone in
 *   it, and a client that joined is not the host.
 *
 * So the map below is written on the server side of a game and read on the client side of the same
 * game, which is what makes the client-only mod able to name a structure at all: a title is still
 * drawn by the client, and the one fact it needs is asked of the only side that has it.
 *
 * The structure of a player is dropped as soon as they are not inside it any more, so leaving one and
 * coming back is seen as the two entries they are - a first visit and a visit after it, which is what
 * decides between the big reveal and the corner title (see {@link TitleOverlay}). What is kept is the
 * id of the structure, not the structure: a dimension is not stored either, since the level a player
 * is in is where the answer came from and two dimensions cannot both be under one player.
 */
final class StructurePresence
{
	/**
	 * How many ticks between two readings of every player in a level. The title is a reveal for
	 * entering a structure, so half a second of latency is fine - the same half second the biome check
	 * of {@link TitleOverlay} waits - and it keeps the per tick cost of a game that is asked this of
	 * every player in it irrelevant.
	 */
	private static final int CHECK_INTERVAL_TICKS = 10;

	/** The structure each player in a hosted world is inside, by the id of the player. */
	private static final Map<UUID, String> INSIDE = new HashMap<>();

	/** Ticks left until every player of a level is read again. */
	private static int ticksUntilCheck = 0;

	private StructurePresence()
	{
	}

	/**
	 * Listens to the tick of a level, which is the server ticking its world: a hosted world ticks its
	 * levels in the same game as the client, so this is called there and never in a world on a server
	 * (see the class of this one). It is registered next to the client events of the mod, since a
	 * client is the only side that has this mod at all, and a game that is only a client simply never
	 * ticks a level of its own.
	 */
	static void initialize()
	{
		ServerTickEvents.END_LEVEL_TICK.register(StructurePresence::tick);
	}

	/** Reads every player of {@code level}, every {@link #CHECK_INTERVAL_TICKS} ticks. */
	private static void tick(ServerLevel level)
	{
		if (ticksUntilCheck > 0)
		{
			ticksUntilCheck--;
			return;
		}

		ticksUntilCheck = CHECK_INTERVAL_TICKS;

		for (ServerPlayer player : level.players())
		{
			UUID id = player.getUUID();
			Optional<String> structure = StructureCatalog.at(level, player.blockPosition());

			if (structure.isPresent())
			{
				INSIDE.put(id, structure.get());
			}
			else
			{
				// not inside one any more: the next one they enter is a visit of its own
				INSIDE.remove(id);
			}
		}
	}

	/**
	 * The structure the player of {@code client} is inside, or an empty optional while they are inside
	 * none, while no world has been asked yet, or while the world is one on a server (see the class of
	 * this one).
	 */
	static Optional<String> of(Minecraft client)
	{
		if (client.player == null)
		{
			return Optional.empty();
		}

		return Optional.ofNullable(INSIDE.get(client.player.getUUID()));
	}

	/**
	 * Forgets what every player was last seen inside. It is called when a world is entered, and it is
	 * what keeps a stale answer - the structure a player was standing in the last time this game had
	 * their world loaded, a structure they may have left the world in - from being read as the
	 * structure they are in now: what is left is nothing until the server has read them again, which
	 * is a title not being played rather than the wrong one being played.
	 *
	 * The map is this game's own memory and not the server's: the server holds its players' structures
	 * while it is running and hands them out again a few ticks after this.
	 */
	static void clear()
	{
		INSIDE.clear();
		ticksUntilCheck = 0;
	}
}
