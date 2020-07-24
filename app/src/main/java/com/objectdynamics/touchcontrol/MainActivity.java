package com.objectdynamics.touchcontrol;

import android.app.Activity;
import android.os.AsyncTask;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MainActivity extends AppCompatActivity {

    ServerSocket server=null;
    Socket client=null;
    DataOutputStream dOut =null;

    Runnable connectRunnable = new Runnable() {
        @Override
        public void run() {
            try{
                client=null;
                if(dOut!=null)
                    dOut.close();
                if(server==null){
                    server = new ServerSocket(3322);
                    System.out.println("Servidor iniciado na porta 3322");
                }
                client = server.accept();
                System.out.println("Cliente conectado do IP "+client.getInetAddress().getHostAddress());
            }catch (IOException e){
                System.out.println(e.getMessage());
            }
        }
    };

    Thread connectThread=null;
    private void bindUser(){
        System.out.println("binding");
        if(connectThread != null)
            if(connectThread.isAlive())
                return;

        if(client!=null)
            if(client.isConnected())
                return;
        Thread t = new Thread(connectRunnable);
        t.start();
    }

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

        bindUser();

        /*server.close();
        client.close();
        dOut.close();*/



    }
    int index;
    int action;
    int pointerID;
    String sned;
    @Override
    public boolean dispatchTouchEvent(MotionEvent event){
        if(client == null) return true;
        if(!client.isConnected()) {
            bindUser();
            return true;
        }
        index = event.getActionIndex();
        action = event.getActionMasked();
        pointerID = event.getPointerId(index);
//        System.err.print("Pack==");
//        System.err.print(ii+"-> "+index+" "+action+" "+pointerID+" ("+(int)event.getX()+","+(int)event.getY()+")");
//        System.err.println("==endpack");
        try{
            if(client==null||server==null)
                throw new Exception("Client closed");

            if(!client.isConnected())
                throw new Exception("Client disconnected");
            if(dOut==null) {
                dOut = new DataOutputStream(client.getOutputStream());
                dOut.writeByte(3);
            }
            sned="c"+pointerID+";";
            switch (action){
                case MotionEvent.ACTION_DOWN:
                    //dOut.writeUTF("0-"+pointerID+";"+((int)event.getX())+","+((int)event.getY()));
                    //dOut.writeInt(0);
                    sned += "0";
                break;
                case MotionEvent.ACTION_CANCEL:
                    //dOut.writeInt(3);
                    sned += "3";
                break;
                case MotionEvent.ACTION_UP:
                    //dOut.writeInt(1);
                    sned += "1";
                break;
                case MotionEvent.ACTION_MOVE:
                    //dOut.writeInt(2);
                    sned += "2";
                break;
            }
            sned+=";"+((int)event.getX())+";"+((int)event.getY())+";d";
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
            bindUser();
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