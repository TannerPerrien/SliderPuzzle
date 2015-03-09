package com.droiddevil.sliderpuzzle;

import android.content.Context;
import android.widget.ImageView;

/**
 * Represents an individual tile for the puzzle board.
 * 
 * @author tperrien
 * 
 */
public class SliderPuzzleTile extends ImageView {

    private int startPosition;

    public SliderPuzzleTile(final Context context, final int startPosition) {
        super(context);
        this.startPosition = startPosition;
    }

    public int getStartPosition() {
        return startPosition;
    }

    @Override
    public String toString() {
        return "Start position: " + startPosition;
    }
}
