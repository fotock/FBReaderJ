/*
 * Copyright (C) 2007-2009 Geometer Plus <contact@geometerplus.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301, USA.
 */

package org.geometerplus.zlibrary.ui.android.view;

import java.util.Map;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Bitmap;
import android.view.*;
import android.util.AttributeSet;

import org.geometerplus.zlibrary.core.view.ZLView;
import org.geometerplus.zlibrary.core.view.ZLViewWidget;
import org.geometerplus.zlibrary.core.application.ZLApplication;

import org.geometerplus.zlibrary.ui.android.util.ZLAndroidKeyUtil;

public class ZLAndroidWidget extends View {
	private ZLAndroidViewWidget myViewWidget;
	private Bitmap myMainBitmap;
	private Bitmap mySecondaryBitmap;
	private boolean mySecondaryBitmapIsUpToDate;
	private boolean myScrollingInProgress;
	private int myScrollingShift;
	private float myScrollingSpeed;
	private int myScrollingBound;

	public ZLAndroidWidget(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		setDrawingCacheEnabled(false);
	}

	public ZLAndroidWidget(Context context, AttributeSet attrs) {
		super(context, attrs);
		setDrawingCacheEnabled(false);
	}

	public ZLAndroidWidget(Context context) {
		super(context);
		setDrawingCacheEnabled(false);
	}

	public ZLAndroidPaintContext getPaintContext() {
		return ZLAndroidPaintContext.Instance();
	}

	void setViewWidget(ZLAndroidViewWidget viewWidget) {
		myViewWidget = viewWidget;
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		if (myScreenIsTouched) {
			final ZLView view = ZLApplication.Instance().getCurrentView();
			myScrollingInProgress = false;
			myScrollingShift = 0;
			myScreenIsTouched = false;
			view.onScrollingFinished(ZLView.PAGE_CENTRAL);
			setPageToScroll(ZLView.PAGE_CENTRAL);
		}
	}

	@Override
	protected void onDraw(final Canvas canvas) {
		super.onDraw(canvas);

		final int w = getWidth();
		final int h = getHeight();

		if ((myMainBitmap != null) && ((myMainBitmap.getWidth() != w) || (myMainBitmap.getHeight() != h))) {
			myMainBitmap = null;
			mySecondaryBitmap = null;
			System.gc();
			System.gc();
			System.gc();
		}
		if (myMainBitmap == null) {
			myMainBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);
			mySecondaryBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);
			mySecondaryBitmapIsUpToDate = false;
			drawOnBitmap(myMainBitmap);
		}

		if (myScrollingInProgress || (myScrollingShift != 0)) {
			onDrawInScrolling(canvas);
		} else {
			onDrawStatic(canvas);
		}
	}

	private void onDrawInScrolling(Canvas canvas) {
		final int w = getWidth();
		final int h = getHeight();
		final ZLAndroidPaintContext context = ZLAndroidPaintContext.Instance();

		boolean stopScrolling = false;
		if (myScrollingInProgress) {
			myScrollingShift += (int)myScrollingSpeed;
			if (myScrollingSpeed > 0) {
				if (myScrollingShift >= myScrollingBound) {
					myScrollingShift = myScrollingBound;
					stopScrolling = true;
				}
			} else {
				if (myScrollingShift <= myScrollingBound) {
					myScrollingShift = myScrollingBound;
					stopScrolling = true;
				}
			}
			myScrollingSpeed *= 1.5;
		}
		final boolean horizontal =
			(myViewPageToScroll == ZLView.PAGE_RIGHT) || 
			(myViewPageToScroll == ZLView.PAGE_LEFT);
		canvas.drawBitmap(
			myMainBitmap,
			horizontal ? myScrollingShift : 0,
			horizontal ? 0 : myScrollingShift,
			context.Paint
		);
		final int size = horizontal ? w : h;
		int shift = (myScrollingShift < 0) ? (myScrollingShift + size) : (myScrollingShift - size);
		canvas.drawBitmap(
			mySecondaryBitmap,
			horizontal ? shift : 0,
			horizontal ? 0 : shift,
			context.Paint
		);
		if (stopScrolling) {
			final ZLView view = ZLApplication.Instance().getCurrentView();
			if (myScrollingBound != 0) {
				Bitmap swap = myMainBitmap;
				myMainBitmap = mySecondaryBitmap;
				mySecondaryBitmap = swap;
				mySecondaryBitmapIsUpToDate = false;
				view.onScrollingFinished(myViewPageToScroll);
			} else {
				view.onScrollingFinished(ZLView.PAGE_CENTRAL);
			}
			setPageToScroll(ZLView.PAGE_CENTRAL);
			myScrollingInProgress = false;
			myScrollingShift = 0;
		} else {
			if (shift < 0) {
				shift += size;
			}
			// TODO: set color
			if (horizontal) {
				canvas.drawLine(shift, 0, shift, h + 1, context.Paint);
			} else {
				canvas.drawLine(0, shift, w + 1, shift, context.Paint);
			}
			if (myScrollingInProgress) {
				postInvalidate();
			}
		}
	}

	private int myViewPageToScroll = ZLView.PAGE_CENTRAL;
	private void setPageToScroll(int viewPage) {
		if (myViewPageToScroll != viewPage) {
			myViewPageToScroll = viewPage;
			mySecondaryBitmapIsUpToDate = false;
		}
	}

	void scrollToPage(int viewPage, int shift) {
		switch (viewPage) {
			case ZLView.PAGE_BOTTOM:
			case ZLView.PAGE_RIGHT:
				shift = -shift;
				break;
		}

		if (myMainBitmap == null) {
			return;
		}
		if (((shift > 0) && (myScrollingShift <= 0)) ||
			((shift < 0) && (myScrollingShift >= 0))) {
			mySecondaryBitmapIsUpToDate = false;
		}
		myScrollingShift = shift;
		setPageToScroll(viewPage);
		drawOnBitmap(mySecondaryBitmap);
		postInvalidate();
	}

	void startAutoScrolling(int viewPage) {
		if (myMainBitmap == null) {
			return;
		}
		myScrollingInProgress = true;
		switch (viewPage) {
			case ZLView.PAGE_CENTRAL:
				switch (myViewPageToScroll) {
					case ZLView.PAGE_CENTRAL:
						myScrollingSpeed = 0;
						break;
					case ZLView.PAGE_LEFT:
					case ZLView.PAGE_TOP:
						myScrollingSpeed = -3;
						break;
					case ZLView.PAGE_RIGHT:
					case ZLView.PAGE_BOTTOM:
						myScrollingSpeed = 3;
						break;
				}
				myScrollingBound = 0;
				break;
			case ZLView.PAGE_LEFT:
				myScrollingSpeed = 3;
				myScrollingBound = getWidth();
				break;
			case ZLView.PAGE_RIGHT:
				myScrollingSpeed = -3;
				myScrollingBound = -getWidth();
				break;
			case ZLView.PAGE_TOP:
				myScrollingSpeed = 3;
				myScrollingBound = getHeight();
				break;
			case ZLView.PAGE_BOTTOM:
				myScrollingSpeed = -3;
				myScrollingBound = -getHeight();
				break;
		}
		if (viewPage != ZLView.PAGE_CENTRAL) {
			setPageToScroll(viewPage);
		}
		drawOnBitmap(mySecondaryBitmap);
		postInvalidate();
	}

	private void drawOnBitmap(Bitmap bitmap) {
		final ZLView view = ZLApplication.Instance().getCurrentView();
		if ((myViewWidget == null) || (view == null)) {
			return;
		}

		if (bitmap == myMainBitmap) {
			mySecondaryBitmapIsUpToDate = false;
		} else if (mySecondaryBitmapIsUpToDate) {
			return;
		} else {
			mySecondaryBitmapIsUpToDate = true;
		}

		final int w = getWidth();
		final int h = getHeight();
		final ZLAndroidPaintContext context = ZLAndroidPaintContext.Instance();

		Canvas canvas = new Canvas(bitmap);
		context.beginPaint(canvas);
		final int rotation = myViewWidget.getRotation();
		context.setRotation(rotation);
		final int scrollbarWidth = getVerticalScrollbarWidth();
		switch (rotation) {
			case ZLViewWidget.Angle.DEGREES0:
				context.setSize(w, h, scrollbarWidth);
				break;
			case ZLViewWidget.Angle.DEGREES90:
				context.setSize(h, w, scrollbarWidth);
				canvas.rotate(270, h / 2, h / 2);
				break;
			case ZLViewWidget.Angle.DEGREES180:
				context.setSize(w, h, scrollbarWidth);
				canvas.rotate(180, w / 2, h / 2);
				break;
			case ZLViewWidget.Angle.DEGREES270:
				context.setSize(h, w, scrollbarWidth);
				canvas.rotate(90, w / 2, w / 2);
				break;
		}
		view.paint((bitmap == myMainBitmap) ? ZLView.PAGE_CENTRAL : myViewPageToScroll);
		context.endPaint();
	}

	private void onDrawStatic(Canvas canvas) {
		drawOnBitmap(myMainBitmap);
		canvas.drawBitmap(myMainBitmap, 0, 0, ZLAndroidPaintContext.Instance().Paint);
	}

	@Override
	public boolean onTrackballEvent(MotionEvent event) {
		ZLApplication.Instance().getCurrentView().onTrackballRotated((int)(10 * event.getX()), (int)(10 * event.getY()));
		return true;
	}

	private boolean myScreenIsTouched;
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		int x = (int)event.getX();
		int y = (int)event.getY();
		switch (myViewWidget.getRotation()) {
			case ZLViewWidget.Angle.DEGREES0:
				break;
			case ZLViewWidget.Angle.DEGREES90:
			{
				int swap = x;
				x = getHeight() - y - 1;
				y = swap;
				break;
			}
			case ZLViewWidget.Angle.DEGREES180:
			{
				x = getWidth() - x - 1;
				y = getHeight() - y - 1;
				break;
			}
			case ZLViewWidget.Angle.DEGREES270:
			{
				int swap = getWidth() - x - 1;
				x = y;
				y = swap;
				break;
			}
		}

		final ZLView view = ZLApplication.Instance().getCurrentView();
		switch (event.getAction()) {
			case MotionEvent.ACTION_UP:
				view.onStylusRelease(x, y);
				myScreenIsTouched = false;
				break;
			case MotionEvent.ACTION_DOWN:
				view.onStylusPress(x, y);
				myScreenIsTouched = true;
				break;
			case MotionEvent.ACTION_MOVE:
				view.onStylusMovePressed(x, y);
				break;
		}

		return true;
	}

	public boolean onKeyDown(int keyCode, KeyEvent event) {
		switch (keyCode) {
			case KeyEvent.KEYCODE_VOLUME_DOWN:
			case KeyEvent.KEYCODE_VOLUME_UP:
			case KeyEvent.KEYCODE_BACK:
				return ZLApplication.Instance().doActionByKey(ZLAndroidKeyUtil.getKeyNameByCode(keyCode));
			case KeyEvent.KEYCODE_DPAD_DOWN:
				ZLApplication.Instance().getCurrentView().onTrackballRotated(0, 1);
				return true;
			case KeyEvent.KEYCODE_DPAD_UP:
				ZLApplication.Instance().getCurrentView().onTrackballRotated(0, -1);
				return true;
			default:
				return false;
		}
	}

	public boolean onKeyUp(int keyCode, KeyEvent event) {
		switch (keyCode) {
			case KeyEvent.KEYCODE_VOLUME_DOWN:
			case KeyEvent.KEYCODE_VOLUME_UP:
			case KeyEvent.KEYCODE_BACK:
				return true;
			default:
				return false;
		}
	}

	/*
	private int myScrollBarRange;
	private int myScrollBarOffset;
	private int myScrollBarThumbSize;

	void setVerticalScrollbarParameters(int full, int from, int to) {
		if (full < 0) {
			full = 0;
		}
		if (from < 0) {
			from = 0;
		} else if (from >= full) {
			from = full - 1;
		}
		if (to <= from) {
			to = from + 1;
		} else if (to > full) {
			to = full;
		}
		myScrollBarRange = full;
		myScrollBarOffset = from;
		myScrollBarThumbSize = to - from;
	}
	*/

	protected int computeVerticalScrollExtent() {
		final ZLView view = ZLApplication.Instance().getCurrentView();
		if (myScrollingInProgress || (myScrollingShift != 0)) {
			final int from = view.getScrollbarThumbLength(ZLView.PAGE_CENTRAL);
			final int to = view.getScrollbarThumbLength(myViewPageToScroll);
			final boolean horizontal =
				(myViewPageToScroll == ZLView.PAGE_RIGHT) || 
				(myViewPageToScroll == ZLView.PAGE_LEFT);
			final int size = horizontal ? getWidth() : getHeight();
			final int shift = Math.abs(myScrollingShift);
			return (from * (size - shift) + to * shift) / size;
		} else {
			return view.getScrollbarThumbLength(ZLView.PAGE_CENTRAL);
		}
	}

	protected int computeVerticalScrollOffset() {
		final ZLView view = ZLApplication.Instance().getCurrentView();
		if (myScrollingInProgress || (myScrollingShift != 0)) {
			final int from = view.getScrollbarThumbPosition(ZLView.PAGE_CENTRAL);
			final int to = view.getScrollbarThumbPosition(myViewPageToScroll);
			final boolean horizontal =
				(myViewPageToScroll == ZLView.PAGE_RIGHT) || 
				(myViewPageToScroll == ZLView.PAGE_LEFT);
			final int size = horizontal ? getWidth() : getHeight();
			final int shift = Math.abs(myScrollingShift);
			return (from * (size - shift) + to * shift) / size;
		} else {
			return view.getScrollbarThumbPosition(ZLView.PAGE_CENTRAL);
		}
	}

	protected int computeVerticalScrollRange() {
		final ZLView view = ZLApplication.Instance().getCurrentView();
		return view.getScrollbarFullSize();
	}
}
