package com.kpa.springbootdemo;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private PermissionListener permissionListener;
    private static final String TAG = MainActivity.class.getSimpleName();
    public static final int REQ_TAKE_PHOTO = 100;
    public static final int REQ_ALBUM = 101;
    public static final int REQ_ZOOM = 102;

    private Uri outputUri;
    private String imgPath;
    private ImageView ivPhoto;
    private Button uploadFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_avatar);

        ivPhoto = (ImageView) findViewById(R.id.iv_photo);

        uploadFile = (Button) findViewById(R.id.uploadFile);
    }


    /**
     * 拍照,使用存储卡路径（需要申请存储权限），即图片的路径在  存储卡目录下 -> 包名 -> icon文件夹下
     */
    public void takePhoto(View view) {
        imgPath = FileUtils.generateImgePathInStoragePath();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            //如果是6.0或6.0以上，则要申请运行时权限,这里需要申请拍照的权限
            requestRuntimePermission(new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, new PermissionListener() {
                @Override
                public void onGranted() {
                    //开启摄像头，拍照完的图片保存在对应路径下
                    openCamera(imgPath);
                }

                @Override
                public void onDenied(List<String> deniedPermissions) {
                    UIUtils.showToast("所需权限被拒绝");
                }
            });
            return;
        }

        openCamera(imgPath);
    }

    /**
     * 打开相册
     */
    public void openAlbum(View view) {
        Intent intent = new Intent(Intent.ACTION_PICK, null);
        intent.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                "image/*");
        startActivityForResult(intent, REQ_ALBUM);
    }

    /**
     * 开启摄像机
     */
    private void openCamera(String imgPath) {
        // 指定调用相机拍照后照片的储存路径
        File imgFile = new File(imgPath);
        Uri imgUri = null;
        if (Build.VERSION.SDK_INT >= 24) {
            //如果是7.0或以上
            imgUri = FileProvider.getUriForFile(this, UIUtils.getPackageName() + ".fileprovider", imgFile);
        } else {
            imgUri = Uri.fromFile(imgFile);
        }
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, imgUri);
        startActivityForResult(intent, REQ_TAKE_PHOTO);
    }

    /**
     * 申请运行时权限
     */
    public void requestRuntimePermission(String[] permissions, PermissionListener permissionListener) {
        this.permissionListener = permissionListener;
        List<String> permissionList = new ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionList.add(permission);
            }
        }

        if (!permissionList.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissionList.toArray(new String[permissionList.size()]), 1);
        } else {
            permissionListener.onGranted();
        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (resultCode) {
            case RESULT_OK://调用图片选择处理成功
                String zoomImgPath = "";
                Bitmap bm = null;
                File temFile = null;
                File srcFile = null;
                File outPutFile = null;
                switch (requestCode) {
                    case REQ_TAKE_PHOTO:// 拍照后在这里回调
                        srcFile = new File(imgPath);
                        outPutFile = new File(FileUtils.generateImgePathInStoragePath());
                        outputUri = Uri.fromFile(outPutFile);
                        FileUtils.startPhotoZoom(this, srcFile, outPutFile, REQ_ZOOM);// 发起裁剪请求
                        break;

                    case REQ_ALBUM:// 选择相册中的图片
                        if (data != null) {
                            Uri sourceUri = data.getData();
                            String[] proj = {MediaStore.Images.Media.DATA};

                            // 好像是android多媒体数据库的封装接口，具体的看Android文档
                            Cursor cursor = managedQuery(sourceUri, proj, null, null, null);

                            // 按我个人理解 这个是获得用户选择的图片的索引值
                            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                            // 将光标移至开头 ，这个很重要，不小心很容易引起越界
                            cursor.moveToFirst();
                            // 最后根据索引值获取图片路径
                            imgPath = cursor.getString(column_index);

                            srcFile = new File(imgPath);
                            outPutFile = new File(FileUtils.generateImgePathInStoragePath());
                            outputUri = Uri.fromFile(outPutFile);
                            FileUtils.startPhotoZoom(this, srcFile, outPutFile, REQ_ZOOM);// 发起裁剪请求
                        }
                        break;

                    case REQ_ZOOM://裁剪后回调
                        //  Bundle extras = data.getExtras();
                        if (data != null) {
                            //  bm = extras.getParcelable("data");
                            if (outputUri != null) {
                                bm = ImageTools.decodeUriAsBitmap(outputUri);
                                //如果是拍照的,删除临时文件
                                temFile = new File(imgPath);
                                if (temFile.exists()) {
                                    temFile.delete();
                                }
                                final String scaleImgPath = FileUtils.saveBitmapByQuality(bm, 80);//复制并压缩到自己的目录并压缩
                                Log.e(TAG, "=========>" + scaleImgPath);
                                //进行上传，上传成功后显示新图片,这里不演示上传的逻辑，上传只需将scaleImgPath路径下的文件上传即可。
//                                uploadFile.setOnClickListener(new View.OnClickListener() {
//                                    @Override
//                                    public void onClick(View v) {
//                                        //uploadFile(scaleImgPath);
//                                        new Thread(new Runnable() {
//                                            @Override
//                                            public void run() {
//                                                uploadFile(scaleImgPath);
////                                                new Test().postParams();
//                                            }
//                                        }).start();
//                                    }
//                                });
                                ivPhoto.setImageBitmap(bm);//显示在iv上
                            }
                        } else {
                            UIUtils.showToast("选择图片发生错误，图片可能已经移位或删除");
                        }
                        break;
                }
                break;
        }
    }

    private void uploadMp4File(String scanleMp4Path) {
        File file1 = new File(scanleMp4Path);
        File file = new File("", file1.getName());

        RequestBody fileBody = RequestBody.create(MediaType.parse("application/octet-stream"), file);

        MultipartBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addPart(Headers.of(
                        "Content-Disposition",
                        "from-data;name=\"username\""
                ), RequestBody.create(null, "kpa"))
                .addPart(Headers.of(
                        "Content-Disposition",
                        "from-data;name\"mFile\"",
                        "filename=\"wjd.mp4\""
                ), fileBody)
                .build();
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        OkHttpClient client = builder.connectTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .build();
        Request request = new Request.Builder()
                .url("")
                .post(requestBody)
                .build();

        Call call = client.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {

            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {

            }
        });


    }

    private void uploadFile(String scaleImgPath) {
        Log.e(TAG, "------->" + scaleImgPath);
        File file1 = new File(scaleImgPath);
        File file = new File("/storage/emulated/0/AndroidNAdaption/icon/", file1.getName());
        RequestBody requestBody = RequestBody.create(MediaType.parse("application/octet-stream"), file);
        MultipartBody multipartBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", file.getName(), requestBody)
                .build();

        Request build = new Request.Builder()
                .url("http://192.168.1.7:9999/uploadPhoto")
                .post(multipartBody)
                .build();

        Log.e("path ----->", build.url().toString());
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        OkHttpClient client = builder.connectTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .build();

        client.newCall(build).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "uploadMultiFile() e=" + e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                Log.i(TAG, "uploadMultiFile() response=" + response.body().string());

            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case 1:
                if (grantResults.length > 0) {
                    List<String> deniedPermissions = new ArrayList<>();
                    for (int i = 0; i < grantResults.length; i++) {
                        int grantResult = grantResults[i];
                        String permission = permissions[i];
                        if (grantResult != PackageManager.PERMISSION_GRANTED) {
                            deniedPermissions.add(permission);
                        }
                    }
                    if (deniedPermissions.isEmpty()) {
                        permissionListener.onGranted();
                    } else {
                        permissionListener.onDenied(deniedPermissions);
                    }
                }
                break;
        }
    }

}
