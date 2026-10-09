package de.growcentral.touranlive;

import org.json.JSONException;
import org.json.JSONObject;

/** Strict validation for the documented classic-CAN gateway protocol, independent of Android. */
final class GatewayProtocol {
    static final int MAX_LINE_CHARS = 4096;

    static JSONObject parse(String line) throws JSONException {
        if (line.length() > MAX_LINE_CHARS) throw new JSONException("gateway line too long");
        JSONObject o = new JSONObject(line);
        String type = text(o, "type");
        switch (type) {
            case "hello":
                if (integer(o, "proto", 1, 1) != 1 ||
                        !"135er-touran-gateway".equals(text(o, "device")) ||
                        !"ESP32-S3-CAN-2CH-U".equals(text(o, "board"))) {
                    throw new JSONException("unsupported gateway identity");
                }
                text(o, "fw");
                if (!bool(o, "readonly")) throw new JSONException("gateway must be listen-only");
                break;
            case "can":
                integer(o, "ch", 1, 2);
                integer(o, "ts_us", 0, Long.MAX_VALUE);
                integer(o, "id", 0, bool(o, "ext") ? 0x1fffffffL : 0x7ffL);
                decodeHex(text(o, "data"));
                break;
            case "value":
                String name = text(o, "name");
                String unit = unit(name);
                if (unit == null) throw new JSONException("unregistered vehicle value: " + name);
                if (!unit.equals(text(o, "unit"))) throw new JSONException("unexpected unit for " + name);
                String source = text(o, "source");
                if (!"can1".equals(source) && !"can2".equals(source)) {
                    throw new JSONException("unregistered value source");
                }
                number(o, "value");
                integer(o, "ts_us", 0, Long.MAX_VALUE);
                break;
            case "health":
                if (!bool(o, "readonly")) throw new JSONException("gateway left listen-only mode");
                for (String field : new String[]{"uptime_ms", "can1_rx", "can2_rx", "drops"}) {
                    integer(o, field, 0, Long.MAX_VALUE);
                }
                integer(o, "can1_rate", 1, 1000);
                integer(o, "can2_rate", 1, 1000);
                break;
            case "error":
                text(o, "code");
                text(o, "message");
                break;
            default: throw new JSONException("unknown gateway message: " + type);
        }
        return o;
    }

    static String text(JSONObject o, String key) throws JSONException {
        Object v = o.get(key);
        if (!(v instanceof String)) throw new JSONException(key + " must be a string");
        return (String) v;
    }

    static boolean bool(JSONObject o, String key) throws JSONException {
        Object v = o.get(key);
        if (!(v instanceof Boolean)) throw new JSONException(key + " must be boolean");
        return (Boolean) v;
    }

    static double number(JSONObject o, String key) throws JSONException {
        Object v = o.get(key);
        if (!(v instanceof Number)) throw new JSONException(key + " must be numeric");
        double d = ((Number) v).doubleValue();
        if (Double.isNaN(d) || Double.isInfinite(d)) throw new JSONException(key + " must be finite");
        return d;
    }

    static long integer(JSONObject o, String key, long min, long max) throws JSONException {
        Object v = o.get(key);
        // Protocol counters are integer JSON tokens. Reject floating point and string coercion.
        if (!(v instanceof Integer) && !(v instanceof Long)) throw new JSONException(key + " must be integer");
        long n = ((Number) v).longValue();
        if (n < min || n > max) throw new JSONException(key + " out of range");
        return n;
    }

    static byte[] decodeHex(String s) throws JSONException {
        if (!s.matches("(?:[0-9A-Fa-f]{2}){0,8}")) throw new JSONException("invalid classic CAN payload");
        byte[] data = new byte[s.length() / 2];
        for (int i = 0; i < data.length; i++) data[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        return data;
    }

    static String unit(String name) {
        switch (name) {
            case "rpm": return "rpm";
            case "speed": return "km/h";
            case "map": return "kPa";
            case "boost": case "fuel_pressure": return "bar";
            case "coolant": case "oil_temp": case "intake_temp": return "°C";
            case "throttle": case "pedal": case "engine_load": return "%";
            case "ignition": return "°";
            case "lambda": return "λ";
            case "ecu_voltage": return "V";
            default: return null;
        }
    }
}
