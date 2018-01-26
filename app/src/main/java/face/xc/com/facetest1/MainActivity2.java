package face.xc.com.facetest1;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.dexafree.materialList.card.Card;
import com.dexafree.materialList.view.MaterialListView;
import com.tzutalin.dlib.Constants;
import com.tzutalin.dlib.FaceDet;
import com.tzutalin.dlib.VisionDetRet;
import com.tzutalin.dlibtest.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class MainActivity2 extends Activity {
    private static final int RESULT_LOAD_IMG = 1;
    private static final int REQUEST_CODE_PERMISSION = 2;
    private MaterialListView imageview;
    private Button btn;
    private  FaceDet faceDet;
    protected String mTestImgPath;
    private String value = "";
    private String savePath = "";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        imageview = (MaterialListView) findViewById(R.id.myImage);
        btn = (Button) findViewById(R.id.fab);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(intent,RESULT_LOAD_IMG);
            }
        });

        savePath = Environment.getExternalStorageDirectory() + File.separator + "save.txt";
        File saveFile = new File(savePath);
        String value = "";
        try{
            if(!saveFile.exists())
            {
                    saveFile.createNewFile();
            }else {
                InputStream inputStream = new FileInputStream(saveFile);
                int length = inputStream.available();
                byte[] buffer = new byte[length];
                inputStream.read(buffer);
                value = new String(buffer);
            }
        }catch (Exception e)
        {}

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        try {
//              String path = Environment.getExternalStorageDirectory().getPath();
//              path += File.separator + "face_img" + File.separator + "125.jpg";
//              runDetectAsync(path);
//              Toast.makeText(this, "Img Path:" + mTestImgPath, Toast.LENGTH_SHORT).show();

            // When an Image is picked
            if (requestCode == RESULT_LOAD_IMG && resultCode == RESULT_OK && null != data) {
                // Get the Image from data
                Uri selectedImage = data.getData();
                String[] filePathColumn = {MediaStore.Images.Media.DATA};
                // Get the cursor
                Cursor cursor = getContentResolver().query(selectedImage, filePathColumn, null, null, null);
                cursor.moveToFirst();
                int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
                mTestImgPath = cursor.getString(columnIndex);
                cursor.close();
                if (mTestImgPath != null) {
                    runDetectAsync(mTestImgPath);
                    Toast.makeText(this, "Img Path:" + mTestImgPath, Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "You haven't picked Image", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Something went wrong", Toast.LENGTH_LONG).show();
        }
    }

    protected void runDetectAsync(@NonNull String imgPath) {
        String path = Environment.getExternalStorageDirectory().getPath();
        int index = 0;
        while(true)
        {
            index++;
            if(index > 10138)
            {
                try{
                    FileOutputStream fout =new FileOutputStream(savePath);
                    byte[] buffer = value.getBytes();
                    fout.write(buffer);
                }catch(Exception e)
                {
                }
                return;
            }
            imgPath = path + File.separator + "face_img" + File.separator + index +".jpg";

          //  imageview.clearAll();
            final String targetPath = Constants.getFaceShapeModelPath();
            if (!new File(targetPath).exists()) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity2.this, "Copy landmark model to " + targetPath, Toast.LENGTH_SHORT).show();
                    }
                });
                FileUtils.copyFileFromRawToOthers(getApplicationContext(), R.raw.shape_predictor_68_face_landmarks, targetPath);
            }

            if(!new File(imgPath).exists())
            {
                Log.i("test","文件不存在" + index + ".jpg");
            }

            // Init
            if (faceDet == null) {
                faceDet = new FaceDet(Constants.getFaceShapeModelPath());
            }

            //List<Card> cardrets = new ArrayList<>();
            List<VisionDetRet> faceList = faceDet.detect(imgPath);
            for(int i=0;i<faceList.size();i++)
            {
                String result = "";
                VisionDetRet ret = faceList.get(i);
                ArrayList<Point> landmarks = ret.getFaceLandmarks();
                //参考数据A
                double disA = Math.sqrt(Math.pow((landmarks.get(12).x-landmarks.get(6).x),2) + Math.pow((landmarks.get(12).y-landmarks.get(6).y),2));
                double disB = Math.sqrt(Math.pow((landmarks.get(17).x-landmarks.get(1).x),2) + Math.pow((landmarks.get(17).y-landmarks.get(1).y),2));
                double resultA = disA / disB;

                //参考数据B
                double maxX = 0;
                double minX = 0;
                double maxY = 0;
                double minY = 0;

                for(int j=0;j<landmarks.size();j++)
                {
                    if(landmarks.get(j).x > maxX)
                    {
                        maxX = landmarks.get(j).x;
                    }

                    if(landmarks.get(j).x < minX)
                    {
                        minX = landmarks.get(j).x;
                    }

                    if(landmarks.get(j).y > maxY)
                    {
                        maxY = landmarks.get(j).y;
                    }

                    if(landmarks.get(j).y < minY)
                    {
                        minY = landmarks.get(j).y;
                    }
                }

                double width = maxX - minX;
                double heigh = maxY - minY;
                double resultB = width /heigh;
                result = index+".jpg" + "   " + resultA + "    " + resultB;

                value += result + "\n";

                Log.i("test", result + "");
            }

            try{
                FileOutputStream fout =new FileOutputStream(savePath);
                byte[] buffer = value.getBytes();
                fout.write(buffer);
            }catch (Exception e)
            {}
        }
//        if (faceList.size() > 0) {
//            Card card = new Card.Builder(MainActivity2.this)
//                    .withProvider(BigImageCardProvider.class)
//                    .setDrawable(drawRect(imgPath, faceList, Color.GREEN))
//                    .setTitle("Face det")
//                    .endConfig()
//                    .build();
//            cardrets.add(card);
//        } else {
//            runOnUiThread(new Runnable() {
//                @Override
//                public void run() {
//                    Toast.makeText(getApplicationContext(), "No face", Toast.LENGTH_LONG).show();
//                }
//            });
//        }
//        addCardListView(cardrets);
    }


    protected BitmapDrawable drawRect(String path, List<VisionDetRet> results, int color) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 1;
        Bitmap bm = BitmapFactory.decodeFile(path, options);
        Bitmap.Config bitmapConfig = bm.getConfig();
        // set default bitmap config if none
        if (bitmapConfig == null) {
            bitmapConfig = Bitmap.Config.ARGB_8888;
        }
        // resource bitmaps are imutable,
        // so we need to convert it to mutable one
        bm = bm.copy(bitmapConfig, true);
        int width = bm.getWidth();
        int height = bm.getHeight();
        // By ratio scale
        float aspectRatio = bm.getWidth() / (float) bm.getHeight();

        final int MAX_SIZE = 512;
        int newWidth = MAX_SIZE;
        int newHeight = MAX_SIZE;
        float resizeRatio = 1;
        newHeight = Math.round(newWidth / aspectRatio);
        if (bm.getWidth() > MAX_SIZE && bm.getHeight() > MAX_SIZE) {
            bm = getResizedBitmap(bm, newWidth, newHeight);
            resizeRatio = (float) bm.getWidth() / (float) width;
        }

        // Create canvas to draw
        Canvas canvas = new Canvas(bm);
        Paint paint = new Paint();
        paint.setColor(color);
        paint.setStrokeWidth(2);
        paint.setStyle(Paint.Style.STROKE);
        // Loop result list
        for (VisionDetRet ret : results) {
            Rect bounds = new Rect();
            bounds.left = (int) (ret.getLeft() * resizeRatio);
            bounds.top = (int) (ret.getTop() * resizeRatio);
            bounds.right = (int) (ret.getRight() * resizeRatio);
            bounds.bottom = (int) (ret.getBottom() * resizeRatio);

            canvas.drawRect(bounds, paint);
            // Get landmark
            ArrayList<Point> landmarks = ret.getFaceLandmarks();
            for (Point point : landmarks) {
                int pointX = (int) (point.x * resizeRatio);
                int pointY = (int) (point.y * resizeRatio);
                 canvas.drawCircle(pointX, pointY, 2, paint);
            }
        }

        return new BitmapDrawable(getResources(), bm);
    }

    protected void addCardListView(List<Card> cardrets) {
        for (Card each : cardrets) {
            imageview.add(each);
        }
    }

    protected Bitmap getResizedBitmap(Bitmap bm, int newWidth, int newHeight) {
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bm, newWidth, newHeight, true);
        return resizedBitmap;
    }
}
