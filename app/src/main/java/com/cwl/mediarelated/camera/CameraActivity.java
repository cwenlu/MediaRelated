package com.cwl.mediarelated.camera;

import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.Environment;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;

import com.cwl.mediarelated.R;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

/**
 * camera preview & capture
 */
public class CameraActivity extends AppCompatActivity {

    @BindView(R.id.camera_surface)
    SurfaceView cameraSurface;
    @BindView(R.id.original_surface)
    SurfaceView originalSurface;
    @BindView(R.id.change_camera)
    Button changeCamera;
    @BindView(R.id.orientation_change)
    Button orientationChange;

    Camera camera;
    int cameraId;
    int width, height;
    @BindView(R.id.take_capture)
    Button takeCapture;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        ButterKnife.bind(this);
        cameraSurface.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {


                try {
                    initCamera();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

                try {
                    camera.setPreviewDisplay(holder);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                camera.startPreview();
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                uninitCamera();
            }
        });

    }

    @OnClick({R.id.change_camera, R.id.orientation_change,R.id.take_capture})
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.change_camera:
                cameraId = cameraId == Camera.CameraInfo.CAMERA_FACING_BACK ? Camera.CameraInfo.CAMERA_FACING_FRONT : Camera.CameraInfo.CAMERA_FACING_BACK;
                uninitCamera();
                try {
                    initCamera();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
            case R.id.orientation_change:
                if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

                } else {
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

                }
                break;
            case R.id.take_capture:
                camera.autoFocus(new Camera.AutoFocusCallback() {
                    @Override
                    public void onAutoFocus(boolean success, Camera camera) {
                        if(success){
                            camera.takePicture(new Camera.ShutterCallback() {
                                @Override
                                public void onShutter() {
                                    Log.i("tag", "预处理，一般不需要要重写");
                                }
                            }, new Camera.PictureCallback() {
                                @Override
                                public void onPictureTaken(byte[] data, Camera camera) {
                                    Log.i("tag","拍照那一帧的原始数据"+data);
                                    //这里data一直为null，不知道为啥？
                                }
                            }, new Camera.PictureCallback() {
                                @Override
                                public void onPictureTaken(byte[] data, Camera camera) {
                                    try {
                                        FileOutputStream fileOutputStream=new FileOutputStream(Environment.getExternalStorageDirectory().getAbsolutePath()+"/out.jpg");
                                        fileOutputStream.write(data);
                                        fileOutputStream.close();
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }

                                    //重新开启预览不然会停在 caputre image 那一帧
                                    camera.startPreview();
                                }
                            });
                        }

                    }
                });
                break;
        }
    }

    private void initCamera() throws IOException {
        camera = Camera.open(cameraId);
        camera.setPreviewDisplay(cameraSurface.getHolder());
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            //调整预览方向
            camera.setDisplayOrientation(90);
        }

        //对于parameters的设置需要注意支持性，不然很容易出现 setparameters failed错误
        Camera.Parameters parameters = camera.getParameters();
        parameters.setPreviewSize(640, 480);
        parameters.setPictureSize(640, 480);
        parameters.setPictureFormat(ImageFormat.JPEG);
        parameters.setPreviewFormat(ImageFormat.NV21);
//        调整拍照之后图片方向
//        parameters.setRotation(90);
        camera.setParameters(parameters);
//        camera.setPreviewCallback(new Camera.PreviewCallback() {
//            @Override
//            public void onPreviewFrame(byte[] data, Camera camera) {
//                Camera.Size size = camera.getParameters().getPreviewSize();
////                only support ImageFormat.NV21 and ImageFormat.YUY2 for now
//                YuvImage yuvImage = new YuvImage(data, ImageFormat.NV21, 640, 480, null);
//                ByteArrayOutputStream stream = new ByteArrayOutputStream();
//                yuvImage.compressToJpeg(new Rect(0, 0, size.width, size.height), 80, stream);
//                Bitmap bitmap = BitmapFactory.decodeByteArray(stream.toByteArray(), 0, stream.size());
//                Canvas canvas = originalSurface.getHolder().lockCanvas();
//                canvas.drawBitmap(bitmap, 0, 0, null);
//                originalSurface.getHolder().unlockCanvasAndPost(canvas);
//            }
//        });

        //这种回调需要注意setPreviewCallbackWithBuffer之前必须使用addCallbackBuffer指定一个缓冲区数组，并且大小必须满足图像格式最小大小
        //主要用于按需回调帧数据
        camera.addCallbackBuffer(new byte[640*480*3/2]);
        camera.setPreviewCallbackWithBuffer(new Camera.PreviewCallback() {
            @Override
            public void onPreviewFrame(byte[] data, Camera camera) {
                Camera.Size size = camera.getParameters().getPreviewSize();
//                only support ImageFormat.NV21 and ImageFormat.YUY2 for now
                YuvImage yuvImage = new YuvImage(data, ImageFormat.NV21, 640, 480, null);
                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                yuvImage.compressToJpeg(new Rect(0, 0, size.width, size.height), 80, stream);
                Bitmap bitmap = BitmapFactory.decodeByteArray(stream.toByteArray(), 0, stream.size());
                Canvas canvas = originalSurface.getHolder().lockCanvas();
                canvas.drawBitmap(bitmap, 0, 0, null);
                originalSurface.getHolder().unlockCanvasAndPost(canvas);
                //必须调用，不然后续就不会回调onPreviewFrame
                camera.addCallbackBuffer(data);
            }
        });

        camera.startPreview();

        dumpAPIInfo();
    }

    private void uninitCamera() {
        camera.setPreviewCallback(null);
        camera.stopPreview();
        camera.release();
        camera = null;
    }

    private void dumpAPIInfo() {
        Camera.Parameters parameters = camera.getParameters();
        //获取支持的预览尺寸
        List<Camera.Size> supportedPreviewSizes = parameters.getSupportedPreviewSizes();
        for (Camera.Size supportedPreviewSize : supportedPreviewSizes) {
            Log.i("tag",supportedPreviewSize.width+"x"+supportedPreviewSize.height);
        }

        //获取预览支持的颜色格式
        for (Integer integer : parameters.getSupportedPreviewFormats()) {
            Log.i("tag",Integer.toHexString(integer));
        }

        //获取拍照支持的格式
        for (Integer integer : parameters.getSupportedPictureFormats()) {
            Log.i("tag",Integer.toHexString(integer)+"===");
        }

        //其他支持类似

        //获取camera数量，方向，前后置
        Camera.CameraInfo cameraInfo=new Camera.CameraInfo();
        int numberOfCameras = Camera.getNumberOfCameras();
        for (int i = 0; i < numberOfCameras; i++) {
            Camera.getCameraInfo(i,cameraInfo);
            Log.i("tag",cameraInfo.orientation+","+cameraInfo.facing);
        }
    }
}
