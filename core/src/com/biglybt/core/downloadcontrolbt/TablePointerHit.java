/*
 * Copyright (C) 2026 DownLoadControlBT experiment contributors
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 */
package com.biglybt.core.downloadcontrolbt;

/**
 * Pure pointer/menu/header hit-test helpers. No SWT, no network, no GUI.
 * Used so table MenuDetect and column-divider logic can be regression-tested
 * without launching the client.
 */
public final class TablePointerHit
{
	/** Default divider slop, matching {@code TableHeaderPainted.RESIZE_HIT_PX}. */
	public static final int DEFAULT_RESIZE_SLOP_PX = 5;

	public static final String DISABLE_MULTI = "multiselect";
	public static final String DISABLE_SKIPPED = "skipped";
	public static final String DISABLE_EMPTY = "empty";

	private TablePointerHit()
	{
	}

	/**
	 * Convert a display-space point to control-relative coordinates given
	 * the control's display origin. Does not add a scroll/header offset.
	 */
	public static int[] displayToControl(int displayX, int displayY, int originX, int originY)
	{
		return new int[] { displayX - originX, displayY - originY };
	}

	/**
	 * Convert a control-relative Y to painted-table content Y by adding the
	 * scroll origin once. Callers that already added {@code clientArea.y}
	 * must not call this again.
	 */
	public static int contentY(int controlY, int clientAreaY)
	{
		return clientAreaY + controlY;
	}

	/**
	 * A pointer context menu should replace the current selection only when
	 * the hit row is present and is not already selected. Keyboard menus
	 * never change selection from a pointer hit (they keep the focused row).
	 * This preserves multi-selection when the pointer is inside it.
	 */
	public static boolean shouldReplaceSelectionFromPointer(
		boolean		keyboard,
		boolean		hitPresent,
		boolean		hitAlreadySelected )
	{
		if ( keyboard || !hitPresent ){
			return false;
		}
		return !hitAlreadySelected;
	}

	/**
	 * Reason Sequential/Random should be disabled on a selection, or
	 * {@code null} if an override may be stored. Auto-queue itself is not
	 * gated by this (it is a torrent-level switch).
	 */
	public static String modeOverrideDisableReason(
		boolean		multiSelect,
		boolean		skipped,
		boolean		empty )
	{
		if ( multiSelect ){
			return DISABLE_MULTI;
		}
		if ( skipped ){
			return DISABLE_SKIPPED;
		}
		if ( empty ){
			return DISABLE_EMPTY;
		}
		return null;
	}

	/**
	 * Open Torrent Options variant (DLCBT Nov 18): multi-select is allowed,
	 * the choice applies to every selected nonempty file. Disabled only when
	 * nothing is selected or every selected file is empty. Unchecked files are
	 * fine because choosing S/R also checks them.
	 */
	public static String addDialogModeDisableReason(
		int		selectedCount,
		int		nonEmptyCount )
	{
		if ( selectedCount <= 0 || nonEmptyCount <= 0 ){
			return DISABLE_EMPTY;
		}
		return null;
	}

	/**
	 * Radio state for a selection: the shared mode when every selected
	 * nonempty file resolves to the same mode, else 0 (no radio checked).
	 */
	public static int commonMode( int[] modes )
	{
		if ( modes == null || modes.length == 0 ){
			return 0;
		}
		int first = modes[0];
		for ( int m : modes ){
			if ( m != first ){
				return 0;
			}
		}
		return first;
	}

	/**
	 * True when {@code mouseX} (same control-relative space as SWT MouseEvent)
	 * sits within {@code slop} pixels of any column divider. {@code scrollX}
	 * is the painted table's horizontal scroll origin ({@code clientArea.x}).
	 */
	public static boolean isColumnBoundaryHit(
		int		mouseX,
		int		scrollX,
		int[]	widths,
		int		slop )
	{
		if ( widths == null || widths.length == 0 || slop < 0 ){
			return false;
		}
		int x = -scrollX;
		for ( int w: widths ){
			x += w;
			if ( mouseX >= x - slop && mouseX <= x + slop ){
				return true;
			}
		}
		return false;
	}
}
