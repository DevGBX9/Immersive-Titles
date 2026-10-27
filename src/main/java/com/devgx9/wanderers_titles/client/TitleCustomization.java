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

import com.devgx9.wanderers_titles.WandererTitles;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/**
 * The title customization of the installation: where the plate, its name and the corner title are
 * drawn, and how large each of them is.
 *
 * It is one setting for the whole game rather than one per world, so it lives in one file beside the
 * other configuration of the client ({@link #file()}). The values are mutable while the customization
 * screen arranges them, and the screen works on a copy (see {@link #copy()}): only Save writes the
 * copy to the file and makes it the live one (see {@link #save()} and {@link #replace}). Closing the
 * screen without Save therefore changes nothing that the game draws.
 *
 * Every value has a range of its own, and every setter clamps into that range (see {@link #setPlateScale}
 * and the sections of the design the values come from). Loading is the one other way a value can
 * arrive, and it reads each field on its own with its default as the fallback, so a file written by
 * hand, or written by an older version, cannot leave the game with a value it cannot draw.
 */
public final class TitleCustomization
{
	/** The schema version written to the file, and the only one read from it. */
	public static final int VERSION = 1;

	static final float PLATE_MIN_SCALE = 0.50F;
	static final float PLATE_MAX_SCALE = 1.75F;
	static final float PLATE_MAX_OFFSET = 0.40F;
	static final float NAME_MIN_SCALE = 0.50F;
	static final float NAME_MAX_SCALE = 1.50F;
	static final float NAME_MAX_OFFSET = 0.50F;
	static final float CORNER_MIN_SCALE = 0.50F;
	static final float CORNER_MAX_SCALE = 3.00F;
	static final float CORNER_MAX_OFFSET = 1.00F;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static TitleCustomization live = new TitleCustomization();

	private float plateOffsetX;
	private float plateOffsetY;
	private float plateScale = 1.0F;
	private float nameOffsetX;
	private float nameOffsetY;
	private float nameScale = 1.0F;
	private float cornerOffsetX;
	private float cornerOffsetY;
	private float cornerScale = 1.0F;

	/**
	 * The customization the game draws with. It is replaced only by a successful Save (see
	 * {@link #replace}) and by the load at client start (see {@link #load}).
	 */
	public static TitleCustomization live()
	{
		return live;
	}

	/**
	 * Makes {@code values} the live customization by copying its fields into the live instance, so a
	 * screen that keeps working on its own copy cannot change the live values by accident.
	 */
	public static void replace(TitleCustomization values)
	{
		if (values == null)
		{
			return;
		}

		live.setPlateOffsetX(values.plateOffsetX());
		live.setPlateOffsetY(values.plateOffsetY());
		live.setPlateScale(values.plateScale());
		live.setNameOffsetX(values.nameOffsetX());
		live.setNameOffsetY(values.nameOffsetY());
		live.setNameScale(values.nameScale());
		live.setCornerOffsetX(values.cornerOffsetX());
		live.setCornerOffsetY(values.cornerOffsetY());
		live.setCornerScale(values.cornerScale());
	}

	/** A fresh customization at today's rendering, which is every offset zero and every scale one. */
	public static TitleCustomization defaults()
	{
		return new TitleCustomization();
	}

	/** The file every save writes and the client start reads: {@code config/immersive_titles.json}. */
	public static Path file()
	{
		return FabricLoader.getInstance().getConfigDir().resolve("immersive_titles.json");
	}

	/**
	 * Reads the file into the live customization, and never writes it back. A missing file means the
	 * defaults, silently, since that is the state of a fresh installation; a file that cannot be read
	 * or parsed means the defaults with one warning in the log, and so does a version this build does
	 * not know. The file itself is not replaced until the player presses Save.
	 */
	public static void load()
	{
		Path file = file();

		if (!Files.isRegularFile(file))
		{
			replace(defaults());
			return;
		}

		try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8))
		{
			JsonElement root = JsonParser.parseReader(reader);
			replace(fromJson(root != null && root.isJsonObject() ? root.getAsJsonObject() : null));
		}
		catch (IOException | RuntimeException exception)
		{
			WandererTitles.LOGGER.warn("Could not read the title customization from {}; using defaults",
					file, exception);
			replace(defaults());
		}
	}

	/**
	 * Reads one object of the file. Each of the three sections is read on its own so a section that is
	 * missing or malformed leaves only its own fields at their defaults, and each field is read with
	 * its own fallback so a missing {@code scale} means one, not zero clamped up to the minimum.
	 */
	private static TitleCustomization fromJson(JsonObject root)
	{
		if (root == null)
		{
			return defaults();
		}

		JsonElement version = root.get("version");

		if (version == null || !version.isJsonPrimitive() || !version.getAsJsonPrimitive().isNumber()
				|| version.getAsDouble() != 1.0D)
		{
			if (version != null)
			{
				WandererTitles.LOGGER.warn("Unsupported title customization version {}; using defaults",
						version);
			}

			return defaults();
		}

		TitleCustomization values = defaults();
		JsonObject plate = object(root, "plate");
		JsonObject name = object(root, "name");
		JsonObject corner = object(root, "corner");

		values.setPlateOffsetX(readFloat(plate, "offsetX", 0.0F, -PLATE_MAX_OFFSET, PLATE_MAX_OFFSET));
		values.setPlateOffsetY(readFloat(plate, "offsetY", 0.0F, -PLATE_MAX_OFFSET, PLATE_MAX_OFFSET));
		values.setPlateScale(readFloat(plate, "scale", 1.0F, PLATE_MIN_SCALE, PLATE_MAX_SCALE));
		values.setNameOffsetX(readFloat(name, "offsetX", 0.0F, -NAME_MAX_OFFSET, NAME_MAX_OFFSET));
		values.setNameOffsetY(readFloat(name, "offsetY", 0.0F, -NAME_MAX_OFFSET, NAME_MAX_OFFSET));
		values.setNameScale(readFloat(name, "scale", 1.0F, NAME_MIN_SCALE, NAME_MAX_SCALE));
		values.setCornerOffsetX(readFloat(corner, "offsetX", 0.0F, -CORNER_MAX_OFFSET, CORNER_MAX_OFFSET));
		values.setCornerOffsetY(readFloat(corner, "offsetY", 0.0F, -CORNER_MAX_OFFSET, CORNER_MAX_OFFSET));
		values.setCornerScale(readFloat(corner, "scale", 1.0F, CORNER_MIN_SCALE, CORNER_MAX_SCALE));
		return values;
	}

	/** One section of the file, or null while it is absent or not an object. */
	private static JsonObject object(JsonObject root, String name)
	{
		JsonElement element = root.get(name);
		return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
	}

	/**
	 * One field of a section: the fallback when it is absent, not a primitive, not a number or not
	 * finite, and otherwise the value clamped into its range.
	 */
	private static float readFloat(JsonObject object, String name, float fallback, float min, float max)
	{
		if (object == null)
		{
			return fallback;
		}

		JsonElement element = object.get(name);

		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber())
		{
			return fallback;
		}

		float value = element.getAsFloat();
		return Float.isFinite(value) ? Math.max(min, Math.min(max, value)) : fallback;
	}

	/**
	 * Writes this customization to the file and returns whether it was written. The file is built
	 * field by field, written to a temporary file beside it and moved into place, so an interrupted
	 * write cannot leave a half-written configuration behind.
	 */
	public boolean save()
	{
		Path target = file();
		Path temporary = target.resolveSibling(target.getFileName().toString() + ".tmp");
		JsonObject root = new JsonObject();

		root.addProperty("version", VERSION);
		root.add("plate", section(plateOffsetX, plateOffsetY, plateScale));
		root.add("name", section(nameOffsetX, nameOffsetY, nameScale));
		root.add("corner", section(cornerOffsetX, cornerOffsetY, cornerScale));

		try
		{
			Files.createDirectories(target.getParent());

			try (BufferedWriter writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8,
					StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
					StandardOpenOption.WRITE))
			{
				GSON.toJson(root, writer);
			}

			try
			{
				Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING,
						StandardCopyOption.ATOMIC_MOVE);
			}
			catch (AtomicMoveNotSupportedException exception)
			{
				Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
			}

			return true;
		}
		catch (IOException exception)
		{
			WandererTitles.LOGGER.warn("Could not save the title customization to {}", target, exception);
			return false;
		}
	}

	/** One section of the file, with its three fields in the order the schema lists them. */
	private static JsonObject section(float offsetX, float offsetY, float scale)
	{
		JsonObject section = new JsonObject();
		section.addProperty("offsetX", offsetX);
		section.addProperty("offsetY", offsetY);
		section.addProperty("scale", scale);
		return section;
	}

	/** A copy of these values, which is what the customization screen arranges before Save. */
	public TitleCustomization copy()
	{
		TitleCustomization values = new TitleCustomization();
		values.plateOffsetX = plateOffsetX;
		values.plateOffsetY = plateOffsetY;
		values.plateScale = plateScale;
		values.nameOffsetX = nameOffsetX;
		values.nameOffsetY = nameOffsetY;
		values.nameScale = nameScale;
		values.cornerOffsetX = cornerOffsetX;
		values.cornerOffsetY = cornerOffsetY;
		values.cornerScale = cornerScale;
		return values;
	}

	/** Puts every field back to the values today's rendering uses. */
	public void reset()
	{
		plateOffsetX = 0.0F;
		plateOffsetY = 0.0F;
		plateScale = 1.0F;
		nameOffsetX = 0.0F;
		nameOffsetY = 0.0F;
		nameScale = 1.0F;
		cornerOffsetX = 0.0F;
		cornerOffsetY = 0.0F;
		cornerScale = 1.0F;
	}

	public float plateOffsetX()
	{
		return plateOffsetX;
	}

	public void setPlateOffsetX(float value)
	{
		plateOffsetX = clamp(value, -PLATE_MAX_OFFSET, PLATE_MAX_OFFSET, plateOffsetX);
	}

	public float plateOffsetY()
	{
		return plateOffsetY;
	}

	public void setPlateOffsetY(float value)
	{
		plateOffsetY = clamp(value, -PLATE_MAX_OFFSET, PLATE_MAX_OFFSET, plateOffsetY);
	}

	public float plateScale()
	{
		return plateScale;
	}

	public void setPlateScale(float value)
	{
		plateScale = clamp(value, PLATE_MIN_SCALE, PLATE_MAX_SCALE, plateScale);
	}

	public float nameOffsetX()
	{
		return nameOffsetX;
	}

	public void setNameOffsetX(float value)
	{
		nameOffsetX = clamp(value, -NAME_MAX_OFFSET, NAME_MAX_OFFSET, nameOffsetX);
	}

	public float nameOffsetY()
	{
		return nameOffsetY;
	}

	public void setNameOffsetY(float value)
	{
		nameOffsetY = clamp(value, -NAME_MAX_OFFSET, NAME_MAX_OFFSET, nameOffsetY);
	}

	public float nameScale()
	{
		return nameScale;
	}

	public void setNameScale(float value)
	{
		nameScale = clamp(value, NAME_MIN_SCALE, NAME_MAX_SCALE, nameScale);
	}

	public float cornerOffsetX()
	{
		return cornerOffsetX;
	}

	public void setCornerOffsetX(float value)
	{
		cornerOffsetX = clamp(value, -CORNER_MAX_OFFSET, CORNER_MAX_OFFSET, cornerOffsetX);
	}

	public float cornerOffsetY()
	{
		return cornerOffsetY;
	}

	public void setCornerOffsetY(float value)
	{
		cornerOffsetY = clamp(value, -CORNER_MAX_OFFSET, CORNER_MAX_OFFSET, cornerOffsetY);
	}

	public float cornerScale()
	{
		return cornerScale;
	}

	public void setCornerScale(float value)
	{
		cornerScale = clamp(value, CORNER_MIN_SCALE, CORNER_MAX_SCALE, cornerScale);
	}

	/**
	 * Clamps a value into its range, and keeps {@code current} instead of a value no number can be
	 * compared with. A non-finite value is what a hand-edited file or a broken scale gesture would
	 * hand over, and neither may leave the title with a scale it cannot draw.
	 */
	private static float clamp(float value, float min, float max, float current)
	{
		if (!Float.isFinite(value))
		{
			return current;
		}

		return Math.max(min, Math.min(max, value));
	}
}
