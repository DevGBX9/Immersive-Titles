/*
 * This file is part of the Wanderer's Titles project, licensed under the
 * GNU Lesser General Public License v3.0
 *
 * Copyright (C) 2026  DevGBX9 and contributors
 *
 * Wanderer's Titles is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Wanderer's Titles is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Wanderer's Titles.  If not, see <https://www.gnu.org/licenses/>.
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
 * The name comes from the same translation key the game uses for biomes ({@code biome.<namespace>.<path>}),
 * uppercased because the title card is set in capitals. A biome without a translation, e.g. one added
 * by a mod that forgot it, falls back to its id with underscores turned into spaces, so the reveal
 * never shows a raw key.
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

	private static Component displayName(String id)
	{
		int separator = id.indexOf(':');
		String namespace = separator < 0 ? "minecraft" : id.substring(0, separator);
		String path = separator < 0 ? id : id.substring(separator + 1);

		String key = BIOME_TRANSLATION_PREFIX + namespace + "." + path;
		String translated = Component.translatable(key).getString();
		String name = translated.equals(key) || translated.isEmpty() ? path.replace('_', ' ') : translated;

		return Component.literal(name.toUpperCase(Locale.ROOT)).withStyle(ChatFormatting.BOLD);
	}
}
