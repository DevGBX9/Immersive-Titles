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

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The structures the collection collects: every structure the game puts in a world and can say a
 * player is standing in, in the order the collection shows them in.
 *
 * The list is not the mod's opinion of which structures are worth a title. It is the game's own
 * registry, read as the one thing the titles need from it: the structures a player can be inside. What
 * the game checks when it wants to know that is what this class asks it - {@link
 * StructureManager#getStructureWithPieceAt(BlockPos, HolderSet)} against the blocks the player stands
 * in, which is the same question the game's own {@code minecraft:location} criterion asks for the
 * advancements that name a structure. So the listed structures are known to the game, the game's own
 * reading of them is used rather than a guess at which blocks belong to which structure, and a
 * structure no world generates is not in the list.
 *
 * A first version of this list was the five structures the game itself writes down as one a player has
 * entered - the trial chambers, the stronghold, the fortress, the bastion remnant and the end city,
 * named by the criteria of five vanilla advancements - and the five are all still here. What that rule
 * is not is the whole of what the game knows: an advancement is written for the structures somebody
 * wants to hand a reward out for, and a player standing in a desert pyramid is inside a structure the
 * game knows of exactly as much as one standing in a trial chamber. Reading the registry instead of
 * the advancements is what the game's own locator items do, and it is what makes the list of the game
 * rather than of the five advancements it happened to spell out.
 *
 * The game also groups its own structures, in the tags it keeps of them, and where it does the mod
 * lists the group and not its variants: a village is one thing a player finds, whether it stands on
 * plains, sand, savanna, snow or taiga, and the game's {@code minecraft:village} tag is the game
 * saying so. The same goes for the mineshafts, the ocean ruins, the shipwrecks, the abandoned camps
 * and the ruined portals of the overworld, and it is why the collection has a row named {@code
 * Village} rather than five rows a player would each have to find before their list looked complete.
 * A group is remembered by an id of the mod's own - {@code immersive_titles:village} - because a group
 * of structures is what it names and no structure of the game carries that id; a structure listed on
 * its own is remembered by the game's id for it.
 *
 * Where each of them is filed is the mod's own reading, because the game states no dimension of a
 * structure: a structure of a world is generated in biomes, the biomes of a dimension are the game's
 * own {@code is_overworld}, {@code is_nether} and {@code is_end} tags, and what a structure declares
 * for itself is the biome tag it generates in - so the check below reads the biome tag of every listed
 * structure and holds it against the dimension written down here. Every structure comes out as one of
 * the three dimensions a player travels in, which is why this list has no {@link
 * BiomeCatalog.Dimension#UNPLACED} section, unlike the biomes. The ruined portals are the one family
 * the game leaves in two dimensions - its tag holds the seven of both worlds - and they are the one
 * family the collection splits: the six of the overworld are a row of this list and the one of the
 * nether is a row of its own, so a player meets ruined portals in the nether as the nether's, and each
 * of the two is discovered and forgotten on its own.
 *
 * The list is held to the game by a check kept beside the sources ({@code
 * _inspect/check_structures.py}): it reads the registry of the structure files the game ships, the
 * tags of them and the biome tags they generate in, and fails if the list below is not exactly the
 * game's own structures, grouped the way the game groups them and filed where their biomes put them -
 * so this class cannot quietly go stale when a Minecraft version adds a structure.
 */
final class StructureCatalog
{
	/**
	 * The namespace a group of structures is remembered by. It is written out rather than taken from
	 * {@link com.devgx9.wanderers_titles.WandererTitles#MOD_ID} because it is also the first half of a
	 * key that outlives a session and a build - the id of a group of structures on disk, which this
	 * check and the language file are held to - and the mod's own folder of discoveries is named the
	 * same way (see {@link WorldIdentity}).
	 */
	private static final String MOD_NAMESPACE = "immersive_titles";

	/**
	 * The structures of the game, in the order the collection lists them: a row per structure, and a
	 * row per family of structures the game tags together.
	 *
	 * The order is the order the sections of the collection are shown in ({@link
	 * BiomeCatalog.Dimension}), and inside a section the order a player is likely to meet them in: the
	 * villages and the camps of the surface first, then what they run into while exploring and sailing,
	 * then what is under their feet - the mineshafts, the trial chambers, the deep dark - and last the
	 * stronghold, which a player only finds once they are following an eye of ender. A line moved here
	 * moves that structure inside its section, which is the way to correct the mod's reading of it.
	 */
	private static final List<Listed> LISTED = List.of(
			// the overworld: what stands on the surface, and the villages and camps a player meets
			// while they are still walking around
			family("village", BiomeCatalog.Dimension.OVERWORLD,
					"minecraft:village_plains",
					"minecraft:village_desert",
					"minecraft:village_savanna",
					"minecraft:village_snowy",
					"minecraft:village_taiga"),
			family("abandoned_camp", BiomeCatalog.Dimension.OVERWORLD,
					"minecraft:abandoned_camp_bamboo_jungle",
					"minecraft:abandoned_camp_birch_forest",
					"minecraft:abandoned_camp_cherry_grove",
					"minecraft:abandoned_camp_dappled_forest",
					"minecraft:abandoned_camp_flower_forest",
					"minecraft:abandoned_camp_forest",
					"minecraft:abandoned_camp_meadow",
					"minecraft:abandoned_camp_old_growth_birch_forest",
					"minecraft:abandoned_camp_old_growth_pine_taiga",
					"minecraft:abandoned_camp_old_growth_spruce_taiga",
					"minecraft:abandoned_camp_pale_garden",
					"minecraft:abandoned_camp_savanna",
					"minecraft:abandoned_camp_snowy_taiga",
					"minecraft:abandoned_camp_sparse_jungle",
					"minecraft:abandoned_camp_swamp",
					"minecraft:abandoned_camp_taiga",
					"minecraft:abandoned_camp_windswept_forest",
					"minecraft:abandoned_camp_wooded_badlands"),
			one("minecraft:pillager_outpost", BiomeCatalog.Dimension.OVERWORLD),
			one("minecraft:desert_pyramid", BiomeCatalog.Dimension.OVERWORLD),
			one("minecraft:jungle_pyramid", BiomeCatalog.Dimension.OVERWORLD),
			one("minecraft:swamp_hut", BiomeCatalog.Dimension.OVERWORLD),
			one("minecraft:igloo", BiomeCatalog.Dimension.OVERWORLD),
			one("minecraft:trail_ruins", BiomeCatalog.Dimension.OVERWORLD),
			one("minecraft:mansion", BiomeCatalog.Dimension.OVERWORLD),

			// the overworld: what is met out on the water, and the portals left from before it
			family("ocean_ruin", BiomeCatalog.Dimension.OVERWORLD,
					"minecraft:ocean_ruin_cold",
					"minecraft:ocean_ruin_warm"),
			family("shipwreck", BiomeCatalog.Dimension.OVERWORLD,
					"minecraft:shipwreck",
					"minecraft:shipwreck_beached"),
			one("minecraft:monument", BiomeCatalog.Dimension.OVERWORLD),
			one("minecraft:buried_treasure", BiomeCatalog.Dimension.OVERWORLD),
			family("ruined_portal", BiomeCatalog.Dimension.OVERWORLD,
					"minecraft:ruined_portal",
					"minecraft:ruined_portal_desert",
					"minecraft:ruined_portal_jungle",
					"minecraft:ruined_portal_mountain",
					"minecraft:ruined_portal_ocean",
					"minecraft:ruined_portal_swamp"),

			// the overworld: what is found by digging, in the order it is likely to be dug into
			family("mineshaft", BiomeCatalog.Dimension.OVERWORLD,
					"minecraft:mineshaft",
					"minecraft:mineshaft_mesa"),
			one("minecraft:trial_chambers", BiomeCatalog.Dimension.OVERWORLD),
			one("minecraft:ancient_city", BiomeCatalog.Dimension.OVERWORLD),

			// the overworld: the one structure every player has to look for, and the last they find
			one("minecraft:stronghold", BiomeCatalog.Dimension.OVERWORLD),

			// the nether: the ruins of the portals that were there before a player was
			one("minecraft:ruined_portal_nether", BiomeCatalog.Dimension.NETHER),

			// the nether: what stands out in the open there, then the two a player is led to by
			// following what the nether is made of
			one("minecraft:nether_fossil", BiomeCatalog.Dimension.NETHER),
			one("minecraft:fortress", BiomeCatalog.Dimension.NETHER),
			one("minecraft:bastion_remnant", BiomeCatalog.Dimension.NETHER),

			// the end: the one structure of it, out on the islands over the void
			one("minecraft:end_city", BiomeCatalog.Dimension.END));

	/**
	 * The row of the list each structure of the game belongs to, i.e. the id a discovery is written
	 * down by for every structure a row covers: what {@link #at} answers with is the row and not the
	 * structure that was found, so that a village found on plains and a village found on sand are the
	 * one discovery a player made, and forgetting the row forgets the whole of it.
	 */
	private static final Map<String, String> LISTED_AS = listedAs();

	private StructureCatalog()
	{
	}

	/**
	 * The structures as the collection lists them, each named the way the game names things of a
	 * registry (see {@link StructureNames}) and filed under the dimension written down above.
	 *
	 * It is the same list in every world and for every player, unlike the biomes of a world: what the
	 * game has a structure for is its own registry and not one a world generates (see
	 * {@link BiomeCatalog#all}), so nothing of the world in front of the player is read here and the
	 * list needs none of it.
	 */
	static List<CollectionEntry> all()
	{
		List<CollectionEntry> entries = new ArrayList<>(LISTED.size());

		for (Listed listed : LISTED)
		{
			entries.add(new Entry(listed.id(), StructureNames.name(listed.id()), listed.dimension()));
		}

		return List.copyOf(entries);
	}

	/**
	 * The structure of this list that {@code pos} is inside, or an empty optional while the position is
	 * in none of them. What is handed back is the id of the row the structure belongs to and not the id
	 * of the structure itself: a village on plains is the village of the collection (see
	 * {@link #LISTED_AS}).
	 *
	 * It is the game's own question asked the game's own way: the structures of the chunk are looked
	 * up, the ones of this list among them are kept, and the answer is the first of those that has a
	 * piece standing where {@code pos} is - which is exactly what the game checks for its own location
	 * criterion (see the class of this one), so a player is inside a structure here at the same moment
	 * the game would say they are. What it costs is one lookup of a chunk's list of structures per
	 * call, and the game itself asks this of every player every tick for its own advancements.
	 *
	 * It takes a server level and not a client one because this is a question only a server can
	 * answer: the structures of a world are generated there, and the client is sent the blocks a
	 * structure is made of and nothing about the structure itself (see {@link StructurePresence}, which
	 * is what calls this and how the answer reaches the titles).
	 *
	 * A listed structure that the world has no registry entry for - a version of the game that dropped
	 * one, or a world whose datapack removed it - is simply not among the ones looked for, and the
	 * position cannot be inside it.
	 */
	static Optional<String> at(ServerLevel level, BlockPos pos)
	{
		Registry<Structure> structures = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		List<Holder<Structure>> listed = new ArrayList<>();

		for (String id : LISTED_AS.keySet())
		{
			ResourceKey<Structure> key = ResourceKey.create(Registries.STRUCTURE, Identifier.parse(id));
			structures.get(key).ifPresent(listed::add);
		}

		if (listed.isEmpty())
		{
			return Optional.empty();
		}

		StructureStart start = level.structureManager().getStructureWithPieceAt(pos, HolderSet.direct(listed));

		if (!start.isValid())
		{
			return Optional.empty();
		}

		return structures.getResourceKey(start.getStructure())
				.map(key -> LISTED_AS.get(key.identifier().toString()));
	}

	/**
	 * One structure of the game, listed on its own: the row is the structure, and the structure is the
	 * row, so the id it is remembered by is the game's own id for it.
	 */
	private static Listed one(String id, BiomeCatalog.Dimension dimension)
	{
		return new Listed(id, dimension, List.of(id));
	}

	/**
	 * A family of structures the game tags together, listed as the one thing a player finds: the row is
	 * remembered by an id of the mod's own (see {@link #MOD_NAMESPACE}), and what it covers is the
	 * members of the game's tag of that name, written out here so that a structure added to the tag by
	 * a later version of the game is a line this check fails on rather than a structure the titles
	 * silently never name.
	 */
	private static Listed family(String name, BiomeCatalog.Dimension dimension, String... structures)
	{
		return new Listed(MOD_NAMESPACE + ":" + name, dimension, List.of(structures));
	}

	/** The rows of {@link #LISTED} turned into the row each structure of the game belongs to. */
	private static Map<String, String> listedAs()
	{
		Map<String, String> rows = new HashMap<>();

		for (Listed listed : LISTED)
		{
			for (String structure : listed.structures())
			{
				rows.put(structure, listed.id());
			}
		}

		return Map.copyOf(rows);
	}

	/**
	 * One row of the list: the id it is remembered by, the dimension it is filed under, and the
	 * structures of the game it covers - one for a structure listed on its own, and the members of a
	 * tag of the game for a family.
	 */
	private record Listed(String id, BiomeCatalog.Dimension dimension, List<String> structures)
	{
	}

	/** One structure of the list, named and filed the way {@link CollectionEntry} is read. */
	private record Entry(String id, Component name, BiomeCatalog.Dimension dimension)
			implements CollectionEntry
	{
	}
}
