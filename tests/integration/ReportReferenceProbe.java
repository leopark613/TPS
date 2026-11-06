import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;

/** Read-only test helper. Output is consumed in memory, never copied into evidence/logs. */
public final class ReportReferenceProbe {
    public static void main(String[] args) {
        try {
            Properties p=new Properties();
            for(String key:List.of("USER","PASSWORD")) {
                String value=System.getenv("TPS_DB_"+key);
                if(value==null || value.isBlank()) throw new IllegalArgumentException();
                p.setProperty(key.toLowerCase(Locale.ROOT),value);
            }
            if(!"true".equals(System.getenv("TPS_DB_TRUST_SERVER_CERTIFICATE"))) throw new IllegalArgumentException();
            p.setProperty("encrypt","true"); p.setProperty("trustServerCertificate","true");
            p.setProperty("loginTimeout","8"); p.setProperty("applicationName","TPS-IMPL-002-reference-probe");
            try(Connection c=DriverManager.getConnection("jdbc:sqlserver://10.121.13.159:1433;databaseName=TPS",p);
                PreparedStatement ps=c.prepareStatement("SELECT TOP (1) SITEID FROM dbo.RPS_REPORT GROUP BY SITEID ORDER BY SITEID")) {
                c.setReadOnly(true); ps.setQueryTimeout(5);
                try(ResultSet rs=ps.executeQuery()) {
                    if(!rs.next()) throw new IllegalStateException();
                    System.out.print(Base64.getEncoder().encodeToString(rs.getString(1).getBytes(StandardCharsets.UTF_8)));
                }
            }
        } catch(Exception e) { System.err.print("Reference probe failed: "+e.getClass().getSimpleName()); System.exit(1); }
    }
}
