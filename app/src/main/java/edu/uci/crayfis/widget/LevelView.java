package edu.uci.crayfis.widget;

/**
 * Created by danielwhiteson on 3/6/15.
 */

import android.content.Context;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Handler;
import android.util.AttributeSet;
import android.widget.ImageView;

import edu.uci.crayfis.CFApplication;
import edu.uci.crayfis.R;
import edu.uci.crayfis.util.CFLog;

import edu.uci.crayfis.CFConfig;

public class LevelView extends ImageView {

    private static final CFConfig CONFIG = CFConfig.getInstance();
    private Handler h;
    private final int FRAME_RATE = 1000; // update delay in ms

    private int textColor = 0xFFFFFFFF;
    private int textSize = 30;

    private Paint textPaint = new Paint();
    private Paint bgPaint = new Paint();

    final int nLevels=5;
    float thresh[] = new float[nLevels];
    String names[] = new String[nLevels];

    private Bitmap level_iconr[] = new Bitmap[nLevels];
    private Bitmap level_icon=null;
    private Bitmap next_icon=null;
    private int icons[] = new int[nLevels];
    int current_level;


    public LevelView(Context context, AttributeSet attrs)  {
        super(context, attrs);
        h = new Handler();


        textPaint.setColor(textColor);
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setAntiAlias(true);
        textPaint.setTextSize(textSize);
        textPaint.setShadowLayer(4, 3, 3, 0xFF000000);

        thresh[0]= (float)0.;
        thresh[1]= (float)100.;
        thresh[2]= (float)1000;
        thresh[3]= (float)10000;
        thresh[4]= (float)100000000;

        names[0]= "New user";
        names[1]= "Beginner";
        names[2]= "Amateur";
        names[3]= "Astrophysicst";
        names[4]= "God";

        icons[0] =R.drawable.welcome;
        icons[1] =R.drawable.baby;
        icons[2] =R.drawable.astronomer;
        icons[3] =R.drawable.astrophysicist;
        icons[4] =R.drawable.expert;


        for (int i=0;i<nLevels;i++)
           level_iconr[i] = BitmapFactory.decodeResource(getResources(), icons[i]);
        current_level = -1;
       // CFLog.d(" make new Levelsview");
    }

    int updateLevel()
    {
        int level=0;
        float score = CONFIG.getAccountScore();
        if (CONFIG.getAccountName() != null) {
            for (; thresh[level+1] < score && level < nLevels; level++) ;
        }
        return level;
    }

    private void updateIcons()
    {

        int new_level = updateLevel();
        if (new_level != current_level)
        {
            current_level = new_level;
            level_icon = level_iconr[current_level].createScaledBitmap(level_iconr[current_level],
                            getWidth(),(int)(getHeight()*0.9),false);
            if (current_level+1 < nLevels) {
                next_icon = level_iconr[current_level+1].createScaledBitmap(level_iconr[current_level+1], (int)(getHeight()*0.1),(int)(getHeight()*0.1), false);
            } else { next_icon = null; }


        }
    }

    private Runnable r = new Runnable() {
        @Override
        public void run() {
            invalidate();
        }
    };

    protected void onDraw(Canvas c) {
        super.onDraw(c);
        final CFApplication application = (CFApplication) getContext().getApplicationContext();

        final CFApplication.State currentState = application.getApplicationState();


        updateIcons();

       // CFLog.d(" level = "+current_level+ " icons are "+level_icon+", "+next_icon);
            c.drawBitmap(level_icon, 0, 0, bgPaint);
            if (next_icon != null)
                c.drawBitmap(next_icon, 0, (int) (getHeight() * 0.9), bgPaint);

        String text = String.format("Current Level: %s",names[current_level]);
        c.drawText(text, getWidth()/4,
                    textSize+20, textPaint);

            String text_next = String.format("Next level (%s) requires: %1.1f",names[current_level+1],thresh[current_level+1]);
            c.drawText(text_next,(int)(getHeight()*0.1)+25,(int)(getHeight()*0.9)+textSize+5,textPaint);
//            CFLog.d(" Levelsview drawing text: " + text);

        h.postDelayed(r, FRAME_RATE);

    }
}
