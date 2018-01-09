package io.crayfis.android.ui.navdrawer.live_view;

/**
 * Created by danielwhiteson on 1/30/15.
 */

import io.crayfis.android.DataProtos;
import io.crayfis.android.camera.CFCamera;

import android.graphics.Paint;
import android.content.Context;
import android.graphics.Canvas;
import android.os.Handler;
import android.support.v7.widget.AppCompatImageView;
import android.util.AttributeSet;
import android.graphics.RectF;

import android.graphics.Color;

class SplashView extends AppCompatImageView
{
    private Handler h;
    private static final int FRAME_RATE = 30;

    private Paint circlePaint;
    private Paint ringPaint;
    private RectF circleRect;
    private RectF ringRect;


    SplashView(Context context, AttributeSet attrs)  {
        super(context, attrs);
        h = new Handler();

       // CFLog.d("new Splashview created!"+this);

        circlePaint = new Paint();
        circlePaint.setColor(Color.WHITE);
        circlePaint.setAntiAlias(true);
        circlePaint.setStyle(Paint.Style.FILL);

        ringPaint = new Paint();
        ringPaint.setColor(Color.WHITE);
        ringPaint.setAntiAlias(true);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(3);
        this.setWillNotDraw(false);

        circleRect = new RectF();
        ringRect = new RectF();


    }

    private Runnable r = new Runnable() {
        @Override
        public void run() {
            invalidate();
        }
    };
    protected void onDraw(Canvas c)
    {
        super.onDraw(c);
        // draw it
        //CFLog.d(" SplashView onDraw()");

        // c.getWidth() c.getHeight();

        float scale_x =1;
        float scale_y =1;




        // make sure the event list is not modified while we loop over it
        synchronized(LayoutLiveView.event_lock) {

            if (LayoutLiveView.events.size()>0)
            {
                int maxCanvasX = c.getWidth();
                int maxCanvasY = c.getHeight();

                scale_x = maxCanvasY / ((float) 1.1 * CFCamera.getInstance().getResX());  // 1.1 to avoid off screen edge effects
                scale_y = maxCanvasX / ((float) 1.1 * CFCamera.getInstance().getResY());
                //CFLog.d("Splashview camera = "+maxCameraX+","+maxCameraY+" canvas = "+maxCanvasX+", "+maxCanvasY+" scaling = "+scale_x+", "+scale_y);
            }

            // loop over events
            for (DataProtos.Event event: LayoutLiveView.events) {
                // get the event and pixels

                // calculate the event age
                long age = (System.currentTimeMillis() - event.getTimestamp());

                // remove it if it's too old
                float ms_to_show = (float) 10000.0;
                if (age <= ms_to_show) {
                    for(DataProtos.Pixel pix : event.getPixelsList()) {
                        int x = (int)(scale_y*pix.getY());
                        int y = (int)(scale_x*pix.getX());

                        int size = 8 + (int) Math.sqrt(pix.getVal());
                        int trans = (int) (255 * (1.0 - (age / ms_to_show)));
                        if (trans < 0) trans = 0;
                        circleRect.set(x - size, y - size, x + size, y + size);
                        circlePaint.setARGB(trans, 255, 255, 255);

                        int ring_size = size + (int) (age / 20.0); // 50 pixels/second
                        ringPaint.setARGB(trans, 255, 255, 255);
                        ringRect.set(x - ring_size, y - ring_size, x + ring_size, y + ring_size);

                        //CFLog.d(" SplashView will draw pixel at pixel "+p.x+","+p.y+" screen " + x + ", " + y + " age = " + age + " trans=" + trans + " val=" + p.val + " size=" + size);

                        // draw a circle at the hit
                        c.drawArc(circleRect, 0, 360, true, circlePaint);
                        // draw a ring around the circle
                        c.drawArc(ringRect, 0, 360, true, ringPaint);

                    }
                }
            }
        }
        h.postDelayed(r, FRAME_RATE);
    }
}
