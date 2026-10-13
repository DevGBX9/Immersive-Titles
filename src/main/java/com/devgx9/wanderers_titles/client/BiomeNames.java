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
 * - {@link #place} is what the automatic titles follow: the same biome, except that a player under
 *   the biome of the column over their head - underground, in the biome the surface above them
 *   wears - has no place of their own there, and gets nothing to be titled for.
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
	 * not the player being under anything; four blocks of terrain over their head is where that
	 * begins.
	 *
	 * What is measured is the game's own {@code MOTION_BLOCKING_NO_LEAVES} heightmap, which the
	 * client is sent like the other heightmaps it can draw, and which leaves out exactly the ground
	 * a player is not under: water, which they swim through, and leaves, which they walk under - so
	 * a swimmer, and a walker under a canopy, are not taken for players in a cave, while the stone
	 * of a shallow cave right behind an entrance already is a roof.
	 */
	private static final int ROOF_DEPTH = 4;

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
	 * while there is nothing of the player's own to name: no world or no player, or a player who is
	 * under the biome that covers the sky over their head.
	 *
	 * Below the surface the game reports the biome of the column the player is under: a player in a
	 * cave is in the biome the ground over them is made of, and someone walking through a cave under
	 * one forest after another would be reported as walking into forests they never see. A biome of
	 * its own is therefore what gets titled down there - the biome the game reports that is *not* the
	 * one over the player's head - which in a world of the game's own are exactly the cave biomes:
	 * a dripstone cave, a lush cave, the deep dark. The surface biome the player is under is not a
	 * biome they entered, so it is neither titled nor written down as discovered, and the titles pick
	 * up again the moment a player is back out in the open.
	 *
	 * Nothing here knows a biome by name, which is what makes this hold for biomes from a mod as
	 * well: a cave biome a mod added is not the biome over the player either, so it is titled like
	 * any other, while a surface biome of a mod is treated like a surface biome of the game's own.
	 */
	static Optional<String> place(Minecraft client)
	{
		Optional<String> here = id(client);

		if (here.isEmpty())
		{
			return Optional.empty();
		}

		// the biome over the player's head is the biome they are under, not a place they are in
		if (overhead(client).filter(here.get()::equals).isPresent())
		{
			return Optional.empty();
		}

		return here;
	}

	/**
	 * The biome of the sky over the player's column, i.e. of the surface directly above the place
	 * they stand in, or an empty optional while there is no such biome to hold against the one the
	 * player is in.
	 *
	 * That is the case while the player is not under ground at all - with less than
	 * {@link #ROOF_DEPTH} blocks of terrain over their head, so on the surface, in the air, in water
	 * they swim through, and under the leaves of a canopy, see there - and it is the case in a world
	 * whose ground a ceiling covers: the nether and a cave world have rock over every place a player
	 * can be, their biomes are stacked by depth instead of sitting under a surface, and the biome a
	 * player is in there is the one they are in and not a column they are under.
	 */
	private static Optional<String> overhead(Minecraft client)
	{
		if (client.level == null || client.player == null)
		{
			return Optional.empty();
		}

		DimensionType dimension = client.level.dimensionType();

		if (!dimension.hasSkyLight() || dimension.hasCeiling())
		{
			return Optional.empty();
		}

		BlockPos pos = client.player.blockPosition();
		int roof = client.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());

		if (roof - pos.getY() < ROOF_DEPTH)
		{
			return Optional.empty();
		}

		// the heightmap gives the first block of air over the ground, i.e. the ground a player
		// standing on the surface of this column would have under their feet
		return id(client.level, new BlockPos(pos.getX(), roof, pos.getZ()));
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
