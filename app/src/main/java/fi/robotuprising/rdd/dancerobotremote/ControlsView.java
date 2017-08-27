package fi.robotuprising.rdd.dancerobotremote;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.LinearLayout;

public class ControlsView extends LinearLayout {
    public int width;
    public int height;
    public float origo;
    public float power;

	public ControlsView(Context context) {
		super(context);
	}

    public ControlsView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ControlsView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }
	
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        
        width = w;
        height = h;
        origo = height / 2f;
        power = height / 2f;
    }

}
