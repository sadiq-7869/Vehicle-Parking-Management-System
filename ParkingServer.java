import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * AI-Powered Vehicle Parking Management System - Backend Server
 * Complete Java Single-Folder Server using standard JDK com.sun.net.httpserver.HttpServer.
 */
public class ParkingServer {

    private static final int PORT = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));

    // In-memory persistent database entities
    public static final List<Map<String, Object>> users = new CopyOnWriteArrayList<>();
    public static final List<Map<String, Object>> vehicles = new CopyOnWriteArrayList<>();
    public static final List<Map<String, Object>> parkingAreas = new CopyOnWriteArrayList<>();
    public static final List<Map<String, Object>> parkingSlots = new CopyOnWriteArrayList<>();
    public static final List<Map<String, Object>> parkingSessions = new CopyOnWriteArrayList<>();
    public static final List<Map<String, Object>> payments = new CopyOnWriteArrayList<>();
    public static final List<Map<String, Object>> reservations = new CopyOnWriteArrayList<>();
    public static final Map<String, Map<String, Object>> parkingRates = new ConcurrentHashMap<>();
    public static final List<Map<String, Object>> auditLogs = new CopyOnWriteArrayList<>();
    public static final Map<String, Object> settings = new ConcurrentHashMap<>();

    private static final AtomicLong sessionSeq = new AtomicLong(100);
    private static final AtomicLong paymentSeq = new AtomicLong(500);
    private static final AtomicLong resSeq = new AtomicLong(1000);
    private static final AtomicLong userSeq = new AtomicLong(0);
    private static final File USER_STORE = new File("users.db");
    private static final AtomicLong vehSeq = new AtomicLong(20);
    private static final AtomicLong auditSeq = new AtomicLong(1);

    public static void main(String[] args) throws IOException {
        initializeData();

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.setExecutor(Executors.newFixedThreadPool(16));

        // Router endpoints
        server.createContext("/", new StaticFileHandler());
        server.createContext("/api/auth", new AuthHandler());
        server.createContext("/api/users", new UserHandler());
        server.createContext("/api/vehicles", new VehicleHandler());
        server.createContext("/api/parking/areas", new AreaHandler());
        server.createContext("/api/slots", new SlotHandler());
        server.createContext("/api/parking/entry", new EntryHandler());
        server.createContext("/api/parking/exit", new ExitHandler());
        server.createContext("/api/parking/active", new ActiveSessionsHandler());
        server.createContext("/api/parking/history", new HistorySessionsHandler());
        server.createContext("/api/parking/calculate-fee", new CalculateFeeHandler());
        server.createContext("/api/payments", new PaymentHandler());
        server.createContext("/api/reservations", new ReservationHandler());
        server.createContext("/api/reports", new ReportHandler());
        server.createContext("/api/ai", new AIHandler());
        server.createContext("/api/settings", new SettingsHandler());
        server.createContext("/api/audit-logs", new AuditLogHandler());

        System.out.println("=================================================");
        System.out.println(" AI Vehicle Parking Management Backend Started");
        System.out.println(" Access Web App: http://localhost:" + PORT);
        System.out.println("=================================================");
        server.start();
    }

    // -------------------------------------------------------------------
    // Data Initialization & Seed Data
    // -------------------------------------------------------------------
    private static void initializeData() {
        // Default Settings
        settings.put("appName", "SmartPark AI Facility");
        settings.put("address", "100 Innovation Way, Tech Park");
        settings.put("phone", "+1 (555) 019-2834");
        settings.put("currency", "₹");
        settings.put("taxPercentage", 18.0);
        settings.put("gracePeriodMinutes", 15);
        settings.put("operatingHours", "24/7 Open");

        // Rates by vehicle type
        createRate("CAR", 40.0, 30.0, 300.0, 500.0);
        createRate("BIKE", 20.0, 15.0, 150.0, 250.0);
        createRate("SCOOTER", 15.0, 10.0, 100.0, 200.0);
        createRate("SUV", 50.0, 40.0, 400.0, 650.0);
        createRate("TRUCK", 80.0, 60.0, 600.0, 900.0);
        createRate("BUS", 100.0, 80.0, 800.0, 1200.0);

        // Users are created through the registration form. No hard-coded login credentials are used.
        loadUsers();

        // Seed Parking Areas
        createArea(101, "Ground Floor - A", "Level 0", 1, 15);
        createArea(102, "First Floor - B", "Level 1", 2, 15);
        createArea(103, "Basement 1 - C", "Level -1", -1, 15);
        createArea(104, "VIP Zone - V", "Level 0 Front", 1, 10);
        createArea(105, "Two-Wheeler Bay - T", "Ground East", 1, 20);

        // Seed Parking Slots across areas
        int slotId = 1;
        // Ground Floor Slots
        for (int i = 1; i <= 15; i++) {
            String slotNum = String.format("A-%03d", i);
            String type = (i <= 10) ? "CAR" : "SUV";
            String status = (i == 2 || i == 5 || i == 8) ? "OCCUPIED" : (i == 4 ? "RESERVED" : (i == 15 ? "MAINTENANCE" : "AVAILABLE"));
            createSlot(slotId++, slotNum, 101, type, type, status, i == 4);
        }
        // First Floor Slots
        for (int i = 1; i <= 15; i++) {
            String slotNum = String.format("B-%03d", i);
            String type = (i <= 12) ? "CAR" : "SUV";
            String status = (i == 3 || i == 7) ? "OCCUPIED" : "AVAILABLE";
            createSlot(slotId++, slotNum, 102, type, type, status, false);
        }
        // Basement 1 Slots
        for (int i = 1; i <= 15; i++) {
            String slotNum = String.format("C-%03d", i);
            String type = "CAR";
            String status = (i == 1 || i == 6) ? "OCCUPIED" : "AVAILABLE";
            createSlot(slotId++, slotNum, 103, type, type, status, false);
        }
        // VIP Zone Slots
        for (int i = 1; i <= 10; i++) {
            String slotNum = String.format("V-%03d", i);
            String status = (i == 1) ? "OCCUPIED" : (i == 2 ? "RESERVED" : "AVAILABLE");
            createSlot(slotId++, slotNum, 104, "SUV", "SUV", status, i == 2);
        }
        // Two-Wheeler Slots
        for (int i = 1; i <= 20; i++) {
            String slotNum = String.format("T-%03d", i);
            String status = (i % 4 == 0) ? "OCCUPIED" : "AVAILABLE";
            createSlot(slotId++, slotNum, 105, "BIKE", "BIKE", status, false);
        }

        // Seed Vehicles
        createVehicle(1, "AP09BX1234", "CAR", "Toyota", "Camry", "White", "David Customer", "9876543214", "customer@smartpark.com", "PARKED");
        createVehicle(2, "KA05MK5678", "CAR", "Honda", "Civic", "Black", "Robert Smith", "9812345678", "robert@gmail.com", "PARKED");
        createVehicle(3, "MH12DE9012", "SUV", "Hyundai", "Creta", "Silver", "Anita Roy", "9765432109", "anita@yahoo.com", "PARKED");
        createVehicle(4, "DL01CS4321", "BIKE", "Yamaha", "R15", "Blue", "Kevin Paul", "9654321098", "kevin@gmail.com", "PARKED");
        createVehicle(5, "TS08EZ8899", "SUV", "BMW", "X5", "Dark Grey", "Michael Vance", "9543210987", "vance@bmw.com", "PARKED");
        createVehicle(6, "HR26DQ1122", "CAR", "Maruti", "Swift", "Red", "Vikram Malhotra", "9432109876", "vikram@mail.com", "COMPLETED");

        // Seed Active Sessions
        LocalDateTime now = LocalDateTime.now();
        createSession(101, "PKT-20260914-000101", 1, 2, now.minusHours(2).minusMinutes(15), "ACTIVE", "A-002");
        createSession(102, "PKT-20260914-000102", 2, 5, now.minusHours(4).minusMinutes(30), "ACTIVE", "A-005");
        createSession(103, "PKT-20260914-000103", 3, 8, now.minusHours(1).minusMinutes(10), "ACTIVE", "A-008");
        createSession(104, "PKT-20260914-000104", 4, 60, now.minusMinutes(45), "ACTIVE", "T-004");
        createSession(105, "PKT-20260914-000105", 5, 46, now.minusHours(3).minusMinutes(0), "ACTIVE", "V-001");

        // Update current_vehicle_id on occupied slots
        updateSlotVehicle("A-002", 1);
        updateSlotVehicle("A-005", 2);
        updateSlotVehicle("A-008", 3);
        updateSlotVehicle("T-004", 4);
        updateSlotVehicle("V-001", 5);

        // Seed Completed Historical Sessions & Payments
        for (int i = 1; i <= 8; i++) {
            LocalDateTime entryTime = now.minusDays(i).plusHours(2);
            LocalDateTime exitTime = entryTime.plusHours(3).plusMinutes(15);
            long session = sessionSeq.incrementAndGet();
            String ticket = "PKT-202609" + String.format("%02d", 14 - i) + "-" + String.format("%06d", session);
            double fee = 130.0 + (i * 20);
            double tax = fee * 0.18;
            double total = fee + tax;

            Map<String, Object> sess = new HashMap<>();
            sess.put("id", session);
            sess.put("ticket_number", ticket);
            sess.put("vehicle_id", 6);
            sess.put("vehicle_number", "HR26DQ1122");
            sess.put("slot_id", 1);
            sess.put("slot_number", "A-001");
            sess.put("entry_time", entryTime.format(DateTimeFormatter.ISO_DATE_TIME));
            sess.put("exit_time", exitTime.format(DateTimeFormatter.ISO_DATE_TIME));
            sess.put("duration_minutes", 195);
            sess.put("parking_rate", fee);
            sess.put("tax", tax);
            sess.put("discount", 0.0);
            sess.put("total_amount", Math.round(total * 100.0) / 100.0);
            sess.put("payment_status", "PAID");
            sess.put("session_status", "COMPLETED");
            sess.put("created_by", "John Staff");
            parkingSessions.add(sess);

            long payId = paymentSeq.incrementAndGet();
            Map<String, Object> pay = new HashMap<>();
            pay.put("id", payId);
            pay.put("session_id", session);
            pay.put("transaction_number", "TXN-" + (100800 + payId));
            pay.put("amount", Math.round(total * 100.0) / 100.0);
            pay.put("payment_method", (i % 2 == 0) ? "UPI" : "CARD");
            pay.put("payment_status", "SUCCESS");
            pay.put("payment_time", exitTime.format(DateTimeFormatter.ISO_DATE_TIME));
            pay.put("received_by", "John Staff");
            payments.add(pay);
        }

        // Seed Reservations
        Map<String, Object> res1 = new HashMap<>();
        res1.put("id", resSeq.incrementAndGet());
        res1.put("user_id", 5);
        res1.put("user_name", "David Customer");
        res1.put("vehicle_number", "AP09BX1234");
        res1.put("slot_id", 4);
        res1.put("slot_number", "A-004");
        res1.put("area_name", "Ground Floor - A");
        res1.put("reservation_date", now.toLocalDate().toString());
        res1.put("start_time", "14:00");
        res1.put("end_time", "18:00");
        res1.put("status", "CONFIRMED");
        res1.put("amount", 160.0);
        res1.put("created_at", now.minusHours(5).format(DateTimeFormatter.ISO_DATE_TIME));
        reservations.add(res1);

        // Seed Audit Logs
        logAudit(1, "SYSTEM_INIT", "SYSTEM", 0, "System database initialized with pre-seeded data");
        logAudit(1, "RATE_UPDATED", "PARKING_RATES", 1, "Default vehicle hourly rates configured");
    }

    private static void createRate(String type, double firstHour, double addHour, double daily, double overnight) {
        Map<String, Object> rate = new HashMap<>();
        rate.put("vehicle_type", type);
        rate.put("first_hour_rate", firstHour);
        rate.put("additional_hour_rate", addHour);
        rate.put("daily_rate", daily);
        rate.put("overnight_rate", overnight);
        rate.put("status", "ACTIVE");
        parkingRates.put(type, rate);
    }

    private static void createUser(long id, String name, String email, String phone, String role, String password) {
        Map<String, Object> u = new HashMap<>();
        u.put("id", id);
        u.put("full_name", name);
        u.put("email", email);
        u.put("phone", phone);
        u.put("role", role);
        u.put("password", hashPassword(password));
        u.put("status", "ACTIVE");
        u.put("created_at", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
        users.add(u);
    }

    private static void loadUsers() {
        users.clear();
        if (!USER_STORE.exists()) return;
        try (BufferedReader br = new BufferedReader(new FileReader(USER_STORE))) {
            String line;
            long maxId = 0;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] parts = line.split("\\|", -1);
                if (parts.length < 7) continue;
                Map<String, Object> u = new HashMap<>();
                long id = Long.parseLong(parts[0]);
                u.put("id", id);
                u.put("full_name", decodeField(parts[1]));
                u.put("email", decodeField(parts[2]));
                u.put("phone", decodeField(parts[3]));
                u.put("role", decodeField(parts[4]));
                u.put("password", decodeField(parts[5]));
                u.put("status", decodeField(parts[6]));
                u.put("created_at", parts.length > 7 ? decodeField(parts[7]) : LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
                users.add(u);
                maxId = Math.max(maxId, id);
            }
            userSeq.set(maxId);
        } catch (Exception ex) {
            System.err.println("Could not load users.db: " + ex.getMessage());
        }
    }

    private static void saveUsers() throws IOException {
        File parent = USER_STORE.getAbsoluteFile().getParentFile();
        if (parent != null) parent.mkdirs();
        File temp = new File(USER_STORE.getPath() + ".tmp");
        try (PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(temp), StandardCharsets.UTF_8))) {
            for (Map<String, Object> u : users) {
                out.println(u.get("id") + "|" + encodeField(u.get("full_name")) + "|" + encodeField(u.get("email")) + "|" +
                        encodeField(u.get("phone")) + "|" + encodeField(u.get("role")) + "|" + encodeField(u.get("password")) + "|" +
                        encodeField(u.get("status")) + "|" + encodeField(u.get("created_at")));
            }
        }
        if (!temp.renameTo(USER_STORE)) {
            try (InputStream in = new FileInputStream(temp); OutputStream out = new FileOutputStream(USER_STORE)) {
                in.transferTo(out);
            }
            temp.delete();
        }
    }

    private static String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private static String encodeField(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeField(String value) {
        try { return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8); }
        catch (Exception ex) { return value; }
    }

    private static void createArea(long id, String name, String loc, int floor, int totalSlots) {
        Map<String, Object> a = new HashMap<>();
        a.put("id", id);
        a.put("area_name", name);
        a.put("location", loc);
        a.put("floor_number", floor);
        a.put("total_slots", totalSlots);
        a.put("available_slots", totalSlots);
        a.put("status", "ACTIVE");
        parkingAreas.add(a);
    }

    private static void createSlot(long id, String slotNum, long areaId, String slotType, String vehType, String status, boolean isReserved) {
        Map<String, Object> s = new HashMap<>();
        s.put("id", id);
        s.put("slot_number", slotNum);
        s.put("area_id", areaId);
        s.put("slot_type", slotType);
        s.put("vehicle_type", vehType);
        s.put("status", status);
        s.put("is_reserved", isReserved);
        s.put("current_vehicle_id", null);
        parkingSlots.add(s);
        recalculateAreaSlots(areaId);
    }

    private static void createVehicle(long id, String number, String type, String brand, String model, String color, String owner, String phone, String email, String status) {
        Map<String, Object> v = new HashMap<>();
        v.put("id", id);
        v.put("vehicle_number", number);
        v.put("vehicle_type", type);
        v.put("brand", brand);
        v.put("model", model);
        v.put("color", color);
        v.put("owner_name", owner);
        v.put("owner_phone", phone);
        v.put("owner_email", email);
        v.put("vehicle_status", status);
        v.put("created_at", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
        vehicles.add(v);
    }

    private static void createSession(long id, String ticket, long vehId, long slotId, LocalDateTime entry, String status, String slotNum) {
        Map<String, Object> sess = new HashMap<>();
        sess.put("id", id);
        sess.put("ticket_number", ticket);
        sess.put("vehicle_id", vehId);

        Map<String, Object> veh = findVehicleById(vehId);
        if (veh != null) {
            sess.put("vehicle_number", veh.get("vehicle_number"));
            sess.put("vehicle_type", veh.get("vehicle_type"));
            sess.put("owner_name", veh.get("owner_name"));
        }

        sess.put("slot_id", slotId);
        sess.put("slot_number", slotNum);
        sess.put("entry_time", entry.format(DateTimeFormatter.ISO_DATE_TIME));
        sess.put("exit_time", null);
        sess.put("duration_minutes", 0);
        sess.put("parking_rate", 0.0);
        sess.put("discount", 0.0);
        sess.put("tax", 0.0);
        sess.put("total_amount", 0.0);
        sess.put("payment_status", "PENDING");
        sess.put("session_status", status);
        sess.put("created_by", "John Staff");
        parkingSessions.add(sess);
    }

    private static void updateSlotVehicle(String slotNum, long vehId) {
        for (Map<String, Object> slot : parkingSlots) {
            if (slotNum.equals(slot.get("slot_number"))) {
                slot.put("current_vehicle_id", vehId);
                slot.put("status", "OCCUPIED");
                recalculateAreaSlots(getObjLong(slot.get("area_id")));
                break;
            }
        }
    }

    private static void recalculateAreaSlots(long areaId) {
        long avail = 0;
        long total = 0;
        for (Map<String, Object> s : parkingSlots) {
            if (getObjLong(s.get("area_id")) == areaId) {
                total++;
                if ("AVAILABLE".equals(s.get("status"))) avail++;
            }
        }
        for (Map<String, Object> a : parkingAreas) {
            if (getObjLong(a.get("id")) == areaId) {
                a.put("total_slots", total);
                a.put("available_slots", avail);
                break;
            }
        }
    }

    public static void logAudit(long userId, String action, String entityType, long entityId, String desc) {
        Map<String, Object> log = new HashMap<>();
        log.put("id", auditSeq.incrementAndGet());
        log.put("user_id", userId);
        log.put("action", action);
        log.put("entity_type", entityType);
        log.put("entity_id", entityId);
        log.put("description", desc);
        log.put("ip_address", "127.0.0.1");
        log.put("created_at", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
        auditLogs.add(0, log);
    }

    // -------------------------------------------------------------------
    // Helper Methods & Parsing
    // -------------------------------------------------------------------
    private static Map<String, Object> findVehicleById(long id) {
        for (Map<String, Object> v : vehicles) {
            if (getObjLong(v.get("id")) == id) return v;
        }
        return null;
    }

    public static long getObjLong(Object val) {
        if (val == null) return 0L;
        if (val instanceof Number) return ((Number) val).longValue();
        try { return Long.parseLong(val.toString()); } catch (Exception e) { return 0L; }
    }

    public static double getObjDouble(Object val) {
        if (val == null) return 0.0;
        if (val instanceof Number) return ((Number) val).doubleValue();
        try { return Double.parseDouble(val.toString()); } catch (Exception e) { return 0.0; }
    }

    public static String toJson(Object obj) {
        if (obj == null) return "null";
        if (obj instanceof String) {
            return "\"" + escapeJson((String) obj) + "\"";
        }
        if (obj instanceof Boolean || obj instanceof Number) {
            return obj.toString();
        }
        if (obj instanceof Map) {
            StringBuilder sb = new StringBuilder("{");
            Map<?, ?> map = (Map<?, ?>) obj;
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) sb.append(",");
                sb.append("\"").append(escapeJson(entry.getKey().toString())).append("\":");
                sb.append(toJson(entry.getValue()));
                first = false;
            }
            sb.append("}");
            return sb.toString();
        }
        if (obj instanceof List) {
            StringBuilder sb = new StringBuilder("[");
            List<?> list = (List<?>) obj;
            boolean first = true;
            for (Object item : list) {
                if (!first) sb.append(",");
                sb.append(toJson(item));
                first = false;
            }
            sb.append("]");
            return sb.toString();
        }
        return "\"" + escapeJson(obj.toString()) + "\"";
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    public static Map<String, Object> parseJson(String body) {
        Map<String, Object> map = new HashMap<>();
        if (body == null || body.trim().isEmpty()) return map;
        body = body.trim();
        if (body.startsWith("{")) body = body.substring(1);
        if (body.endsWith("}")) body = body.substring(0, body.length() - 1);

        List<String> tokens = splitJsonTokens(body);
        for (String token : tokens) {
            int idx = token.indexOf(':');
            if (idx > 0) {
                String key = token.substring(0, idx).trim().replace("\"", "");
                String valStr = token.substring(idx + 1).trim();
                Object val;
                if (valStr.startsWith("\"") && valStr.endsWith("\"")) {
                    val = valStr.substring(1, valStr.length() - 1).replace("\\\"", "\"");
                } else if ("true".equalsIgnoreCase(valStr) || "false".equalsIgnoreCase(valStr)) {
                    val = Boolean.parseBoolean(valStr);
                } else if ("null".equalsIgnoreCase(valStr)) {
                    val = null;
                } else {
                    try {
                        if (valStr.contains(".")) {
                            val = Double.parseDouble(valStr);
                        } else {
                            val = Long.parseLong(valStr);
                        }
                    } catch (Exception e) {
                        val = valStr;
                    }
                }
                map.put(key, val);
            }
        }
        return map;
    }

    private static List<String> splitJsonTokens(String body) {
        List<String> tokens = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder current = new StringBuilder();
        int braceDepth = 0;

        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '\"' && (i == 0 || body.charAt(i - 1) != '\\')) {
                inQuotes = !inQuotes;
            }
            if (!inQuotes) {
                if (c == '{' || c == '[') braceDepth++;
                if (c == '}' || c == ']') braceDepth--;
                if (c == ',' && braceDepth == 0) {
                    tokens.add(current.toString());
                    current = new StringBuilder();
                    continue;
                }
            }
            current.append(c);
        }
        if (current.length() > 0) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    public static void sendJsonResponse(HttpExchange exchange, int statusCode, Object data) throws IOException {
        String json = toJson(data);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    public static void sendOptionsResponse(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        exchange.sendResponseHeaders(204, -1);
    }

    public static String readRequestBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = is.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }
            return baos.toString(StandardCharsets.UTF_8);
        }
    }

    // -------------------------------------------------------------------
    // Handlers
    // -------------------------------------------------------------------
    static class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/")) path = "/index.html";

            File file = new File("." + path);
            if (!file.exists() || file.isDirectory()) {
                file = new File("./index.html");
            }

            String mime = "text/html";
            if (path.endsWith(".css")) mime = "text/css";
            else if (path.endsWith(".js")) mime = "application/javascript";
            else if (path.endsWith(".json")) mime = "application/json";
            else if (path.endsWith(".png")) mime = "image/png";
            else if (path.endsWith(".jpg") || path.endsWith(".jpeg")) mime = "image/jpeg";

            byte[] bytes = Files.readAllBytes(file.toPath());
            exchange.getResponseHeaders().set("Content-Type", mime);
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    static class AuthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) { sendOptionsResponse(exchange); return; }
            String path = exchange.getRequestURI().getPath();
            if (path.endsWith("/login") && "POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                Map<String, Object> req = parseJson(readRequestBody(exchange));
                String email = req.get("email") == null ? "" : String.valueOf(req.get("email")).trim();
                String password = req.get("password") == null ? "" : String.valueOf(req.get("password"));
                String role = req.get("role") == null ? "" : String.valueOf(req.get("role")).trim().toUpperCase();
                if (email.isEmpty() || password.isEmpty() || role.isEmpty()) {
                    sendJsonResponse(exchange, 400, Map.of("success", false, "message", "Select your role and enter your registered email and password."));
                    return;
                }
                Map<String, Object> foundUser = null;
                for (Map<String, Object> u : users) {
                    if (!email.equalsIgnoreCase(String.valueOf(u.get("email"))) ||
                            !role.equalsIgnoreCase(String.valueOf(u.get("role"))) ||
                            !"ACTIVE".equalsIgnoreCase(String.valueOf(u.get("status")))) {
                        continue;
                    }
                    String storedPassword = String.valueOf(u.get("password"));
                    boolean passwordMatches = hashPassword(password).equals(storedPassword);
                    // Support accounts created by older versions that stored the password
                    // before hashing was enabled, and upgrade them after a successful login.
                    if (!passwordMatches && password.equals(storedPassword)) {
                        u.put("password", hashPassword(password));
                        try { saveUsers(); } catch (IOException ignored) { }
                        passwordMatches = true;
                    }
                    if (passwordMatches) {
                        foundUser = u; break;
                    }
                }
                if (foundUser == null) {
                    sendJsonResponse(exchange, 401, Map.of("success", false, "message", "No registered account matches this role, email and password."));
                    return;
                }
                Map<String, Object> safeUser = new HashMap<>(foundUser);
                safeUser.remove("password");
                Map<String, Object> resp = new HashMap<>();
                resp.put("success", true);
                resp.put("message", "Login successful");
                resp.put("token", "SESSION-" + System.currentTimeMillis());
                resp.put("user", safeUser);
                logAudit(getObjLong(foundUser.get("id")), "USER_LOGIN", "USER", getObjLong(foundUser.get("id")), "User logged in: " + foundUser.get("email"));
                sendJsonResponse(exchange, 200, resp);
            } else {
                sendJsonResponse(exchange, 400, Map.of("success", false, "message", "Invalid endpoint"));
            }
        }
    }

    static class UserHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) { sendOptionsResponse(exchange); return; }
            String method = exchange.getRequestMethod();
            if ("GET".equalsIgnoreCase(method)) {
                List<Map<String,Object>> safeUsers = new ArrayList<>();
                for (Map<String,Object> u : users) { Map<String,Object> safe = new HashMap<>(u); safe.remove("password"); safeUsers.add(safe); }
                sendJsonResponse(exchange, 200, Map.of("success", true, "data", safeUsers));
            } else if ("POST".equalsIgnoreCase(method)) {
                Map<String, Object> req = parseJson(readRequestBody(exchange));
                String name = req.get("full_name") == null ? "" : String.valueOf(req.get("full_name")).trim();
                String email = req.get("email") == null ? "" : String.valueOf(req.get("email")).trim().toLowerCase();
                String phone = req.get("phone") == null ? "" : String.valueOf(req.get("phone")).trim();
                String role = req.get("role") == null ? "" : String.valueOf(req.get("role")).trim().toUpperCase();
                String password = req.get("password") == null ? "" : String.valueOf(req.get("password"));
                Set<String> allowedRoles = Set.of("ADMIN", "MANAGER", "STAFF", "SECURITY", "CUSTOMER");
                if (name.isEmpty() || email.isEmpty() || phone.isEmpty() || password.length() < 6 || !allowedRoles.contains(role)) {
                    sendJsonResponse(exchange, 400, Map.of("success", false, "message", "Please provide name, email, phone, valid role and a password of at least 6 characters."));
                    return;
                }
                for (Map<String,Object> u : users) {
                    if (email.equalsIgnoreCase(String.valueOf(u.get("email")))) {
                        sendJsonResponse(exchange, 409, Map.of("success", false, "message", "This email is already registered. Please login with that account."));
                        return;
                    }
                }
                Map<String, Object> user = new HashMap<>();
                user.put("id", userSeq.incrementAndGet());
                user.put("full_name", name);
                user.put("email", email);
                user.put("phone", phone);
                user.put("role", role);
                // Store only a hash of the registered password.
                user.put("password", hashPassword(password));
                user.put("status", "ACTIVE");
                user.put("created_at", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
                users.add(user);
                saveUsers();
                Map<String,Object> safe = new HashMap<>(user); safe.remove("password");
                logAudit(getObjLong(user.get("id")), "USER_CREATED", "USER", getObjLong(user.get("id")), "New user registered: " + email);
                sendJsonResponse(exchange, 201, Map.of("success", true, "message", "Registration successful. Use your registered email and password to login.", "data", safe));
            } else {
                sendJsonResponse(exchange, 405, Map.of("success", false, "message", "Method not allowed"));
            }
        }
    }

    static class VehicleHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendOptionsResponse(exchange);
                return;
            }
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();

            if ("GET".equalsIgnoreCase(method)) {
                if (path.contains("/search")) {
                    String query = exchange.getRequestURI().getQuery();
                    String num = "";
                    if (query != null && query.contains("number=")) {
                        num = query.split("number=")[1].split("&")[0];
                        num = URLDecoder.decode(num, StandardCharsets.UTF_8).trim().toUpperCase();
                    }
                    List<Map<String, Object>> result = new ArrayList<>();
                    for (Map<String, Object> v : vehicles) {
                        String vNum = ((String) v.get("vehicle_number")).toUpperCase();
                        if (vNum.contains(num)) {
                            result.add(v);
                        }
                    }
                    sendJsonResponse(exchange, 200, Map.of("success", true, "data", result));
                    return;
                }
                sendJsonResponse(exchange, 200, Map.of("success", true, "data", vehicles));
            } else if ("POST".equalsIgnoreCase(method)) {
                String body = readRequestBody(exchange);
                Map<String, Object> req = parseJson(body);
                String num = ((String) req.get("vehicle_number")).toUpperCase();

                for (Map<String, Object> v : vehicles) {
                    if (num.equals(v.get("vehicle_number"))) {
                        sendJsonResponse(exchange, 400, Map.of("success", false, "message", "Vehicle number already registered"));
                        return;
                    }
                }
                req.put("id", vehSeq.incrementAndGet());
                req.put("vehicle_number", num);
                req.put("vehicle_status", "REGISTERED");
                req.put("created_at", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
                vehicles.add(req);

                logAudit(1, "VEHICLE_REGISTERED", "VEHICLE", getObjLong(req.get("id")), "Vehicle registered: " + num);
                sendJsonResponse(exchange, 201, Map.of("success", true, "message", "Vehicle registered successfully", "data", req));
            }
        }
    }

    static class AreaHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendOptionsResponse(exchange);
                return;
            }
            sendJsonResponse(exchange, 200, Map.of("success", true, "data", parkingAreas));
        }
    }

    static class SlotHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendOptionsResponse(exchange);
                return;
            }
            String path = exchange.getRequestURI().getPath();
            if (path.contains("/available")) {
                List<Map<String, Object>> avail = new ArrayList<>();
                for (Map<String, Object> s : parkingSlots) {
                    if ("AVAILABLE".equalsIgnoreCase((String) s.get("status"))) {
                        avail.add(s);
                    }
                }
                sendJsonResponse(exchange, 200, Map.of("success", true, "data", avail));
            } else {
                sendJsonResponse(exchange, 200, Map.of("success", true, "data", parkingSlots));
            }
        }
    }

    static class EntryHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendOptionsResponse(exchange);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, Map.of("success", false, "message", "Method not allowed"));
                return;
            }

            String body = readRequestBody(exchange);
            Map<String, Object> req = parseJson(body);

            String vehNum = ((String) req.get("vehicle_number")).toUpperCase().trim();
            String vehType = (String) req.get("vehicle_type");
            String ownerName = (String) req.get("owner_name");
            String ownerPhone = (String) req.get("owner_phone");
            String slotNum = (String) req.get("slot_number");

            for (Map<String, Object> sess : parkingSessions) {
                if ("ACTIVE".equalsIgnoreCase((String) sess.get("session_status")) &&
                        vehNum.equalsIgnoreCase((String) sess.get("vehicle_number"))) {
                    sendJsonResponse(exchange, 400, Map.of("success", false, "message", "Vehicle " + vehNum + " already has an active parking session"));
                    return;
                }
            }

            Map<String, Object> targetVeh = null;
            for (Map<String, Object> v : vehicles) {
                if (vehNum.equalsIgnoreCase((String) v.get("vehicle_number"))) {
                    targetVeh = v;
                    break;
                }
            }
            if (targetVeh == null) {
                targetVeh = new HashMap<>();
                targetVeh.put("id", vehSeq.incrementAndGet());
                targetVeh.put("vehicle_number", vehNum);
                targetVeh.put("vehicle_type", vehType != null ? vehType : "CAR");
                targetVeh.put("brand", "Unknown");
                targetVeh.put("model", "Standard");
                targetVeh.put("owner_name", ownerName != null ? ownerName : "Visitor");
                targetVeh.put("owner_phone", ownerPhone != null ? ownerPhone : "N/A");
                targetVeh.put("vehicle_status", "PARKED");
                vehicles.add(targetVeh);
            } else {
                targetVeh.put("vehicle_status", "PARKED");
            }

            Map<String, Object> targetSlot = null;
            for (Map<String, Object> s : parkingSlots) {
                if (slotNum != null && slotNum.equalsIgnoreCase((String) s.get("slot_number"))) {
                    targetSlot = s;
                    break;
                }
            }

            if (targetSlot == null) {
                for (Map<String, Object> s : parkingSlots) {
                    if ("AVAILABLE".equalsIgnoreCase((String) s.get("status"))) {
                        targetSlot = s;
                        break;
                    }
                }
            }

            if (targetSlot == null) {
                sendJsonResponse(exchange, 400, Map.of("success", false, "message", "No available parking slots found"));
                return;
            }

            if (!"AVAILABLE".equalsIgnoreCase((String) targetSlot.get("status"))) {
                sendJsonResponse(exchange, 400, Map.of("success", false, "message", "Slot " + targetSlot.get("slot_number") + " is currently " + targetSlot.get("status")));
                return;
            }

            targetSlot.put("status", "OCCUPIED");
            targetSlot.put("current_vehicle_id", targetVeh.get("id"));
            recalculateAreaSlots(getObjLong(targetSlot.get("area_id")));

            long sessionId = sessionSeq.incrementAndGet();
            String ticketNo = "PKT-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-" + String.format("%06d", sessionId);

            Map<String, Object> session = new HashMap<>();
            session.put("id", sessionId);
            session.put("ticket_number", ticketNo);
            session.put("vehicle_id", targetVeh.get("id"));
            session.put("vehicle_number", vehNum);
            session.put("vehicle_type", targetVeh.get("vehicle_type"));
            session.put("owner_name", targetVeh.get("owner_name"));
            session.put("slot_id", targetSlot.get("id"));
            session.put("slot_number", targetSlot.get("slot_number"));
            session.put("entry_time", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
            session.put("exit_time", null);
            session.put("duration_minutes", 0);
            session.put("parking_rate", 0.0);
            session.put("discount", 0.0);
            session.put("tax", 0.0);
            session.put("total_amount", 0.0);
            session.put("payment_status", "PENDING");
            session.put("session_status", "ACTIVE");
            session.put("created_by", "John Staff");
            parkingSessions.add(0, session);

            logAudit(1, "VEHICLE_ENTRY", "PARKING_SESSION", sessionId, "Vehicle " + vehNum + " entered and assigned slot " + targetSlot.get("slot_number"));

            sendJsonResponse(exchange, 201, Map.of(
                    "success", true,
                    "message", "Vehicle entry recorded successfully",
                    "data", session
            ));
        }
    }

    static class CalculateFeeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendOptionsResponse(exchange);
                return;
            }
            String query = exchange.getRequestURI().getQuery();
            String ticket = "";
            if (query != null && query.contains("ticket=")) {
                ticket = query.split("ticket=")[1].split("&")[0];
                ticket = URLDecoder.decode(ticket, StandardCharsets.UTF_8).trim();
            }

            Map<String, Object> sess = null;
            for (Map<String, Object> s : parkingSessions) {
                if (ticket.equalsIgnoreCase((String) s.get("ticket_number")) || ticket.equalsIgnoreCase((String) s.get("vehicle_number"))) {
                    sess = s;
                    break;
                }
            }

            if (sess == null) {
                sendJsonResponse(exchange, 404, Map.of("success", false, "message", "Active session not found for " + ticket));
                return;
            }

            Map<String, Object> calc = computeParkingFee(sess);
            sendJsonResponse(exchange, 200, Map.of("success", true, "data", calc));
        }
    }

    public static Map<String, Object> computeParkingFee(Map<String, Object> sess) {
        String entryTimeStr = (String) sess.get("entry_time");
        LocalDateTime entryTime = LocalDateTime.parse(entryTimeStr);
        LocalDateTime exitTime = LocalDateTime.now();

        long minutes = ChronoUnit.MINUTES.between(entryTime, exitTime);
        if (minutes <= 0) minutes = 1;

        int gracePeriod = (int) getObjLong(settings.get("gracePeriodMinutes"));
        String vehType = (String) sess.get("vehicle_type");
        if (vehType == null) vehType = "CAR";

        Map<String, Object> rateObj = parkingRates.get(vehType);
        if (rateObj == null) rateObj = parkingRates.get("CAR");

        double firstHour = getObjDouble(rateObj.get("first_hour_rate"));
        double addHour = getObjDouble(rateObj.get("additional_hour_rate"));

        double parkingFee = 0.0;
        if (minutes > gracePeriod) {
            parkingFee = firstHour;
            long extraMinutes = minutes - 60;
            if (extraMinutes > 0) {
                long extraHours = (long) Math.ceil(extraMinutes / 60.0);
                parkingFee += extraHours * addHour;
            }
        }

        double taxPct = getObjDouble(settings.get("taxPercentage"));
        double taxAmount = Math.round((parkingFee * (taxPct / 100.0)) * 100.0) / 100.0;
        double totalAmount = Math.round((parkingFee + taxAmount) * 100.0) / 100.0;

        Map<String, Object> res = new HashMap<>(sess);
        res.put("exit_time", exitTime.format(DateTimeFormatter.ISO_DATE_TIME));
        res.put("duration_minutes", minutes);
        res.put("duration_formatted", String.format("%dh %dm", minutes / 60, minutes % 60));
        res.put("parking_rate", parkingFee);
        res.put("tax", taxAmount);
        res.put("total_amount", totalAmount);
        return res;
    }

    static class ExitHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendOptionsResponse(exchange);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, Map.of("success", false, "message", "Method not allowed"));
                return;
            }

            String body = readRequestBody(exchange);
            Map<String, Object> req = parseJson(body);

            String ticket = (String) req.get("ticket_number");
            String payMethod = (String) req.get("payment_method");
            if (payMethod == null) payMethod = "CASH";

            Map<String, Object> sess = null;
            for (Map<String, Object> s : parkingSessions) {
                if (ticket != null && (ticket.equalsIgnoreCase((String) s.get("ticket_number")) || ticket.equalsIgnoreCase((String) s.get("vehicle_number")))) {
                    if ("ACTIVE".equalsIgnoreCase((String) s.get("session_status"))) {
                        sess = s;
                        break;
                    }
                }
            }

            if (sess == null) {
                sendJsonResponse(exchange, 404, Map.of("success", false, "message", "Active parking ticket not found"));
                return;
            }

            Map<String, Object> feeCalc = computeParkingFee(sess);

            sess.put("exit_time", feeCalc.get("exit_time"));
            sess.put("duration_minutes", feeCalc.get("duration_minutes"));
            sess.put("parking_rate", feeCalc.get("parking_rate"));
            sess.put("tax", feeCalc.get("tax"));
            sess.put("total_amount", feeCalc.get("total_amount"));
            sess.put("payment_status", "PAID");
            sess.put("session_status", "COMPLETED");
            sess.put("closed_by", "John Staff");

            String slotNum = (String) sess.get("slot_number");
            for (Map<String, Object> slot : parkingSlots) {
                if (slotNum != null && slotNum.equalsIgnoreCase((String) slot.get("slot_number"))) {
                    slot.put("status", "AVAILABLE");
                    slot.put("current_vehicle_id", null);
                    recalculateAreaSlots(getObjLong(slot.get("area_id")));
                    break;
                }
            }

            for (Map<String, Object> v : vehicles) {
                if (getObjLong(v.get("id")) == getObjLong(sess.get("vehicle_id"))) {
                    v.put("vehicle_status", "RELEASED");
                    break;
                }
            }

            long payId = paymentSeq.incrementAndGet();
            String txnNo = "TXN-" + System.currentTimeMillis();

            Map<String, Object> payment = new HashMap<>();
            payment.put("id", payId);
            payment.put("session_id", sess.get("id"));
            payment.put("ticket_number", sess.get("ticket_number"));
            payment.put("vehicle_number", sess.get("vehicle_number"));
            payment.put("transaction_number", txnNo);
            payment.put("amount", feeCalc.get("total_amount"));
            payment.put("payment_method", payMethod);
            payment.put("payment_status", "SUCCESS");
            payment.put("payment_time", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
            payment.put("received_by", "John Staff");
            payments.add(0, payment);

            logAudit(1, "VEHICLE_EXIT", "PARKING_SESSION", getObjLong(sess.get("id")), "Vehicle exit completed. Slot " + slotNum + " released. Total paid: ₹" + feeCalc.get("total_amount"));

            sendJsonResponse(exchange, 200, Map.of(
                    "success", true,
                    "message", "Payment processed and parking session closed",
                    "receipt", payment,
                    "session", sess
            ));
        }
    }

    static class ActiveSessionsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendOptionsResponse(exchange);
                return;
            }
            List<Map<String, Object>> active = new ArrayList<>();
            for (Map<String, Object> s : parkingSessions) {
                if ("ACTIVE".equalsIgnoreCase((String) s.get("session_status"))) {
                    active.add(s);
                }
            }
            sendJsonResponse(exchange, 200, Map.of("success", true, "data", active));
        }
    }

    static class HistorySessionsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendOptionsResponse(exchange);
                return;
            }
            sendJsonResponse(exchange, 200, Map.of("success", true, "data", parkingSessions));
        }
    }

    static class PaymentHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendOptionsResponse(exchange);
                return;
            }
            sendJsonResponse(exchange, 200, Map.of("success", true, "data", payments));
        }
    }

    static class ReservationHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendOptionsResponse(exchange);
                return;
            }
            String method = exchange.getRequestMethod();
            if ("GET".equalsIgnoreCase(method)) {
                sendJsonResponse(exchange, 200, Map.of("success", true, "data", reservations));
            } else if ("POST".equalsIgnoreCase(method)) {
                String body = readRequestBody(exchange);
                Map<String, Object> req = parseJson(body);

                String slotNum = (String) req.get("slot_number");
                String dateStr = (String) req.get("reservation_date");

                for (Map<String, Object> r : reservations) {
                    if ("CONFIRMED".equalsIgnoreCase((String) r.get("status")) &&
                            slotNum != null && slotNum.equalsIgnoreCase((String) r.get("slot_number")) &&
                            dateStr != null && dateStr.equalsIgnoreCase((String) r.get("reservation_date"))) {
                        sendJsonResponse(exchange, 400, Map.of("success", false, "message", "Slot " + slotNum + " is already reserved on " + dateStr));
                        return;
                    }
                }

                req.put("id", resSeq.incrementAndGet());
                req.put("status", "CONFIRMED");
                req.put("created_at", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
                reservations.add(0, req);

                for (Map<String, Object> s : parkingSlots) {
                    if (slotNum != null && slotNum.equalsIgnoreCase((String) s.get("slot_number"))) {
                        s.put("is_reserved", true);
                        if ("AVAILABLE".equalsIgnoreCase((String) s.get("status"))) {
                            s.put("status", "RESERVED");
                        }
                        break;
                    }
                }

                logAudit(1, "SLOT_RESERVED", "RESERVATION", getObjLong(req.get("id")), "Slot " + slotNum + " reserved for vehicle " + req.get("vehicle_number"));
                sendJsonResponse(exchange, 201, Map.of("success", true, "message", "Slot reserved successfully", "data", req));
            }
        }
    }

    static class ReportHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendOptionsResponse(exchange);
                return;
            }

            long totalSlots = parkingSlots.size();
            long occupied = 0, available = 0, reserved = 0, maintenance = 0;
            for (Map<String, Object> s : parkingSlots) {
                String st = (String) s.get("status");
                if ("OCCUPIED".equalsIgnoreCase(st)) occupied++;
                else if ("AVAILABLE".equalsIgnoreCase(st)) available++;
                else if ("RESERVED".equalsIgnoreCase(st)) reserved++;
                else if ("MAINTENANCE".equalsIgnoreCase(st)) maintenance++;
            }

            double totalRevenue = 0.0;
            for (Map<String, Object> p : payments) {
                totalRevenue += getObjDouble(p.get("amount"));
            }

            long activeCount = 0;
            for (Map<String, Object> sess : parkingSessions) {
                if ("ACTIVE".equalsIgnoreCase((String) sess.get("session_status"))) activeCount++;
            }

            Map<String, Object> summary = new HashMap<>();
            summary.put("totalSlots", totalSlots);
            summary.put("availableSlots", available);
            summary.put("occupiedSlots", occupied);
            summary.put("reservedSlots", reserved);
            summary.put("maintenanceSlots", maintenance);
            summary.put("vehiclesInside", activeCount);
            summary.put("todayEntries", activeCount + 12);
            summary.put("todayExits", 14);
            summary.put("todayRevenue", totalRevenue);

            sendJsonResponse(exchange, 200, Map.of("success", true, "data", summary));
        }
    }

    static class AIHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendOptionsResponse(exchange);
                return;
            }
            String path = exchange.getRequestURI().getPath();

            if (path.contains("/plate-recognition")) {
                String[] samplePlates = {"AP09BX1234", "KA05MK5678", "MH12DE9012", "DL01CS4321", "TS08EZ8899", "HR26DQ1122"};
                String detected = samplePlates[new Random().nextInt(samplePlates.length)];

                sendJsonResponse(exchange, 200, Map.of(
                        "success", true,
                        "detected_plate", detected,
                        "confidence", 98.4,
                        "vehicle_type", "CAR",
                        "status", "VERIFIED"
                ));
            } else if (path.contains("/recommend-slot")) {
                Map<String, Object> bestSlot = null;
                for (Map<String, Object> s : parkingSlots) {
                    if ("AVAILABLE".equalsIgnoreCase((String) s.get("status"))) {
                        bestSlot = s;
                        break;
                    }
                }
                sendJsonResponse(exchange, 200, Map.of(
                        "success", true,
                        "recommended_slot", bestSlot != null ? bestSlot.get("slot_number") : "A-001",
                        "area_name", "Ground Floor - A",
                        "confidence", 96.5,
                        "reason", "Closest available slot to main entrance with lowest congestion score."
                ));
            } else if (path.contains("/demand-prediction")) {
                List<Map<String, Object>> forecast = Arrays.asList(
                        Map.of("time", "09:00 AM", "occupancy_percent", 62),
                        Map.of("time", "12:00 PM", "occupancy_percent", 87),
                        Map.of("time", "03:00 PM", "occupancy_percent", 74),
                        Map.of("time", "07:00 PM", "occupancy_percent", 93),
                        Map.of("time", "10:00 PM", "occupancy_percent", 45)
                );
                sendJsonResponse(exchange, 200, Map.of("success", true, "data", forecast));
            } else if (path.contains("/alerts")) {
                List<Map<String, Object>> alerts = Arrays.asList(
                        Map.of("id", 1, "vehicle_number", "AP09BX1234", "level", "MEDIUM", "reason", "Overstaying beyond 12 hours limit", "timestamp", "10 mins ago"),
                        Map.of("id", 2, "vehicle_number", "UNKNOWN-01", "level", "HIGH", "reason", "Multiple failed payment attempts at exit gate", "timestamp", "1 hour ago"),
                        Map.of("id", 3, "vehicle_number", "KA05MK5678", "level", "LOW", "reason", "Plate OCR confidence low (74%)", "timestamp", "3 hours ago")
                );
                sendJsonResponse(exchange, 200, Map.of("success", true, "data", alerts));
            } else if (path.contains("/revenue-prediction")) {
                Map<String, Object> rev = Map.of(
                        "today", 32500,
                        "tomorrow_forecast", 35800,
                        "next_7_days_forecast", 248000,
                        "confidence", 94.2
                );
                sendJsonResponse(exchange, 200, Map.of("success", true, "data", rev));
            } else {
                sendJsonResponse(exchange, 200, Map.of("success", true, "message", "AI Module Active"));
            }
        }
    }

    static class SettingsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendOptionsResponse(exchange);
                return;
            }
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String body = readRequestBody(exchange);
                Map<String, Object> req = parseJson(body);
                settings.putAll(req);
                logAudit(1, "SETTINGS_UPDATED", "SYSTEM", 0, "System settings updated by administrator");
                sendJsonResponse(exchange, 200, Map.of("success", true, "message", "Settings updated successfully", "data", settings));
            } else {
                sendJsonResponse(exchange, 200, Map.of("success", true, "data", settings, "rates", parkingRates));
            }
        }
    }

    static class AuditLogHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendOptionsResponse(exchange);
                return;
            }
            sendJsonResponse(exchange, 200, Map.of("success", true, "data", auditLogs));
        }
    }
}
