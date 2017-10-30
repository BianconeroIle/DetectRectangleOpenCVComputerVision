package com.example.ilijaangeleski.detectrectangle;

import android.app.Dialog;
import android.graphics.Bitmap;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.example.ilijaangeleski.detectrectangle.models.CameraData;
import com.example.ilijaangeleski.detectrectangle.models.MatData;
import com.example.ilijaangeleski.detectrectangle.utils.ImageUtils;
import com.example.ilijaangeleski.detectrectangle.utils.OpenCVHelper;
import com.example.ilijaangeleski.detectrectangle.utils.PerspectiveTransformation;
import com.example.ilijaangeleski.detectrectangle.utils.RectFinder;
import com.example.ilijaangeleski.detectrectangle.views.CameraPreview;
import com.example.ilijaangeleski.detectrectangle.views.DrawView;

import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint2f;

import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import rx.subjects.PublishSubject;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();
    static {
        if (!OpenCVLoader.initDebug()) {
            Log.v(TAG, "init OpenCV");
        }
    }

    private PublishSubject<CameraData> subject = PublishSubject.create();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        CameraPreview cameraPreview = (CameraPreview) findViewById(R.id.camera_preview);
        cameraPreview.setCallback((data, camera) -> {
            CameraData cameraData = new CameraData();
            cameraData.data = data;
            cameraData.camera = camera;
            subject.onNext(cameraData);
        });
        cameraPreview.setOnClickListener(v -> cameraPreview.focus());
        DrawView drawView = (DrawView) findViewById(R.id.draw_layout);
        subject.concatMap(cameraData ->
                OpenCVHelper.getRgbMat(new MatData(), cameraData.data, cameraData.camera))
                .concatMap(matData -> OpenCVHelper.resize(matData, 400, 400))
                .map(matData -> {
                    matData.resizeRatio = (float) matData.oriMat.height() / matData.resizeMat.height();
                    matData.cameraRatio = (float) cameraPreview.getHeight() / matData.oriMat.height();

                    return matData;
                })
                .concatMap(this::detectRect)
                .compose(mainAsync())
                .subscribe(matData -> {
                    if (drawView != null) {
                        if (matData.cameraPath != null) {
                            drawView.setPath(matData.cameraPath);
                            showImage(matData);
                        } else {
                            drawView.setPath(null);
                        }
                        drawView.invalidate();
                    }
                });
    }
    public void showImage(MatData matData) {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.customdialog);
        ImageView imageView = new ImageView(this);
        dialog.addContentView(imageView, new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        dialog.show();

        Mat srcMat = ImageUtils.bitmapToMat(matData.bitmap);
        RectFinder rectFinder = new RectFinder(0.2, 0.98);
        MatOfPoint2f rectangle = rectFinder.findRectangle(srcMat);

        if (rectangle == null) {
            Toast.makeText(this, "No rectangles were found.", Toast.LENGTH_LONG).show();
            return;
        }
        PerspectiveTransformation perspective = new PerspectiveTransformation();
        Mat dstMat = perspective.transform(srcMat, rectangle);
        Bitmap resultBitmap = ImageUtils.matToBitmap(dstMat);
        imageView.setImageBitmap(Bitmap.createScaledBitmap(ImageUtils.rotateBitmap(resultBitmap, 90), 900, 1400, false));

    }
    private Observable<MatData> detectRect(MatData mataData) {
        return Observable.just(mataData)
                .concatMap(OpenCVHelper::getMonochromeMat)
                .concatMap(OpenCVHelper::getContoursMat)
                .concatMap(OpenCVHelper::getPath);
    }
    private static <T> Observable.Transformer<T, T> mainAsync() {
        return obs -> obs.subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread());
    }
}

