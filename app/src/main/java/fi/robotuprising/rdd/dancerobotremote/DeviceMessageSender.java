/*
 * Partially adapted from https://github.com/jfedor2/nxt-remote-control
 */

package fi.robotuprising.rdd.dancerobotremote;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class DeviceMessageSender {

    public static final int STATE_NONE = 0;
    public static final int STATE_CONNECTING = 1;
    public static final int STATE_CONNECTED = 2;
    
    private int state;
    private Handler handler;
    private BluetoothAdapter bluetoothAdapter;
    
    private ConnectThread connectThread;
    private ConnectedThread connectedThread;
    
    public DeviceMessageSender(Handler handler) {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        this.handler = handler;
        setState(STATE_NONE);
    }

    private synchronized void setState(int state) {
        this.state = state;
        if (handler != null) {
            handler.obtainMessage(MainActivity.MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
        }
    }
    
    public synchronized int getState() {
        return state;
    }
    
    public synchronized void setHandler(Handler handler) {
        this.handler = handler;
    }
    
    private void showMessage(String text) {
        if (handler != null) {
            Message msg = handler.obtainMessage(MainActivity.MESSAGE_TOAST);
            Bundle bundle = new Bundle();
            bundle.putString("", text);
            msg.setData(bundle);
            handler.sendMessage(msg);
        }
    }

    public synchronized void connect(BluetoothDevice device) {
        if (state == STATE_CONNECTING) {
            if (connectThread != null) {
                connectThread.cancel();
                connectThread = null;
            }
        }
        
        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }
        
        connectThread = new ConnectThread(device);
        connectThread.start();
        setState(STATE_CONNECTING);
    }
    
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice device) {
        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }
        
        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }
        
        connectedThread = new ConnectedThread(socket);
        connectedThread.start();
        
        showMessage("Connected to " + device.getName());
        
        setState(STATE_CONNECTED);
    }
    
    public synchronized void stop() {
        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }
        
        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }
        setState(STATE_NONE);
    }
    
    private void connectionFailed() {
        setState(STATE_NONE);
        showMessage("Connection failed");
    }
    
    private void connectionLost() {
        setState(STATE_NONE);
        showMessage("Connection lost");
    }
    
    public void driveMotor(int motor, byte power) {
        byte[] data = { 0x0c, 0x00, (byte) 0x80, 0x04, 0x02, 0x32, 0x07, 0x00, 0x00, 0x20, 0x00, 0x00, 0x00, 0x00 };
        
        Log.i("MessageSender", "driving motor: " + Integer.toString(motor) + ", power " + Byte.toString(power));
        
        if (motor == 0) {
            data[4] = 0x00;
        } else if (motor == 1) {
            data[4] = 0x01;
        }
        else {
            data[4] = 0x02;
        }
        
        data[5] = power;
        write(data);
    }
    
    private void write(byte[] out) {
        ConnectedThread r;
        synchronized (this) {
            if (state != STATE_CONNECTED) {
                return;
            }
            r = connectedThread;
        }
        r.write(out);
    }
    
    private class ConnectThread extends Thread {
        private BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;
        
        public ConnectThread(BluetoothDevice device) {
            mmDevice = device;
        }
        
        public void run() {
            setName("ConnectThread");
            bluetoothAdapter.cancelDiscovery();
            
            try {
                mmSocket = mmDevice.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));
                mmSocket.connect();
            } catch (IOException e) {
                e.printStackTrace();
                try {
                    // This is a workaround that reportedly helps on some older devices like HTC Desire, where using
                    // the standard createRfcommSocketToServiceRecord() method always causes connect() to fail.
                    Method method = mmDevice.getClass().getMethod("createRfcommSocket", new Class[] { int.class });
                    mmSocket = (BluetoothSocket) method.invoke(mmDevice, Integer.valueOf(1));
                    mmSocket.connect();
                } catch (Exception e1) {
                    e1.printStackTrace();
                    connectionFailed();
                    try {
                        mmSocket.close();
                    } catch (IOException e2) {
                        e2.printStackTrace();
                    }
                    return;
                }
            }
            
            synchronized (DeviceMessageSender.this) {
                connectThread = null;
            }
            
            connected(mmSocket, mmDevice);
        }
        
        public void cancel() {
            try {
                if (mmSocket != null) {
                    mmSocket.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        
        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;
            
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }
            
            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }
        
        public void run() {
            byte[] buffer = new byte[1024];
            int bytes;
            
            while (true) {
                try {
                    bytes = mmInStream.read(buffer);
                    //toast(Integer.toString(bytes) + " bytes read from device");
                } catch (IOException e) {
                    e.printStackTrace();
                    connectionLost();
                    break;
                }
            }
        }
        
        public void write(byte[] buffer) {
            try {
                mmOutStream.write(buffer);
            } catch (IOException e) {
                e.printStackTrace();
                // XXX?
            }
        }
        
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
