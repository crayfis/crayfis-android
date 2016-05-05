/*
 * Copyright (c) 2011-2013, Peter Abeles. All Rights Reserved.
 *
 * This file is part of BoofCV (http://boofcv.org).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package edu.uci.crayfis.camera;

import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;

import java.io.IOException;

import edu.uci.crayfis.util.CFLog;

/**
 * Displays (or hides) the camera preview.  Adjusts the camera preview so that the displayed ratio is the same
 * as the input image ratio.
 *
 * @author Peter Abeles
 */
public class CameraPreviewView extends ViewGroup implements SurfaceHolder.Callback {
	private final String TAG = "CameraPreview";

	SurfaceView mSurfaceView;
	SurfaceHolder mHolder;
	Camera mCamera;
	Camera.PreviewCallback previewCallback;
	boolean hidden;
	int n_cycle;

	public CameraPreviewView(Context context, Camera.PreviewCallback previewCallback, boolean hidden, int n_cycle) {
		super(context);
		this.previewCallback = previewCallback;
		this.hidden = hidden;
		this.n_cycle = n_cycle;

		mSurfaceView = new SurfaceView(context);
		addView(mSurfaceView);

		// Install a SurfaceHolder.Callback so we get notified when the
		// underlying surface is created and destroyed.
		mHolder = mSurfaceView.getHolder();
		mHolder.addCallback(this);
		mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
	}

	public void setCamera(Camera camera) {
		mCamera = camera;
		if (mCamera != null) {
			requestLayout();
			
			try {
	            mCamera.setPreviewDisplay(mHolder);
				if (n_cycle > 0) {
					mCamera.setPreviewCallbackWithBuffer(previewCallback);


					for (int i = 0; i < n_cycle; ++i) {
						mCamera.addCallbackBuffer(createPreviewBuffer());
					}
				} else {
					mCamera.setPreviewCallback(previewCallback);
				}
	        } catch (IOException e) {
	            e.printStackTrace();
	        }
			
			mCamera.startPreview();
		}
	}

	public byte[] createPreviewBuffer() {
		Camera.Parameters params = mCamera.getParameters();
		Camera.Size sz = params.getPreviewSize();
		int bsize = sz.height*sz.width* ImageFormat.getBitsPerPixel(params.getPreviewFormat())/8;
		return new byte[bsize+1];
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		if( hidden ) {
			this.setMeasuredDimension(2, 2);
		} else {
			// We purposely disregard child measurements because act as a
			// wrapper to a SurfaceView that centers the camera preview instead
			// of stretching it.
			final int width = resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec);
			final int height = resolveSize(getSuggestedMinimumHeight(), heightMeasureSpec);
			setMeasuredDimension(width, height);
		}

	}

	@Override
	protected void onLayout(boolean changed, int l, int t, int r, int b) {
		if( mCamera == null )
			return;

		if (changed && getChildCount() > 0) {
			final View child = getChildAt(0);

			final int width = r - l;
			final int height = b - t;

			Camera.Size size = mCamera.getParameters().getPreviewSize();
			int previewWidth = size.width;
			int previewHeight = size.height;

			// Center the child SurfaceView within the parent.
			if (width * previewHeight > height * previewWidth) {
				final int scaledChildWidth = previewWidth * height / previewHeight;
				l = (width - scaledChildWidth) / 2;
				t = 0;
				r = (width + scaledChildWidth) / 2;
				b = height;
			} else {
				final int scaledChildHeight = previewHeight * width / previewWidth;
				l = 0;
				t = (height - scaledChildHeight) / 2;
				r = width;
				b = (height + scaledChildHeight) / 2;
			}
			child.layout(l,t,r,b);
		}
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {

		// The Surface has been created, acquire the camera and tell it where
		// to draw.
		try {
			if (mCamera != null) {
				mCamera.setPreviewDisplay(holder);
			}
		} catch (IOException exception) {
			CFLog.e("DAQActivity IOException caused by setPreviewDisplay()", exception);
		}
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		// Surface will be destroyed when we return, so stop the preview.
		if (mCamera != null) {
			mCamera.stopPreview();
		}
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
		if( mCamera == null )
			return;

		// If your preview can change or rotate, take care of those events here.
		// Make sure to stop the preview before resizing or reformatting it.

		if (mHolder.getSurface() == null){
			// preview surface does not exist
			return;
		}

		// stop preview before making changes
		try {
			mCamera.stopPreview();
		} catch (Exception e){
			// ignore: tried to stop a non-existent preview
		}

		// set preview size and make any resize, rotate or
		// reformatting changes here

		// start preview with new settings
		try {
			mCamera.setPreviewDisplay(mHolder);
			if (n_cycle>0) {
				mCamera.setPreviewCallbackWithBuffer(previewCallback);
			} else {
				mCamera.setPreviewCallback(previewCallback);
			}
			mCamera.startPreview();
			CFLog.d("DAQActivity No error starting camera preview! ");
		} catch (Exception e){
			CFLog.d("DAQActivity Error starting camera preview: " + e.getMessage());
		}
	}
}
