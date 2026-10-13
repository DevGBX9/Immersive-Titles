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

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Locale;
import java.util.Optional;

/**
 * The biome the player is standing in, as the titles need it: an id to notice that it changed, and a
 * display name to write into the plate.
 *
 * The name comes from the same translation key the game uses for biomes ({@code biome.<namespace>.<path>}).
 * A biome without a translation, e.g. one added by a mod that forgot it, falls back to its id with
 * underscores turned into spaces, so neither a reveal nor the collection ever shows a raw key. The
 * title card is set in capitals and asks for that form ({@link #displayName}); the collection lists
 * biomes the way every other screen names them ({@link #name}).
 *
 * There are two reads of the player in here, and which one a caller wants is the difference between
 * naming a biome and noticing a place:
 *
 * - {@link #id} is the biome the game reports at the position the player stands in, whatever that
 *   is. The keys that play a title on demand ask for this one, since they name where the player is
 *   and nothing else.
 * - {@link #place} is what the automatic titles follow: the same biome, except while the game is
 *   reporting ground for the player instead of a place they are in - under the biome over their
 *   head, and in any biome the surface of the ground around them wears, see there.
 */
final class BiomeNames
{
	private static final String BIOME_TRANSLATION_PREFIX = "biome.";

	/**
	 * How far the ground over the player's head has to rise before they count as being under it, in
	 * blocks. Below the surface the game reports the biome of the column a player is under instead
	 * of a biome they are in, so the titles have to tell "standing on the surface of this biome"
	 * from "in the rock under it" apart, and the height of the ground over the player is what they
	 * have to go by. A dip in the terrain, the mouth of a tunnel and the ground floor of a house are
	 * not the player being under anything; four blocks of ground over their head is where that
	 * begins.
	 *
	 * What is measured is the game's own {@code MOTION_BLOCKING_NO_LEAVES} heightmap, which the
	 * client is sent like the other heightmaps it can draw, and which leaves the leaves out of its
	 * ground: walking under a canopy is not being under anything, while the stone of a shallow cave
	 * right behind an entrance already is a roof.
	 *
	 * Water does count as ground here, since that is what the game measures in that heightmap: a
	 * player more than {@link #ROOF_DEPTH} blocks under the surface of a stretch of water is under
	 * it like a player under the rock of a cave. That is what makes an underground lake behave like
	 * the stone around it - no surface biome is handed out to someone swimming in one - and it costs
	 * a player diving that deep in the open sea the title of what they swim into until they are back
	 * up, which is the one case this reading is wrong about.
	 */
	private static final int ROOF_DEPTH = 4;

	/**
	 * How far from the player the surface of the ground is read for the biomes it wears, in blocks.
	 *
	 * The ground over the player's own column says which biome they are standing on the surface of,
	 * but it is not the whole of it: the game reads the biome of a position from the climate there,
	 * and the climate of a cave is the climate of its depth. The region one surface biome reaches
	 * across the ground down there does not end where the same biome ends up on the surface, so the
	 * ground over a player can wear one biome while the game reports the one that reaches under it,
	 * and the biome the ground around them wears is what says whether that is a place of the surface
	 * or a place of the underground.
	 *
	 * It is a reach and not a boundary on purpose: at thirty-two blocks the ground read covers what a
	 * player can walk out to from where they stand in a few seconds, and the reads are still a
	 * handful of them twice a second.
	 */
	private static final int SURFACE_REACH = 32;

	/** The columns of that read: the eight around the player's own, at {@link #SURFACE_REACH}. */
	private static final int[][] SURFACE_OFFSETS = {
			{-1, -1}, {-1, 0}, {-1, 1}, {0, -1}, {0, 1}, {1, -1}, {1, 0}, {1, 1}};

	private BiomeNames()
	{
	}

	/**
	 * The id of the biome the player is in, e.g. {@code minecraft:pale_garden}, or an empty optional
	 * while there is no world or no player yet.
	 *
	 * This is the biome of the position the player stands in, which is the one the game reports for
	 * them, and it is read as it is: in a cave that is the biome of the column the player is down
	 * under rather than a biome of the cave, which is what {@link #place} is there for.
	 */
	static Optional<String> id(Minecraft client)
	{
		if (client.level == null || client.player == null)
		{
			return Optional.empty();
		}

		return id(client.level, client.player.blockPosition());
	}

	/** The id of the biome at {@code pos} of {@code level}, e.g. {@code minecraft:pale_garden}. */
	private static Optional<String> id(LevelReader level, BlockPos pos)
	{
		Holder<Biome> biome = level.getBiome(pos);
		return biome.unwrapKey().map(key -> key.identifier().toString());
	}

	/**
	 * The biome the automatic titles have to name for the player right now, or an empty optional
	 * while there is nothing of the player's own to name: no world or no player, or a player the
	 * game is reporting ground for instead of a place they are in.
	 *
	 * Standing out in the open, the two are one: the game reports the biome of the column the player
	 * stands in, and the ground of that column wears the biome they are standing on the surface of,
	 * so it is theirs and is titled. Down in a cave the game goes on reporting the ground of the
	 * column the player is under - the biome the climate of their depth belongs to - so a walk
	 * through a cave under one forest after another is not a walk into biomes: the biome over the
	 * player's head is not titled while they are under it, and nothing is written down as discovered
	 * either.
	 *
	 * The ground over the player's own column is not the whole of it, though, because the game reads
	 * the biome of a position from the climate there and the climate of a cave is read at the depth
	 * of the cave: the region a surface biome reaches across the ground at that depth does not end
	 * where the same biome ends up on the surface, and the game can report for a player in a cave a
	 * biome the ground over them does not wear - one whose region reaches under the ground of a
	 * neighbour there. A biome like that is a place up there and not a place down here, which is what
	 * the surface of the ground around the player is read for, see {@link #SURFACE_REACH}: a biome
	 * that ground wears is not titled, while a biome no ground around them wears is one of the
	 * underground and is - in a world of the game's own, a dripstone cave, a lush cave, the deep
	 * dark.
	 *
	 * Nothing here knows a biome by name, which is what makes this hold for biomes from a mod as
	 * well: a cave biome a mod added is not worn by the surface around the player either, so it is
	 * titled like any other, while a surface biome of a mod is worn by it like one of the game's
	 * own.
	 */
	static Optional<String> place(Minecraft client)
	{
		if (client.level == null || client.player == null)
		{
			return Optional.empty();
		}

		BlockPos pos = client.player.blockPosition();
		Optional<String> here = id(client.level, pos);

		if (here.isEmpty())
		{
			return Optional.empty();
		}

		// a world whose ground a ceiling covers has rock over every place a player can be, its
		// biomes stacked by depth instead of sitting under a surface: nothing in it is a player
		// being under ground, and the biome they are in is the place they are in
		if (!hasSurface(client))
		{
			return here;
		}

		BlockPos surface = surfaceOf(client, pos.getX(), pos.getZ());

		if (id(client.level, surface).filter(here.get()::equals).isPresent())
		{
			// the ground over the player wears the biome they are in: it is theirs while they stand
			// on its surface, and it is not while they are under it
			return surface.getY() - pos.getY() < ROOF_DEPTH ? here : Optional.empty();
		}

		// the game is reporting a biome the ground over the player does not wear, which it only does
		// for a player in the rock, and it does so from the climate of their depth: a biome the
		// surface of the ground around them wears is a place up there, not one down here
		return surfaceAroundWears(client, here.get()) ? Optional.empty() : here;
	}

	/**
	 * Whether the world the player is in has a surface to be under at all: ground with a sky over it
	 * that no ceiling covers.
	 *
	 * The nether and a cave world have rock over every place a player can be, and their biomes are
	 * stacked by depth instead of sitting under a surface, so the titles read them as they always
	 * have: the biome a player is in there is the place they are in, and nothing is held against it.
	 */
	private static boolean hasSurface(Minecraft client)
	{
		DimensionType dimension = client.level.dimensionType();
		return dimension.hasSkyLight() && !dimension.hasCeiling();
	}

	/**
	 * The air just over the ground of the column {@code x}/{@code z}, from the game's own
	 * {@code MOTION_BLOCKING_NO_LEAVES} heightmap: the ground a player standing on the surface of
	 * that column would have under their feet, see {@link #ROOF_DEPTH}.
	 */
	private static BlockPos surfaceOf(Minecraft client, int x, int z)
	{
		return new BlockPos(x, client.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
	}

	/**
	 * Whether the surface of the ground around the player wears {@code biomeId}: the ground of the
	 * eight columns {@link #SURFACE_REACH} blocks away from theirs, read with {@link #surfaceOf}.
	 *
	 * That ground is what tells a place of the surface from a place of the underground while the
	 * game reports the same player both, and it is read at the height of the ground and not at the
	 * depth the player is at, since the question is what a player standing out in the open there
	 * would have under their feet.
	 */
	private static boolean surfaceAroundWears(Minecraft client, String biomeId)
	{
		BlockPos pos = client.player.blockPosition();

		for (int[] offset : SURFACE_OFFSETS)
		{
			BlockPos surface = surfaceOf(client, pos.getX() + offset[0] * SURFACE_REACH,
					pos.getZ() + offset[1] * SURFACE_REACH);

			if (id(client.level, surface).filter(biomeId::equals).isPresent())
			{
				return true;
			}
		}

		return false;
	}

	/** The uppercase display name of the biome the player is in, e.g. {@code PALE GARDEN}. */
	static Optional<Component> of(Minecraft client)
	{
		return id(client).map(BiomeNames::displayName);
	}

	/**
	 * The name the game gives a biome, e.g. {@code Pale Garden}, or its id with underscores turned
	 * into spaces while the biome has no translation of its own.
	 */
	static Component name(String id)
	{
		int separator = id.indexOf(':');
		String namespace = separator < 0 ? "minecraft" : id.substring(0, separator);
		String path = separator < 0 ? id : id.substring(separator + 1);

		String key = BIOME_TRANSLATION_PREFIX + namespace + "." + path;
		String translated = Component.translatable(key).getString();
		String name = translated.equals(key) || translated.isEmpty() ? path.replace('_', ' ') : translated;

		return Component.literal(name);
	}

	/** The uppercase display name of a title card, e.g. {@code PALE GARDEN}. */
	private static Component displayName(String id)
	{
		return Component.literal(name(id).getString().toUpperCase(Locale.ROOT)).withStyle(ChatFormatting.BOLD);
	}
}
