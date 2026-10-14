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
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * What the collection and the titles call a structure, in the two readings {@link BiomeNames} gives a
 * biome: the name a line of the list is written with ({@link #name}), and the shorter, louder one a
 * title is set in ({@link #displayName}).
 *
 * The names come from the language files like the names of the biomes do, under the key the game
 * itself names things of a registry by - {@code structure.<namespace>.<path>}, the same shape as the
 * {@code biome.<namespace>.<path>} the game names its biomes with. The difference is that the game
 * leaves the key empty: it ships a name for every biome it has ({@code biome.minecraft.plains} is
 * {@code Plains}) but none for any structure, so the mod writes down the name of every structure it
 * lists itself, in its own language file - and a name for each family of structures it lists, whose
 * key is of the mod's own namespace, since a family is one thing a player finds and is remembered by
 * an id of the mod's rather than by an id of the game's (see {@link StructureCatalog}). The lookup is
 * the game's own all the same, which is what keeps a
 * resource pack, or a version of the game that does name its structures one day, in front of what the
 * mod ships.
 *
 * A structure without a translation - one of a version of the game this mod predates, or a name the
 * mod forgot - falls back to its id with the underscores turned into spaces, exactly as a biome
 * without one does, so no line of the collection and no title ever shows a raw key.
 *
 * The names the mod writes down are written at the length of a title - at most two words, the room a
 * plate has for a name, which is the same rule the names of the biomes are shortened to (see
 * {@link BiomeNames}) - and that is a check kept beside the sources rather than a shortening rule
 * here: they are the mod's own strings, so {@code _inspect/check_structures.py} reads them back and
 * holds them to it, while a name of any length is still scaled into the plate at draw time (see
 * {@link TitleOverlay}).
 */
final class StructureNames
{
	private static final String STRUCTURE_TRANSLATION_PREFIX = "structure.";

	private StructureNames()
	{
	}

	/**
	 * The name the game (or the mod, for the structures the game does not name) gives a structure,
	 * e.g. {@code Nether Fortress}, or its id with underscores turned into spaces while the structure
	 * has no translation of its own.
	 */
	static Component name(String id)
	{
		int separator = id.indexOf(':');
		String namespace = separator < 0 ? "minecraft" : id.substring(0, separator);
		String path = separator < 0 ? id : id.substring(separator + 1);

		String key = STRUCTURE_TRANSLATION_PREFIX + namespace + "." + path;
		String translated = Component.translatable(key).getString();
		String name = translated.equals(key) || translated.isEmpty() ? path.replace('_', ' ') : translated;

		return Component.literal(name);
	}

	/** The uppercase display name of a title card, e.g. {@code NETHER FORTRESS}. */
	static Component displayName(String id)
	{
		return Component.literal(name(id).getString().toUpperCase(Locale.ROOT))
				.withStyle(ChatFormatting.BOLD);
	}
}
