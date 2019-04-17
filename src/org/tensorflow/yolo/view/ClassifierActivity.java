package org.tensorflow.yolo.view;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Typeface;
import android.media.Image;
import android.media.ImageReader;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.SystemClock;
import android.util.Log;
import android.util.Size;
import android.util.TypedValue;

import org.tensorflow.yolo.R;
import org.tensorflow.yolo.TensorFlowImageRecognizer;
import org.tensorflow.yolo.model.Recognition;
import org.tensorflow.yolo.util.ImageUtils;
import org.tensorflow.yolo.view.components.BorderedText;

import java.util.List;
import java.util.Vector;
//added by me
import java.io.File;
import java.io.OutputStream;
import java.io.IOException;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import android.graphics.BitmapFactory;
import java.io.FileNotFoundException;
import java.io.FileInputStream;

import static org.tensorflow.yolo.Config.INPUT_SIZE;
import static org.tensorflow.yolo.Config.LOGGING_TAG;

/**
 * Classifier activity class
 * Modified by Zoltan Szabo
 */
public class ClassifierActivity extends TextToSpeechActivity implements OnImageAvailableListener {
    private boolean MAINTAIN_ASPECT = true;
    private float TEXT_SIZE_DIP = 10;

    private TensorFlowImageRecognizer recognizer;
    private Integer sensorOrientation;
    private int previewWidth = 0;
    private int previewHeight = 0;
    private Bitmap croppedBitmap = null;
    private boolean computing = false;
    private Matrix frameToCropTransform;

    private OverlayView overlayView;
    private BorderedText borderedText;
    private long lastProcessingTimeMs;

    //added by me
    //final File file = new File("/sdcard/pic.png");
    int counter = 0;

    @Override
    public void onPreviewSizeChosen(final Size size, final int rotation) {
        final float textSizePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                TEXT_SIZE_DIP, getResources().getDisplayMetrics());
        borderedText = new BorderedText(textSizePx);
        borderedText.setTypeface(Typeface.MONOSPACE);

        recognizer = TensorFlowImageRecognizer.create(getAssets());

        overlayView = (OverlayView) findViewById(R.id.overlay);
        previewWidth = size.getWidth();
        previewHeight = size.getHeight();

        final int screenOrientation = getWindowManager().getDefaultDisplay().getRotation();

        Log.i(LOGGING_TAG, String.format("Sensor orientation: %d, Screen orientation: %d",
                rotation, screenOrientation));

        sensorOrientation = rotation + screenOrientation;

        Log.i(LOGGING_TAG, String.format("Initializing at size %dx%d", previewWidth, previewHeight));

        croppedBitmap = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Config.ARGB_8888);

        frameToCropTransform = ImageUtils.getTransformationMatrix(previewWidth, previewHeight,
                INPUT_SIZE, INPUT_SIZE, sensorOrientation, MAINTAIN_ASPECT);
        frameToCropTransform.invert(new Matrix());

        addCallback((final Canvas canvas) -> renderAdditionalInformation(canvas));
    }

    @Override
    public void onImageAvailable(final ImageReader reader) {
        Image image = null;

        try {
            image = reader.acquireLatestImage();



            if (image == null) {
                return;
            }

            if (computing) {
                image.close();
                return;
            }

            //added by me
            /*if(image != null) {
                ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                byte[] bytes = new byte[buffer.capacity()];
                buffer.get(bytes);
                save(bytes);
            }
            */
            computing = true;

            fillCroppedBitmap(image);
            Log.i("4", "Checkpoint 4");
            image.close();
        } catch (final Exception ex) {
            if (image != null) {
                image.close();
            }
            Log.e(LOGGING_TAG, ex.getMessage());
        }
        Log.i("5", "Checkpoint 5");
        runInBackground(() -> {
            final long startTime = SystemClock.uptimeMillis();
            final List<Recognition> results = recognizer.recognizeImage(croppedBitmap);
            lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;
            overlayView.setResults(results);
            Log.i("Results", ": " + results);
            Log.i("Others", " time required = " + lastProcessingTimeMs + ", counter = " + counter);
            speak(results);
            requestRender();
            computing = false;
        });
    }
    /* //storing the image captured by camera2 api (doesn't work)
    private void save(byte[] bytes) throws IOException {
        OutputStream output = null;
        Log.i("[save] ", "capturing Image");
        try {
            Log.i("1", "Checkpoint 1");
            output = new FileOutputStream(file);
            Log.i("2", "Checkpoint 2");
            output.write(bytes);
            Log.i("3", "Checkpoint 3");
        } finally {
            if (null != output) {
                output.close();
            }
        }
    }
    */

    private void fillCroppedBitmap(final Image image) {
            Bitmap rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Config.ARGB_8888);
            rgbFrameBitmap.setPixels(ImageUtils.convertYUVToARGB(image, previewWidth, previewHeight),
                    0, previewWidth, 0, 0, previewWidth, previewHeight);
            //new Canvas(croppedBitmap).drawBitmap(rgbFrameBitmap, frameToCropTransform, null);
            //Log.i("Details", "width = " + croppedBitmap.getWidth() + ",height = " + croppedBitmap.getHeight());

            try {
                //String picfile = "/sdcard/bitmap/pic_" + Integer.toString(counter) + ".png";
                String picfile = "/sdcard/bitmap/cat.png";
                File file = new File(picfile);
                Bitmap temp_Bitmap = BitmapFactory.decodeStream(new FileInputStream(file));
                Log.i("Pre_Details", "width = " + temp_Bitmap.getWidth() + ",height = " + temp_Bitmap.getHeight());
                croppedBitmap = Bitmap.createScaledBitmap(
                        temp_Bitmap, INPUT_SIZE, INPUT_SIZE, false); // this one is used for resizing the image
                Log.i("Post_Details", "width = " + croppedBitmap.getWidth() + ",height = " + croppedBitmap.getHeight());
                //ImageView img=(ImageView)findViewById(R.id.imgPicker);
                //img.setImageBitmap(b);
            }
            catch (FileNotFoundException e)
            {
            e.printStackTrace();
            }


            //modified by me, storing the bitmap into the file
            String picfile = "/sdcard/bitmap_store/pic_" + Integer.toString(counter) + ".png";
            File file = new File(picfile);
            try (FileOutputStream out = new FileOutputStream(file)) {
                croppedBitmap.compress(Bitmap.CompressFormat.PNG, 100, out); // bmp is your Bitmap instance
                out.flush(); // Not really required
                out.close();
                counter += 1;
            // PNG is a lossless format, the compression factor (100) is ignored
            } catch (IOException e) {
                e.printStackTrace();
            }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (recognizer != null) {
            recognizer.close();
        }
    }

    private void renderAdditionalInformation(final Canvas canvas) {
        final Vector<String> lines = new Vector();
        if (recognizer != null) {
            for (String line : recognizer.getStatString().split("\n")) {
                lines.add(line);
            }
        }

        lines.add("Frame: " + previewWidth + "x" + previewHeight);
        lines.add("View: " + canvas.getWidth() + "x" + canvas.getHeight());
        lines.add("Rotation: " + sensorOrientation);
        lines.add("Inference time: " + lastProcessingTimeMs + "ms");

        borderedText.drawLines(canvas, 10, 10, lines);
    }
}
