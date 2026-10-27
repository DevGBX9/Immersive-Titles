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

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import static com.devgx9.wanderers_titles.client.TitleOverlay.CONTENT_HEIGHT;
import static com.devgx9.wanderers_titles.client.TitleOverlay.CONTENT_WIDTH;
import static com.devgx9.wanderers_titles.client.TitleOverlay.CORNER_BOTTOM_MARGIN;
import static com.devgx9.wanderers_titles.client.TitleOverlay.CORNER_HUD_BAR_HALF;
import static com.devgx9.wanderers_titles.client.TitleOverlay.CORNER_HUD_GAP;
import static com.devgx9.wanderers_titles.client.TitleOverlay.CORNER_HUD_ROOM;
import static com.devgx9.wanderers_titles.client.TitleOverlay.CORNER_KICKER_SCALE;
import static com.devgx9.wanderers_titles.client.TitleOverlay.CORNER_KICKER_SPACING;
import static com.devgx9.wanderers_titles.client.TitleOverlay.CORNER_LINE_GAP;
import static com.devgx9.wanderers_titles.client.TitleOverlay.CORNER_MARGIN;
import static com.devgx9.wanderers_titles.client.TitleOverlay.CORNER_NAME_SCALE;
import static com.devgx9.wanderers_titles.client.TitleOverlay.FONT_CAP_HEIGHT;
import static com.devgx9.wanderers_titles.client.TitleOverlay.FRAME_CROP;
import static com.devgx9.wanderers_titles.client.TitleOverlay.NAME_CENTER_X;
import static com.devgx9.wanderers_titles.client.TitleOverlay.NAME_CENTER_Y;
import static com.devgx9.wanderers_titles.client.TitleOverlay.NAME_MAX_HEIGHT;
import static com.devgx9.wanderers_titles.client.TitleOverlay.NAME_MAX_WIDTH;
import static com.devgx9.wanderers_titles.client.TitleOverlay.NAME_ROOM_HEIGHT;
import static com.devgx9.wanderers_titles.client.TitleOverlay.NAME_ROOM_WIDTH;

/**
 * Where the two titles are laid out, shared by the overlay that plays them and the screen that
 * arranges them.
 *
 * It owns no state and draws nothing: every method takes the screen size, the customization and the
 * text it is asked about, and returns the rectangle or the scale the drawing needs. The overlay and
 * the preview therefore agree by construction, and the customization screen never re-implements a
 * formula of the game.
 *
 * The default values keep the expressions the overlay used before the customization existed, in the
 * same order and with the same integer rounding (see the plate, name and corner methods), so a
 * default configuration draws exactly what the mod drew before it.
 */
final class TitleGeometry
{
	/** The four pixels the corner title always keeps free at every edge of the screen. */
	static final int CORNER_SCREEN_MARGIN = 4;

	/** The drawn plate: the content rectangle and the scale its frame pixels are drawn at. */
	record Plate(int x, int y, int width, int height, float scale)
	{
	}

	/** A rectangle in gui pixels, with the fractional edges the font's scaled width produces. */
	record Box(float left, float top, float right, float bottom)
	{
	}

	/** The name of the large title: its centre and text scale, its box, and the room it sits in. */
	record Name(float centerX, float centerY, float scale, Box box,
			float roomCenterX, float roomCenterY, float roomWidth, float roomHeight)
	{
	}

	/** The corner title: both lines' origins, both scales, the gap and the box of the whole block. */
	record Corner(int x, int baseline, int kickerX, int kickerY, float nameScale,
			float kickerScale, int kickerSpacing, int lineGap, Box box)
	{
	}

	/** The two lines of a corner title: the small word over it, and the name itself. */
	record Parts(Component kicker, Component name)
	{
	}

	private TitleGeometry()
	{
	}

	/**
	 * The plate at its screen: the content rectangle fitted into the screen, then scaled and moved by
	 * the customization. The halving is integer halving before the offset, as the overlay always did
	 * it, so the default rectangle is the one the mod drew before.
	 */
	static Plate plate(int screenWidth, int screenHeight, TitleCustomization values)
	{
		float fit = Math.min(screenWidth / (float) CONTENT_WIDTH,
				screenHeight / (float) CONTENT_HEIGHT);
		float scale = fit * values.plateScale();
		int width = Math.max(1, Math.round(CONTENT_WIDTH * scale));
		int height = Math.max(1, Math.round(CONTENT_HEIGHT * scale));
		int x = (screenWidth - width) / 2 + Math.round(values.plateOffsetX() * screenWidth);
		int y = (screenHeight - height) / 2 + Math.round(values.plateOffsetY() * screenHeight);
		return new Plate(x, y, width, height, scale);
	}

	/**
	 * The name in the plate's empty room: the automatic fit at the plate's scale, multiplied by the
	 * name's own scale and then hard-capped so the text box can never leave the room. The offset is a
	 * fraction of that room, and the box is clamped inside it on both axes.
	 *
	 * At the defaults the automatic fit is already inside both caps, so the result is the expression
	 * the overlay used before, unchanged.
	 */
	static Name name(Plate plate, Font font, Component text, TitleCustomization values)
	{
		float scale = plate.scale();
		int textWidth = Math.max(1, font.width(text));
		float roomWidth = NAME_ROOM_WIDTH * scale;
		float roomHeight = NAME_ROOM_HEIGHT * scale;
		float auto = Math.min(
				NAME_ROOM_HEIGHT * scale * NAME_MAX_HEIGHT
						/ FONT_CAP_HEIGHT,
				NAME_ROOM_WIDTH * scale * NAME_MAX_WIDTH / textWidth);
		float textScale = Math.min(auto * values.nameScale(),
				Math.min(roomWidth / textWidth, roomHeight / FONT_CAP_HEIGHT));
		float width = textWidth * textScale;
		float height = FONT_CAP_HEIGHT * textScale;
		float roomCenterX = plate.x() + NAME_CENTER_X * scale;
		float roomCenterY = plate.y() + (NAME_CENTER_Y - FRAME_CROP) * scale;
		float centerX = roomCenterX + values.nameOffsetX() * roomWidth;
		float centerY = roomCenterY + values.nameOffsetY() * roomHeight;

		// the box is kept inside the room rather than the centre alone, since a long name has little
		// room to move; where the two limits would cross, the room's own centre is the only answer
		float slackX = (roomWidth - width) / 2.0F;
		float slackY = (roomHeight - height) / 2.0F;
		centerX = slackX < 0.0F ? roomCenterX
				: Math.max(roomCenterX - slackX, Math.min(roomCenterX + slackX, centerX));
		centerY = slackY < 0.0F ? roomCenterY
				: Math.max(roomCenterY - slackY, Math.min(roomCenterY + slackY, centerY));

		Box box = new Box(centerX - width / 2.0F, centerY - height / 2.0F,
				centerX + width / 2.0F, centerY + height / 2.0F);
		return new Name(centerX, centerY, textScale, box, roomCenterX, roomCenterY,
				roomWidth, roomHeight);
	}

	/**
	 * The last word of {@code title} and the words before it, each carrying the style of the whole:
	 * a one-word title has no small word at all.
	 */
	static Parts split(Component title)
	{
		String text = title.getString();
		int lastSpace = text.lastIndexOf(' ');

		if (lastSpace < 0)
		{
			return new Parts(null, title);
		}

		Component kicker = Component.literal(text.substring(0, lastSpace)).withStyle(title.getStyle());
		Component name = Component.literal(text.substring(lastSpace + 1)).withStyle(title.getStyle());
		return new Parts(kicker, name);
	}

	/**
	 * The corner title before the screen-edge clamp: the old corner position plus the customized
	 * offset and scale, with the fit cap applied only once the customization has left its defaults,
	 * and the hotbar fallback still part of the exact-default path.
	 *
	 * The editor measures this block when it writes an offset back to its limit (see
	 * {@link TitleCustomizationScreen#clampStoredOffsets}), which is why it is separate from
	 * {@link #corner}: the clamp must not hide where the block really is.
	 */
	static Corner cornerRaw(int screenWidth, int screenHeight, Font font, Component kicker,
			Component name, TitleCustomization values)
	{
		float m = values.cornerScale();
		boolean exact = values.cornerOffsetX() == 0.0F && values.cornerOffsetY() == 0.0F
				&& values.cornerScale() == 1.0F;
		float nameScale = CORNER_NAME_SCALE * m;
		float kickerScale = CORNER_KICKER_SCALE * m;
		float nameRoom = font.width(name) * nameScale;
		float kickerRoom = kicker == null ? 0.0F
				: (font.width(kicker) + CORNER_KICKER_SPACING
						* Math.max(0, kicker.getString().length() - 1)) * kickerScale;
		float blockWidth = Math.max(nameRoom, kickerRoom);
		float blockHeight = FONT_CAP_HEIGHT * nameScale
				+ CORNER_LINE_GAP * m + FONT_CAP_HEIGHT * kickerScale;

		if (!exact && blockWidth > 0.0F && blockHeight > 0.0F)
		{
			// the block may not be larger than the screen at the size that was asked for; the old
			// exact-default path skips this, which is what keeps today's corner drawing untouched
			float cap = Math.min(1.0F, Math.min((screenWidth - 8) / blockWidth,
					(screenHeight - 8) / blockHeight));
			m *= cap;
			nameScale = CORNER_NAME_SCALE * m;
			kickerScale = CORNER_KICKER_SCALE * m;
			nameRoom = font.width(name) * nameScale;
			kickerRoom = kicker == null ? 0.0F
					: (font.width(kicker) + CORNER_KICKER_SPACING
							* Math.max(0, kicker.getString().length() - 1)) * kickerScale;
			blockWidth = Math.max(nameRoom, kickerRoom);
			blockHeight = FONT_CAP_HEIGHT * nameScale
					+ CORNER_LINE_GAP * m + FONT_CAP_HEIGHT * kickerScale;
		}

		int x = CORNER_MARGIN
				+ Math.round(values.cornerOffsetX() * screenWidth);
		int baseline = screenHeight - CORNER_BOTTOM_MARGIN
				- (int) (FONT_CAP_HEIGHT * nameScale)
				+ Math.round(values.cornerOffsetY() * screenHeight);

		if (exact && CORNER_MARGIN + nameRoom
				> screenWidth / 2.0F - CORNER_HUD_BAR_HALF)
		{
			baseline = screenHeight - CORNER_HUD_ROOM - CORNER_HUD_GAP
					- (int) (font.lineHeight * nameScale);
		}

		int kickerX = x + Math.round((nameRoom - kickerRoom) / 2.0F);
		int kickerY = baseline - (int) (CORNER_LINE_GAP * m)
				- (int) (FONT_CAP_HEIGHT * kickerScale);

		Box nameBox = new Box(x, baseline, x + nameRoom,
				baseline + FONT_CAP_HEIGHT * nameScale);
		Box box = kicker == null ? nameBox
				: union(nameBox, new Box(kickerX, kickerY, kickerX + kickerRoom,
						kickerY + FONT_CAP_HEIGHT * kickerScale));

		return new Corner(x, baseline, kickerX, kickerY, nameScale, kickerScale,
				CORNER_KICKER_SPACING, (int) (CORNER_LINE_GAP * m), box);
	}

	/**
	 * The corner title as it is drawn: the raw block, shifted by the smallest whole-pixel vector that
	 * keeps it inside the four-pixel screen margin once the player has moved or scaled it.
	 */
	static Corner corner(int screenWidth, int screenHeight, Font font, Component kicker,
			Component name, TitleCustomization values)
	{
		Corner raw = cornerRaw(screenWidth, screenHeight, font, kicker, name, values);
		boolean exact = values.cornerOffsetX() == 0.0F && values.cornerOffsetY() == 0.0F
				&& values.cornerScale() == 1.0F;

		if (exact)
		{
			return raw;
		}

		Box box = raw.box();
		float wantedX = Math.max((float) CORNER_SCREEN_MARGIN - box.left(),
				Math.min(screenWidth - (float) CORNER_SCREEN_MARGIN - box.right(), 0.0F));
		float wantedY = Math.max((float) CORNER_SCREEN_MARGIN - box.top(),
				Math.min(screenHeight - (float) CORNER_SCREEN_MARGIN - box.bottom(), 0.0F));
		int shiftX = wantedX >= 0.0F ? (int) Math.ceil(wantedX) : (int) Math.floor(wantedX);
		int shiftY = wantedY >= 0.0F ? (int) Math.ceil(wantedY) : (int) Math.floor(wantedY);

		if (shiftX == 0 && shiftY == 0)
		{
			return raw;
		}

		return new Corner(raw.x() + shiftX, raw.baseline() + shiftY,
				raw.kickerX() + shiftX, raw.kickerY() + shiftY, raw.nameScale(),
				raw.kickerScale(), raw.kickerSpacing(), raw.lineGap(),
				new Box(box.left() + shiftX, box.top() + shiftY,
						box.right() + shiftX, box.bottom() + shiftY));
	}

	/** The smallest box that holds both of the two, or the one that is not null. */
	static Box union(Box first, Box second)
	{
		if (first == null)
		{
			return second;
		}

		if (second == null)
		{
			return first;
		}

		return new Box(Math.min(first.left(), second.left()), Math.min(first.top(), second.top()),
				Math.max(first.right(), second.right()), Math.max(first.bottom(), second.bottom()));
	}
}
