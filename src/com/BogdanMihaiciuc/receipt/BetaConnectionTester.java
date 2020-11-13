package com.BogdanMihaiciuc.receipt;

import android.app.Activity;
import android.os.AsyncTask;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.BogdanMihaiciuc.util.$;
import com.BogdanMihaiciuc.util.LegacyActionBar;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.Executor;

public class BetaConnectionTester implements LegacyActionBar.CustomViewProvider {

    final static Object Lock = new Object();

    private Activity activity;

    private View root;

    private Button connect;
    private Button send;

    private TextView receiver;
    private EditText sender;

    private boolean connectionActive = false;
    private PrintWriter outWriter;

    final static int OutWriterCreated = 0;
    final static int MessageReceived = 1;

    private static class ProgressMessage {
        int message;
        Object data;

        static ProgressMessage make(int message, Object data) {
            ProgressMessage message1 = new ProgressMessage();

            message1.message = message;
            message1.data = data;

            return message1;
        }
    }

    private AsyncTask<Void, ProgressMessage, Void> connectionTask = new AsyncTask<Void, ProgressMessage, Void>() {
        public boolean stop;

        @Override
        protected Void doInBackground(Void... params) {

            try {
                Log.e("", "Connceting!");
                Socket socket = new Socket("192.168.0.181", 10523);

                synchronized (Lock) {
                    connectionActive = true;
                }

                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                // Authenticate immediately upon establishing connection
                out.println("AUTH");
                out.flush();

                publishProgress(ProgressMessage.make(OutWriterCreated, out));

                while (true) {
                    Log.e("", "Reading message...");
                    String message;
                    try {
                        message = in.readLine();
                    }
                    catch (InterruptedIOException e) {
                        message = null;
                    }

                    if (message != null) {
                        Log.e("", "Message received " + message + "!");
                        publishProgress(ProgressMessage.make(MessageReceived, message));
                    }

                    if (this.isCancelled()) {

                        Log.e("", "Closing connection...");

                        synchronized (Lock) {
                            outWriter = null;

                            connectionActive = false;
                        }
                        socket.close();
                        break;
                    }
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }

            return null;
        }

        protected void onProgressUpdate(ProgressMessage ... messages) {
            ProgressMessage message = messages[0];

            if (message.message == OutWriterCreated) {
                synchronized (Lock) {
                    outWriter = (PrintWriter) message.data;
                }
            }
            else if (message.message == MessageReceived) {
                if (receiver != null) receiver.setText((CharSequence) message.data);
            }
        }

    };

    @Override
    public View onCreateCustomView(LayoutInflater inflater, ViewGroup container) {
        activity = (Activity) container.getContext();

        root = inflater.inflate(R.layout.beta_connection_tester, container, false);

        connect = (Button) root.findViewById(R.id.BetaConnect);
        send =  (Button) root.findViewById(R.id.BetaSend);

        receiver =  (TextView) root.findViewById(R.id.BetaReceiver);
        sender =  (EditText) root.findViewById(R.id.BetaSender);

        $.wrap(connect).click(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                connectionTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            }
        });

        $.wrap(send).click(new View.OnClickListener() {
            public void onClick(View v) {
                synchronized (Lock) {
                    if (connectionActive) {
                        new AsyncTask<Void, Void, Void>() {
                            private String text;
                            protected void onPreExecute() {
                                text = sender.getText().toString();
                            }

                            @Override
                            protected Void doInBackground(Void... params) {
                                if (outWriter != null) {
                                    outWriter.print(text);
                                    outWriter.flush();
                                }
                                return null;
                            }
                        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                    }
                }
            }
        });

        return root;
    }

    @Override
    public void onDestroyCustomView(View customView) {

        Log.e("", "Popover dismissed, closing connection!");

        connectionTask.cancel(true);

        activity = null;

        root = null;

        connect = null;
        sender = null;
        receiver = null;
        send = null;
    }

}
