/*
 * Copyright (C) 2026 DownLoadControlBT experiment contributors
 */
package com.biglybt.core.downloadcontrolbt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public class TablePointerHitTest
{
	@Test
	public void displayToControlSubtractsOriginOnce()
	{
		int[] pt = TablePointerHit.displayToControl( 400, 260, 100, 200 );
		assertThat( pt ).containsExactly( 300, 60 );
	}

	@Test
	public void contentYAddsScrollOriginOnce()
	{
		assertThat( TablePointerHit.contentY( 40, 0 )).isEqualTo( 40 );
		assertThat( TablePointerHit.contentY( 40, 80 )).isEqualTo( 120 );
	}

	@Test
	public void keyboardMenuDoesNotReplaceSelection()
	{
		assertThat( TablePointerHit.shouldReplaceSelectionFromPointer( true, true, false )).isFalse();
		assertThat( TablePointerHit.shouldReplaceSelectionFromPointer( false, true, false )).isTrue();
		assertThat( TablePointerHit.shouldReplaceSelectionFromPointer( false, true, true )).isFalse();
		assertThat( TablePointerHit.shouldReplaceSelectionFromPointer( false, false, false )).isFalse();
	}

	@Test
	public void modeOverrideDisableReasons()
	{
		assertThat( TablePointerHit.modeOverrideDisableReason( true, false, false )).isEqualTo( TablePointerHit.DISABLE_MULTI );
		assertThat( TablePointerHit.modeOverrideDisableReason( false, true, false )).isEqualTo( TablePointerHit.DISABLE_SKIPPED );
		assertThat( TablePointerHit.modeOverrideDisableReason( false, false, true )).isEqualTo( TablePointerHit.DISABLE_EMPTY );
		assertThat( TablePointerHit.modeOverrideDisableReason( false, false, false )).isNull();
	}

	@Test
	public void columnBoundaryHitUsesControlRelativeXAndScroll()
	{
		int[] widths = { 80, 120, 60 };
		assertThat( TablePointerHit.isColumnBoundaryHit( 80, 0, widths, 5 )).isTrue();
		assertThat( TablePointerHit.isColumnBoundaryHit( 85, 0, widths, 5 )).isTrue();
		assertThat( TablePointerHit.isColumnBoundaryHit( 100, 0, widths, 5 )).isFalse();
		assertThat( TablePointerHit.isColumnBoundaryHit( 200, 0, widths, 5 )).isTrue();
		assertThat( TablePointerHit.isColumnBoundaryHit( 40, 0, widths, 5 )).isFalse();
		assertThat( TablePointerHit.isColumnBoundaryHit( 75, 5, widths, 5 )).isTrue();
		assertThat( TablePointerHit.isColumnBoundaryHit( 0, 0, null, 5 )).isFalse();
	}
}
