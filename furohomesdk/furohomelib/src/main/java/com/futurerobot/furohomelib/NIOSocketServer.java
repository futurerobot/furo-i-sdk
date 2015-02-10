package com.futurerobot.furohomelib;

import android.util.Log;

//import com.futurerobot.furomain.FuroMainMessage;
//import com.futurerobot.furomain.GlobalManager;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by jaekwon on 2014. 12. 2..
 */
public class NIOSocketServer extends Thread {
    private static final String TAG = "NIOSocketServer";

    private ServerSocketChannel serverSocketChannel = null;
    private Selector selector = null;

    private final static int BYTE_BUFFER_SIZE = 8192;
    private boolean loop = false;
    private static final long TIME_OUT = 3000;

    private final ByteBuffer buffer = ByteBuffer.allocate(BYTE_BUFFER_SIZE);

    List<SocketChannel> listClientChannels;

    public interface OnPacketHandler {
        public void onConnected(SocketChannel clientChannel);
        public void onPacketReceived(String packet);
    }

    private OnPacketHandler onPacketHandler;
    public NIOSocketServer(int port, OnPacketHandler handler) {
        // initailiz server
        listClientChannels = new ArrayList<SocketChannel>();
        this.onPacketHandler = handler;
        try {
            SocketAddress socketAddress = new InetSocketAddress(port);
            selector = Selector.open();
            serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.configureBlocking(false);
            ServerSocket serverSocket = serverSocketChannel.socket();
            serverSocket.bind(socketAddress);
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
            loop = true;
            Log.v(TAG, "Start OK");
        } catch (IOException e) {
            e.printStackTrace();
            Log.e("Error", e.toString());
        }
    }

    public void stopServer() {
        loop = false;
    }
    public void run() {
        Log.i(TAG, "Waiting Client Connection");
        try {
            while(loop) {
                // Selector 객체에 등록된 ServerSocketChannel의 이벤트를 대기한다.
                // 또한, 주기적으로 타임아웃되어 빠져나오도록 한다.
                int n = selector.select(TIME_OUT);
                if(n == 0)  // 타임아웃이 되면 반환값은 0이다.
                    continue;

                Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
                while(iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    if(key.isValid() == false)
                        continue;;

                    // Finish connection in case of an error
                    if(key.isConnectable()) {
                        SocketChannel channel = (SocketChannel) key.channel();
                        if(channel.isConnectionPending()) {
                            channel.finishConnect();
                        }
                    }
                    else if(key.isAcceptable()) {
                        accept(key);
                    }
                    else if(key.isReadable()) {
                        read(key);
                    }
                    iterator.remove();
                }
            }
        } catch (Exception e) {
            Log.e("Error", e.toString());
        }
    }


    private void accept(SelectionKey key) {
        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
        try {
            SocketChannel clientChannel =  serverChannel.accept();
            clientChannel.configureBlocking(false);

            Socket socket = clientChannel.socket();
            SocketAddress remoteAddr = socket.getRemoteSocketAddress();
            Log.v(TAG, "Connected from : " + remoteAddr);

            SelectionKey clientKey = clientChannel.register(selector, SelectionKey.OP_READ);
            clientKey.attach(new ClientHandler(clientChannel));
            listClientChannels.add(clientChannel);
//            broadcast("accepted from " + remoteAddr + "\r\n");
//            broadcast(new FuroMainMessage("accepted", remoteAddr));
            onPacketHandler.onConnected(clientChannel);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void read(SelectionKey key) {
        Log.v(TAG, "readKey: " + key);
        if(key == null)
            return;

        try {
            ClientHandler clientHandler = (ClientHandler)key.attachment();
            SocketChannel channel = (SocketChannel)key.channel();
            int count, totalRead = 0;
            buffer.clear();

            count = channel.read(buffer);
            if(count > 0) {
                buffer.flip();
                clientHandler.onRead(buffer);

                if(buffer.remaining()> 0) {
                    key.selector().wakeup();
                }
            }
            else if(count < 0) {
                // 채널내 버퍼에서 EOF를 만났을 때 발생한다. 즉, 클라이언트가 접속을 종료시키기 위해 소켓채널을 닫았을 때, -1을 반환한다.
                Log.v(TAG, "read count 0");
                Socket socket = channel.socket();
                SocketAddress remoteAddr = socket.getRemoteSocketAddress();
                Log.v(TAG, "Disconnected from: " + remoteAddr);
                channel.close();
                key.cancel();
                return;
            }

        } catch (Exception e) {
            Log.v("Error", e.toString());
       }
    }

    public void write(SocketChannel channel, String message) {
        buffer.clear();
        try {
            buffer.put(message.getBytes("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            Log.v("Error", e.toString());
        }
        buffer.flip();
        try {
            channel.write(buffer);
        } catch (IOException e) {
            e.printStackTrace();
            Log.v("Error", e.toString());
        }
    }

    public void sendMessage(SocketChannel channel, String message) {
        write(channel, message);
    }

    public void broadcast(String msg) {
        Log.v("Info", msg);
        if(listClientChannels != null) {
            for(SocketChannel aChannel : listClientChannels) {
                write(aChannel, msg);
                write(aChannel, "\r\n");
            }
        }
    }

    private class ClientHandler {
        String name;
        SocketChannel channel;
        byte []uncompleted = null;

        public ClientHandler(SocketChannel channel) {
            this.channel = channel;
        }
        public void onRead(ByteBuffer buffer) {
            Log.v(TAG, "onRead:");
//            StringBuffer sBuffer = new StringBuffer();
            int lineBufCount = 0;
            for(int i=0; i < buffer.limit(); i++) {
                byte ch  = buffer.get(i);
                if(ch == '\r') {
                    // skip
                } else if(ch == '\n') {
                    byte[] lineBuf = new byte[lineBufCount];
                    buffer.get(lineBuf, 0, lineBufCount);
                    if(uncompleted != null && uncompleted.length > 0) {
                        byte[] combined = new byte[uncompleted.length + lineBuf.length];
                        System.arraycopy(uncompleted, 0, combined, 0, uncompleted.length);
                        // 이게 왜 2번 있지?
                        System.arraycopy(uncompleted, 0, combined, 0, uncompleted.length);
                        lineBuf = combined;
                    }

                    String message = null;
                    try {
                        message = new String(lineBuf, "UTF-8");
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                    onPacket(message);
                    int remain = buffer.limit()-1 - i;
                    if(remain > 0) {
                        uncompleted = new byte[remain];
                        for(int j=0; j<remain && i < buffer.limit(); j++, i++) {
                            uncompleted[j] = buffer.get(i);
                        }
                    }
                    return;
                } else {
                    lineBufCount++;
                }
            }
        }

        public void onPacket(String packet) {
            Log.v(TAG, "Packet: " + packet);
//            broadcast(packet + "\r\n");
//            NIOSocketServer.this.write(this.channel, packet + "\r\n");

            if(packet == null || packet.length() == 0)
                return;
//            if(packet.compareTo("heartbeat") == 0) {
//            }
//            else if("forward".equalsIgnoreCase(packet)) {
//                RobotController.INSTANCE.goForward();
//            }
//            else if("backward".equalsIgnoreCase(packet)) {
//                RobotController.INSTANCE.goBackward();
//            }
//            else if("turn_left".equalsIgnoreCase(packet)) {
//                RobotController.INSTANCE.turnLeft();
//            }
//            else if("turn_right".equalsIgnoreCase(packet)) {
//                RobotController.INSTANCE.turnRight();
//            }
//            else if("background".equalsIgnoreCase(packet)) {
//                RobotController.INSTANCE.turnRight();
//            }
            onPacketHandler.onPacketReceived(packet);
        }
    }
}
