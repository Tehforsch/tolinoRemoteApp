package com.example.tolinoremote;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.StrictMode;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity implements KeyEvent.Callback {
    private String DefaultServerIP = "192.168.178.248";
    private int ServerPort = 5000;
    private long lastPressTime;
    private Socket socket;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);//  set status text dark
        getWindow().setStatusBarColor(ContextCompat.getColor(MainActivity.this, R.color.colorPrimaryDark));// set status background white
    }

    @Override
    protected void onStart( ) {
        super.onStart();
        initSocket();
    }

    private boolean enoughTimePassed() {
        long time = System.currentTimeMillis();
        if (time > lastPressTime + 2000) {
            lastPressTime = time;
            return true;
        }
        return false;
    }

    public void sendMessageAndCreateSomeSideEffects(String s) {
        if (!enoughTimePassed()) {
            return;
        }

        if (socket == null) {
            initSocket();
        }
        if (socket == null) {
            return;
        }
        try
        {
            PrintWriter outToServer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));
            outToServer.print(s + "\n");
            outToServer.flush();
        }
        catch (UnknownHostException e) {
            initSocket();
            Log.d("SendDebug", e.toString());
        } catch (IOException e) {
            initSocket();
            Log.d("SendDebug", e.toString());
        }catch (Exception e) {
            initSocket();
            Log.d("SendDebug", e.toString());
        }
    }


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        Log.d("Remote","Key pressed: "+event.getKeyCode());
        if ((keyCode == KeyEvent.KEYCODE_VOLUME_UP)){
            sendMessageAndCreateSomeSideEffects("next");
        }
        if ((keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)){
            sendMessageAndCreateSomeSideEffects("back");
        }
        return true;
    }

    private boolean tryInitSocketWithIp(String ip) {
        try {
            socket = new Socket(ip, ServerPort);
            Log.d("RemoteSocket", "Established socket with " + ip);
            return true;
        }
        catch (Exception e) {
            Log.d("RemoteSocket", "Failed establishing socket with " + ip);
            return false;
        }
    }

    public void initSocket() {
        Context context = getApplicationContext();
        WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        ConnectivityManager connectivityManager = (ConnectivityManager)context.getSystemService(context.CONNECTIVITY_SERVICE);
        if (connectivityManager.isActiveNetworkMetered()) {
            ArrayList<String> ipsFromHotspot = getArpLiveIps();
            Log.d("asd", ipsFromHotspot.toString());
            for (String ip: ipsFromHotspot) {
                if (tryInitSocketWithIp(ip)) {
                    return;
                }
            }
        }
        else {
            int gatewayIp = wifi.getDhcpInfo().dns1;
            int baseIp = gatewayIp & 0x00FFFFFF;
            for (int i = 1; i < 256; i++) {
                String ip = intToIp(baseIp + (i << 24));
                boolean reachable = isReachable(ip);
                if (reachable) {
                    if (tryInitSocketWithIp(ip)) {
                        return;
                    }
                }
            }
        }
        socket = null;
    }

    public String intToIp(int ip) {
        return (ip & 0xFF) + "."
                + ((ip >> 8) & 0xFF) + "."
                + ((ip >> 16) & 0xFF) + "."
                + ((ip >> 24) & 0xFF);
    }

    public ArrayList<String> getArpLiveIps() {
        BufferedReader bufRead = null;
        ArrayList<String> result = null;

        try {
            result = new ArrayList<String>();
            bufRead = new BufferedReader(new FileReader("/proc/net/arp"));
            String fileLine;
            while ((fileLine = bufRead.readLine()) != null) {
                String[] splitted = fileLine.split(" +");

                if ((splitted != null) && (splitted.length >= 4)) {

                    String mac = splitted[3];
                    if (mac.matches("..:..:..:..:..:..")) {
                        boolean isReachable = pingCmd(splitted[0]);/**
                         * Method to Ping  IP Address
                         * @return true if the IP address is reachable
                         */
                        if (isReachable) {
                            result.add(splitted[0]);
                        }
                    }
                }
            }
        } catch (Exception e) {

        } finally {
            try {
                bufRead.close();
            } catch (IOException e) {

            }
        }
        return result;
    }


    public boolean isReachable(String addr){
        try {
            InetAddress address = InetAddress.getByName(addr);
            return address.isReachable(50);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean pingCmd(String addr){
        try {
            String ping = "ping  -c 1 -W 0.2 " + addr;
            Runtime run = Runtime.getRuntime();
            Process pro = run.exec(ping);
            try {
                pro.waitFor();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            int exit = pro.exitValue();
            if (exit == 0) {
                return true;
            } else {
                return false;
            }
        }
        catch (IOException e) {
        }
        return false;
   }
}