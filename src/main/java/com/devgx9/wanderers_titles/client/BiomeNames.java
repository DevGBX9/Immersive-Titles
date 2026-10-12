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
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.biome.Biome;

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
 */
final class BiomeNames
{
	private static final String BIOME_TRANSLATION_PREFIX = "biome.";

	private BiomeNames()
	{
	}

	/**
	 * The id of the biome the player is in, e.g. {@code minecraft:pale_garden}, or an empty optional
	 * while there is no world or no player yet.
	 */
	static Optional<String> id(Minecraft client)
	{
		if (client.level == null || client.player == null)
		{
			return Optional.empty();
		}

		Holder<Biome> biome = client.level.getBiome(client.player.blockPosition());
		return biome.unwrapKey().map(key -> key.identifier().toString());
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
