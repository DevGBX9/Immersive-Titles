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

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.biome.Biome;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Every biome of the game, in the order the collection screen puts them in.
 *
 * The list itself comes from the game: the biomes a client knows are the ones its world has
 * registered, so a biome added by a mod or a datapack is listed like a vanilla one, and what the
 * player has to discover is exactly what that world can hand out. Only the order is the mod's own
 * (see {@link #DISCOVERY_ORDER}).
 *
 * A biome the mod has never heard of - anything from a mod, or a vanilla biome added after this
 * list was written - is not hidden for that: it lands behind the listed ones, sorted by name, so a
 * new biome shows up in the collection by itself. That is the whole point of keeping this class
 * apart from the screen that draws it: the day a Minecraft version adds a biome, the collection is
 * one name short in its order, not broken.
 */
final class BiomeCatalog
{
	/**
	 * The vanilla biomes from the ones a player walks into on the way out of a spawn to the ones
	 * that ask for a portal, a very long trip or a lucky world. The game has no opinion on how rare
	 * a biome is - nothing in it says that an ice spikes biome is rarer than a plains one - so this
	 * order is written by hand, roughly by how much of the world each biome covers and how hard it
	 * is to reach:
	 *
	 * - what a player meets while simply walking around: the open biomes of the surface,
	 * - the common variants of those, including the shallow and deep oceans,
	 * - the biomes that need a specific part of the world to walk into,
	 * - what a careful look for it is needed for: the rare surface biomes and the cave biomes,
	 * - the other dimensions, which need a portal to get to at all,
	 * - and the one biome no world generates.
	 *
	 * The order is followed exactly as written, so moving a line is the way to correct the mod's
	 * reading of a biome. A biome of a version this list predates is simply not in it, and is shown
	 * after everything else (see {@link #rank}).
	 */
	private static final List<String> DISCOVERY_ORDER = List.of(
			// the open surface: met within minutes of a fresh spawn
			"minecraft:plains",
			"minecraft:forest",
			"minecraft:birch_forest",
			"minecraft:taiga",
			"minecraft:snowy_plains",
			"minecraft:desert",
			"minecraft:savanna",
			"minecraft:ocean",
			"minecraft:river",
			"minecraft:beach",
			// the common variants of those, and the oceans around them
			"minecraft:sunflower_plains",
			"minecraft:flower_forest",
			"minecraft:dark_forest",
			"minecraft:snowy_taiga",
			"minecraft:snowy_beach",
			"minecraft:stony_shore",
			"minecraft:frozen_river",
			"minecraft:swamp",
			"minecraft:warm_ocean",
			"minecraft:lukewarm_ocean",
			"minecraft:cold_ocean",
			"minecraft:frozen_ocean",
			"minecraft:deep_ocean",
			"minecraft:deep_lukewarm_ocean",
			"minecraft:deep_cold_ocean",
			"minecraft:deep_frozen_ocean",
			"minecraft:windswept_hills",
			"minecraft:savanna_plateau",
			"minecraft:dappled_forest",
			// further out: these need a specific part of the world to walk into
			"minecraft:jungle",
			"minecraft:sparse_jungle",
			"minecraft:bamboo_jungle",
			"minecraft:windswept_forest",
			"minecraft:windswept_savanna",
			"minecraft:windswept_gravelly_hills",
			"minecraft:old_growth_birch_forest",
			"minecraft:old_growth_pine_taiga",
			"minecraft:old_growth_spruce_taiga",
			"minecraft:badlands",
			"minecraft:wooded_badlands",
			"minecraft:eroded_badlands",
			"minecraft:mangrove_swamp",
			"minecraft:cherry_grove",
			"minecraft:meadow",
			"minecraft:grove",
			"minecraft:snowy_slopes",
			"minecraft:stony_peaks",
			"minecraft:jagged_peaks",
			"minecraft:frozen_peaks",
			// looking for it is the point: the rare surface biomes and the caves
			"minecraft:dripstone_caves",
			"minecraft:lush_caves",
			"minecraft:sulfur_caves",
			"minecraft:deep_dark",
			"minecraft:mushroom_fields",
			"minecraft:ice_spikes",
			"minecraft:pale_garden",
			// behind a portal: the other dimensions
			"minecraft:nether_wastes",
			"minecraft:soul_sand_valley",
			"minecraft:warped_forest",
			"minecraft:crimson_forest",
			"minecraft:basalt_deltas",
			"minecraft:the_end",
			"minecraft:end_highlands",
			"minecraft:end_midlands",
			"minecraft:small_end_islands",
			"minecraft:end_barrens",
			// generated by no world: the barrier of the end, only ever seen through commands
			"minecraft:the_void");

	/**
	 * How the collection is sorted: by the mod's own order first, then - for everything the mod
	 * does not know - by name and id, so a world full of modded biomes still reads alphabetically
	 * instead of in the order the game happens to have registered them in.
	 */
	private static final Comparator<Entry> DISPLAY_ORDER = Comparator
			.comparingInt(Entry::rank)
			.thenComparing(Entry::sortKey)
			.thenComparing(Entry::id);

	private BiomeCatalog()
	{
	}

	/**
	 * One biome of the game, as the collection lists it: the id its reveal is remembered by, its
	 * name as the game translates it, and where it sits in the order.
	 */
	record Entry(String id, Component name, int rank)
	{
		/** The name as plain text, for the alphabetical part of the order. */
		String sortKey()
		{
			return name.getString().toLowerCase(Locale.ROOT);
		}
	}

	/**
	 * Every biome {@code level} has registered, in display order: what the player has to collect in
	 * that world, and the order the collection screen shows it in.
	 */
	static List<Entry> all(ClientLevel level)
	{
		Registry<Biome> biomes = level.registryAccess().lookupOrThrow(Registries.BIOME);
		List<Entry> entries = new ArrayList<>(biomes.keySet().size());

		for (Identifier id : biomes.keySet())
		{
			String key = id.toString();
			entries.add(new Entry(key, BiomeNames.name(key), rank(key)));
		}

		entries.sort(DISPLAY_ORDER);

		return List.copyOf(entries);
	}

	/**
	 * The biomes the mod itself knows, together with the ones in {@code extra} that it does not: the
	 * ones of {@link #DISCOVERY_ORDER}, each named the way the game names a biome, and then whatever
	 * the caller has that is not among them, named the same way and shown in the same order.
	 *
	 * It is what the collection has to list while there is no world to ask - from the mod list, where
	 * no world may be open at all (see {@link ModMenuIntegration}) - and it is deliberately only what
	 * the mod tracks plus what it is handed: the biomes of a world are its registry's, and without a
	 * world there is none to read, so what is shown is a list of what the mod knows rather than a
	 * guess at what a world would hold. The names still come from the game, so every one of them is
	 * translated like any other.
	 *
	 * What {@code extra} is for is the collection of every world at once: the biomes a player has
	 * already found somewhere are handed in here (see {@link DiscoveredBiomes#loadAll}), so a biome
	 * from a mod that is not installed any more, or one this order was written before, is listed with
	 * the rest instead of being missing. That is the point of it: the collection of a player's worlds
	 * is a list of what they have found, and something they have found cannot be the one thing the
	 * list does not have.
	 */
	static List<Entry> known(Set<String> extra)
	{
		List<Entry> entries = new ArrayList<>(DISCOVERY_ORDER.size() + extra.size());
		Set<String> listed = new HashSet<>(DISCOVERY_ORDER);

		for (String id : DISCOVERY_ORDER)
		{
			entries.add(new Entry(id, BiomeNames.name(id), rank(id)));
		}

		// a set is in no order of its own, so what is added here is sorted with everything else at
		// the end of this method rather than in the order it happened to be found in
		for (String id : extra)
		{
			if (listed.add(id))
			{
				entries.add(new Entry(id, BiomeNames.name(id), rank(id)));
			}
		}

		entries.sort(DISPLAY_ORDER);

		return List.copyOf(entries);
	}

	/**
	 * Where {@code id} sits in {@link #DISCOVERY_ORDER}, or one place behind every listed biome when
	 * the mod does not know it, which is where the biomes of mods and of newer versions end up.
	 */
	private static int rank(String id)
	{
		int rank = DISCOVERY_ORDER.indexOf(id);

		return rank < 0 ? DISCOVERY_ORDER.size() : rank;
	}
}
