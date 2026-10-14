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

import net.minecraft.network.chat.Component;

/**
 * One thing the collection lists: the id its reveal is remembered by, the name it is read by, and the
 * dimension it is listed under.
 *
 * The collection is a list of two kinds of things - the biomes of a world (see {@link BiomeCatalog})
 * and the structures the game itself recognizes a player entering (see {@link StructureCatalog}) -
 * and the two are the same thing to everything that draws, counts, filters and forgets them: a row of
 * the list, a check beside it, a name in it and a line of a section over it. What a page of the
 * collection is made of is therefore a list of these, whichever catalog built it, and
 * {@link BiomeCollectionScreen} reads no further than this.
 *
 * What is not in here is how a catalog orders its list: the two kinds are sorted by the mod's own
 * order and then by name (see {@link BiomeCatalog#DISPLAY_ORDER}), which is a decision of the catalog
 * that hands the list over rather than one the screen makes.
 *
 * The dimension is the one the sections of the list are - {@link BiomeCatalog.Dimension} - and not a
 * dimension of the game: what the collection divides its list by is that enum, and a structure is
 * filed under it exactly like a biome (see {@link StructureCatalog}), since a list of what a player
 * has found is read the same way whatever kind of thing it is made of.
 */
interface CollectionEntry
{
	/** The id this entry is remembered by, e.g. {@code minecraft:plains}. */
	String id();

	/** The name this entry is read by, as the game or the mod names it, translated. */
	Component name();

	/** The dimension of the collection this entry is listed under. */
	BiomeCatalog.Dimension dimension();
}
