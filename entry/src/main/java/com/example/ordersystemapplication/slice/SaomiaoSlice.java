package com.example.ordersystemapplication.slice;

import com.example.ordersystemapplication.data.Result;
import com.example.ordersystemapplication.domain.Customer;
import com.example.ordersystemapplication.domain.Order;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.example.ordersystemapplication.ResourceTable;
import com.zzrv5.mylibrary.ZZRCallBack;
import com.zzrv5.mylibrary.ZZRHttp;
import net.sourceforge.zbar.*;
import ohos.aafwk.ability.AbilitySlice;
import ohos.aafwk.content.Intent;
import ohos.agp.components.Text;
import ohos.agp.components.surfaceprovider.SurfaceProvider;
import ohos.agp.graphics.Surface;
import ohos.agp.graphics.SurfaceOps;
import ohos.agp.window.dialog.ToastDialog;
import ohos.eventhandler.EventHandler;
import ohos.eventhandler.EventRunner;
import ohos.media.camera.CameraKit;
import ohos.media.camera.device.Camera;
import ohos.media.camera.device.CameraConfig;
import ohos.media.camera.device.CameraStateCallback;
import ohos.media.camera.device.FrameConfig;
import ohos.media.common.BufferInfo;
import ohos.media.image.ImageReceiver;
import ohos.media.image.common.ImageFormat;

import java.nio.ByteBuffer;

import static ohos.media.camera.device.Camera.FrameConfigType.FRAME_CONFIG_PREVIEW;

public class SaomiaoSlice extends AbilitySlice{
    private ImageScanner scanner;
    private ImageReceiver imageReceiver;
    private CameraKit cameraKit;
    private Surface previewSurface;
    private Surface dataSurface;
    private Camera mcamera;
    private Text scanText;
    private EventHandler handler;
    SurfaceProvider surfaceProvider;
    public static final int VIDEO_WIDTH = 640;
    public static final int VIDEO_HEIGHT = 480;
    String tableId;
    private Customer customer;
    private Gson gson = new GsonBuilder().serializeNulls().setDateFormat("yyyy-MM-dd").create();
    @Override
    public void onStart(Intent intent) {
        super.onStart(intent);
        super.setUIContent(ResourceTable.Layout_ability_zbar);

        customer = intent.getSerializableParam("my");

        handler = new EventHandler(EventRunner.getMainEventRunner());
        scanner = new ImageScanner();
        scanner.setConfig(0, Config.X_DENSITY, 3);
        scanner.setConfig(0, Config.Y_DENSITY, 3);
        //?????????UI????????????????????????????????????

        surfaceProvider = (SurfaceProvider) findComponentById(ResourceTable.Id_zbar_surfaceprovider);
        surfaceProvider.getSurfaceOps().get().addCallback(new SurfaceOps.Callback() {
            @Override
            public void surfaceCreated(SurfaceOps surfaceOps) {
                previewSurface = surfaceOps.getSurface();
                openCamera();
            }
            @Override
            public void surfaceChanged(SurfaceOps surfaceOps, int i, int i1, int i2) {
            }

            @Override
            public void surfaceDestroyed(SurfaceOps surfaceOps) {
            }
        });
        surfaceProvider.pinToZTop(true);

        scanText =(Text) findComponentById(ResourceTable.Id_zbar_text);
        //??????????????????????????????????????????
        imageReceiver = ImageReceiver.create(VIDEO_WIDTH, VIDEO_HEIGHT, ImageFormat.YUV420_888, 10);
        imageReceiver.setImageArrivalListener( new IImageArrivalListenerImpl());
    }

    @Override
    public void onActive() {
        super.onActive();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    private class IImageArrivalListenerImpl implements ImageReceiver.IImageArrivalListener {
        //????????????????????????????????????????????????????????????????????????????????????
        @Override
        public void onImageArrival(ImageReceiver imageReceiver) {

            ohos.media.image.Image mImage = imageReceiver.readNextImage();
            if (mImage != null) {
                BufferInfo bufferInfo = new BufferInfo();
                ByteBuffer mBuffer;
                byte[] YUV_DATA = new byte[VIDEO_HEIGHT * VIDEO_WIDTH * 3 / 2];
                int i;
                //??????YUV????????????
                mBuffer = mImage.getComponent(ImageFormat.ComponentType.YUV_Y).getBuffer();
                for (i = 0; i < VIDEO_WIDTH * VIDEO_HEIGHT; i++) {
                    YUV_DATA[i] = mBuffer.get(i);
                }
                mBuffer = mImage.getComponent(ImageFormat.ComponentType.YUV_V).getBuffer();
                for (i = 0; i < VIDEO_WIDTH * VIDEO_HEIGHT / 4; i++) {
                    YUV_DATA[(VIDEO_WIDTH * VIDEO_HEIGHT) + i * 2] =
                            mBuffer.get(i * 2);
                }
                mBuffer = mImage.getComponent(ImageFormat.ComponentType.YUV_U).getBuffer();
                for (i = 0; i < VIDEO_WIDTH * VIDEO_HEIGHT / 4; i++) {
                    YUV_DATA[(VIDEO_WIDTH * VIDEO_HEIGHT) + i * 2 + 1] = mBuffer.get(i * 2);
                }
                bufferInfo.setInfo(0, VIDEO_WIDTH * VIDEO_HEIGHT * 3 / 2, mImage.getTimestamp(), 0);
                Image barcode = new Image(mImage.getImageSize().width, mImage.getImageSize().height, "Y800");
                barcode.setData(YUV_DATA);


                if (scanner.scanImage(barcode) != 0 && tableId==null) {
                    for (Symbol sym1 : scanner.getResults()) {
                        if(sym1!=null){
                            tableId=sym1.getData();
                        }
                    }
                    handler.postTask(new Runnable() {
                        @Override
                        public void run() {
                            System.out.println(tableId);
                            Order order = new Order();
                            order.setCustPhone(customer.getCustPhone());
                            order.setTableId(tableId);
                            String json = gson.toJson(order);
                            System.out.println("??????json"+json);
                            ZZRHttp.postJson("http://101.132.74.147:8082/order/takeOrder", json, new ZZRCallBack.CallBackString() {
                                @Override
                                public void onFailure(int i, String s) {
                                    new ToastDialog(SaomiaoSlice.this)
                                            .setText("???????????????")
                                            .show();
                                }
                                @Override
                                public void onResponse(String s) {
                                    Result result1 = gson.fromJson(s, Result.class);
                                    String json = gson.toJson(result1.getResult());
                                    Order order1;
                                    order1 = gson.fromJson(json,Order.class);
                                    Intent intent = new Intent();
                                    intent.setParam("Order",order1);
                                    intent.setParam("Customer",customer);
                                    present(new OrderFoodAbilitySlice(), intent);
                                }
                            });

                        }
                    });
                }
                mImage.release();
                return;
            }
        }
    }
    private void openCamera(){
        // ?????? CameraKit ??????
        cameraKit = CameraKit.getInstance(this);
        if (cameraKit == null) {
            return;
        }
        try {
            // ???????????????????????????????????????cameraIds
            String[] cameraIds = cameraKit.getCameraIds();
            // ???????????????
            cameraKit.createCamera(cameraIds[0], new CameraStateCallbackImpl(), new EventHandler(EventRunner.create("CameraCb")));
        } catch (IllegalStateException e) {
            System.out.println("getCameraIds fail");
        }
    }


    private final class CameraStateCallbackImpl extends CameraStateCallback {
        //????????????
        @Override
        public void onCreated(Camera camera) {
            mcamera = camera;
            //?????????????????????
            CameraConfig.Builder cameraConfigBuilder = camera.getCameraConfigBuilder();
            if (cameraConfigBuilder == null) { return; }
            // ??????????????? Surface
            cameraConfigBuilder.addSurface(previewSurface);
            // ??????????????? Surface
            dataSurface = imageReceiver.getRecevingSurface();
            cameraConfigBuilder.addSurface(dataSurface);
            try {
                // ??????????????????
                camera.configure(cameraConfigBuilder.build());
            } catch (IllegalArgumentException e) {
                System.out.println("Argument Exception");
            } catch (IllegalStateException e) {
                System.out.println("State Exception");
            }
        }
        @Override
        public void onConfigured(Camera camera) {
            FrameConfig.Builder frameConfigBuilder = mcamera.getFrameConfigBuilder(FRAME_CONFIG_PREVIEW);
            // ???????????? Surface
            frameConfigBuilder.addSurface(previewSurface);
            // ??????????????? Surface
            frameConfigBuilder.addSurface(dataSurface);
            try {
                // ?????????????????????
                mcamera.triggerLoopingCapture(frameConfigBuilder.build());
            } catch (IllegalArgumentException e) {
                System.out.println("Argument Exception");
            } catch (IllegalStateException e) {
                System.out.println("State Exception");
            }
            //????????????
        }
        @Override
        public void onReleased(Camera camera) {
            // ??????????????????
            if (mcamera != null) {
                mcamera.stopLoopingCapture();
                mcamera.release();
                mcamera = null;
            }
        }
    }

    @Override
    protected void onBackground() {
        if (mcamera != null) {
            mcamera.stopLoopingCapture();
            mcamera.release();
            mcamera = null;
        }
        surfaceProvider.removeFromWindow();
        SaomiaoSlice.super.terminate();
    }

    @Override
    public void onForeground(Intent intent) {
        if (mcamera != null) {
            mcamera.stopLoopingCapture();
            mcamera.release();
            mcamera = null;
        }
        SaomiaoSlice.super.terminate();
    }
}
