package aero.panasonic.nsdconsumer;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

public class NSDConsumer extends AppCompatActivity {

    private static final String TAG = NSDConsumer.class.getSimpleName();

    private NsdManager.DiscoveryListener mDiscoveryListener = new NsdManager.DiscoveryListener() {
        @Override
        public void onStartDiscoveryFailed(String serviceType, int errorCode) {
            Log.d(TAG, "onStartDiscoveryFailed() " + errorCode);
        }

        @Override
        public void onStopDiscoveryFailed(String serviceType, int errorCode) {
            Log.d(TAG, "onStopDiscoveryFailed() " + errorCode);
        }

        @Override
        public void onDiscoveryStarted(String serviceType) {
            Log.d(TAG, "onDiscoveryStarted() ");
        }

        @Override
        public void onDiscoveryStopped(String serviceType) {
            Log.d(TAG, "onDiscoveryStopped()");
        }

        @Override
        public void onServiceFound(NsdServiceInfo serviceInfo) {
            Log.d(TAG, "onServiceFound() ");
            NsdManager nsdManager = (NsdManager) getSystemService(Context.NSD_SERVICE);
            nsdManager.resolveService(serviceInfo, mResolveListener);
        }

        @Override
        public void onServiceLost(NsdServiceInfo serviceInfo) {
            Log.d(TAG, "onServiceLost() ");
        }
    };

    private NsdManager.ResolveListener mResolveListener = new NsdManager.ResolveListener() {

        @Override
        public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
            // Called when the resolve fails.  Use the error code to debug.
            Log.e(TAG, "Resolve failed" + errorCode);
        }

        @Override
        public void onServiceResolved(final NsdServiceInfo serviceInfo) {
            Log.e(TAG, "Resolve Succeeded. " + serviceInfo);

            final TextView textView = (TextView) findViewById(R.id.text);
            final String text = serviceInfo.getServiceName() + "\n" + serviceInfo.getHost();
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    textView.setText(text);

                        new Thread() {
                            @Override
                            public void run() {
                                try {
                                    final Socket socket = new Socket(serviceInfo.getHost().getHostAddress(), serviceInfo.getPort());
                                    DataInputStream dataInputStream = new DataInputStream(socket.getInputStream());
                                    Log.d(TAG, "message: " + dataInputStream.readUTF());

                                    while (true) {
                                        final String data = dataInputStream.readUTF();
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                textView.setText(text + "\n" + data);
                                            }
                                        });
                                    }
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        }.start();
                }
            });
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nsdconsumer);

        NsdManager nsdManager = (NsdManager) getSystemService(Context.NSD_SERVICE);
        nsdManager.discoverServices("_ep._tcp.", NsdManager.PROTOCOL_DNS_SD, mDiscoveryListener);
    }
}
