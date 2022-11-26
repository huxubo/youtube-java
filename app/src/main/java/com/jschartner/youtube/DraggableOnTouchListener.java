package com.jschartner.youtube;

import android.view.MotionEvent;
import android.view.View;

public class DraggableOnTouchListener implements View.OnTouchListener {
    private float dX;
    private float dY ;
    private int lastAction;
    private boolean halt;
    private View.OnTouchListener onTouchListener;

    public DraggableOnTouchListener(View.OnTouchListener OnTouchListener) {
        this.onTouchListener = OnTouchListener;
    }

    public DraggableOnTouchListener() {
        this.onTouchListener = null;
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        switch (event.getActionMasked()) {
	case MotionEvent.ACTION_DOWN:
	    dX = v.getX() - event.getRawX();
	    dY = v.getY() - event.getRawY();
	    lastAction = MotionEvent.ACTION_DOWN;
	    break;

	case MotionEvent.ACTION_MOVE:
	    v.setY(event.getRawY() + dY);
	    v.setX(event.getRawX() + dX);
	    lastAction = MotionEvent.ACTION_MOVE;
	    break;

	case MotionEvent.ACTION_UP:
	    //ON CLICK
	    if (lastAction == MotionEvent.ACTION_DOWN) {
		if(onTouchListener != null) {
		    return onTouchListener.onTouch(v, event);
		}
	    }
	    //ON STOP DRAG
	    else {
	    }

	    break;

	default:
	    return false;
        }

        return false;
    }
}
