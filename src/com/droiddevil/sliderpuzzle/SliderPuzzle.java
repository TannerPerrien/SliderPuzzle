package com.droiddevil.sliderpuzzle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.VelocityTracker;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;

/**
 * <p>
 * The game board for a slider puzzle. As it stands, this puzzle can take up any
 * amount of space in a view, but the puzzle tiles are forced to remain square.
 * A small amount of modification would allow the width/height of tiles to be
 * disconnected.
 * </p>
 * 
 * <p>
 * The puzzle is not automatically created. Instead, createPuzzle() should be
 * called from users of this view.
 * </p>
 * 
 * @author tperrien
 * 
 */
public class SliderPuzzle extends ViewGroup {

    /** Crop the image to fit on the tiles */
    public static final int IMAGE_MODE_CROP = 0;

    /** Stretch the provided image across the tiles */
    public static final int IMAGE_MODE_STRETCH = 1;

    /** Length of animation */
    private static final long ANIMATION_DURATION = 100;

    /** Tap velocity threshold */
    private static final float TAP_VELOCITY = 100.0f;

    /** Max time to be considered a tap */
    private static final long MAX_TAP_THRESHOLD = 200;

    /** Fling velocity threshold */
    private static final float FLING_VELOCITY = 200.0f;

    /** Fling velocity units */
    private static final int VELOCITY_UNITS = 1000;

    /** Default board size */
    private static final int DEFAULT_SIZE = 3;

    /** Default image **/
    private static final int DEFAULT_IMAGE_ID = R.drawable.globe;

    /** The default number of random moves to make when initializing the tiles */
    private static final int DEFAULT_RANDOM_MOVES = 15;

    /** The puzzle tiles */
    private List<SliderPuzzleTile> tiles;

    /** The size of the puzzle */
    private int puzzleSize;

    /** The bitmap resource used to generate tile images */
    private int puzzleBitmapResource;

    /** The width of the puzzle bitmap */
    private int bitmapWidth;

    /** The height of the puzzle bitmap */
    private int bitmapHeight;

    /** The image mode describing how to handle the tile bitmap image */
    private int imageMode;

    /** The number of random moves to make when initializing the tiles */
    private int randomMoves;

    /** The size of an individual tile */
    private int tileSize;

    /** The index position of the open space on the puzzle */
    private int emptyIndex;

    /** True when the board is ready for play */
    private boolean initialized;

    /** True when tracking user touch events */
    private boolean tracking;

    /** The bounds of the tiles on the puzzle */
    private Rect tileBounds = new Rect();

    /** Random number generator */
    private Random random = new Random();

    /** True if a tile move should finish */
    private boolean shouldComplete;

    /** Used to track touch velocity */
    private VelocityTracker velocityTracker;

    /** Time when the screen was first touched */
    private long tapStartTime;

    /** Computed velocity units */
    private int velocityUnits;

    /** Maximum tap threshold */
    private int tapVelocityMaximum;

    /** The screen x-position of the first DOWN touch event */
    private float startX;

    /** The screen y-position of the first DOWN touch event */
    private float startY;

    /** The delta between the first x-position and the left edge of the tile */
    private int touchDeltaX;

    /** The delta between the first y-position and the top edge of the tile */
    private int touchDeltaY;

    /** True when tiles are animating */
    private boolean animating;

    /** The list of animating tiles */
    private List<SliderPuzzleTile> animatingTiles = new ArrayList<SliderPuzzleTile>();

    /** The listener for puzzle events */
    private SliderPuzzleListener puzzleListener;

    /**
     * Handler used to stop animation and finish putting tiles in their proper
     * location.
     */
    private Handler animationHandler = new Handler() {
        @Override
        public void handleMessage(final Message msg) {
            // Stop animating tiles and empty the list
            for (SliderPuzzleTile tile : animatingTiles) {
                tile.clearAnimation();
            }
            animatingTiles.clear();

            // If the tile movement should complete, simulate a touch event
            if (shouldComplete) {
                touchTile(getIndexForXY((int) startX, (int) startY));

                // Check if the puzzle is solved
                if (isPuzzleSolved() && puzzleListener != null) {
                    initialized = false;
                    addView(tiles.get(emptyIndex));
                    puzzleListener.onPuzzleSolved();
                }
            }

            requestLayout();
            invalidate();
            animating = false;
        }
    };

    /**
     * Constructor.
     * 
     * @param context
     *            The context.
     */
    public SliderPuzzle(final Context context) {
        super(context);
        init(null);
    }

    /**
     * Constructor.
     * 
     * @param context
     *            The context.
     * @param attrs
     *            Attributes.
     */
    public SliderPuzzle(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    /**
     * Constructor.
     * 
     * @param context
     *            The context.
     * @param attrs
     *            The attributes.
     * @param defStyle
     *            The style.
     */
    public SliderPuzzle(final Context context, final AttributeSet attrs,
            final int defStyle) {
        super(context, attrs, defStyle);
        init(attrs);
    }

    /**
     * Set the puzzle listener to listen for various puzzle events.
     * 
     * @param listener
     *            The listener to set.
     */
    public void setPuzzleListener(final SliderPuzzleListener listener) {
        puzzleListener = listener;
    }

    /**
     * Set the image for the tiles.
     * 
     * @param bitmapResource
     *            The tiles image resource.
     */
    public void setPuzzleTileImage(final int bitmapResource) {
        puzzleBitmapResource = bitmapResource;
    }

    /**
     * Set the mode for the tile bitmap. Crop will crop and center the image.
     * Stretch will use the entire image over the tiles.
     * 
     * @param mode
     *            IMAGE_MODE_CROP or IMAGE_MODE_STRETCH
     */
    public void setPuzzleImageMode(final int mode) {
        imageMode = mode;
    }

    /**
     * Set the number of random moves to make when the puzzle tiles are
     * initialized.
     * 
     * @param moves
     *            The number of random moves to make.
     */
    public void setPuzzleRandomMoves(final int moves) {
        randomMoves = moves;
    }

    /**
     * Set the size of the puzzle. For example, a puzzle of size 3 will be a 3x3
     * puzzle with 9 tiles.
     * 
     * NOTE: Setting the puzzle size will force the puzzle to be recreated.
     * 
     * @param size
     *            The size of the puzzle.
     */
    public void setPuzzleSize(final int size) {
        // The puzzle size is a value used throughout the puzzle, so tiles need
        // to be recreated
        puzzleSize = size;
        createTiles();
    }

    /**
     * Build the puzzle. This is useful as a way to restart the puzzle or force
     * the puzzle to be rebuilt with new parameters.
     */
    public void createPuzzle() {
        createTiles();
    }

    public void restoreState() {

    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * android.view.ViewGroup#onInterceptTouchEvent(android.view.MotionEvent)
     */
    @Override
    public boolean onInterceptTouchEvent(final MotionEvent event) {
        int action = event.getAction();
        float x = event.getX();
        float y = event.getY();

        // Ignore events before initialization is complete
        if (!initialized) {
            return false;
        }

        // Ignore events while animating
        if (animating) {
            return false;
        }

        // Ignore touch events outside the game board
        if (!tileBounds.contains((int) x, (int) y)) {
            return false;
        }

        // Ignore touch events on the open slot
        if (getIndexForXY((int) x, (int) y) == emptyIndex) {
            return false;
        }

        // Capture DOWN and save touch position
        if (action == MotionEvent.ACTION_DOWN) {
            velocityTracker = VelocityTracker.obtain();
            velocityTracker.addMovement(event);

            tapStartTime = System.currentTimeMillis();

            tracking = true;
            startX = x;
            startY = y;

            SliderPuzzleTile tile = tiles.get(getIndexForXY((int) x, (int) y));
            touchDeltaX = (int) (x - tile.getLeft());
            touchDeltaY = (int) (y - tile.getTop());
        }

        return true;
    }

    /*
     * (non-Javadoc)
     * 
     * @see android.view.View#onTouchEvent(android.view.MotionEvent)
     */
    @Override
    public boolean onTouchEvent(final MotionEvent event) {
        if (tracking) {
            int action = event.getAction();

            switch (action) {
            case MotionEvent.ACTION_MOVE:
                float x = event.getX();
                float y = event.getY();

                int tileIndex = getIndexForXY((int) startX, (int) startY);
                moveTile(tileIndex, (int) x, (int) y);
                velocityTracker.addMovement(event);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                tracking = false;

                // Detect a fling and set shouldComplete accordingly
                velocityTracker.computeCurrentVelocity(velocityUnits);
                float yVelocity = velocityTracker.getYVelocity();
                float xVelocity = velocityTracker.getXVelocity();
                float velocity = (float) Math.hypot(xVelocity, yVelocity);

                long now = System.currentTimeMillis();
                if (velocity < tapVelocityMaximum
                        && now - tapStartTime < MAX_TAP_THRESHOLD) {
                    shouldComplete = true;
                    playSoundEffect(SoundEffectConstants.CLICK);
                } else {
                    shouldComplete |= velocity > FLING_VELOCITY;
                }

                // Clean up velocity tracker
                if (velocityTracker != null) {
                    velocityTracker.recycle();
                    velocityTracker = null;
                }

                // Start tile animation
                slideTiles(getIndexForXY((int) startX, (int) startY));

                // Prepare to stop animation and force tiles into final position
                animationHandler.sendEmptyMessageDelayed(0, ANIMATION_DURATION);
                break;
            }
        }

        return tracking || super.onTouchEvent(event);
    }

    /*
     * (non-Javadoc)
     * 
     * @see android.view.View#onRestoreInstanceState(android.os.Parcelable)
     */
    @Override
    protected void onRestoreInstanceState(final Parcelable state) {
        if (state instanceof Bundle) {
            Bundle b = (Bundle) state;
            super.onRestoreInstanceState(b.getParcelable("super"));
            puzzleSize = b.getInt("puzzleSize");
            puzzleBitmapResource = b.getInt("puzzleBitmap");
            randomMoves = b.getInt("puzzleRandomMoves");

            createTiles();
            emptyIndex = b.getInt("puzzleEmptyIndex");

            // Add the tiles back in the correct order
            int[] tilePositions = b.getIntArray("puzzleTilePositions");
            SliderPuzzleTile[] newTiles = new SliderPuzzleTile[tiles.size()];
            for (SliderPuzzleTile tile : tiles) {
                int newPosition = tilePositions[tile.getStartPosition()];
                newTiles[newPosition] = tile;
            }
            tiles = Arrays.asList(newTiles);

            removeAllViews();
            for (SliderPuzzleTile tile : tiles) {
                addView(tile);
            }
            if (!isPuzzleSolved()) {
                removeView(tiles.get(emptyIndex));
            }
            requestLayout();
        } else {
            super.onRestoreInstanceState(state);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see android.view.View#onSaveInstanceState()
     */
    @Override
    protected Parcelable onSaveInstanceState() {
        int[] tilePositions = new int[tiles.size()];
        for (int i = 0; i < tiles.size(); i++) {
            tilePositions[tiles.get(i).getStartPosition()] = i;
        }

        Bundle bundle = new Bundle();
        bundle.putParcelable("super", super.onSaveInstanceState());
        bundle.putInt("puzzleSize", puzzleSize);
        bundle.putInt("puzzleBitmap", puzzleBitmapResource);
        bundle.putInt("puzzleImageMode", imageMode);
        bundle.putInt("puzzleRandomMoves", randomMoves);
        bundle.putInt("puzzleEmptyIndex", emptyIndex);
        bundle.putIntArray("puzzleTilePositions", tilePositions);

        return bundle;
    }

    /*
     * (non-Javadoc)
     * 
     * @see android.view.View#onMeasure(int, int)
     */
    @Override
    protected void onMeasure(final int widthMeasureSpec,
            final int heightMeasureSpec) {
        final int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        final int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        final int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        final int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        int width;
        int height;

        if (widthMode == MeasureSpec.EXACTLY
                && heightMode == MeasureSpec.EXACTLY) {
            // Make puzzle an exact size
            width = widthSize;
            height = heightSize;
        } else {
            // Keep the puzzle square
            width = Math.min(widthSize, bitmapWidth);
            height = Math.min(heightSize, bitmapHeight);

            width = Math.min(width, height);
            height = Math.min(width, height);
        }

        setMeasuredDimension(width, height);

        tileSize = Math.min(width, height) / puzzleSize;
    }

    /*
     * (non-Javadoc)
     * 
     * @see android.view.ViewGroup#onLayout(boolean, int, int, int, int)
     */
    @Override
    protected void onLayout(final boolean changed, final int l, final int t,
            final int r, final int b) {
        if (tracking) {
            return;
        }

        for (int i = 0; i < tiles.size(); i++) {
            int left = getTileLeft(i);
            int top = getTileTop(i);
            int right = left + tileSize;
            int bottom = top + tileSize;

            SliderPuzzleTile tile = tiles.get(i);
            tile.layout(left, top, right, bottom);

            // Store tile bounds
            Rect bounds = new Rect();
            tile.getHitRect(bounds);
            tileBounds.union(bounds);
        }
    }

    /**
     * Initialize the game board.
     */
    private void init(final AttributeSet attrs) {
        // Set screen density related items
        final float density = getResources().getDisplayMetrics().density;
        tapVelocityMaximum = (int) (TAP_VELOCITY * density + 0.5f);
        velocityUnits = (int) (VELOCITY_UNITS * density + 0.5f);

        // Set default values
        puzzleSize = DEFAULT_SIZE;
        puzzleBitmapResource = DEFAULT_IMAGE_ID;
        imageMode = IMAGE_MODE_CROP;
        randomMoves = DEFAULT_RANDOM_MOVES;

        // Attempt to set attribute values
        if (attrs != null) {
            TypedArray a = getContext().obtainStyledAttributes(attrs,
                    R.styleable.SliderPuzzle);
            puzzleSize = a.getInt(R.styleable.SliderPuzzle_size, DEFAULT_SIZE);
            puzzleBitmapResource = a.getResourceId(
                    R.styleable.SliderPuzzle_image, DEFAULT_IMAGE_ID);
            imageMode = a.getInt(R.styleable.SliderPuzzle_imageMode, imageMode);
            randomMoves = a.getInt(R.styleable.SliderPuzzle_randomMoves,
                    DEFAULT_RANDOM_MOVES);
            a.recycle();
        }
    }

    /**
     * Creates and adds the tiles for the puzzle. When complete, tiles will be
     * randomized based on the set random moves.
     */
    private void createTiles() {
        // Make sure tiles are cleaned up
        removeAllViews();

        int leftCenterOffset = 0;
        int topCenterOffset = 0;
        int tileBmpWidth = 0;
        int tileBmpHeight = 0;

        Bitmap bitmap = BitmapFactory.decodeResource(getResources(),
                puzzleBitmapResource);
        bitmapWidth = bitmap.getWidth();
        bitmapHeight = bitmap.getHeight();

        // Setup image mode
        if (imageMode == IMAGE_MODE_CROP) {
            if (bitmapWidth > bitmapHeight) {
                leftCenterOffset = (bitmapWidth - bitmapHeight) / 2;
            } else {
                topCenterOffset = (bitmapHeight - bitmapWidth) / 2;
            }

            // Find tile width/height
            int length = Math.min(bitmapWidth, bitmapHeight);
            tileBmpWidth = (int) ((float) length / puzzleSize);
            tileBmpHeight = (int) ((float) length / puzzleSize);
        } else if (imageMode == IMAGE_MODE_STRETCH) {
            // Get the floor of the width/height
            tileBmpWidth = (int) ((float) bitmapWidth / puzzleSize);
            tileBmpHeight = (int) ((float) bitmapHeight / puzzleSize);
        }

        // Create tiles
        tiles = new ArrayList<SliderPuzzleTile>(puzzleSize * puzzleSize);
        for (int i = 0; i < puzzleSize * puzzleSize; i++) {
            int left = (i % puzzleSize) * tileBmpWidth + leftCenterOffset;
            int top = (i / puzzleSize) * tileBmpHeight + topCenterOffset;
            Bitmap tileBitmap = Bitmap.createBitmap(bitmap, left, top,
                    tileBmpWidth, tileBmpHeight);

            SliderPuzzleTile tile = new SliderPuzzleTile(getContext(), i);
            tile.setBackgroundDrawable(new BitmapDrawable(tileBitmap));
            tiles.add(tile);

            addView(tile);
        }

        // Free up memory
        bitmap.recycle();
        bitmap = null;

        // Remove one tile
        emptyIndex = tiles.size() - 1;
        removeView(tiles.get(emptyIndex));

        // Randomize the puzzle
        randomizeTiles(randomMoves);

        // Set the board as being ready for play
        initialized = true;
    }

    /**
     * Randomizes the tiles on the board by alternating between left/right and
     * up/down movements so that one move never undoes another.
     * 
     * @param moves
     *            The number of moves to make.
     */
    private void randomizeTiles(final int moves) {
        int orientation = 0;
        for (int i = 0; i < moves; i++) {
            int col = getColumn(emptyIndex);
            int row = getRow(emptyIndex);

            // Alternate every other
            if (orientation++ % 2 == 0) {
                // Move a tile on the left side
                if (random.nextBoolean() && col > 0 || col == puzzleSize - 1) {
                    touchTile(getIndexForColRow(random.nextInt(col), row));
                } else {
                    // Move a tile on the right side
                    int randRightCol = getRandom(col + 1, puzzleSize - 1);
                    touchTile(getIndexForColRow(randRightCol, row));
                }
            } else {
                // Move a tile on the top side
                if (random.nextBoolean() && row > 0 || row == puzzleSize - 1) {
                    touchTile(getIndexForColRow(col, random.nextInt(row)));
                } else {
                    // Move a tile on the bottom side
                    int randLowerRow = getRandom(row + 1, puzzleSize - 1);
                    touchTile(getIndexForColRow(col, randLowerRow));
                }
            }
        }
    }

    /**
     * Handle a touch on the given tile index. This will rearrange tiles in the
     * list and request a new layout pass so that the game board will be
     * updated.
     * 
     * @param index
     *            The index of the touched tile.
     */
    private void touchTile(final int index) {
        boolean moved = false;

        if (getColumn(index) == getColumn(emptyIndex)) {
            moved = true;

            // up
            for (int i = emptyIndex; i < index; i += puzzleSize) {
                swapTiles(i, i + puzzleSize);
            }

            // down
            for (int i = emptyIndex; i > index; i -= puzzleSize) {
                swapTiles(i, i - puzzleSize);
            }
        } else if (getRow(index) == getRow(emptyIndex)) {
            moved = true;

            // left
            for (int i = emptyIndex; i < index; i++) {
                swapTiles(i, i + 1);
            }

            // right
            for (int i = emptyIndex; i > index; i--) {
                swapTiles(i, i - 1);
            }
        }

        // Update empty tile location and refresh layout when a move was made
        if (moved) {
            emptyIndex = index;
            requestLayout();
        }
    }

    /**
     * Moves a tile based on the x/y coordinates. This is used strictly as a way
     * to move the tile(s) in correspondence with touch movement.
     * 
     * @param index
     *            The index of the tile to move.
     * @param x
     *            The x position.
     * @param y
     *            The y position.
     */
    private void moveTile(final int index, final int x, final int y) {
        // Compute deltas
        int dx = (int) (startX - x);
        int dy = (int) (startY - y);

        int offset;

        if (getColumn(index) == getColumn(emptyIndex)) { // Same column
            shouldComplete = Math.abs(dy) > tileSize / 2;

            // Move tiles to their max position when the x/y coordinates exceed
            // allowed movement
            if (Math.abs(dy) < tileSize) {
                offset = y - touchDeltaY - tiles.get(index).getTop();
            } else {
                int startTop = (int) startY - touchDeltaY;
                int tileSizeOffset = emptyIndex < index ? -tileSize : tileSize;
                offset = startTop - tiles.get(index).getTop() + tileSizeOffset;
            }

            // Accept upward movement
            if (emptyIndex < index && dy > 0) {
                for (int i = index; i > emptyIndex; i -= puzzleSize) {
                    tiles.get(i).offsetTopAndBottom(offset);
                }
            } else if (emptyIndex > index && dy < 0) {
                // Accept downward movement
                for (int i = index; i < emptyIndex; i += puzzleSize) {
                    tiles.get(i).offsetTopAndBottom(offset);
                }
            }
        } else if (getRow(index) == getRow(emptyIndex)) { // Same row
            shouldComplete = Math.abs(dx) > tileSize / 2;

            // Move tiles to their max position when the x/y coordinates exceed
            // allowed movement
            if (Math.abs(dx) < tileSize) {
                offset = x - touchDeltaX - tiles.get(index).getLeft();
            } else {
                int startLeft = (int) startX - touchDeltaX;
                int tileSizeOffset = emptyIndex < index ? -tileSize : tileSize;
                offset = startLeft - tiles.get(index).getLeft()
                        + tileSizeOffset;
            }

            // Accept leftward movement
            if (emptyIndex < index && dx > 0) {
                for (int i = index; i > emptyIndex; i--) {
                    tiles.get(i).offsetLeftAndRight(offset);
                }
            } else if (emptyIndex > index && dx < 0) {
                // Accept rightward movement
                for (int i = index; i < emptyIndex; i++) {
                    tiles.get(i).offsetLeftAndRight(offset);
                }
            }
        }

        // Repaint screen
        invalidate();
    }

    /**
     * Animate tile(s) for the given tile index.
     * 
     * @param index
     *            The index of the tile to animate.
     */
    private void slideTiles(final int index) {
        animating = true;
        int offset;

        if (getColumn(index) == getColumn(emptyIndex)) { // Same column
            int posDiff = shouldComplete ? puzzleSize : 0;

            // Move up
            if (emptyIndex < index) {
                int newPos = getTileTop(index - posDiff);
                offset = newPos - tiles.get(index).getTop();
                for (int i = index; i > emptyIndex; i -= puzzleSize) {
                    animateTile(i, 0, offset);
                }
            } else {
                // Move down
                int newPos = getTileTop(index + posDiff);
                offset = newPos - tiles.get(index).getTop();
                for (int i = index; i < emptyIndex; i += puzzleSize) {
                    animateTile(i, 0, offset);
                }
            }
        } else if (getRow(index) == getRow(emptyIndex)) { // Same row
            int posDiff = shouldComplete ? 1 : 0;

            // Move left
            if (emptyIndex < index) {
                int newPos = getTileLeft(index - posDiff);
                offset = newPos - tiles.get(index).getLeft();
                for (int i = index; i > emptyIndex; i--) {
                    animateTile(i, offset, 0);
                }
            } else {
                // Move right
                int newPos = getTileLeft(index + posDiff);
                offset = newPos - tiles.get(index).getLeft();
                for (int i = index; i < emptyIndex; i++) {
                    animateTile(i, offset, 0);
                }
            }
        }
    }

    /**
     * Set and start an animation on the given tile index.
     * 
     * @param index
     *            The index of the tile to set/start the animation on.
     * @param xOffset
     *            The x offset.
     * @param yOffset
     *            The y offset.
     */
    private void animateTile(final int index, final int xOffset,
            final int yOffset) {
        Animation animation = new TranslateAnimation(0, xOffset, 0, yOffset);
        animation.setDuration(ANIMATION_DURATION);
        animation.setFillAfter(true);
        animation.setFillEnabled(true);

        SliderPuzzleTile tile = tiles.get(index);
        animatingTiles.add(tile);
        tile.startAnimation(animation);
    }

    /**
     * Determine whether or not the puzzle has been solved.
     * 
     * @return True if the puzzle is solved.
     */
    private boolean isPuzzleSolved() {
        for (int i = 0; i < tiles.size(); i++) {
            if (tiles.get(i).getStartPosition() != i) {
                return false;
            }
        }
        return true;
    }

    /**
     * Get the tile index for a given x/y coordinate.
     * 
     * @param x
     *            The x position.
     * @param y
     *            The y position.
     * @return The tile index for the given x/y.
     */
    private int getIndexForXY(final int x, final int y) {
        int col = 0;
        int row = 0;

        for (int i = 0; i < puzzleSize; i++) {
            if (x < i * tileSize + tileSize) {
                col = i;
                break;
            }
        }

        for (int i = 0; i < puzzleSize; i++) {
            if (y < i * tileSize + tileSize) {
                row = i;
                break;
            }
        }

        return row * puzzleSize + col;
    }

    /**
     * Get the tile index for the given column/row.
     * 
     * @param col
     *            The column.
     * @param row
     *            The row.
     * @return The tile index for the given column/row.
     */
    private int getIndexForColRow(final int col, final int row) {
        return col + (row * puzzleSize);
    }

    /**
     * Get the column for the given tile index.
     * 
     * @param index
     *            The tile index.
     * @return The column value.
     */
    private int getColumn(final int index) {
        return index % puzzleSize;
    }

    /**
     * Get the row for the given row index.
     * 
     * @param index
     *            The tile index.
     * @return The row value.
     */
    private int getRow(final int index) {
        return index / puzzleSize;
    }

    /**
     * Get the left position for a given tile position on the board.
     * 
     * @param index
     *            The index.
     * @return The left position for a given tile index.
     */
    private int getTileLeft(final int index) {
        return (index % puzzleSize) * tileSize;
    }

    /**
     * Get the top position for a given tile position on the board.
     * 
     * @param index
     *            The index.
     * @return The top position for a given tile index.
     */
    private int getTileTop(final int index) {
        return (index / puzzleSize) * tileSize;
    }

    /**
     * Swap two tiles.
     * 
     * @param t1
     *            Tile 1.
     * @param t2
     *            Tile 2.
     */
    private void swapTiles(final int t1, final int t2) {
        SliderPuzzleTile temp = tiles.get(t1);
        tiles.set(t1, tiles.get(t2));
        tiles.set(t2, temp);
    }

    /**
     * Get a random int for a given range.
     * 
     * @param min
     *            The minimum value, inclusive.
     * @param max
     *            The maximum value, inclusive.
     * @return An int for a given range.
     */
    private int getRandom(final int min, final int max) {
        return random.nextInt(max - min + 1) + min;
    }
}
