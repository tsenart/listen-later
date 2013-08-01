package com.soundcloud.android.utils.images;


import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.media.ExifInterface;
import android.net.Uri;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.animation.Transformation;
import android.widget.ImageView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

public final class ImageUtils {
    private static final String TAG = ImageUtils.class.getSimpleName();
    public static final int GRAPHIC_DIMENSIONS_BADGE = 47;
    public static final int RECOMMENDED_IMAGE_SIZE = 2048;

    private ImageUtils() {}


    public static BitmapFactory.Options decode(File imageFile) throws IOException {
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;  /* don't allocate bitmap */
        InputStream is = new FileInputStream(imageFile);
        /* output ignored */ BitmapFactory.decodeStream(is, null, options);
        is.close();
        return options;
    }

    public static BitmapFactory.Options determineResizeOptions(File imageFile,
                                                               int targetWidth,
                                                               int targetHeight,
                                                               boolean crop) throws IOException {

        if (targetWidth == 0 || targetHeight == 0) return new BitmapFactory.Options(); // some devices report 0
        BitmapFactory.Options options = decode(imageFile);

        final int height = options.outHeight;
        final int width = options.outWidth;

        if (crop) {
            if (height > targetHeight || width > targetWidth) {
                if (targetHeight / height < targetWidth / width) {
                    options.inSampleSize = Math.round((float)height / (float)targetHeight);
                } else {
                    options.inSampleSize = Math.round((float)width / (float)targetWidth);
                }

            }
        } else if (targetHeight / height > targetWidth / width) {
            options.inSampleSize = Math.round((float)height / (float)targetHeight);
        } else {
            options.inSampleSize = Math.round((float)width / (float)targetWidth);
        }
        return options;
    }

    public static int getExifRotation(File imageFile) {
        if (imageFile == null) return -1;
        try {
            ExifInterface exif = new ExifInterface(imageFile.getAbsolutePath());
            // We only recognize a subset of orientation tag values.
            switch (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)) {
                case ExifInterface.ORIENTATION_ROTATE_90:  return 90;
                case ExifInterface.ORIENTATION_ROTATE_180: return 180;
                case ExifInterface.ORIENTATION_ROTATE_270: return 270;
                default: return ExifInterface.ORIENTATION_UNDEFINED;
            }
        } catch (IOException e) {
            Log.e(TAG, "error", e);
            return -1;
        }
    }

    public static void clearBitmap(Bitmap bmp) {
        if (bmp != null) {
            bmp.recycle();
            System.gc();
        }
    }

    public static Bitmap loadContactPhoto(ContentResolver cr, long id) {
        Uri uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, id);
        InputStream input = ContactsContract.Contacts.openContactPhotoInputStream(cr, uri);
        if (input == null) {
            return null;
        }
        return BitmapFactory.decodeStream(input);
    }

    public static Bitmap getConfiguredBitmap(File imageFile, int minWidth, int minHeight) {
        Bitmap bitmap;
        try {
            BitmapFactory.Options opt = determineResizeOptions(imageFile, minWidth, minHeight, false);

            BitmapFactory.Options sampleOpt = new BitmapFactory.Options();
            sampleOpt.inSampleSize = opt.inSampleSize;

            bitmap = BitmapFactory.decodeFile(imageFile.getAbsolutePath(), sampleOpt);

            Matrix m = new Matrix();
            float scale;
            float dx = 0, dy = 0;

            // assumes height and width are the same
            if (bitmap.getWidth() > bitmap.getHeight()) {
                scale = (float) minHeight / (float) bitmap.getHeight();
                dx = (minWidth - bitmap.getWidth() * scale) * 0.5f;
            } else {
                scale = (float) minWidth / (float) bitmap.getWidth();
                dy = (minHeight - bitmap.getHeight() * scale) * 0.5f;
            }

            m.setScale(scale, scale);
            m.postTranslate((int) (dx + 0.5f), (int) (dy + 0.5f));
            int exifRotation = getExifRotation(imageFile);
            if (exifRotation != 0) {
                m.postRotate(exifRotation, minWidth / 2, minHeight / 2);
            }

            return (m.isIdentity()) ? bitmap : Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), m, true);

        } catch (IOException e) {
            Log.e(TAG, "error", e);
        } catch (OutOfMemoryError e) {
            Log.e(TAG, "error", e);
        }
        return null;
    }

    public static boolean setImage(File imageFile, ImageView imageView, int viewWidth, int viewHeight) {
        Bitmap bitmap;
        try {
            BitmapFactory.Options opt = determineResizeOptions(imageFile, viewWidth, viewHeight, false);
            BitmapFactory.Options sampleOpt = new BitmapFactory.Options();
            sampleOpt.inSampleSize = opt.inSampleSize;

            bitmap = BitmapFactory.decodeFile(imageFile.getAbsolutePath(), sampleOpt);
            if (bitmap == null) {
                Log.w(TAG, "error decoding "+imageFile);
                return false;
            }

            setImage(bitmap,imageView,viewWidth,viewHeight,getExifRotation(imageFile));
            return true;

        } catch (IOException e) {
            Log.e(TAG, "error", e);
            return false;
        }
    }

    /**
     * Set ImageView from Bitmap with a custom matrix that takes into account scaling and exif rotation
     * @param bitmap source bitmap
     * @param imageView imageview to set
     * @param viewWidth imageview width, passed in as this call often occurs before measurement
     * @param viewHeight imageview height
     * @param exifRotation exif rotation to account for in the matrix
     */
    public static void setImage(Bitmap bitmap, ImageView imageView, int viewWidth, int viewHeight, int exifRotation) {
        Matrix m = new Matrix();
        float scale;
        float dx = 0, dy = 0;

        // assumes height and width are the same
        if (bitmap.getWidth() > bitmap.getHeight()) {
            scale = (float) viewHeight / (float) bitmap.getHeight();
            dx = (viewWidth - bitmap.getWidth() * scale) * 0.5f;
        } else {
            scale = (float) viewWidth / (float) bitmap.getWidth();
            dy = (viewHeight - bitmap.getHeight() * scale) * 0.5f;
        }

        m.setScale(scale, scale);
        m.postTranslate((int) (dx + 0.5f), (int) (dy + 0.5f));
        if (exifRotation != 0) {
            m.postRotate(exifRotation, viewWidth / 2, viewHeight / 2);
        }

        imageView.setScaleType(ImageView.ScaleType.MATRIX);
        imageView.setImageMatrix(m);

        imageView.setImageBitmap(bitmap);
        imageView.setVisibility(View.VISIBLE);
    }

    public static boolean resizeImageFile(File inputFile, File outputFile, int width, int height) throws IOException {
        BitmapFactory.Options options = determineResizeOptions(inputFile, width, height, false);

        final int sampleSize = options.inSampleSize;
        final int degree = getExifRotation(inputFile);

        if (sampleSize > 1 || degree > 0) {
            InputStream is = new FileInputStream(inputFile);

            options = new BitmapFactory.Options();
            options.inSampleSize = sampleSize;
            Bitmap bitmap = BitmapFactory.decodeStream(is, null, options);
            is.close();

            if (degree != 0) {
                Bitmap preRotate = bitmap;
                Matrix mat = new Matrix();
                mat.postRotate(degree);
                bitmap = Bitmap.createBitmap(preRotate, 0, 0, preRotate.getWidth(),
                        preRotate.getHeight(), mat, true);
                preRotate.recycle();
            }

            if (bitmap == null) throw new IOException("error decoding bitmap (bitmap == null)");

            FileOutputStream out = new FileOutputStream(outputFile);
            final boolean success = bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
            out.close();
            clearBitmap(bitmap);

            if (!success) {
                Log.w(TAG, "bitmap.compress returned false");
            }
            return success;
        } else {
            Log.w(TAG, String.format("not resizing: sampleSize %d, degree %d", sampleSize, degree));
            return false;
        }
    }

    @SuppressWarnings("UnnecessaryLocalVariable")
    public static void drawBubbleOnCanvas(Canvas c,
                                          Paint bgPaint,
                                          Paint linePaint,
                                          int width,
                                          int height,
                                          int arc,
                                          int arrowWidth,
                                          int arrowHeight,
                                          int arrowOffset) {

        /*
             A ---- B
           I          C
           H ----G-E- D
                 F

         */

        final boolean arrowLeft = arrowOffset <= width/2;

        final int Ax = arc;
        final int Ay = 0;
        final int Bx = width - arc;
        final int By = 0;
        final int Cx = width;
        final int Cy = arc;
        final int Dx = width;
        final int Dy = height;
        final int Ex = arrowLeft ? arrowWidth + arrowOffset : arrowOffset;
        final int Ey = height;
        final int Fx = arrowOffset;
        final int Fy = height + arrowHeight;
        final int Gx = arrowLeft ? arrowOffset : arrowOffset - arrowWidth;
        final int Gy = height;
        final int Hx = 0;
        final int Hy = height;
        final int Ix = 0;
        final int Iy = arc;

        Path ctx = new Path();
        ctx.moveTo(Ax, Ay);
        ctx.lineTo(Bx, By);
        ctx.arcTo(new RectF(Bx, By, Cx, Cy), 270, 90); //B-C arc

        ctx.lineTo(Dx, Dy);

        if (arrowWidth > 0){
            ctx.lineTo(Ex, Ey);
            ctx.lineTo(Fx, Fy);
            ctx.lineTo(Gx, Gy);
        }


        ctx.lineTo(Hx, Hy);
        ctx.lineTo(Ix, Iy);
        //noinspection PointlessArithmeticExpression
        ctx.arcTo(new RectF(Ax - arc, Ay, Ix + arc, Iy), 180, 90); //F-A arc
        c.drawPath(ctx, bgPaint);

        if (linePaint != null){
            c.drawLine(arrowOffset,height,arrowOffset,height+arrowOffset,linePaint);
        }
    }

    @SuppressWarnings("UnnecessaryLocalVariable")
    public static void drawSquareBubbleOnCanvas(Canvas c, Paint bgPaint, Paint linePaint, int width, int height, int arrowWidth, int arrowHeight, int arrowOffset){

        /*
             A ---- B
           I          C
           H ----G-E- D
                 F

         */

        final boolean arrowLeft = arrowOffset <= width / 2;

        final int Ax = 0;
        final int Ay = 0;
        final int Bx = width;
        final int By = 0;
        final int Cx = width;
        final int Cy = height;
        final int Dx = arrowLeft ? arrowWidth + arrowOffset : arrowOffset;
        final int Dy = height;
        final int Ex = arrowOffset;
        final int Ey = height + arrowHeight;
        final int Fx = arrowLeft ? arrowOffset : arrowOffset - arrowWidth;
        final int Fy = height;
        final int Gx = 0;
        final int Gy = height;

        Path ctx = new Path();
        ctx.moveTo(Ax, Ay);
        ctx.lineTo(Bx, By);
        ctx.lineTo(Cx, Cy);

        if (arrowWidth > 0) {
            ctx.lineTo(Dx, Dy);
            ctx.lineTo(Ex, Ey);
            ctx.lineTo(Fx, Fy);
        }

        ctx.lineTo(Gx, Gy);
        c.drawPath(ctx, bgPaint);
        if (linePaint != null) {
            c.drawLine(arrowOffset, height, arrowOffset, height + arrowOffset, linePaint);
        }
    }

    public static float getCurrentTransformY(View v){
        if (v.getAnimation() == null) return 0f;
        Transformation t = new Transformation();
        float[] values = new float[9];
        v.getAnimation().getTransformation(v.getDrawingTime(), t);
        t.getMatrix().getValues(values);
        return values[5];
    }

    @TargetApi(9)
    public static boolean isScreenXL(Context context){
        return ((context.getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) == Configuration.SCREENLAYOUT_SIZE_XLARGE);
    }

    public static boolean checkIconShouldLoad(String url) {
        return !(TextUtils.isEmpty(url)
                || url.toLowerCase(Locale.US).equals("null")
                || url.contains("default_avatar"));
    }



    public static void recycleImageViewBitmap(ImageView view) {
        if (view.getDrawable() instanceof BitmapDrawable) {
            Bitmap bitmap = ((BitmapDrawable) view.getDrawable()).getBitmap();
            if (bitmap != null && !bitmap.isRecycled()) {
                clearBitmap(bitmap);
                view.setImageDrawable(null);
            }
        }
    }

    /*
    http://developer.android.com/training/displaying-bitmaps/load-bitmap.html
     */

    public static int calculateInSampleSize(
            BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            if (width > height) {
                inSampleSize = Math.round((float) height / (float) reqHeight);
            } else {
                inSampleSize = Math.round((float) width / (float) reqWidth);
            }
        }
        return inSampleSize;
    }

    public static Bitmap decodeSampledBitmapFromResource(Resources res, int resId,
            int reqWidth, int reqHeight) {

        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(res, resId, options);

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeResource(res, resId, options);
    }
}
