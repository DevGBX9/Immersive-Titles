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
import net.minecraft.world.phys.Vec3;

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
 *
 * The same reading answers a second question about a player, which is asked of the server for the same
 * reason and kept apart from the answer above: the structure they are looking at while standing outside
 * it (see {@link #lookedAt}). Being inside a structure and standing outside one and looking at it are
 * two different visits of it, and the second of them is a reveal a player can have without ever walking
 * in - so what is watched here is whether the player's line of sight has reached a structure, for how
 * long it stays on it, and where the player is standing while it does (see {@link #gazing}).
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

	/**
	 * How many ticks between two readings of what every player is looking at. It is half of
	 * {@link #CHECK_INTERVAL_TICKS} rather than the same interval because of what the two readings are
	 * for: being inside a structure is a state, while a look is a length of time that has to be
	 * measured, and a quarter of a second is as coarse as a second and a half (see {@link #GAZE_TICKS})
	 * can be counted without the title arriving noticeably late. What a reading of a look costs is the
	 * laser it reads and nothing else (see {@link #GAZE_DISTANCE} and {@link #lookedAt}).
	 */
	private static final int GAZE_INTERVAL_TICKS = 5;

	/**
	 * How long a player has to keep looking at one structure before it is handed to the client as a
	 * reveal, in ticks: a second and a half. The count is kept in ticks rather than in readings, so the
	 * look is the same second and a half whatever {@link #GAZE_INTERVAL_TICKS} is set to, and it is the
	 * look that has to last: a laser that leaves the structure resets it (see {@link #gazing}).
	 */
	private static final int GAZE_TICKS = 30;

	/**
	 * How far the laser reaches from the player's eyes, in blocks: the structure a player looks at has to
	 * be within six blocks of them, which is what keeps this a look at something in front of the player
	 * rather than at a structure on the horizon. It is also the whole of the cost of one reading of a
	 * look, since it is the length of the line that is read (see {@link #lookedAt}).
	 */
	private static final double GAZE_DISTANCE = 6.0D;

	/** The structure each player in a hosted world is inside, by the id of the player. */
	private static final Map<UUID, String> INSIDE = new HashMap<>();

	/**
	 * The structure each player is looking at right now, with how long they have been looking at it:
	 * emptied of a player the moment their laser leaves a structure, since a look is one look and a player
	 * who looks away and back has started a look of its own (see {@link Gaze}).
	 */
	private static final Map<UUID, Gaze> LOOKING = new HashMap<>();

	/**
	 * The structure each player has looked at long enough to be given a reveal, by the id of the
	 * player, waiting to be read by the client side of the game (see {@link #takeLookedAt}).
	 */
	private static final Map<UUID, String> LOOKED_AT = new HashMap<>();

	/** Ticks left until every player of a level is read again. */
	private static int ticksUntilCheck = 0;

	/** Ticks left until what every player of a level is looking at is read again. */
	private static int ticksUntilGaze = 0;

	/**
	 * One player's look: the structure the laser of the player is on, how many ticks it has been on it, and
	 * whether the reveal of it has already been handed over for this look. The last of the three is
	 * what keeps a player who stands there staring at a structure - which they are told to do - from
	 * being handed the same reveal again at every reading of it (see {@link #gazing}).
	 */
	private record Gaze(String structure, int ticks, boolean told)
	{
	}

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

	/**
	 * Reads every player of {@code level}: the structure they are inside, every
	 * {@link #CHECK_INTERVAL_TICKS} ticks, and the structure they are looking at, every
	 * {@link #GAZE_INTERVAL_TICKS} ticks. The two are counted apart, and each of them is read for every
	 * player of the level only when its own turn comes, so one reading of a look costs one short ray
	 * per player and one reading of a place costs one lookup (see {@link #inside} and {@link #gazing}).
	 */
	private static void tick(ServerLevel level)
	{
		if (ticksUntilCheck > 0)
		{
			ticksUntilCheck--;
		}
		else
		{
			ticksUntilCheck = CHECK_INTERVAL_TICKS;
			inside(level);
		}

		if (ticksUntilGaze > 0)
		{
			ticksUntilGaze--;
		}
		else
		{
			ticksUntilGaze = GAZE_INTERVAL_TICKS;
			gazing(level);
		}
	}

	/** Reads the structure the own block of every player of {@code level} is inside, if any. */
	private static void inside(ServerLevel level)
	{
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
	 * Counts, for every player of {@code level}, how long the laser at {@link #lookedAt} has been on the
	 * same structure, and hands over the reveal of it once that look has lasted {@link #GAZE_TICKS}
	 * ticks.
	 *
	 * A look is one look (see {@link #LOOKING}): the count is dropped the moment the laser leaves the
	 * structure it was on, whether for the world short of it, for another structure, or for a structure
	 * the player is standing inside (see {@link #lookedAt}), so looking away and back is the start of a
	 * fresh second and a half rather than the rest of an old one.
	 *
	 * The structure is handed over once per look and not on every reading after it: what a player who
	 * has been told to keep looking at a structure would otherwise get is the same reveal again every
	 * quarter of a second, which is why the tick a look has reached is remembered along with it. Whether
	 * the reveal is played at all is not decided here - a structure that already had one is a visit after
	 * a visit, and what the world knows of its structures is known on the client (see {@link TitleOverlay},
	 * which reads what is put down here and nothing else does).
	 */
	private static void gazing(ServerLevel level)
	{
		for (ServerPlayer player : level.players())
		{
			UUID id = player.getUUID();
			String structure = lookedAt(level, player);

			if (structure == null)
			{
				LOOKING.remove(id);
				continue;
			}

			Gaze look = LOOKING.get(id);

			if (look == null || !look.structure().equals(structure))
			{
				// the laser met nothing, or met something else: this look starts here
				LOOKING.put(id, new Gaze(structure, 0, false));
				continue;
			}

			int ticks = look.ticks() + GAZE_INTERVAL_TICKS;
			boolean told = look.told() || ticks >= GAZE_TICKS;

			if (told && !look.told())
			{
				LOOKED_AT.put(id, structure);
			}

			LOOKING.put(id, new Gaze(structure, ticks, told));
		}
	}

	/**
	 * The structure of {@link StructureCatalog} the player of {@code player} is looking at, or null
	 * while they are looking at none.
	 *
	 * What is read is an invisible laser rather than the block under the crosshair: a line from the
	 * player's eyes along the way they are facing, no longer than {@link #GAZE_DISTANCE} blocks, asked
	 * whether it has entered the region of a structure at all (see {@link StructureCatalog#along}, which
	 * reads the same answer the game's own location criterion is read out of). The line is stopped by
	 * nothing - not by a wall of the structure, not by the ground, not by a tree standing in front of it
	 * - so a player looking at a structure is looking at it whichever block of the world their eye
	 * happens to land on, and the block that is under the crosshair does not have to be one of the
	 * structure's own for the structure to be read here.
	 *
	 * A structure the player is inside does not count, whatever the laser meets first: it is the structure
	 * they are standing in, its title is the one they get for being in it, and the look here is the other
	 * way of arriving at one - standing outside it and reading it from where they are.
	 */
	private static String lookedAt(ServerLevel level, ServerPlayer player)
	{
		Vec3 eye = player.getEyePosition();
		Vec3 reach = eye.add(player.getViewVector(1.0F).scale(GAZE_DISTANCE));
		String structure = StructureCatalog.along(level, eye, reach).orElse(null);

		if (structure == null || structure.equals(INSIDE.get(player.getUUID())))
		{
			return null;
		}

		return structure;
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
	 * The structure the player of {@code client} has just finished looking at for long enough to be
	 * given its reveal, or an empty optional while they have looked at none: it is taken out of
	 * {@link #LOOKED_AT} by this call, so the same look is handed to the client once and once only.
	 *
	 * What is handed over is the id of the structure and nothing about what to do with it: a look at a
	 * structure says nothing about whether it already had its reveal, and that is a question about the
	 * world the player is in, which the client keeps (see {@link TitleOverlay}, which is the one reader
	 * of this).
	 */
	static Optional<String> takeLookedAt(Minecraft client)
	{
		if (client.player == null)
		{
			return Optional.empty();
		}

		return Optional.ofNullable(LOOKED_AT.remove(client.player.getUUID()));
	}

	/**
	 * Forgets what every player was last seen inside. It is called when a world is entered, and it is
	 * what keeps a stale answer - the structure a player was standing in the last time this game had
	 * their world loaded, a structure they may have left the world in - from being read as the
	 * structure they are in now: what is left is nothing until the server has read them again, which
	 * is a title not being played rather than the wrong one being played.
	 *
	 * The maps are this game's own memory and not the server's: the server holds its players' structures
	 * while it is running and hands them out again a few ticks after this.
	 *
	 * What every player was looking at goes with them, and for the same reason: a look that was under
	 * way in another world is not a look at this one, and a reveal that is already waiting to be read is
	 * about a structure of the world that was left.
	 */
	static void clear()
	{
		INSIDE.clear();
		LOOKING.clear();
		LOOKED_AT.clear();
		ticksUntilCheck = 0;
		ticksUntilGaze = 0;
	}
}
