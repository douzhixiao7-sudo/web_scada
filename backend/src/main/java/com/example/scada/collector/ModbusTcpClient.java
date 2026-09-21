package com.example.scada.collector;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
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

    public void writeValue(String host, Integer port, String modbusType, String address, String dataType, BigDecimal targetValue) throws IOException {
        int type = parseModbusType(modbusType);
        int function = switch (type) {
            case 0 -> 5;
            case 4 -> 6;
            default -> throw new IOException("该 Modbus 区域不支持写入");
        };
        int startAddress = parseAddress(address);
        int payloadValue = function == 5 ? coilPayload(targetValue) : registerPayload(targetValue, dataType);
        byte[] request = request(function, startAddress, payloadValue);
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host == null || host.isBlank() ? "127.0.0.1" : host, port == null ? 1502 : port), 800);
            socket.setSoTimeout(1200);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            out.write(request);
            out.flush();
            byte[] header = in.readNBytes(7);
            if (header.length < 7) {
                throw new IOException("Modbus write response header incomplete");
            }
            int length = unsignedShort(header[4], header[5]);
            byte[] pdu = in.readNBytes(length - 1);
            if (pdu.length < 5 || ((pdu[0] & 0xff) >= 0x80)) {
                throw new IOException("Modbus write exception response");
            }
            if ((pdu[0] & 0xff) != function || unsignedShort(pdu[1], pdu[2]) != startAddress) {
                throw new IOException("Modbus write response mismatch");
            }
        }
    }

    private byte[] request(int function, int address) {
        return request(function, address, 1);
    }

    private byte[] request(int function, int address, int value) {
        int transaction = TRANSACTION_ID.getAndUpdate(current -> current >= 65535 ? 1 : current + 1);
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
        request[10] = (byte) (value >> 8);
        request[11] = (byte) value;
        return request;
    }

    private int functionCode(String modbusType) {
        return switch (parseModbusType(modbusType)) {
            case 0 -> 1;
            case 1 -> 2;
            case 3 -> 4;
            case 4 -> 3;
            default -> 3;
        };
    }

    private int parseModbusType(String modbusType) {
        try {
            return Integer.parseInt(modbusType == null || modbusType.isBlank() ? "4" : modbusType.trim());
        } catch (NumberFormatException ex) {
            return 4;
        }
    }

    private int coilPayload(BigDecimal value) {
        return value.compareTo(BigDecimal.ZERO) == 0 ? 0x0000 : 0xff00;
    }

    private int registerPayload(BigDecimal value, String dataType) {
        BigDecimal raw = "INTEGER".equalsIgnoreCase(dataType) ? value : value.multiply(BigDecimal.valueOf(100));
        int rounded = raw.setScale(0, RoundingMode.HALF_UP).intValue();
        return Math.max(0, Math.min(65535, rounded));
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
