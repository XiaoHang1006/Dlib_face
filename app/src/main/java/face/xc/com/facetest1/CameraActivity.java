package face.xc.com.facetest1;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.View;
import android.widget.RelativeLayout;

import com.tzutalin.dlib.Constants;
import com.tzutalin.dlib.FaceDet;
import com.tzutalin.dlib.VisionDetRet;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import face.xc.com.facetest1.mediacodec.MediaHelper;
import face.xc.com.facetest1.util.CameraMatrix;
import face.xc.com.facetest1.util.ConUtil;
import face.xc.com.facetest1.util.DialogUtil;
import face.xc.com.facetest1.util.ICamera;
import face.xc.com.facetest1.util.OpenGLDrawRect;
import face.xc.com.facetest1.util.OpenGLUtil;
import face.xc.com.facetest1.util.PointsMatrix;
import face.xc.com.facetest1.util.Screen;


/**
 * Created by Administrator on 2018/1/25.
 */

public class CameraActivity extends Activity
    implements Camera.PreviewCallback,GLSurfaceView.Renderer,SurfaceTexture.OnFrameAvailableListener {

    private GLSurfaceView mGlSurfaceView;
    private ICamera mICamera;
    private Camera mCamera;
    private DialogUtil mDialogUtil;
    private float roi_ratio = 0.8f;
    private MediaHelper mMediaHelper;

    private FaceDet mFaceDet;
    private Handler inferenceHandler;
    private HandlerThread inferenceThread;
    private Paint mFaceLandmardkPaint;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Screen.initialize(this);
        setContentView(R.layout.activity_opengl);

        init();
    }

    private int mTextureID = -1;
    private SurfaceTexture mSurface;
    private CameraMatrix mCameraMatrix;
    private PointsMatrix mPointsMatrix;

    private int Angle;
    @Override
    protected void onResume() {
        super.onResume();
        ConUtil.acquireWakeLock(this);
        HashMap resolutionMap = new HashMap();
        resolutionMap.put("width",960);
        resolutionMap.put("height",720);
        mCamera = mICamera.openCamera(false,this,resolutionMap);

        mFaceLandmardkPaint = new Paint();
        mFaceLandmardkPaint.setColor(Color.GREEN);
        mFaceLandmardkPaint.setStrokeWidth(2);
        mFaceLandmardkPaint.setStyle(Paint.Style.STROKE);

        if (mCamera != null) {
            Angle = 360 - mICamera.Angle;
            if (false)
                Angle = mICamera.Angle;

            RelativeLayout.LayoutParams layout_params = mICamera.getLayoutParam();
            mGlSurfaceView.setLayoutParams(layout_params);

        } else {
            mDialogUtil.showDialog(getResources().getString(R.string.camera_error));
        }
        mMediaHelper = new MediaHelper(mICamera.cameraWidth, mICamera.cameraHeight, true, mGlSurfaceView);
    }

    private void init()
    {

        mFaceDet = new FaceDet(Constants.getFaceShapeModelPath());

        inferenceThread = new HandlerThread("InferenceThread");
        inferenceThread.start();
        inferenceHandler = new Handler(inferenceThread.getLooper());

        mGlSurfaceView = findViewById(R.id.opengl_layout_surfaceview);
        mGlSurfaceView.setEGLContextClientVersion(2);
        mGlSurfaceView.setRenderer(this);
        mGlSurfaceView.setRenderMode(mGlSurfaceView.RENDERMODE_WHEN_DIRTY);
        mGlSurfaceView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                autoFocus();
            }
        });

        mICamera = new ICamera();
        mDialogUtil = new DialogUtil(this);
    }

    private List<VisionDetRet> results;
    private Bitmap bitmap;
    private  ByteArrayOutputStream out;
    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
            final Camera cameraTemp = camera;
            final byte[] datas = data;
            Camera.Parameters parameters = cameraTemp.getParameters();
            int width = parameters.getPreviewSize().width;
            int height = parameters.getPreviewSize().height;
            YuvImage yuv = new YuvImage(datas, parameters.getPreviewFormat(), width, height, null);
            out = new ByteArrayOutputStream();
            yuv.compressToJpeg(new Rect(0, 0, width, height), 100, out);
            byte[] bytes = out.toByteArray();
            bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);

            bitmap = imageSideInversion(bitmap);

            inferenceHandler.post(new Runnable() {
                @Override
                public void run() {
                    try{
                    if(bitmap == null)
                        return;
                        results = mFaceDet.detect(bitmap);
                        Log.i("test","detect face : " + results.size());
                        for(int i=0;i<results.size();i++)
                        {
                            Canvas canvas = new Canvas(bitmap);

                            // Draw landmark
                            ArrayList<Point> landmarks = results.get(i).getFaceLandmarks();
                            for (Point point : landmarks) {
                                int pointX = (int) (point.x );
                                int pointY = (int) (point.y );
                                canvas.drawCircle(pointX, pointY, 4, mFaceLandmardkPaint);
                            }
                        }
                        //bitmap.recycle();
                        //bitmap = null;
                    }catch (Exception e)
                    {}
                }
            });
            System.gc();
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        mGlSurfaceView.requestRender();
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        surfaceInit();
    }

    private void surfaceInit() {
        mTextureID = OpenGLUtil.createTextureID();

        mSurface = new SurfaceTexture(mTextureID);
        if (false) {
            mMediaHelper.startRecording(mTextureID);
        }
        // 这个接口就干了这么一件事，当有数据上来后会进到onFrameAvailable方法
        mSurface.setOnFrameAvailableListener(this);// 设置照相机有数据时进入
        mCameraMatrix = new CameraMatrix(mTextureID);
        mPointsMatrix = new PointsMatrix(false);
        mPointsMatrix.isShowFaceRect = true;
        mICamera.startPreview(mSurface);// 设置预览容器
        mICamera.actionDetect(this);
        if (false)
            drawShowRect();
    }


    /**
     * 画绿色框
     */
    private void drawShowRect() {
        mPointsMatrix.vertexBuffers = OpenGLDrawRect.drawCenterShowRect(false, mICamera.cameraWidth,
                mICamera.cameraHeight, roi_ratio);
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {

    }

    private final float[] mMVPMatrix = new float[16];
    private final float[] mProjMatrix = new float[16];
    private final float[] mVMatrix = new float[16];
    private final float[] mRotationMatrix = new float[16];
    @Override
    public void onDrawFrame(GL10 gl) {

        final long actionTime = System.currentTimeMillis();
//		Log.w("ceshi", "onDrawFrame===");
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);// 清除屏幕和深度缓存
        float[] mtx = new float[16];
        mSurface.getTransformMatrix(mtx);
        mCameraMatrix.draw(mtx);
        // Set the camera position (View matrix)
        Matrix.setLookAtM(mVMatrix, 0, 0, 0, -3, 0f, 0f, 0f, 0f, 1f, 0f);

        // Calculate the projection and view transformation
        Matrix.multiplyMM(mMVPMatrix, 0, mProjMatrix, 0, mVMatrix, 0);

        mPointsMatrix.draw(mMVPMatrix);


        mSurface.updateTexImage();// 更新image，会调用onFrameAvailable方法
    }

    private void autoFocus() {
        if (mCamera != null && false) {
            mCamera.cancelAutoFocus();
            Camera.Parameters parameters = mCamera.getParameters();
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
            mCamera.setParameters(parameters);
            mCamera.autoFocus(null);
        }
    }

    private void saveBitmap(Bitmap bitmap) throws IOException
    {
        File file = new File(Environment.getExternalStorageDirectory() + "/1.png");
        if(file.exists()){
            file.delete();
        }
        FileOutputStream out;
        try{
            out = new FileOutputStream(file);
            if(bitmap.compress(Bitmap.CompressFormat.PNG, 90, out))
            {
                out.flush();
                out.close();
            }
        }
        catch (FileNotFoundException e)
        {
            e.printStackTrace();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    public Bitmap imageSideInversion(Bitmap src){
        android.graphics.Matrix sideInversion = new android.graphics.Matrix();
        sideInversion.setRotate(-90);
        Bitmap inversedImage = Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), sideInversion, false);
        return inversedImage;
    }

}
