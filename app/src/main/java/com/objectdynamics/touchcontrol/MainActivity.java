package com.objectdynamics.touchcontrol;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.Buffer;
import java.util.zip.Inflater;

public class MainActivity extends AppCompatActivity {

    private static int[] scrDimensionsList = new int[2];
    private static int screenFPS=0;

    private static ServerSocket touchSocket = null;
    private static Socket touchClient = null;
    private static DataOutputStream t_dOut = null;
    //=====
    private static ServerSocket screenSocket =null;
    private static Socket screenClient=null;
    private static DataInputStream scrDIS=null;
    private static Inflater decompressor=null;
    private static Bitmap btm;
    private static BitmapFactory.Options options = new BitmapFactory.Options();
    private static byte[] scrDout = new byte[0];
    private static int comprDataLength = 0,decompDataLen = 0;
    private static Canvas lckCanvas=null;

    // https://stackoverflow.com/questions/37443481/how-to-draw-buffer-to-a-textureview-on-android
    // IMPLEMENT THIS ASAP
    // copied to mouseBehavior
    private static boolean isDrawing=false;
    private static TextureView textureView=null;
    //private static SurfaceTexture surfaceTexture=null;

    //https://stackoverflow.com/questions/6116880/stream-live-video-from-phone-to-phone-using-socket-fd
    //https://www.google.com/search?q=java+Socket+stream+pc+screen+to+android+videofeed

    /*private void drawingOnScreen(final byte[] scrDout){
        runOnUiThread(new Runnable() {
        @Override
            public void run() {
            imgv.setImageBitmap(BitmapFactory.decodeByteArray(scrDout, 0, scrDout.length));
            }
        });
    }*/

    Runnable asyncDrawingRunnable = new Runnable() {
        @Override
        public void run() {
        isDrawing=true;
        try{
            btm.recycle();
        }catch (Exception e){ }
        btm=BitmapFactory.decodeByteArray(scrDout,0,decompDataLen,options);
        lckCanvas=textureView.lockCanvas();
        if (lckCanvas != null){
            System.out.println("Drawing on screen ");
            lckCanvas.drawBitmap(btm, 0, 0, null);
            textureView.unlockCanvasAndPost(lckCanvas);
            screenFPS++;
        }
        isDrawing=false;
        }
    };

    private void asyncDrawing(){
        if(!isDrawing){
            Thread t=new Thread(asyncDrawingRunnable);
            t.start();
        }
    }

    Runnable screenServerRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                if (screenClient != null) screenClient.close();
                screenClient = null;
                if (scrDIS != null) scrDIS.close();
                if (screenSocket == null) {
                    screenSocket = new ServerSocket(3323);
                    System.out.println("Screen Server initialized at 3323");
                }
                screenClient = screenSocket.accept();
                System.out.println("Screen Server Connected");
                scrDIS = new DataInputStream(screenClient.getInputStream());
                decompressor = new Inflater();
                byte[] buffer = new byte[8192];
                byte[] compressedbb=new byte[0];
                textureView = findViewById(R.id.textureView);
                options.inMutable=true;

                // https://developer.android.com/reference/kotlin/android/view/TextureView
                //https://stackoverflow.com/questions/2732260/in-java-when-i-call-outputstream-close-do-i-always-need-to-call-outputstream
                //System.out.println("Screen Data set");
                while (screenClient.isConnected() && screenClient.isBound() && !screenClient.isClosed()) {
                    //System.out.println("Screen buffer Loop");

                    decompDataLen=scrDIS.readInt();
                    comprDataLength = scrDIS.readInt();
                    if(scrDout.length < decompDataLen){
                        if(decompDataLen>=30000000)
                            throw new Exception("Tamanho invalido");
                        scrDout = new byte[decompDataLen];
                    }
                    if(comprDataLength > compressedbb.length) {
                        if (comprDataLength >= 30000000)
                            throw new Exception("Tamanho invalido");
                        compressedbb = new byte[comprDataLength];// GC LAG
                    }
                    scrDIS.readFully(compressedbb, 0, comprDataLength);
                    int ii=0;
                    if(!isDrawing) {
                        decompressor.setInput(compressedbb, 0, comprDataLength);
                        while (!decompressor.finished()) {
                            int count = decompressor.inflate(buffer);
                            System.arraycopy(buffer, 0, scrDout, ii, count);
                            ii += count;
                        }
                        asyncDrawing();
                        decompressor.reset();
                    }
                }
            }catch (Exception e){
                e.printStackTrace();
            }finally {
                fpsThread.interrupt();
            }
        }
    };

    Thread sendVideo=null;
    private boolean bindUserScreen() {
        if (screenClient != null) if (screenClient.isConnected()||!screenClient.isClosed()) return true;
        sendVideo = new Thread(screenServerRunnable);
        sendVideo.start();
        return false;
    }



    Runnable conTouchServerRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                if (touchClient != null) touchClient.close();
                touchClient = null;
                if (t_dOut != null) t_dOut.close();
                if (touchSocket == null) {
                    scrDimensionsList[0] = findViewById(R.id.mainwindow).getWidth();
                    scrDimensionsList[1] = findViewById(R.id.mainwindow).getHeight();
                    touchSocket = new ServerSocket(3322);
                    System.out.println("Touch Server initialized at 3322");
                }
                touchClient = touchSocket.accept();
                System.out.println("TouchServer connected IP " + touchClient.getInetAddress().getHostAddress());
            } catch (IOException e) {
                System.out.println(e.getMessage());
            }
        }
    };
    Thread connectThread = null;
    private boolean bindUserTouch() {
        if (touchClient != null) if (!touchClient.isClosed() && !touchClient.isClosed()) return false;
        if (connectThread != null) if (connectThread.isAlive()) return true;
        System.out.println("binding touch");
        connectThread = new Thread(conTouchServerRunnable);
        connectThread.start();
        return true;
    }


    Thread fpsThread=null;
    private void fpsThread(){
        if(fpsThread==null){
            fpsThread=new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        while (true) {
                            System.out.println("FPS: " + screenFPS);
                            screenFPS=0;
                            Thread.sleep(1000);
                        }
                    }catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            });
            fpsThread.start();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        /*requestWindowFeature(Window.FEATURE_NO_TITLE); //will hide the title
        getSupportActionBar().hide(); // hide the title bar
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN); //enable full screen
        //carrega o layout*/
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE);

        fpsThread();
        //bindUserTouch();
        bindUserScreen();
        /*server.close();
        client.close();
        dOut.close();*/
    }

    int pointerID;
    int actionPointerID;
    int[] dataList = new int[5];
    int[][] coordList = new int[dataList.length][2];
    int pointerCount = 0;
    String sned;

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (bindUserTouch()) return true;
//        System.err.print("Pack==");
//        System.err.print(ii+"-> "+index+" "+action+" "+pointerID+" ("+(int)event.getX()+","+(int)event.getY()+")");
//        System.err.println("==endpack");
        try {
            if (touchClient == null || touchSocket == null)
                throw new Exception("Client closed");

            if (!touchClient.isConnected())
                throw new Exception("Client disconnected");
            if (t_dOut == null) {
                t_dOut = new DataOutputStream(touchClient.getOutputStream());
            }

            actionPointerID = (event.getAction() & MotionEvent.ACTION_POINTER_INDEX_MASK) >> MotionEvent.ACTION_POINTER_INDEX_SHIFT;
            dataList[actionPointerID] = event.getPointerId(actionPointerID);
            //System.out.print(actionPointerID+".."+event.getActionMasked()+"-> ");
            //======================================================
            sned = "c";
            for (int ii = 0; ii < event.getPointerCount(); ii++) {
                pointerID = event.getPointerId(ii);
                coordList[pointerID][0] = (int) event.getX(ii);
                coordList[pointerID][1] = (int) event.getY(ii);
                sned += pointerID + ";";
                if (ii == actionPointerID) {
                    switch (event.getAction() & MotionEvent.ACTION_MASK) {
                        case MotionEvent.ACTION_POINTER_DOWN:
                        case MotionEvent.ACTION_DOWN:
                            dataList[ii] = 0;
                            break;
                        case MotionEvent.ACTION_CANCEL:
                            dataList[ii] = 3;
                            break;
                        case MotionEvent.ACTION_POINTER_UP:
                        case MotionEvent.ACTION_UP:
                            dataList[ii] = 1;
                            break;
                        case MotionEvent.ACTION_HOVER_MOVE:
                        case MotionEvent.ACTION_MOVE:
                            dataList[ii] = 2;
                            break;
                        default:
                            dataList[ii] = -1;
                    }
                }
                sned += dataList[ii] + ";" + coordList[ii][0] + ";" + coordList[ii][1] + ";|";
            }
            sned += "d";
            /*if(1==1){
                System.out.println(sned);
                return true;
            }*/
            t_dOut.writeBytes(sned);
            System.out.println(sned);
            t_dOut.flush(); // Send off the data
        } catch (SocketException e) {
            try {
                touchClient.close();
                t_dOut.close();
                touchClient = null;
                t_dOut = null;
            } catch (IOException ex) {
                ex.printStackTrace();
>>>>>>> Stashed changes
            }
        });
    }
<<<<<<< Updated upstream
=======
}

//        MotionEvent.ACTION_DOWN
//        MotionEvent.ACTION_UP
//        MotionEvent.ACTION_MOVE
//        MotionEvent.ACTION_CANCEL
//        MotionEvent.ACTION_OUTSIDE

/*
https://stackoverflow.com/questions/29009210/texture-view-video-and-bitmap-display
https://stackoverflow.com/questions/16916877/whats-differences-between-surfaceview-and-textureview
https://github.com/dalinaum/TextureViewDemo/blob/master/src/kr/gdg/android/textureview/CameraActivity.java
https://www.google.com/search?q=android+GLES+videoView
https://stackoverflow.com/questions/14590852/android-textureview-vs-videoview-performance
https://stackoverflow.com/questions/12519235/modifying-camera-output-using-surfacetexture-and-opengl
 */


    /*Runnable screenServerRunnable = new Runnable() {
        @Override
        public void run() {
            try{
                screenServer = new DatagramSocket(3323);
                imgv=findViewById(R.id.imageView);
                byte[] receiveData = new byte[50000];
                scripadd=InetAddress.getByName("127.0.0.1");
                screenClient=screenServer.accept();
                scrDIS=new DataInputStream(screenClient.getInputStream());
                decompressor=new Inflater();
                scrDout=new ByteArrayOutputStream();
                while(screenServer.isBound()&&!screenServer.isClosed()) {
                    DatagramPacket dgp = new DatagramPacket(receiveData,receiveData.length,scripadd,3323);
                    System.out.println("Awaiting package");
                    screenServer.receive(dgp);
                    decompressor.setInput(dgp.getData());
                    System.out.println("received Compressed "+dgp.getData().length);
                    byte[] buffer = new byte[2048];
                    while (!decompressor.finished()) { int count = decompressor.inflate(buffer);scrDout.write(buffer, 0, count); }
                    scrDout.close();
                    byte[] output = scrDout.toByteArray();
                    System.out.println("decompressed to "+output.length);
                    scrDout.reset();
                    System.out.println("Drawing on screen");
                    try{ drawOnScreen(BitmapFactory.decodeByteArray(output,0,output.length));
                        }catch (Exception e){ e.printStackTrace(); }
                    System.out.println("Drawn on screen");
                }
            }catch (Exception e){
                System.out.println(e.getMessage());
                e.printStackTrace();
            }
        }
    };*/

    /*Runnable screenServerRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                screenServer = new Socket("127.0.0.1",3323);
                ParcelFileDescriptor pfd = ParcelFileDescriptor.fromSocket(screenServer);
                pfd.getFileDescriptor().sync();
                mp.setDataSource(pfd.getFileDescriptor());
                pfd.close();
                mp.setDisplay(holder);
                mp.prepareAsync();
                mp.start();
            }catch (IOException e){
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    };*/

//=============================================

                while (screenClient.isConnected()&&screenClient.isBound()&&!screenClient.isClosed()){
                    comprDataLength=scrDIS.readInt();
                    compressedbb=new byte[comprDataLength];
                    scrDIS.readFully(compressedbb,0,comprDataLength);
                    decompressor.setInput(compressedbb);
                    //System.out.println("received Compressed: "+comprDataLength+" total, doing: " +decompressor.getBytesRead()+" "+decompressor.getBytesWritten());
                    while (!decompressor.finished()) {
                        int count = decompressor.inflate(buffer);
                        scrDout.write(buffer, 0, count); }
                    //System.out.println("Drawing on screen");
                    drawOnScreen(BitmapFactory.decodeByteArray(scrDout.toByteArray(),0, scrDout.size()));

                    buffer = new byte[8192];
                    scrDout.flush();
                    scrDout.reset();
                    decompressor.reset();
                }
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    };

    Runnable conTouchServerRunnable = new Runnable() {
        @Override
        public void run() {
            try{
                dimensionsList[0]= findViewById(R.id.mainwindow).getWidth();
                dimensionsList[1]=findViewById(R.id.mainwindow).getHeight();
                if(client!=null) client.close();
                client=null;
                if(dOut!=null) dOut.close();
                if(touchServerSocket ==null){
                    touchServerSocket = new ServerSocket(3322);
                    System.out.println("Touch Server initialized at 3322");
                }
                client = touchServerSocket.accept();
                System.out.println("TouchServer connected IP "+client.getInetAddress().getHostAddress());
            }catch (IOException e){
                System.out.println(e.getMessage());
            }
        }
    };

    Thread connectThread=null;
    private boolean bindUserTouch(){
        if(client!=null) if(!client.isClosed()&&!client.isClosed()) return false;
        if(connectThread != null) if(connectThread.isAlive()) return true;
        System.out.println("binding touch");
        connectThread = new Thread(conTouchServerRunnable);
        connectThread.start();
        return true;
    }

    Thread sendVideo=null;
    private boolean bindUserScreen(){
        if(sendVideo != null) if(sendVideo.isAlive()) return true;

        mPreview = (SurfaceView) findViewById(R.id.surfaceView);
        holder = mPreview.getHolder();
        //holder.addCallback(this);
        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        mp = new MediaPlayer();

        sendVideo = new Thread(screenServerRunnable);
        sendVideo.start();
        return false;
    }
=======
//=============================================
>>>>>>> Stashed changes

    /*Thread screenServerThread=null;
    private boolean bindUserScreen(){
        if(clientScreen!=null) if(!clientScreen.isBound()&&!clientScreen.isClosed()) return false;
        if(screenServerThread!=null) if(screenServerThread.isAlive()) return true;
        System.out.println("binding screen");
        screenServerThread = new Thread(screenDataServerRunnable);
        screenServerThread.start();
        return true;
    }*/

    /*private void drawOnScreen(final Bitmap btm){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                imgv.setImageBitmap(Bitmap.createScaledBitmap(btm, 800, 480, false));
                System.out.println("Drawn: "+iip);
                iip++;
            }
        });
    }
    static int iip=0;
    Runnable screenCastingServiceRunnable = new Runnable() {
        @Override
        public void run() {
            int base64Length=0,b64s=0;
            String base64Input="";int cbyte=0;
            byte[] decodedString;
            GZIPInputStream dIsg;
            ByteArrayInputStream baIsg;
            try{
                imgv = ((ImageView)findViewById(R.id.displayScreen));
                if(dIs==null) dIs = new DataInputStream(clientScreen.getInputStream());
                while(!clientScreen.isClosed()){
                    cbyte=dIs.readByte();
                    if(cbyte==-25) {
                        base64Length = Integer.parseInt(base64Input);
                        System.out.println("Drawing");
                        base64Input = "";
                        byte[] bbcmp = new byte[base64Length];
                        dIs.readFully(bbcmp,0,base64Length);

                        dIsg = new GZIPInputStream(new ByteArrayInputStream(bbcmp));
                        BufferedReader bf = new BufferedReader(new InputStreamReader(dIsg, StandardCharsets.ISO_8859_1));
                        String line;
                        while ((line=bf.readLine())!=null)
                            base64Input += line;
                        System.out.println("Output String length : " + base64Input.length());

                        decodedString = Base64.decode(base64Input, Base64.DEFAULT);
                        drawOnScreen(BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length));
                        decodedString=null;base64Input="";
                        //System.gc();
                    }else{base64Input+=(char)cbyte;}
                }
            }catch (EOFException e) {
                System.err.println("Data Input (ScreenCast) not avaliable / properly connected");
                try { if(clientScreen!=null)client.close(); } catch (IOException ex) { ex.printStackTrace(); }
                try { if(dIs!=null)client.close(); } catch (IOException ex) { ex.printStackTrace(); }
            }catch (IOException e) {
                e.printStackTrace();
            }catch (Exception e){
                System.err.println(e);
                e.printStackTrace();
            }
        }
    };
    Thread castingScreenThread=null;
    private void runScreenCastingService(){
        if(castingScreenThread!=null) if(castingScreenThread.isAlive()) return;
        castingScreenThread=new Thread(screenCastingServiceRunnable);
        castingScreenThread.start();
    }*/

    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        /*requestWindowFeature(Window.FEATURE_NO_TITLE); //will hide the title
        getSupportActionBar().hide(); // hide the title bar
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN); //enable full screen
        //carrega o layout*/
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE);
        //bindUserTouch();
        bindUserScreen();
        /*server.close();
        client.close();
        dOut.close();*/
    }
    int pointerID;
    int actionPointerID;
    int[] dataList = new int[5];
    int[][] coordList = new int[dataList.length][2];
    int pointerCount=0;
    String sned;
    @Override
    public boolean dispatchTouchEvent(MotionEvent event){
        //if(bindUserTouch())return true;
        //if(bindUserScreen())return true;
//        System.err.print("Pack==");
//        System.err.print(ii+"-> "+index+" "+action+" "+pointerID+" ("+(int)event.getX()+","+(int)event.getY()+")");
//        System.err.println("==endpack");
        try{
            if(client==null|| touchServerSocket ==null)
                throw new Exception("Client closed");

            if(!client.isConnected())
                throw new Exception("Client disconnected");
            if(dOut==null) {
                dOut = new DataOutputStream(client.getOutputStream());
            }

            actionPointerID = (event.getAction() & MotionEvent.ACTION_POINTER_INDEX_MASK) >> MotionEvent.ACTION_POINTER_INDEX_SHIFT;
            dataList[actionPointerID] = event.getPointerId(actionPointerID);
            //System.out.print(actionPointerID+".."+event.getActionMasked()+"-> ");
            //======================================================
            sned="c";
            for(int ii=0;ii < event.getPointerCount();ii++){
                pointerID=event.getPointerId(ii);
                coordList[pointerID][0]=(int)event.getX(ii);
                coordList[pointerID][1]=(int)event.getY(ii);
                sned+=pointerID+";";
                if(ii == actionPointerID){
                    switch (event.getAction() & MotionEvent.ACTION_MASK){
                        case MotionEvent.ACTION_POINTER_DOWN:
                        case MotionEvent.ACTION_DOWN:
                            dataList[ii]=0;break;
                        case MotionEvent.ACTION_CANCEL:
                            dataList[ii]=3;break;
                        case MotionEvent.ACTION_POINTER_UP:
                        case MotionEvent.ACTION_UP:
                            dataList[ii]=1;break;
                        case MotionEvent.ACTION_HOVER_MOVE:
                        case MotionEvent.ACTION_MOVE:
                            dataList[ii]=2;break;
                        default:
                            dataList[ii]=-1;
                    }
                }
                sned+=dataList[ii]+";"+coordList[ii][0]+";"+coordList[ii][1]+";|";
            }
            sned+="d";
            /*if(1==1){
                System.out.println(sned);
                return true;
            }*/
            dOut.writeBytes(sned);
            System.out.println(sned);
            dOut.flush(); // Send off the data
        }catch (SocketException e){
            try {
                client.close();
                dOut.close();
                client=null;
                dOut=null;
            } catch (IOException ex) {
                ex.printStackTrace();
            }
            bindUserTouch();
            bindUserScreen();
            return true;
        } catch (Exception e) {
            System.err.println(e);
            e.printStackTrace();
        }
        finally {
            return true;
        }
//        MotionEvent.ACTION_DOWN
//        MotionEvent.ACTION_UP
//        MotionEvent.ACTION_MOVE
//        MotionEvent.ACTION_CANCEL
//        MotionEvent.ACTION_OUTSIDE
    }

    /*@Override
    public boolean onTouchEvent(MotionEvent event){
        int index = event.getActionIndex();
        int action = event.getActionMasked();
        int action2= event.getAction();
        int pointerID = event.getPointerId(index);
        sendData(index,action2,pointerID,(int)event.getX(),(int)event.getY());
        return true;
    }*/
}