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
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Every biome of the game, in the order the collection screen puts them in.
 *
 * The list itself comes from the game: the biomes a client knows are the ones its world has
 * registered, so a biome added by a mod or a datapack is listed like a vanilla one, and what the
 * player has to discover is exactly what that world can hand out. Only the order is the mod's own
 * (see {@link #DISCOVERY_ORDER}), and the dimensions the list is divided into are the game's own
 * again (see {@link Dimension}).
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
	 *
	 * Since the collection divides its biomes by dimension, the order is read inside each dimension
	 * rather than across the whole list: the blocks of this list are the sections of that one - the
	 * overworld first, then the nether's biomes and the end's - and a biome is placed in its section
	 * whether or not it is one of these (see {@link #DISPLAY_ORDER}).
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
	 * The dimensions the collection is divided into: the three a player can build, mine and travel
	 * in, and the one place left for the things that belong to none of them.
	 *
	 * They are the sections of the collection whatever a page of it is made of: the biomes of a world
	 * are filed under them by the world's own tags (see {@link #dimensions}), and the structures the
	 * game recognizes a player entering are filed under them by the mod's own reading of which
	 * dimension each is in (see {@link StructureCatalog}), so the two pages of the collection are read
	 * section by section alike - and the structures, being of the three dimensions a player travels in,
	 * are a list of the three with no {@link #UNPLACED} among them.
	 *
	 * The constants are in the order the collection shows its sections in, which is the order
	 * {@link #DISCOVERY_ORDER} is written in as well: the overworld first, because that is where a
	 * world starts and where by far most of the biomes are, then the two dimensions behind a portal
	 * in the order a player reaches them - the nether, and then the end - and last the biomes that
	 * belong to no dimension of their own.
	 *
	 * A dimension of a biome is not a thing the game states about it. What the game does state, and
	 * what its own dimensions are generated from, is the three biome tags {@code is_overworld},
	 * {@code is_nether} and {@code is_end}; reading those is reading which dimension a biome is in
	 * (see {@link #dimensions}), and a biome of a mod or of a datapack is placed by them as exactly
	 * as a vanilla one is, provided whoever wrote it tagged it like a biome of a dimension.
	 */
	enum Dimension
	{
		/** The world a player spawns in: every biome of the surface and of the caves under it. */
		OVERWORLD,
		/** Behind a portal of obsidian, under the rock over a sea of lava. */
		NETHER,
		/** Behind the portal of a stronghold, out on the islands over the void. */
		END,
		/**
		 * The biomes no dimension has. The game ships one of them - the void, the barrier of the end,
		 * which no world generates and only a command reaches - and a biome of a mod lands here while
		 * nothing says which dimension it is in. It is what is left rather than a dimension of its own:
		 * a biome that is in none of the three tags is one the collection has no dimension to file
		 * under, and listing it anyway is the whole point of the collection.
		 */
		UNPLACED
	}

	/**
	 * The biomes of the nether and of the end as the game ships them: the mod's own reading of which
	 * dimension a biome is in, for the one screen that has no world behind it whose tags it could read
	 * (see {@link #known}), and for the biomes a world's tags place nowhere (see {@link #shipped}).
	 *
	 * These two lists are the one place in this mod that copies a fact out of the game rather than
	 * reading it there, so they are held to the game's own {@code is_nether} and {@code is_end} tags by
	 * a check kept beside the sources - {@code _inspect/check_dimension_groups.py}, which reads those
	 * tags out of the game the build is against. Everything else the game ships is a biome of the
	 * overworld or of no dimension at all (see {@link #THE_VOID}), so the two short lists are the whole
	 * of what the mod has to know.
	 */
	private static final Set<String> NETHER_BIOMES = Set.of(
			"minecraft:nether_wastes",
			"minecraft:soul_sand_valley",
			"minecraft:crimson_forest",
			"minecraft:warped_forest",
			"minecraft:basalt_deltas");

	private static final Set<String> END_BIOMES = Set.of(
			"minecraft:the_end",
			"minecraft:end_highlands",
			"minecraft:end_midlands",
			"minecraft:small_end_islands",
			"minecraft:end_barrens");

	/**
	 * The one biome of the game that belongs to no dimension: the void, the barrier of the end, which
	 * no world generates. It is in the game's own registry, so the collection lists it, and it is in
	 * none of the game's dimension tags, so the collection files it under {@link Dimension#UNPLACED}
	 * like anything else that is nowhere.
	 */
	private static final String THE_VOID = "minecraft:the_void";

	/**
	 * How the collection is sorted: the sections first, in the order of {@link Dimension} - so the list
	 * is the overworld's biomes, then the nether's, then the end's, and then whatever belongs to no
	 * dimension - and inside a section by the mod's own order (see {@link #DISCOVERY_ORDER}), then -
	 * for everything the mod does not know - by name and id, so a world full of modded biomes still
	 * reads alphabetically inside its sections instead of in the order the game happens to have
	 * registered them in.
	 */
	private static final Comparator<Entry> DISPLAY_ORDER = Comparator
			.comparing(Entry::dimension)
			.thenComparingInt(Entry::rank)
			.thenComparing(Entry::sortKey)
			.thenComparing(Entry::id);

	private BiomeCatalog()
	{
	}

	/**
	 * One biome of the game, as the collection lists it: the id its reveal is remembered by, its
	 * name as the game translates it, the dimension it is listed under, and where it sits in the
	 * order of the mod's own list.
	 *
	 * The first three of those are what every entry of the collection is, whichever catalog built it
	 * (see {@link CollectionEntry}), and the collection reads a biome no further than that. The rank is
	 * the biome's own: where the mod's own order puts it, which is what the list of biomes is sorted by
	 * and what nothing else in the mod has any use for (see {@link #DISCOVERY_ORDER}).
	 */
	record Entry(String id, Component name, Dimension dimension, int rank) implements CollectionEntry
	{
		/** The name as plain text, for the alphabetical part of the order. */
		String sortKey()
		{
			return name.getString().toLowerCase(Locale.ROOT);
		}
	}

	/**
	 * Every biome {@code level} has registered, in display order: what the player has to collect in
	 * that world, and the order the collection screen shows it in. Each biome is filed under the
	 * dimension the world's own tags put it in (see {@link #dimensions}).
	 *
	 * What is handed back is a list of the shape the collection reads, not of this class's own record:
	 * a page of the collection is a list of its entries whichever catalog built it (see
	 * {@link CollectionEntry}), and the two pages of it are handed over the same way.
	 */
	static List<CollectionEntry> all(ClientLevel level)
	{
		Registry<Biome> biomes = level.registryAccess().lookupOrThrow(Registries.BIOME);
		Map<String, Dimension> dimensions = dimensions(biomes);
		List<Entry> entries = new ArrayList<>(biomes.keySet().size());

		for (Identifier id : biomes.keySet())
		{
			String key = id.toString();
			entries.add(new Entry(key, BiomeNames.name(key), dimension(dimensions, key), rank(key)));
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
	 * translated like any other, and the dimension of a biome of it is the mod's own reading of the
	 * biomes the game ships rather than a world's tags (see {@link #shipped}) - a modded biome found
	 * in some other world is listed under {@link Dimension#UNPLACED} here, since nothing says where it
	 * was found.
	 *
	 * What {@code extra} is for is the collection of every world at once: the biomes a player has
	 * already found somewhere are handed in here (see {@link DiscoveredBiomes#loadAll}), so a biome
	 * from a mod that is not installed any more, or one this order was written before, is listed with
	 * the rest instead of being missing. That is the point of it: the collection of a player's worlds
	 * is a list of what they have found, and something they have found cannot be the one thing the
	 * list does not have.
	 *
	 * {@code level} is the world that is open behind the screen, or null while none is - the mod list
	 * can be opened from the title screen, and then there is nothing to read a dimension off. When
	 * there is one, its tags are read for it (see {@link #dimensions}), so the biomes a player found
	 * in other worlds are still filed under the dimension they are in, and only what not even a world
	 * places stays unplaced.
	 */
	static List<CollectionEntry> known(Set<String> extra, ClientLevel level)
	{
		Map<String, Dimension> dimensions = level == null
				? Map.of()
				: dimensions(level.registryAccess().lookupOrThrow(Registries.BIOME));
		List<Entry> entries = new ArrayList<>(DISCOVERY_ORDER.size() + extra.size());
		Set<String> listed = new HashSet<>(DISCOVERY_ORDER);

		for (String id : DISCOVERY_ORDER)
		{
			entries.add(new Entry(id, BiomeNames.name(id), dimension(dimensions, id), rank(id)));
		}

		// a set is in no order of its own, so what is added here is sorted with everything else at
		// the end of this method rather than in the order it happened to be found in
		for (String id : extra)
		{
			if (listed.add(id))
			{
				entries.add(new Entry(id, BiomeNames.name(id), dimension(dimensions, id), rank(id)));
			}
		}

		entries.sort(DISPLAY_ORDER);

		return List.copyOf(entries);
	}

	/**
	 * The dimension of every biome that one of the game's own dimension tags holds, read from
	 * {@code biomes}: the three tags the game generates the three dimensions of a world from, so what
	 * this returns is the game's own answer to which dimension a biome is in rather than a reading of
	 * a biome's id.
	 *
	 * It is a map rather than three sets because the question the collection asks of a biome is which
	 * section it belongs under, and the answer for a biome a tag does not hold is not one of the three
	 * dimensions but nothing at all - the void is in none of the tags, and so is a modded biome whose
	 * author tagged it with nothing (see {@link #dimension}).
	 *
	 * A world whose tags are not read at all answers with an empty map here, and one whose tags hold
	 * only part of its biomes answers for the part it holds. Both are the same case to the caller: the
	 * mod's own reading of the biomes the game ships takes over for whatever this map does not place
	 * (see {@link #shipped}). That is what keeps a client that has not been sent the tags of its
	 * server from showing one section of everything, and it is why the tags are read through
	 * {@code getTagOrEmpty}: a tag that a world does not have is an empty tag here, not a fault.
	 */
	private static Map<String, Dimension> dimensions(Registry<Biome> biomes)
	{
		Map<String, Dimension> dimensions = new HashMap<>();

		place(dimensions, biomes, BiomeTags.IS_OVERWORLD, Dimension.OVERWORLD);
		place(dimensions, biomes, BiomeTags.IS_NETHER, Dimension.NETHER);
		place(dimensions, biomes, BiomeTags.IS_END, Dimension.END);

		return dimensions;
	}

	/** Files the biomes of one of those tags under the dimension that tag is about. */
	private static void place(Map<String, Dimension> dimensions, Registry<Biome> biomes,
			TagKey<Biome> tag, Dimension dimension)
	{
		for (Holder<Biome> biome : biomes.getTagOrEmpty(tag))
		{
			biome.unwrapKey().ifPresent(key -> dimensions.put(key.identifier().toString(), dimension));
		}
	}

	/**
	 * Which dimension a biome is in: what the world's own tags say, and what the mod itself reads where
	 * they say nothing.
	 *
	 * The two answer different halves of the question. A tag is the game's own answer and it is the one
	 * the collection shows, because it is what a biome of a mod takes part in: tagging a biome into
	 * {@code is_nether} is how its author says it is generated in the nether, and the collection reads
	 * the same statement the game generates from. Where no tag says anything the mod is left with what
	 * it knows of the game's own biomes (see {@link #shipped}), which places the vanilla ones - so a
	 * world whose tags are not loaded still has every vanilla biome under its dimension - and leaves
	 * the void and anything modded that was never tagged as belonging to no dimension, which is exactly
	 * what they are to a screen that can only read what the world says.
	 */
	private static Dimension dimension(Map<String, Dimension> dimensions, String id)
	{
		Dimension dimension = dimensions.get(id);

		return dimension == null ? shipped(id) : dimension;
	}

	/**
	 * Which dimension the mod itself reads {@code id} into, for a biome that no tag of a world places:
	 * the biomes of the two lists above, everything else of {@link #DISCOVERY_ORDER} for the overworld -
	 * which is every other biome of the game - and {@link Dimension#UNPLACED} for the void and for
	 * everything the mod has never heard of.
	 */
	private static Dimension shipped(String id)
	{
		if (NETHER_BIOMES.contains(id))
		{
			return Dimension.NETHER;
		}

		if (END_BIOMES.contains(id))
		{
			return Dimension.END;
		}

		// everything else the order holds is a biome of the overworld, but the void is not a place:
		// it is the biome the order ends on, the one no world generates
		return DISCOVERY_ORDER.contains(id) && !THE_VOID.equals(id)
				? Dimension.OVERWORLD
				: Dimension.UNPLACED;
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
