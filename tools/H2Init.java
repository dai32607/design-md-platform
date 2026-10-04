import org.h2.tools.RunScript;

import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;

/**
 * 构建期工具：以 UTF-8 编码导入 H2 初始化脚本，生成随包分发的演示数据库文件。
 */
public class H2Init {
    public static void main(String[] args) throws Exception {
        String url = args[0];
        Class.forName("org.h2.Driver");
        try (Connection conn = DriverManager.getConnection(url, "sa", "")) {
            for (int i = 1; i < args.length; i++) {
                try (Reader reader = new InputStreamReader(new FileInputStream(args[i]), StandardCharsets.UTF_8)) {
                    RunScript.execute(conn, reader);
                }
                System.out.println("EXEC OK: " + args[i]);
            }
        }
        System.out.println("ALL OK");
    }
}
