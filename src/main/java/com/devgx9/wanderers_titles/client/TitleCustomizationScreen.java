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
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.joml.Matrix3x2fStack;

/**
 * The screen that arranges the mod's two titles: the large plate and its name, and the small corner
 * title, moved and scaled against a preview of the real drawing.
 *
 * The preview is the game's own drawing at a smaller size, not a mock: the stage is the GUI-scaled
 * screen scaled uniformly into the room between the header and the buttons, and the plate, the name
 * and the corner title are drawn inside it by the same {@link TitleOverlay} helpers and the same
 * {@link TitleGeometry} the game uses. The customization is edited on a copy (see
 * {@link TitleCustomization#copy()}), so Done and Escape discard the arrangement and only Save writes
 * it and makes it live.
 *
 * The screen is reached from the gear in the Collection (see {@link BiomeCollectionScreen}), which is
 * the parent this screen returns to.
 */
public final class TitleCustomizationScreen extends Screen
{
	private static final int HANDLE_SIZE = 5;
	private static final int HANDLE_HIT_RADIUS = 7;
	private static final float WHEEL_STEP = 1.05F;
	private static final int SELECTION_RED = 0xFF3B30;
	private static final long SAVED_MESSAGE_MS = 2000L;

	private static final Identifier FRAME = Identifier.fromNamespaceAndPath(
			WandererTitles.MOD_ID, "textures/title/frame_0100.png");
	private static final Identifier BUTTON_SPRITE = Identifier.fromNamespaceAndPath(
			WandererTitles.MOD_ID, "widget/button");
	private static final Identifier LIT_BUTTON_SPRITE = Identifier.fromNamespaceAndPath(
			WandererTitles.MOD_ID, "widget/button_highlighted");
	private static final Component SAMPLE = Component.translatable(
			"screen.immersive_titles.customize.sample").withStyle(ChatFormatting.BOLD);

	/** The three things the player can select: the plate, the name on it, and the corner title. */
	private enum Element
	{
		PLATE,
		NAME,
		CORNER
	}

	/** What a held mouse is doing: nothing, moving the selection, or resizing it. */
	private enum Drag
	{
		NONE,
		MOVE,
		RESIZE
	}

	private final Screen parent;
	private final TitleCustomization working;
	private final TitleGeometry.Parts parts;
	private Button saveButton;
	private Button resetButton;
	private Button doneButton;
	private Component message;
	private int messageColor;
	private long messageUntilMs;
	private int stageLeft, stageTop, stageWidth, stageHeight;
	private float stageScale, virtualLeft, virtualTop;
	private Element selected = Element.PLATE;
	private Drag drag = Drag.NONE;
	private float dragStartOffsetX, dragStartOffsetY;
	private float dragDenominatorX, dragDenominatorY;
	private double dragMouseX, dragMouseY;
	private float resizeStartScale, resizeStartDistance;
	private float anchorX, anchorY;

	public TitleCustomizationScreen(Screen parent)
	{
		super(Component.translatable("screen.immersive_titles.customize.title"));
		this.parent = parent;
		this.working = TitleCustomization.live().copy();
		this.parts = TitleGeometry.split(SAMPLE);
	}

	@Override
	protected void init()
	{
		int buttonY = height - 8 - 20;
		int roomTop = height >= 210 ? 44 : 24;
		int roomBottom = buttonY - 16;
		int availableWidth = Math.max(1, width - 2 * 8);
		int availableHeight = Math.max(1, roomBottom - roomTop);
		float fit = Math.min(availableWidth / (float) width, availableHeight / (float) height);
		stageWidth = Math.max(1, (int) Math.floor(width * fit));
		stageHeight = Math.max(1, (int) Math.floor(height * fit));
		stageLeft = 8 + (availableWidth - stageWidth) / 2;
		stageTop = roomTop + (availableHeight - stageHeight) / 2;
		stageScale = Math.min(stageWidth / (float) width, stageHeight / (float) height);
		virtualLeft = stageLeft + (stageWidth - width * stageScale) / 2.0F;
		virtualTop = stageTop + (stageHeight - height * stageScale) / 2.0F;
		drag = Drag.NONE;

		int gap = 6;
		int buttonWidth = Math.max(1, Math.min(100, (width - 2 * 8 - 2 * gap) / 3));
		int buttonX = (width - (buttonWidth * 3 + gap * 2)) / 2;

		saveButton = Button.builder(Component.translatable("screen.immersive_titles.customize.save"),
				button -> save()).bounds(buttonX, buttonY, buttonWidth, 20).build();
		resetButton = Button.builder(Component.translatable("screen.immersive_titles.customize.reset"),
				button -> reset()).bounds(buttonX + buttonWidth + gap, buttonY, buttonWidth, 20).build();
		doneButton = Button.builder(Component.translatable("gui.done"), button -> onClose())
				.bounds(buttonX + 2 * (buttonWidth + gap), buttonY, buttonWidth, 20).build();
		addRenderableWidget(saveButton);
		addRenderableWidget(resetButton);
		addRenderableWidget(doneButton);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick)
	{
		if (messageUntilMs != 0L && Util.getMillis() > messageUntilMs)
		{
			message = null;
			messageUntilMs = 0L;
		}

		int buttonY = height - 8 - 20;

		drawCentered(graphics, title, 8, 0xFFFFFFFF);

		if (height >= 210)
		{
			drawCentered(graphics, Component.translatable(
					"screen.immersive_titles.customize.hint.move"), 20, 0xFF8A8A8A);
			drawCentered(graphics, Component.translatable(
					"screen.immersive_titles.customize.hint.save"), 30, 0xFF8A8A8A);
		}

		graphics.fill(stageLeft - 1, stageTop - 1, stageLeft + stageWidth + 1,
				stageTop + stageHeight + 1, 0xFF3A3A42);
		graphics.fill(stageLeft, stageTop, stageLeft + stageWidth, stageTop + stageHeight,
				0xFF0E0E12);

		TitleGeometry.Plate plate = TitleGeometry.plate(width, height, working);
		TitleGeometry.Name name = TitleGeometry.name(plate, font, SAMPLE, working);
		TitleGeometry.Corner corner = TitleGeometry.corner(width, height, font, parts.kicker(),
				parts.name(), working);

		graphics.enableScissor(stageLeft, stageTop, stageLeft + stageWidth, stageTop + stageHeight);
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(virtualLeft, virtualTop);
		pose.scale(stageScale, stageScale);
		TitleOverlay.drawPlate(graphics, FRAME, plate);
		TitleOverlay.drawName(graphics, font, SAMPLE, name, 255);
		TitleOverlay.drawCorner(graphics, font, parts.kicker(), parts.name(), corner, 0xFFFFFFFF);
		pose.popMatrix();
		drawSelection(graphics);
		graphics.disableScissor();
		drawHandles(graphics);

		super.extractRenderState(graphics, mouseX, mouseY, partialTick);

		drawButtonFace(graphics, saveButton);
		drawButtonFace(graphics, resetButton);
		drawButtonFace(graphics, doneButton);
		drawButtonFocus(graphics, saveButton);
		drawButtonFocus(graphics, resetButton);
		drawButtonFocus(graphics, doneButton);

		if (message != null)
		{
			drawCentered(graphics, message, buttonY - 12, messageColor);
		}
	}

	/**
	 * Writes {@code text} centred at {@code y}. This is the collection's own form of the call, which
	 * exists on both of the Minecraft versions the mod is built for.
	 */
	private void drawCentered(GuiGraphicsExtractor graphics, Component text, int y, int color)
	{
		graphics.text(font, text, (width - font.width(text)) / 2, y, color, true);
	}

	/**
	 * The rectangle of one element, in the virtual screen space the preview is drawn in. It is read
	 * from the working copy every time, so an edit is on the screen as soon as it is made.
	 */
	private TitleGeometry.Box boxOf(Element element)
	{
		TitleGeometry.Plate plate = TitleGeometry.plate(width, height, working);

		return switch (element)
		{
			case PLATE -> new TitleGeometry.Box(plate.x(), plate.y(),
					plate.x() + plate.width(), plate.y() + plate.height());
			case NAME -> TitleGeometry.name(plate, font, SAMPLE, working).box();
			case CORNER -> TitleGeometry.corner(width, height, font, parts.kicker(), parts.name(),
					working).box();
		};
	}

	/** The four corner points of a box in screen pixels, kept inside the stage so they stay grabbable. */
	private float[][] handlePoints(TitleGeometry.Box box)
	{
		float left = clampHandleX(virtualLeft + box.left() * stageScale);
		float right = clampHandleX(virtualLeft + box.right() * stageScale);
		float top = clampHandleY(virtualTop + box.top() * stageScale);
		float bottom = clampHandleY(virtualTop + box.bottom() * stageScale);
		return new float[][] {{left, top}, {right, top}, {left, bottom}, {right, bottom}};
	}

	private float clampHandleX(float x)
	{
		return (float) Math.max(stageLeft + HANDLE_SIZE / 2.0,
				Math.min(stageLeft + stageWidth - HANDLE_SIZE / 2.0, x));
	}

	private float clampHandleY(float y)
	{
		return (float) Math.max(stageTop + HANDLE_SIZE / 2.0,
				Math.min(stageTop + stageHeight - HANDLE_SIZE / 2.0, y));
	}

	/** Whether the pointer is on one of the selected element's four handles. */
	private boolean isOverHandle(Element element, double mouseX, double mouseY)
	{
		for (float[] point : handlePoints(boxOf(element)))
		{
			if (Math.abs(mouseX - point[0]) <= HANDLE_HIT_RADIUS
					&& Math.abs(mouseY - point[1]) <= HANDLE_HIT_RADIUS)
			{
				return true;
			}
		}

		return false;
	}

	/** The clamped handle point nearest the pointer, which is where a resize starts measuring from. */
	private float[] nearestHandle(Element element, double mouseX, double mouseY)
	{
		float[][] points = handlePoints(boxOf(element));
		float[] nearest = points[0];
		double best = Double.MAX_VALUE;

		for (float[] point : points)
		{
			double distance = Math.hypot(point[0] - mouseX, point[1] - mouseY);

			if (distance < best)
			{
				best = distance;
				nearest = point;
			}
		}

		return nearest;
	}

	/**
	 * The first element under a point of the virtual screen, in the order a name over the plate and a
	 * corner title over both are read: the smallest thing wins, and the plate is the fallback.
	 */
	private Element hitElement(double virtualX, double virtualY)
	{
		double screenX = virtualLeft + virtualX * stageScale;
		double screenY = virtualTop + virtualY * stageScale;

		for (Element element : new Element[] {Element.NAME, Element.CORNER, Element.PLATE})
		{
			TitleGeometry.Box box = boxOf(element);

			if (screenX >= virtualLeft + box.left() * stageScale
					&& screenX <= virtualLeft + box.right() * stageScale
					&& screenY >= virtualTop + box.top() * stageScale
					&& screenY <= virtualTop + box.bottom() * stageScale)
			{
				return element;
			}
		}

		return null;
	}

	private boolean isOverStage(double mouseX, double mouseY)
	{
		return mouseX >= stageLeft && mouseX < stageLeft + stageWidth
				&& mouseY >= stageTop && mouseY < stageTop + stageHeight;
	}

	private double toVirtualX(double screenX)
	{
		return (screenX - virtualLeft) / stageScale;
	}

	private double toVirtualY(double screenY)
	{
		return (screenY - virtualTop) / stageScale;
	}

	private float offsetX(Element element)
	{
		return switch (element)
		{
			case PLATE -> working.plateOffsetX();
			case NAME -> working.nameOffsetX();
			case CORNER -> working.cornerOffsetX();
		};
	}

	private float offsetY(Element element)
	{
		return switch (element)
		{
			case PLATE -> working.plateOffsetY();
			case NAME -> working.nameOffsetY();
			case CORNER -> working.cornerOffsetY();
		};
	}

	private void setOffsets(Element element, float x, float y)
	{
		switch (element)
		{
			case PLATE ->
			{
				working.setPlateOffsetX(x);
				working.setPlateOffsetY(y);
			}
			case NAME ->
			{
				working.setNameOffsetX(x);
				working.setNameOffsetY(y);
			}
			case CORNER ->
			{
				working.setCornerOffsetX(x);
				working.setCornerOffsetY(y);
			}
		}
	}

	/** What one unit of a move is measured against: the screen, or the room the name sits in. */
	private float denominatorX(Element element)
	{
		if (element != Element.NAME)
		{
			return width;
		}

		TitleGeometry.Plate plate = TitleGeometry.plate(width, height, working);
		return TitleGeometry.name(plate, font, SAMPLE, working).roomWidth();
	}

	private float denominatorY(Element element)
	{
		if (element != Element.NAME)
		{
			return height;
		}

		TitleGeometry.Plate plate = TitleGeometry.plate(width, height, working);
		return TitleGeometry.name(plate, font, SAMPLE, working).roomHeight();
	}

	/** The customization multiplier of an element, not the plate's combined drawn scale. */
	private float scaleOf(Element element)
	{
		return switch (element)
		{
			case PLATE -> working.plateScale();
			case NAME -> working.nameScale();
			case CORNER -> working.cornerScale();
		};
	}

	/** Starts a resize at a handle: the anchor is the element's centre, and stays fixed for the drag. */
	private void beginResize(Element element, double mouseX, double mouseY)
	{
		TitleGeometry.Box box = boxOf(element);
		selected = element;
		drag = Drag.RESIZE;
		anchorX = (box.left() + box.right()) / 2.0F;
		anchorY = (box.top() + box.bottom()) / 2.0F;
		resizeStartScale = scaleOf(element);

		float[] handle = nearestHandle(element, mouseX, mouseY);
		resizeStartDistance = (float) Math.hypot(
				(handle[0] - virtualLeft) / stageScale - anchorX,
				(handle[1] - virtualTop) / stageScale - anchorY);
		dragMouseX = toVirtualX(mouseX);
		dragMouseY = toVirtualY(mouseY);
	}

	/**
	 * Handles a press: the buttons first, then a handle of the selection, then the first element under
	 * the pointer, which is selected and starts moving at once. An empty part of the stage clears the
	 * selection and is still the editor's click rather than the screen's.
	 */
	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick)
	{
		if (super.mouseClicked(event, doubleClick))
		{
			return true;
		}

		if (event.button() != InputConstants.MOUSE_BUTTON_LEFT || !isOverStage(event.x(), event.y()))
		{
			return false;
		}

		if (selected != null && isOverHandle(selected, event.x(), event.y()))
		{
			beginResize(selected, event.x(), event.y());
			return true;
		}

		double virtualX = toVirtualX(event.x());
		double virtualY = toVirtualY(event.y());
		selected = hitElement(virtualX, virtualY);

		if (selected == null)
		{
			return true;
		}

		drag = Drag.MOVE;
		dragStartOffsetX = offsetX(selected);
		dragStartOffsetY = offsetY(selected);
		dragDenominatorX = denominatorX(selected);
		dragDenominatorY = denominatorY(selected);
		dragMouseX = virtualX;
		dragMouseY = virtualY;
		return true;
	}

	/**
	 * Moves the selection, or resizes it from the anchor, adding the pointer's travel since the press
	 * to the offsets the drag started with. The stored offsets are clamped after every step, so the
	 * value the next drag starts from is the one the screen shows.
	 */
	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY)
	{
		if (drag == Drag.NONE || selected == null)
		{
			return super.mouseDragged(event, dragX, dragY);
		}

		double virtualX = toVirtualX(event.x());
		double virtualY = toVirtualY(event.y());

		if (drag == Drag.MOVE)
		{
			float offsetX = (float) (dragStartOffsetX
					+ (virtualX - dragMouseX) / dragDenominatorX);
			float offsetY = (float) (dragStartOffsetY
					+ (virtualY - dragMouseY) / dragDenominatorY);
			setOffsets(selected, offsetX, offsetY);
			clampStoredOffsets();
			edited();
			return true;
		}
		else if (drag == Drag.RESIZE)
		{
			float distance = (float) Math.hypot(virtualX - anchorX, virtualY - anchorY);
			float ratio = Math.max(1.0F, distance) / Math.max(1.0F, resizeStartDistance);
			applyScale(resizeStartScale * ratio);
			edited();
			return true;
		}

		return true;
	}

	/** Ends a drag where it is and keeps the selection so the handles stay on the element. */
	@Override
	public boolean mouseReleased(MouseButtonEvent event)
	{
		if (drag != Drag.NONE)
		{
			drag = Drag.NONE;
			return true;
		}

		return super.mouseReleased(event);
	}

	/**
	 * Resizes the selection in place with the wheel, one step per notch. The anchor is the element's
	 * current centre, so the wheel grows it from the middle the way a handle drag does.
	 */
	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical)
	{
		if (!isOverStage(mouseX, mouseY) || selected == null || vertical == 0.0D)
		{
			return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
		}

		TitleGeometry.Box box = boxOf(selected);
		anchorX = (box.left() + box.right()) / 2.0F;
		anchorY = (box.top() + box.bottom()) / 2.0F;

		int notches = Math.max(1, (int) Math.abs(vertical));
		float factor = vertical > 0 ? WHEEL_STEP : 1.0F / WHEEL_STEP;

		for (int notch = 0; notch < notches; notch++)
		{
			applyScale(scaleOf(selected) * factor);
		}

		clampStoredOffsets();
		edited();
		return true;
	}

	/**
	 * Applies a scale to the selection. The plate and the name are centred where their room already
	 * centres them; the corner keeps the centre the resize started from, and the stored offsets are
	 * moved by the difference, so a resize does not jump the block across the screen.
	 */
	private void applyScale(float value)
	{
		if (selected == null)
		{
			return;
		}

		if (selected == Element.CORNER)
		{
			working.setCornerScale(value);
			TitleGeometry.Box box = boxOf(Element.CORNER);
			float dx = anchorX - (box.left() + box.right()) / 2.0F;
			float dy = anchorY - (box.top() + box.bottom()) / 2.0F;
			working.setCornerOffsetX(working.cornerOffsetX() + (float) (dx / width));
			working.setCornerOffsetY(working.cornerOffsetY() + (float) (dy / height));
		}
		else if (selected == Element.PLATE)
		{
			working.setPlateScale(value);
		}
		else
		{
			working.setNameScale(value);
		}

		clampStoredOffsets();
	}

	/**
	 * Writes the limit the drawing enforces into the stored offsets themselves: the name's box is
	 * centred on its room, so the largest offset that keeps it in the room is half the spare room, and
	 * the corner's block is measured before its four-pixel screen clamp and moved by the shift that
	 * brings it inside. The plate's setter already stops at its own limit.
	 */
	private void clampStoredOffsets()
	{
		if (selected == Element.NAME)
		{
			TitleGeometry.Plate plate = TitleGeometry.plate(width, height, working);
			TitleGeometry.Name name = TitleGeometry.name(plate, font, SAMPLE, working);
			float boxWidth = name.box().right() - name.box().left();
			float boxHeight = name.box().bottom() - name.box().top();
			float maxX = Math.max(0.0F, (name.roomWidth() - boxWidth)
					/ (2.0F * name.roomWidth()));
			float maxY = Math.max(0.0F, (name.roomHeight() - boxHeight)
					/ (2.0F * name.roomHeight()));
			working.setNameOffsetX(Math.max(-maxX, Math.min(maxX, working.nameOffsetX())));
			working.setNameOffsetY(Math.max(-maxY, Math.min(maxY, working.nameOffsetY())));
		}
		else if (selected == Element.CORNER
				&& (working.cornerOffsetX() != 0.0F || working.cornerOffsetY() != 0.0F
						|| working.cornerScale() != 1.0F))
		{
			TitleGeometry.Corner raw = TitleGeometry.cornerRaw(width, height, font,
					parts.kicker(), parts.name(), working);
			float shiftX = Math.max(TitleGeometry.CORNER_SCREEN_MARGIN - raw.box().left(),
					Math.min(width - TitleGeometry.CORNER_SCREEN_MARGIN - raw.box().right(), 0.0F));
			float shiftY = Math.max(TitleGeometry.CORNER_SCREEN_MARGIN - raw.box().top(),
					Math.min(height - TitleGeometry.CORNER_SCREEN_MARGIN - raw.box().bottom(), 0.0F));
			working.setCornerOffsetX(working.cornerOffsetX() + shiftX / width);
			working.setCornerOffsetY(working.cornerOffsetY() + shiftY / height);
		}
	}

	/** An edit puts the saved message away, since what is on the screen is no longer what was saved. */
	private void edited()
	{
		message = null;
		messageUntilMs = 0L;
	}

	/** The selected box's outline: a black shadow just outside it and a red line just inside it. */
	private void drawSelection(GuiGraphicsExtractor graphics)
	{
		if (selected == null)
		{
			return;
		}

		TitleGeometry.Box box = boxOf(selected);
		int left = Math.round(virtualLeft + box.left() * stageScale);
		int top = Math.round(virtualTop + box.top() * stageScale);
		int right = Math.round(virtualLeft + box.right() * stageScale);
		int bottom = Math.round(virtualTop + box.bottom() * stageScale);

		graphics.fill(left - 1, top - 1, right + 1, top, 0xFF000000);
		graphics.fill(left - 1, bottom, right + 1, bottom + 1, 0xFF000000);
		graphics.fill(left - 1, top, left, bottom, 0xFF000000);
		graphics.fill(right, top, right + 1, bottom, 0xFF000000);

		graphics.fill(left, top, right, top + 1, SELECTION_RED);
		graphics.fill(left, bottom - 1, right, bottom, SELECTION_RED);
		graphics.fill(left, top + 1, left + 1, bottom - 1, SELECTION_RED);
		graphics.fill(right - 1, top + 1, right, bottom - 1, SELECTION_RED);
	}

	/** The four handles, in screen space and outside the stage clip, kept inside the stage by clamping. */
	private void drawHandles(GuiGraphicsExtractor graphics)
	{
		if (selected == null)
		{
			return;
		}

		for (float[] point : handlePoints(boxOf(selected)))
		{
			int x = Math.round(point[0]);
			int y = Math.round(point[1]);
			graphics.fill(x - 3, y - 3, x + 4, y + 4, 0xFF000000);
			graphics.fill(x - 2, y - 2, x + 3, y + 3, SELECTION_RED);
		}
	}

	/**
	 * Writes the working copy to the file and, on success, makes it the live customization. The
	 * screen stays open and the message above the buttons says whether it was written; a failure
	 * leaves the live values and the file as they were, and stays until the next edit.
	 */
	private void save()
	{
		if (working.save())
		{
			TitleCustomization.replace(working);
			message = Component.translatable("screen.immersive_titles.customize.saved");
			messageColor = 0xFF55FF55;
			messageUntilMs = Util.getMillis() + SAVED_MESSAGE_MS;
		}
		else
		{
			message = Component.translatable("screen.immersive_titles.customize.save_failed");
			messageColor = 0xFFFF5555;
			messageUntilMs = 0L;
		}
	}

	/** Returns the working copy to the default placement and size; the file is written only by Save. */
	private void reset()
	{
		working.reset();
		message = null;
		messageUntilMs = 0L;
	}

	@Override
	public void onClose()
	{
		minecraft.gui.setScreen(parent);
	}

	/**
	 * Paints the pack's button face over the game's own, then writes the label over it again: the game
	 * drew the button and its label before this screen painted the face, so both have to be drawn here
	 * to keep the pack's dark face and the white label together.
	 */
	private void drawButtonFace(GuiGraphicsExtractor graphics, Button button)
	{
		Identifier sprite = button.isHoveredOrFocused() ? LIT_BUTTON_SPRITE : BUTTON_SPRITE;
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite,
				button.getX(), button.getY(), button.getWidth(), button.getHeight());

		Component label = button.getMessage();
		graphics.text(font, label, button.getX() + (button.getWidth() - font.width(label)) / 2,
				button.getY() + (button.getHeight() - font.lineHeight) / 2, 0xFFFFFFFF, true);
	}

	/** Draws a 1 px silver border around a button the keyboard is on, so the focus is visible. */
	private void drawButtonFocus(GuiGraphicsExtractor graphics, Button button)
	{
		if (!button.isFocused())
		{
			return;
		}

		int left = button.getX() - 1;
		int top = button.getY() - 1;
		int right = button.getX() + button.getWidth() + 1;
		int bottom = button.getY() + button.getHeight() + 1;

		graphics.fill(left, top, right, top + 1, 0xFFC9CCD1);
		graphics.fill(left, bottom - 1, right, bottom, 0xFFC9CCD1);
		graphics.fill(left, top + 1, left + 1, bottom - 1, 0xFFC9CCD1);
		graphics.fill(right - 1, top + 1, right, bottom - 1, 0xFFC9CCD1);
	}
}
