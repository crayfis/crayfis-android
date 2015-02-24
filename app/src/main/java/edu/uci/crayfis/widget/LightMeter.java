package edu.uci.crayfis.widget;

/**
 * Created by danielwhiteson on 11/19/14.
 */

import edu.uci.crayfis.util.CFLog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import edu.uci.crayfis.R;


/**
 * An indicator of progress, similar to Android's ProgressBar.
 * Can be used in 'spin mode' or 'increment mode'
 *
 * @author Todd Davies
 *         <p/>
 *         Licensed under the Creative Commons Attribution 3.0 license see:
 *         http://creativecommons.org/licenses/by/3.0/
 */
public class LightMeter extends View {

    //Sizes (with defaults)
    private int layout_height = 0;
    private int layout_width = 0;

    private int barWidth = 15;
    private int textSize = 25;

    //Padding (with defaults)
    private int paddingTop = 5;
    private int paddingBottom = 5;
    private int paddingLeft = 5;
    private int paddingRight = 5;

    //Colors (with defaults)
    private int barColor = 0xAAFF0000;
    private int barColorGray = 0xAA555555;


    private int textColor = 0xFFFFFFFF;

    //Paints
    private Paint barPaint = new Paint();
    private Paint barPaintGray = new Paint();


    private Paint textPaint = new Paint();



    //Animation

    //The number of milliseconds to wait inbetween each draw
    private int delayMillis = 0;
    int progress = 0;

    //Other
    private String text = "text";

    /**
     * The constructor for the ProgressWheel
     *
     * @param context
     * @param attrs
     */
    public LightMeter(Context context, AttributeSet attrs) {
        super(context, attrs);

    }



    //----------------------------------
    //Setting up stuff
    //----------------------------------


    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // The first thing that happen is that we call the superclass
        // implementation of onMeasure. The reason for that is that measuring
        // can be quite a complex process and calling the super method is a
        // convenient way to get most of this complexity handled.
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);




    }

    /**
     * Use onSizeChanged instead of onAttachedToWindow to get the dimensions of the view,
     * because this method is called after measuring the dimensions of MATCH_PARENT & WRAP_CONTENT.
     * Use this dimensions to setup the bounds and paints.
     */
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        // Share the dimensions
        layout_width = w;
        layout_height = h;

        setupBounds();
        setupPaints();
        invalidate();
    }

    /**
     * Set the properties of the paints we're using to
     * draw the progress wheel
     */
    private void setupPaints() {
        barPaint.setColor(barColor);
        barPaint.setAntiAlias(true);
        barPaint.setStyle(Style.STROKE);
        barPaint.setStrokeWidth(barWidth);

        barPaintGray.setColor(barColorGray);
        barPaintGray.setAntiAlias(true);
        barPaintGray.setStyle(Style.STROKE);
        barPaintGray.setStrokeWidth(barWidth);



        textPaint.setColor(textColor);
        textPaint.setStyle(Style.FILL);
        textPaint.setAntiAlias(true);
        textPaint.setTextSize(textSize);


    }

    /**
     * Set the bounds of the component
     */
    private void setupBounds() {
        // Width should equal to Height, find the min value to steup the circle
        //int minValue = Math.min(layout_width, layout_height);

        // Calc the Offset if needed
        int xOffset = 0;//layout_width - minValue;
        int yOffset = 0;//layout_height - minValue;

        // Add the offset
        paddingTop = this.getPaddingTop() + (yOffset / 2);
        paddingBottom = this.getPaddingBottom() + (yOffset / 2);
        paddingLeft = this.getPaddingLeft() + (xOffset / 2);
        paddingRight = this.getPaddingRight() + (xOffset / 2);



    }



    //----------------------------------
    //Animation stuff
    //----------------------------------

    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        String text = "Light Level: Low (good)";
        int color = 0xFF00AA00;
        if (progress > 20)
        {
            text = "Light Level: High (poor)";
            color = 0xFFFF0000;
        }
        textPaint.setColor(color);
        barPaint.setColor(color);
        canvas.drawText(text,paddingLeft,paddingTop+textSize,textPaint);

        canvas.drawLine(paddingLeft,
                paddingTop+textSize+paddingTop+barWidth,
                layout_width-paddingRight,
                paddingTop+textSize+paddingTop+barWidth,
                barPaintGray);

        int barlength = (int)((layout_width-paddingLeft-paddingRight)*(progress/255.0));
       // CFLog.d(" level = "+progress+" = "+(progress/255.0)+"% drawing line length "+barlength+" from "+paddingLeft+" to "+(paddingLeft+barlength));
        canvas.drawLine(paddingLeft,
                    paddingTop+textSize+paddingTop+barWidth,
                    (paddingLeft+barlength),
                    paddingTop+textSize+paddingTop+barWidth,
                    barPaint);


    }








    /**
     * Set the progress to a specific value
     */
    public void setLevel(int i) {
        progress = i;
        //CFLog.d("Light meter level = "+i);
        postInvalidate();
    }

    //----------------------------------
    //Getters + setters
    //----------------------------------

    /**
     * Set the text in the progress bar
     * Doesn't invalidate the view
     *
     * @param text the text to show ('\n' constitutes a new line)
     */
    public void setText(String text) {
        this.text = text;
    }



    public int getTextSize() {
        return textSize;
    }

    public void setTextSize(int textSize) {
        this.textSize = textSize;

        if ( this.textPaint != null ) {
            this.textPaint.setTextSize( this.textSize );
        }
    }

    public int getPaddingTop() {
        return paddingTop;
    }

    public void setPaddingTop(int paddingTop) {
        this.paddingTop = paddingTop;
    }

    public int getPaddingBottom() {
        return paddingBottom;
    }

    public void setPaddingBottom(int paddingBottom) {
        this.paddingBottom = paddingBottom;
    }

    public int getPaddingLeft() {
        return paddingLeft;
    }

    public void setPaddingLeft(int paddingLeft) {
        this.paddingLeft = paddingLeft;
    }

    public int getPaddingRight() {
        return paddingRight;
    }

    public void setPaddingRight(int paddingRight) {
        this.paddingRight = paddingRight;
    }


    public int getTextColor() {
        return textColor;
    }

    public void setTextColor(int textColor) {
        this.textColor = textColor;

        if ( this.textPaint != null ) {
            this.textPaint.setColor( this.textColor );
        }
    }

}
