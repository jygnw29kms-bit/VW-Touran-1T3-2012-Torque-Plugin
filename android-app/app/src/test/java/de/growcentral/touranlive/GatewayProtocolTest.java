package de.growcentral.touranlive;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class GatewayProtocolTest {
    static final String HELLO = "{\"type\":\"hello\",\"proto\":1,\"device\":\"135er-touran-gateway\",\"board\":\"ESP32-S3-CAN-2CH-U\",\"fw\":\"0.2.0\",\"readonly\":true}";
    static final String CAN = "{\"type\":\"can\",\"ch\":1,\"ts_us\":1,\"id\":2047,\"ext\":false,\"data\":\"0001020304050607\"}";
    static final String HEALTH = "{\"type\":\"health\",\"uptime_ms\":1,\"can1_rate\":500,\"can2_rate\":100,\"readonly\":true,\"can1_rx\":0,\"can2_rx\":0,\"drops\":0}";

    static void rejects(String line) {
        assertThrows(Exception.class, () -> GatewayProtocol.parse(line));
    }
    @Test public void acceptsActualFirmwareMessageShapes() throws Exception {
        assertEquals("hello", GatewayProtocol.parse(HELLO).getString("type"));
        assertEquals(8, GatewayProtocol.decodeHex(GatewayProtocol.parse(CAN).getString("data")).length);
        assertEquals(500, GatewayProtocol.parse(HEALTH).getInt("can1_rate"));
    }
    @Test public void rejectsUnsupportedHandshake() {
        rejects(HELLO.replace("\"proto\":1", "\"proto\":2"));
        rejects(HELLO.replace("ESP32-S3-CAN-2CH-U", "other-board"));
        rejects(HELLO.replace("readonly\":true", "readonly\":false"));
    }
    @Test public void doesNotRepairCorruptPayloads() throws Exception {
        for (String data : new String[]{"A", "0X00", "AA BB", "ZZ", "000102030405060708"}) {
            rejects(new JSONObject(CAN).put("data", data).toString());
        }
        assertArrayEquals(new byte[]{(byte)0xff}, GatewayProtocol.decodeHex("ff"));
        assertEquals(0, GatewayProtocol.decodeHex("").length);
    }
    @Test public void checksIdentifierAndChannelRanges() throws Exception {
        rejects(new JSONObject(CAN).put("id", 2048).toString());
        rejects(new JSONObject(CAN).put("id", -1).toString());
        rejects(new JSONObject(CAN).put("ch", 0).toString());
        rejects(new JSONObject(CAN).put("ch", 3).toString());
        GatewayProtocol.parse(new JSONObject(CAN).put("ext", true).put("id", 0x1fffffff).toString());
        rejects(new JSONObject(CAN).put("ext", true).put("id", 0x20000000).toString());
    }
    @Test public void rejectsMissingOrCoercedFields() throws Exception {
        JSONObject missing = new JSONObject(CAN); missing.remove("ts_us"); rejects(missing.toString());
        rejects(new JSONObject(CAN).put("ch", "1").toString());
        rejects(new JSONObject(CAN).put("ts_us", 1.5).toString());
        rejects(new JSONObject(CAN).put("ts_us", -1).toString());
        rejects(new JSONObject(CAN).put("ext", "false").toString());
    }
    @Test public void checksValueNamesSourcesAndUnits() throws Exception {
        JSONObject value = new JSONObject("{\"type\":\"value\",\"name\":\"rpm\",\"value\":0,\"unit\":\"rpm\",\"ts_us\":1,\"source\":\"can1\"}");
        GatewayProtocol.parse(value.toString());
        rejects(new JSONObject(value.toString()).put("unit", "kPa").toString());
        rejects(new JSONObject(value.toString()).put("name", "unverified").toString());
        rejects(new JSONObject(value.toString()).put("source", "guessed").toString());
        rejects(new JSONObject(value.toString()).put("value", "NaN").toString());
    }
    @Test public void checksHealthModeAndCounters() throws Exception {
        rejects(new JSONObject(HEALTH).put("readonly", false).toString());
        rejects(new JSONObject(HEALTH).put("drops", -1).toString());
        rejects(new JSONObject(HEALTH).put("can1_rx", "0").toString());
    }
    @Test public void rejectsOversizedAndUnknownMessages() {
        rejects(new String(new char[GatewayProtocol.MAX_LINE_CHARS + 1]).replace('\0', ' '));
        rejects("{\"type\":\"unsupported\"}");
        rejects("{not-json}");
    }
    @Test public void acceptsReadOnlyErrorWithoutVehicleData() throws Exception {
        assertEquals("READ_ONLY", GatewayProtocol.parse("{\"type\":\"error\",\"code\":\"READ_ONLY\",\"message\":\"active CAN transmit disabled\"}").getString("code"));
    }
}
