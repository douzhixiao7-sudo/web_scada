package com.example.scada.collector;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

@Component
public class ModbusTcpSimulatorServer implements SmartLifecycle {
    private static final int PORT = 1502;

    private final ExecutorService clientExecutor = Executors.newCachedThreadPool();
    private final ConcurrentMap<Integer, Integer> coilOverrides = new ConcurrentHashMap<>();
    private final ConcurrentMap<Integer, Integer> holdingRegisterOverrides = new ConcurrentHashMap<>();
    private volatile boolean running;
    private Thread serverThread;
    private ServerSocket serverSocket;

    @Override
    public void start() {
        if (running) {
            return;
        }
        running = true;
        serverThread = new Thread(this::serve, "modbus-tcp-simulator");
        serverThread.setDaemon(true);
        serverThread.start();
    }

    @Override
    public void stop() {
        running = false;
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException ignored) {
        }
        clientExecutor.shutdownNow();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void serve() {
        try (ServerSocket socket = new ServerSocket(PORT)) {
            serverSocket = socket;
            while (running) {
                try {
                    Socket client = socket.accept();
                    clientExecutor.submit(() -> handle(client));
                } catch (IOException ex) {
                    if (running) {
                        sleepQuietly();
                    }
                }
            }
        } catch (IOException ex) {
            running = false;
        }
    }

    private void handle(Socket socket) {
        try (socket) {
            socket.setSoTimeout(5000);
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();
            while (running) {
                byte[] header = in.readNBytes(7);
                if (header.length < 7) {
                    return;
                }
                int length = unsignedShort(header[4], header[5]);
                byte[] pdu = in.readNBytes(length - 1);
                if (pdu.length < 5) {
                    return;
                }
                byte[] responsePdu = response(pdu);
                byte[] response = new byte[7 + responsePdu.length];
                response[0] = header[0];
                response[1] = header[1];
                response[2] = 0;
                response[3] = 0;
                response[4] = (byte) ((responsePdu.length + 1) >> 8);
                response[5] = (byte) (responsePdu.length + 1);
                response[6] = header[6];
                System.arraycopy(responsePdu, 0, response, 7, responsePdu.length);
                out.write(response);
                out.flush();
            }
        } catch (SocketTimeoutException ignored) {
        } catch (IOException ignored) {
        }
    }

    private byte[] response(byte[] request) {
        int function = request[0] & 0xff;
        int address = unsignedShort(request[1], request[2]);
        int quantity = Math.max(1, Math.min(unsignedShort(request[3], request[4]), 120));
        return switch (function) {
            case 1, 2 -> bitResponse(function, address, quantity);
            case 3, 4 -> registerResponse(function, address, quantity);
            case 5 -> writeSingleCoil(address, unsignedShort(request[3], request[4]));
            case 6 -> writeSingleRegister(address, unsignedShort(request[3], request[4]));
            default -> new byte[]{(byte) (function | 0x80), 0x01};
        };
    }

    private byte[] bitResponse(int function, int address, int quantity) {
        int byteCount = (quantity + 7) / 8;
        byte[] response = new byte[2 + byteCount];
        response[0] = (byte) function;
        response[1] = (byte) byteCount;
        long tick = Instant.now().getEpochSecond();
        for (int i = 0; i < quantity; i++) {
            int bit = bitValue(function, address + i, tick);
            if (bit == 1) {
                response[2 + i / 8] |= (byte) (1 << (i % 8));
            }
        }
        return response;
    }

    private byte[] registerResponse(int function, int address, int quantity) {
        byte[] response = new byte[2 + quantity * 2];
        response[0] = (byte) function;
        response[1] = (byte) (quantity * 2);
        long tick = Instant.now().getEpochSecond();
        for (int i = 0; i < quantity; i++) {
            int value = registerValue(function, address + i, tick);
            response[2 + i * 2] = (byte) (value >> 8);
            response[3 + i * 2] = (byte) value;
        }
        return response;
    }

    private byte[] writeSingleCoil(int address, int value) {
        if (value != 0xff00 && value != 0x0000) {
            return new byte[]{(byte) 0x85, 0x03};
        }
        coilOverrides.put(address, value == 0xff00 ? 1 : 0);
        return new byte[]{0x05, (byte) (address >> 8), (byte) address, (byte) (value >> 8), (byte) value};
    }

    private byte[] writeSingleRegister(int address, int value) {
        holdingRegisterOverrides.put(address, Math.max(0, Math.min(65535, value)));
        return new byte[]{0x06, (byte) (address >> 8), (byte) address, (byte) (value >> 8), (byte) value};
    }

    private int bitValue(int function, int address, long tick) {
        if (function == 1 && coilOverrides.containsKey(address)) {
            return coilOverrides.get(address);
        }
        if (Math.floorMod(address, 23) == 3) {
            return Math.floorMod(address + tick / 20, 29) == 0 ? 1 : 0;
        }
        return Math.floorMod(address + function + tick / 10, 4) == 0 ? 0 : 1;
    }

    private int registerValue(int function, int address, long tick) {
        if (function == 3 && holdingRegisterOverrides.containsKey(address)) {
            return holdingRegisterOverrides.get(address);
        }
        double wave = Math.sin((tick + address + function * 17) / 10.0);
        double base = function == 4 ? 50 : 25;
        double value = base + wave * 20 + Math.floorMod(address, 100) / 10.0;
        return Math.max(0, Math.min(65535, (int) Math.round(value * 100)));
    }

    private int unsignedShort(byte high, byte low) {
        return ((high & 0xff) << 8) | (low & 0xff);
    }

    private void sleepQuietly() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
