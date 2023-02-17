package com.jschartner.youtubetv;

import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.jschartner.youtubebase.JexoPlayer;
import com.jschartner.youtubebase.Utils;
import com.jschartner.youtubebase.Youtube;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Iterator;
import java.util.function.Consumer;

import js.Io;
import js.Req;

public class TvMainActivity extends AppCompatActivity {
    protected JexoPlayer jexoPlayer;
    private ServerThread serverThread;

    public void toast(final Object ...os) {
        Utils.toast(this, Io.concat(os));
    }

    public void playVideo(@NonNull final String id) {
        Youtube.fetchVideo(id, (bytes) -> {
            final String response = Req.utf8(bytes);

            Youtube.fetchFormatsAndVideoInfoFromResponse(response, (formats, info) -> {
                final String lengthString = info.optString("lengthSeconds");
                if(lengthString == null) {
                    Utils.toast(this, "Can not find length in videoDetails");
                    return;
                }

                jexoPlayer.setOnPlayerError((error) -> {
                    toast("fetching Youtube again ...");
                    playVideo(id);
                });
                jexoPlayer.playFirst(new Iterator<String>() {
                    int state = 0;

                    @Override
                    public boolean hasNext() {
                        return state<3;
                    }

                    @Override
                    public String next() {
                        switch(state++) {
                            case 0:
                                try {
                                    return Youtube.buildMpd(formats, Integer.parseInt(lengthString));
                                }
                                catch(final JSONException e) {
                                    return null;
                                }
                            case 1:
                                try{
                                    return info.getString("hlsManifestUrl");
                                } catch(final JSONException e) {
                                    return null;
                                }
                            case 2:
                                try{
                                    return info.getString("dashManifestUrl");
                                } catch(final JSONException e) {
                                    return null;
                                }
                            default:
                                return null;
                        }
                    }
                }, Youtube.toMediaItem(id, info));
            }, () -> toast("Failed to fetch Formats"));

            final JSONObject initialData = Youtube.getInitialDataFromResponse(response);
            if(initialData == null) {
                toast("Failed to parse initialData");
                return;
            }

            final String nextVideoId = Youtube.getNextVideoIdFromInitialData(initialData);
            if(nextVideoId !=null ) {
                jexoPlayer.setOnMediaEnded(() -> {
                    playVideo(nextVideoId);
                });
            }

        }, () -> toast("Failed to fetch Youtube"));
    }

    @Override
    public void onCreate(Bundle savedInstance) {
        super.onCreate(savedInstance);
        setContentView(R.layout.activity_tv_main);

        jexoPlayer = new JexoPlayer(this);

        serverThread = new ServerThread(8080, (message) -> {
            playVideo(message);
        });
        serverThread.start();

        playVideo("XFJcYGFmUQw");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        serverThread.interrupt();
    }

    public static void reverse(byte[] array) {
        if (array == null) {
            return;
        }
        int i = 0;
        int j = array.length - 1;
        byte tmp;
        while (j > i) {
            tmp = array[j];
            array[j] = array[i];
            array[i] = tmp;
            j--;
            i++;
        }
    }

    private class ServerThread extends Thread implements Runnable {
        private ServerSocket serverSocket;
        private final int port;
        private final Consumer<String> onThen;

        public ServerThread(int port, final Consumer<String> onThen){
            this.port = port;
            this.onThen = onThen;
        }

        @Override
        public void interrupt() {
            try{
                serverSocket.close();
            }
            catch(IOException e) {
                e.printStackTrace();
            }
            super.interrupt();
        }

        public void run() {
            Socket socket = null;
            try {
                serverSocket = new ServerSocket(port);
            } catch (IOException e) {
                e.printStackTrace();
            }
            //runOnUiThread(() -> Toast.makeText(context, concat("started Server\n", ips, "\nOn port ", port), Toast.LENGTH_LONG).show());
            while (serverSocket!=null && !serverSocket.isClosed() && !Thread.currentThread().isInterrupted()) {
                try {
                    socket = serverSocket.accept();

                    if(socket==null) continue;

                    CommunicationThread commThread = new CommunicationThread(socket, onThen);
                    new Thread(commThread).start();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            try{
                if(socket!=null) socket.close();
                if(serverSocket!=null) serverSocket.close();
            }
            catch(Exception e) {
                e.printStackTrace();
            }
        }
    }

    private class CommunicationThread implements Runnable {
        private Socket client;
        private BufferedReader input;
        private final Consumer<String> onThen;

        public void run() {
            if(input==null) return;

            while (client!=null && !client.isClosed() && client.isConnected()) {
                if(client.isClosed()) {
                    break;
                }
                String read ="";
                try {
                    read = input.readLine();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                if(read==null) {
                    try {
                        client.close();
                        input.close();
                        client = null;
                    }
                    catch(Exception e) {
                        e.printStackTrace();
                    }
                    break;
                }

                try {
                    String finalRead = read;
                    if(onThen != null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            runOnUiThread(() -> onThen.accept(finalRead));
                        }
                    }

                    //Thread thread = new Thread(new StartVideoThread(finalRead));
                    //thread.start();
                }
                catch(Exception e) {
                    e.printStackTrace();
                }
            }

            try{
                input.close();
                client.close();
            }
            catch(Exception e) {
                e.printStackTrace();
            }
        }

        public CommunicationThread(Socket client, final Consumer<String> onThen) {
            this.client = client;
            this.onThen = onThen;

            try {
                this.input = new BufferedReader(new InputStreamReader(this.client.getInputStream()));

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


}
