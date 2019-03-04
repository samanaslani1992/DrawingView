package com.raed.drawingviewapp;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.SeekBar;
import android.widget.Toast;

import com.raed.drawingview.BrushView;
import com.raed.drawingview.DrawingView;
import com.raed.drawingview.brushes.BrushSettings;
import com.raed.drawingview.brushes.Brushes;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_IMPORT_IMAGE = 10;

    private DrawingView mDrawingView;
    private SeekBar mSizeSeekBar;
    private Button mUndoButton;
    private Button mRedoButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        mDrawingView = findViewById(R.id.drawing_view);
        mDrawingView.setUndoAndRedoEnable(true);

        BrushView brushView = findViewById(R.id.brush_view);
        brushView.setDrawingView(mDrawingView);

        mSizeSeekBar = findViewById(R.id.size_seek_bar);
        mSizeSeekBar.setMax(100);
        mSizeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                BrushSettings brushSettings = mDrawingView.getBrushSettings();
                brushSettings.setSelectedBrushSize(i / 100f);
            }

            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        findViewById(R.id.clear).setOnClickListener(view -> {
            if (mDrawingView.clear()) {
                mUndoButton.setEnabled(true);
                mRedoButton.setEnabled(false);
            }
        });

        findViewById(R.id.reset_zoom).setOnClickListener(view -> mDrawingView.resetZoom());

        final Button zoomModeButton = findViewById(R.id.zoom_mode_button);
        zoomModeButton.setOnClickListener(view -> {
            if (mDrawingView.isInZoomMode()) {
                mDrawingView.exitZoomMode();
                zoomModeButton.setText(R.string.enterzoommode);
                return;
            }
            mDrawingView.enterZoomMode();
            zoomModeButton.setText(R.string.exitzoommode);
        });

        findViewById(R.id.set_background).setOnClickListener(view -> {
            if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 0);
                return;
            }
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/*");
            startActivityForResult(intent, REQUEST_CODE_IMPORT_IMAGE);
        });

        findViewById(R.id.set_background_null).setOnClickListener(view -> {
            mDrawingView.setBackgroundImage(null);
            mUndoButton.setEnabled(!mDrawingView.isUndoStackEmpty());
            mRedoButton.setEnabled(!mDrawingView.isRedoStackEmpty());
        });

        findViewById(R.id.export).setOnClickListener(view -> {
            if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 0);//ignoring the request code
                return;
            }
            Bitmap bitmap = mDrawingView.exportDrawing();
            exportImage(bitmap);
        });

        findViewById(R.id.export_without_bg).setOnClickListener(view -> {
            if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 0);//ignoring the request code
                return;
            }
            Bitmap bitmap = mDrawingView.exportDrawingWithoutBackground();
            exportImage(bitmap);
        });

        setupUndoAndRedo();
        setupBrushes();
        setupColorViews();

        //  mDrawingView.setNewDraw(path, paint, rect);
        mDrawingView.setOnDrawListener(new DrawingView.OnDrawListener() {

            @Override
            public void onDraw(Bitmap bitmap, Rect rect) {
                Toast.makeText(MainActivity.this, "Bitmap bitmap, Rect rect", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onDraw(Path path, Paint paint, Rect rect) {
                Toast.makeText(MainActivity.this, "Path path, Paint paint, Rect rect", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void exportImage(Bitmap bitmap) {
        File folder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        folder.mkdirs();
        File imageFile = new File(folder, UUID.randomUUID() + ".png");
        try {
            storeBitmap(imageFile, bitmap);
        } catch (Exception e) {
            e.printStackTrace();
        }
        MediaScannerConnection.scanFile(
                this,
                new String[]{},
                new String[]{"image/png"},
                null);
        sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(imageFile)));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_IMPORT_IMAGE) {
            if (AppCompatActivity.RESULT_OK != resultCode)
                return;
            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), data.getData());
                mDrawingView.setBackgroundImage(bitmap);
                mUndoButton.setEnabled(!mDrawingView.isUndoStackEmpty());
                mRedoButton.setEnabled(!mDrawingView.isRedoStackEmpty());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void setBrushSelected(int brushID) {
        BrushSettings settings = mDrawingView.getBrushSettings();
        settings.setSelectedBrush(brushID);
        int sizeInPercentage = (int) (settings.getSelectedBrushSize() * 100);
        mSizeSeekBar.setProgress(sizeInPercentage);
    }

    private void setupUndoAndRedo() {
        mUndoButton = findViewById(R.id.undo);
        mUndoButton.setOnClickListener(view -> {
            mDrawingView.undo();
            mUndoButton.setEnabled(!mDrawingView.isUndoStackEmpty());
            mRedoButton.setEnabled(!mDrawingView.isRedoStackEmpty());
        });

        mRedoButton = findViewById(R.id.redo);
        mRedoButton.setOnClickListener(view -> {
            mDrawingView.redo();
            mUndoButton.setEnabled(!mDrawingView.isUndoStackEmpty());
            mRedoButton.setEnabled(!mDrawingView.isRedoStackEmpty());
        });

        mDrawingView.setOnDrawListener(new DrawingView.OnDrawListener() {
            @Override
            public void onDraw(Bitmap bitmap, Rect rect) {
                mUndoButton.setEnabled(true);
                mRedoButton.setEnabled(false);
            }

            @Override
            public void onDraw(Path path, Paint paint, Rect rect) {
                mUndoButton.setEnabled(true);
                mRedoButton.setEnabled(false);
            }
        });
    }

    private void setupColorViews() {
        View.OnClickListener colorClickListener = view -> {
            int color = ((ColorDrawable) view.getBackground()).getColor();
            BrushSettings brushSettings = mDrawingView.getBrushSettings();
            brushSettings.setColor(color);
        };
        ViewGroup colorsContainer = findViewById(R.id.brush_colors_container);
        for (View colorView : colorsContainer.getTouchables())
            colorView.setOnClickListener(colorClickListener);

        View.OnClickListener bgClickListener = view -> {
            int color = ((ColorDrawable) view.getBackground()).getColor();
            mDrawingView.setDrawingBackground(color);
        };
        ViewGroup bgColorsContainer = findViewById(R.id.bg_colors_container);
        for (View colorView : bgColorsContainer.getTouchables())
            colorView.setOnClickListener(bgClickListener);
    }

    private void setupBrushes() {
        RadioButton pen = findViewById(R.id.pen);
        pen.setOnCheckedChangeListener((compoundButton, b) -> {
            if (b) setBrushSelected(Brushes.PEN);
        });

        RadioButton pencil = findViewById(R.id.pencil);
        pencil.setOnCheckedChangeListener((compoundButton, b) -> {
            if (b) setBrushSelected(Brushes.PENCIL);
        });

        RadioButton eraser = findViewById(R.id.eraser);
        eraser.setOnCheckedChangeListener((compoundButton, b) -> {
            if (b) setBrushSelected(Brushes.ERASER);
        });

        RadioButton calligraphy = findViewById(R.id.calligraphy);
        calligraphy.setOnCheckedChangeListener((compoundButton, b) -> {
            if (b)
                setBrushSelected(Brushes.CALLIGRAPHY);
        });

        RadioButton airBrush = findViewById(R.id.air_brush);
        airBrush.setOnCheckedChangeListener((compoundButton, b) -> {
            if (b) setBrushSelected(Brushes.AIR_BRUSH);
        });
    }

    private void storeBitmap(File file, Bitmap bitmap) throws Exception {
        if (!file.exists() && !file.createNewFile())
            throw new Exception("Not able to create " + file.getPath());
        FileOutputStream stream = new FileOutputStream(file);
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
        stream.flush();
        stream.close();
    }

}

