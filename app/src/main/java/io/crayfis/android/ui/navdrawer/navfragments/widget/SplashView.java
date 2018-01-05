package io.crayfis.android.ui.navdrawer.navfragments.widget;

/**
 * Created by danielwhiteson on 1/30/15.
 */

import android.hardware.Camera;
import io.crayfis.android.ui.navdrawer.navfragments.LayoutLiveView;

import android.graphics.Paint;
import android.content.Context;
import android.graphics.Canvas;
import android.os.Handler;
import android.support.v7.widget.AppCompatImageView;
import android.util.AttributeSet;
import android.graphics.RectF;

import java.util.ArrayList;
import java.util.Iterator;

import android.graphics.Color;
import android.util.Pair;

import static io.crayfis.android.trigger.L2Task.*;
import static io.crayfis.android.ui.navdrawer.navfragments.LayoutLiveView.*;
import io.crayfis.android.trigger.L2Task;

public class SplashView extends AppCompatImageView
{
    private Context mContext;
    private Handler h;
    private final int FRAME_RATE = 30;

    private Paint circlePaint;
    private Paint ringPaint;
    private RectF circleRect;
    private RectF ringRect;

    // this holds the data, since its static
    private LayoutLiveView mLayoutBlack = getInstance();

    public SplashView(Context context, AttributeSet attrs)  {
        super(context, attrs);
        mContext = context;
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
        synchronized(mLayoutBlack.event_lock) {

            if (mLayoutBlack != null && mLayoutBlack.previewSize != null && mLayoutBlack.events.size()>0)
            {
                Camera.Size pv = mLayoutBlack.previewSize;
                int maxCameraX = pv.width;
                int maxCameraY = pv.height;

                int maxCanvasX = c.getWidth();
                int maxCanvasY = c.getHeight();

                scale_x = maxCanvasY / ((float) 1.1 * maxCameraX);  // 1.1 to avoid off screen edge effects
                scale_y = maxCanvasX / ((float) 1.1 * maxCameraY);
                //CFLog.d("Splashview camera = "+maxCameraX+","+maxCameraY+" canvas = "+maxCanvasX+", "+maxCanvasY+" scaling = "+scale_x+", "+scale_y);
            } else {
                //CFLog.d("The splashView does not have the camera size "+mLayoutBlack);
            }

            Iterator<Pair<Long, ArrayList<L2Task.RecoPixel>>> iterator = mLayoutBlack.events.iterator();
            // loop over events
            for (iterator = mLayoutBlack.events.iterator(); iterator.hasNext();) {
                // get the event and pixels

                Pair<Long, ArrayList<L2Task.RecoPixel>> event = iterator.next();
                long event_time = event.first;
                //CFLog.d(" SplashView draw event with "+pixels.size()+ " from time "+mLayoutBlack.events.get(ie).time);

                // calculate the event age
                long age = (System.currentTimeMillis() - event_time);

                // remove it if it's too old
                float ms_to_show = (float) 10000.0;
                if (age > ms_to_show) {
                    //CFLog.d("Splashview removing event of age " + age + " from time " + event_time);
                    iterator.remove();
                } else {
                    Iterator<L2Task.RecoPixel> pixelIterator = event.second.iterator();
                    while(pixelIterator.hasNext()) {
                        RecoPixel p = pixelIterator.next();
                        int x = (int)(scale_y*p.getY());
                        int y = (int)(scale_x*p.getX());

                        int size = 8 + (int) Math.sqrt(p.getVal());
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
