package com.droiddevil.sliderpuzzle;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;

/**
 * <p>
 * Main puzzle activity. Various controls and callbacks are used here to control
 * the puzzle.
 * </p>
 * 
 * <p>
 * It turns out that Android Spinner components are rather buggy and have
 * issues. Namely, the OnItemSelectedListener fires off events when the widget
 * is undergoing its measure() operation. This is ridiculous, but there is no
 * elegant solution.
 * http://stackoverflow.com/questions/5624825/spinner-onitemselected
 * -executes-when-it-is-not-suppose-to/5918177#5918177
 * </p>
 * 
 * @author tperrien
 * 
 */
public class ActivitySliderPuzzle extends Activity implements
        SliderPuzzleListener {

    /** This is a bit of a workaround hack. See class javadoc. */
    private static final int SPINNER_COUNT = 4;

    private int spinnersInitialized;

    private SliderPuzzle puzzle;

    private Dialog dialog;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        puzzle = (SliderPuzzle) findViewById(R.id.puzzle);
        puzzle.setPuzzleListener(this);

        spinnersInitialized = 0;

        // Setup image selector
        Spinner imageSelector = (Spinner) findViewById(R.id.image_selector);
        ArrayAdapter<CharSequence> imageAdapter = ArrayAdapter
                .createFromResource(this, R.array.puzzle_image,
                        android.R.layout.simple_spinner_item);
        imageAdapter
                .setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        imageSelector.setAdapter(imageAdapter);
        imageSelector.setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(final AdapterView<?> parent,
                    final View view, final int pos, final long id) {
                if (!canHandleEvent()) {
                    return;
                }

                int imageResource = R.drawable.weebly;
                switch (pos) {
                case 0:
                    imageResource = R.drawable.globe;

                    break;
                case 1:
                    imageResource = R.drawable.weebly;
                    break;
                case 2:
                    imageResource = R.drawable.zoniana_panoramic;
                    break;
                }

                puzzle.setPuzzleTileImage(imageResource);
            }

            @Override
            public void onNothingSelected(final AdapterView<?> arg0) {
            }
        });

        // Setup puzzle size
        Spinner sizeSelector = (Spinner) findViewById(R.id.size_selector);
        ArrayAdapter<CharSequence> sizeAdapter = ArrayAdapter
                .createFromResource(this, R.array.puzzle_size,
                        android.R.layout.simple_spinner_item);
        sizeAdapter
                .setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sizeSelector.setAdapter(sizeAdapter);
        sizeSelector.setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(final AdapterView<?> parent,
                    final View view, final int pos, final long id) {
                if (!canHandleEvent()) {
                    return;
                }

                puzzle.setPuzzleSize(pos + 3);
            }

            @Override
            public void onNothingSelected(final AdapterView<?> arg0) {
            }
        });

        // Setup puzzle difficulty
        Spinner difficultySelector = (Spinner) findViewById(R.id.difficulty_selector);
        ArrayAdapter<CharSequence> difficultyAdapter = ArrayAdapter
                .createFromResource(this, R.array.puzzle_difficulty,
                        android.R.layout.simple_spinner_item);
        difficultyAdapter
                .setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        difficultySelector.setAdapter(difficultyAdapter);
        difficultySelector
                .setOnItemSelectedListener(new OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(final AdapterView<?> parent,
                            final View view, final int pos, final long id) {
                        if (!canHandleEvent()) {
                            return;
                        }

                        puzzle.setPuzzleRandomMoves(5 + (int) Math.pow(4, pos));
                    }

                    @Override
                    public void onNothingSelected(final AdapterView<?> arg0) {
                    }
                });

        // Setup puzzle image mode
        Spinner imageModeSelector = (Spinner) findViewById(R.id.image_mode_selector);
        ArrayAdapter<CharSequence> imageModeAdapter = ArrayAdapter
                .createFromResource(this, R.array.puzzle_image_mode,
                        android.R.layout.simple_spinner_item);
        imageModeAdapter
                .setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        imageModeSelector.setAdapter(imageModeAdapter);
        imageModeSelector
                .setOnItemSelectedListener(new OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(final AdapterView<?> parent,
                            final View view, final int pos, final long id) {
                        if (!canHandleEvent()) {
                            return;
                        }

                        puzzle.setPuzzleImageMode(pos == 0 ? SliderPuzzle.IMAGE_MODE_CROP
                                : SliderPuzzle.IMAGE_MODE_STRETCH);
                    }

                    @Override
                    public void onNothingSelected(final AdapterView<?> arg0) {
                    }
                });

        // Setup play button
        Button play = (Button) findViewById(R.id.play);
        play.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                puzzle.createPuzzle();
            }
        });

        // Start the puzzle
        puzzle.createPuzzle();
    }

    /**
     * Compare and increment the initialized spinner count against the known
     * total. See class javadoc for details.
     * 
     * @return True if the spinners are allowed to fire events.
     */
    private boolean canHandleEvent() {
        return spinnersInitialized++ > SPINNER_COUNT - 1;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }
    }

    @Override
    public void onPuzzleSolved() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.puzzle_solved);
        builder.setMessage(R.string.play_again);
        builder.setPositiveButton(getString(R.string.yes),
                new OnClickListener() {
                    @Override
                    public void onClick(final DialogInterface dialog,
                            final int which) {
                        puzzle.createPuzzle();
                    }
                });
        builder.setNegativeButton(getString(R.string.no),
                new OnClickListener() {
                    @Override
                    public void onClick(final DialogInterface dialog,
                            final int which) {
                        // do nothing
                    }
                });
        dialog = builder.create();
        dialog.show();
    }
}