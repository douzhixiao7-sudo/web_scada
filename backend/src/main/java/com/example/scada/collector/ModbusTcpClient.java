package com.example.scada.collector;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

@Component
public class ModbusTcpClient {
    private static final AtomicInteger TRANSACTION_ID = new AtomicInteger(1);

    public String readValue(String host, Integer port, String modbusType, String address, String dataType) throws IOException {
        int function = functionCode(modbusType);
        int startAddress = parseAddress(address);
        byte[] request = request(function, startAddress);
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host == null || host.isBlank() ? "127.0.0.1" : host, port == null ? 1502 : port), 800);
            socket.setSoTimeout(1200);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            out.write(request);
            out.flush();
            byte[] header = in.readNBytes(7);
            if (header.length < 7) {
                throw new IOException("Modbus response header incomplete");
            }
            int length = unsignedShort(header[4], header[5]);
            byte[] pdu = in.readNBytes(length - 1);
            if (pdu.length < 2 || ((pdu[0] & 0xff) >= 0x80)) {
                throw new IOException("Modbus exception response");
            }
            if (function == 1 || function == 2) {
                return (pdu[2] & 0x01) == 1 ? "1" : "0";
            }
            int raw = unsignedShort(pdu[2], pdu[3]);
            if ("INTEGER".equalsIgnoreCase(dataType)) {
                return String.valueOf(raw);
            }
            return String.format(java.util.Locale.ROOT, "%.2f", raw / 100.0);
        }
    }

    private byte[] request(int function, int address) {
        int transaction = TRANSACTION_ID.getAndUpdate(value -> value >= 65535 ? 1 : value + 1);
        byte[] request = new byte[12];
        request[0] = (byte) (transaction >> 8);
        request[1] = (byte) transaction;
        request[2] = 0;
        request[3] = 0;
        request[4] = 0;
        request[5] = 6;
        request[6] = 1;
        request[7] = (byte) function;
        request[8] = (byte) (address >> 8);
        request[9] = (byte) address;
        request[10] = 0;
        request[11] = 1;
        return request;
    }

    private int functionCode(String modbusType) {
        return switch (modbusType == null ? "" : modbusType.trim()) {
            case "0" -> 1;
            case "1" -> 2;
            case "3" -> 4;
            case "4" -> 3;
            default -> 3;
        };
    }

    private int parseAddress(String address) {
        try {
            return Math.max(0, Integer.parseInt(address == null || address.isBlank() ? "0" : address.trim()) - 1);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private int unsignedShort(byte high, byte low) {
        return ((high & 0xff) << 8) | (low & 0xff);
    }
}
