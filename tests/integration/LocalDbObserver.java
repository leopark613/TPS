import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;

/** Test-only, fixed-endpoint observer. Never ships in the application JAR. */
public final class LocalDbObserver {
    static final String[] DOC = {"TPS_DOC_TEMPLATE", "TPS_DOC_REVISION", "TPS_DOC_REQUEST", "TPS_DOC_ACTIVATION", "TPS_DOC_ASSET", "TPS_DOC_REVISION_ASSET", "TPS_DOC_IMPORT"};
    static final String ACTOR = "tps-developer";
    static String stage = "arguments";
    public static void main(String[] args) throws Exception {
        try {
            if (args.length < 2 || !Set.of("snapshot", "cleanup").contains(args[0])) throw new IllegalArgumentException("Expected snapshot SITE or cleanup PLAN_FILE");
            String site = args[0].equals("snapshot") ? args[1] : Files.readAllLines(Path.of(args[1]), StandardCharsets.UTF_8).getFirst();
            if (!site.matches("TPS_IT_[A-Za-z0-9_]{8,32}") || site.length() > 40) throw new IllegalArgumentException("Dedicated TPS_IT site required");
            stage = "credentials";
            Properties p = new Properties();
            p.setProperty("user", required("TPS_DB_USER")); p.setProperty("password", required("TPS_DB_PASSWORD"));
            p.setProperty("encrypt", "true");
            p.setProperty("trustServerCertificate", System.getenv().getOrDefault("TPS_DB_TRUST_SERVER_CERTIFICATE", "false"));
            p.setProperty("loginTimeout", "8"); p.setProperty("applicationName", "TPS-IMPL-001-test-observer");
            stage = "connect";
            try (Connection c = DriverManager.getConnection("jdbc:sqlserver://10.121.13.159:1433;databaseName=TPS", p)) {
                stage = "verify-database";
                try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT DB_NAME()")) {
                    rs.next(); if (!"TPS".equals(rs.getString(1))) throw new IllegalStateException("Wrong database");
                }
                System.out.println(args[0].equals("snapshot") ? snapshot(c, site) : cleanup(c, Path.of(args[1]), site));
            }
        } catch (Exception e) {
            // No exception message, SQL statement, server or business values.
            StringJoiner chain = new StringJoiner(",", "[", "]");
            Throwable cause = e;
            Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
            while (cause != null && seen.size() < 8 && seen.add(cause)) {
                if (cause instanceof SQLException sql) {
                    String state = sql.getSQLState();
                    chain.add("{\"type\":" + q(sql.getClass().getSimpleName()) + ",\"sqlState\":" + (state != null && state.matches("[A-Za-z0-9]{5}") ? q(state) : "null") + ",\"errorCode\":" + sql.getErrorCode() + "}");
                    cause = sql.getNextException() != null ? sql.getNextException() : sql.getCause();
                } else { cause = cause.getCause(); }
            }
            System.err.println("{\"diagnosticKind\":\"tps-observer-v1\",\"stage\":" + q(stage) + ",\"type\":" + q(e.getClass().getSimpleName()) + ",\"sqlErrors\":" + chain + "}");
            System.exit(1);
        }
    }
    static String required(String key) { String v = System.getenv(key); if (v == null || v.isEmpty()) throw new IllegalArgumentException("Missing credential environment"); return v; }
    static String q(String s) { return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\""; }
    static String hex(byte[] b) { return HexFormat.of().formatHex(b); }
    static void feed(MessageDigest d, byte[] b) { d.update((b.length + ":").getBytes(StandardCharsets.UTF_8)); d.update(b); }
    static String digestTable(Connection c, String table, String where, String site) throws Exception {
        if (!table.matches("(?:RPS_|TPS_DOC_)[A-Z0-9_]+")) throw new IllegalArgumentException("Unsafe table name");
        stage = "snapshot-table-" + table;
        List<String> hashes = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT * FROM dbo.[" + table + "]" + where)) {
            ps.setQueryTimeout(20); if (site != null) ps.setString(1, site);
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData md = rs.getMetaData();
                while (rs.next()) {
                    if (hashes.size() >= 10000) throw new IllegalStateException("Bounded observer row cap exceeded");
                    MessageDigest d = MessageDigest.getInstance("SHA-256");
                    for (int i = 1; i <= md.getColumnCount(); i++) {
                        feed(d, (md.getColumnName(i) + ":" + md.getColumnTypeName(i)).getBytes(StandardCharsets.UTF_8));
                        Object value = rs.getObject(i);
                        if (value == null) d.update((byte) 0);
                        else { d.update((byte) 1); feed(d, value instanceof byte[] b ? b : rs.getString(i).getBytes(StandardCharsets.UTF_8)); }
                    }
                    hashes.add(hex(d.digest()));
                }
            }
        }
        Collections.sort(hashes); MessageDigest d = MessageDigest.getInstance("SHA-256");
        hashes.forEach(h -> feed(d, h.getBytes(StandardCharsets.US_ASCII)));
        return "{\"rows\":" + hashes.size() + ",\"sha256\":" + q(hex(d.digest())) + "}";
    }
    static String snapshot(Connection c, String site) throws Exception {
        stage = "snapshot-table-list";
        List<String> tables = new ArrayList<>();
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT t.name FROM sys.tables t JOIN sys.schemas s ON s.schema_id=t.schema_id WHERE s.name='dbo' AND t.name LIKE 'RPS[_]%' ORDER BY t.name")) { while (rs.next()) tables.add(rs.getString(1)); }
        if (tables.size() != 12) throw new IllegalStateException("Expected RPS12 baseline");
        StringJoiner rps = new StringJoiner(",", "{", "}"); for (String t : tables) rps.add(q(t) + ":" + digestTable(c, t, "", null));
        StringJoiner other = new StringJoiner(",", "{", "}");
        StringJoiner own = new StringJoiner(",", "{", "}");
        for (String t : DOC) { other.add(q(t) + ":" + digestTable(c, t, " WHERE SITE_ID <> ?", site)); own.add(q(t) + ":" + digestTable(c, t, " WHERE SITE_ID = ?", site)); }
        stage = "snapshot-site-heads";
        StringJoiner ids = new StringJoiner(",", "[", "]");
        try (PreparedStatement ps = c.prepareStatement("SELECT TEMPLATE_ID FROM dbo.TPS_DOC_TEMPLATE WHERE SITE_ID=? ORDER BY TEMPLATE_ID")) { ps.setString(1,site); try(ResultSet rs=ps.executeQuery()) { while(rs.next()) ids.add(q(rs.getString(1).toLowerCase(Locale.ROOT))); } }
        long active = count(c, "SELECT COUNT_BIG(*) FROM dbo.TPS_DOC_TEMPLATE WHERE SITE_ID=? AND (ACTIVE_REVISION_ID IS NOT NULL OR ACTIVE_TOKEN<>0)", site);
        long orphan = count(c, "SELECT COUNT_BIG(*) FROM dbo.TPS_DOC_TEMPLATE WHERE SITE_ID=? AND (HEAD_REVISION_ID IS NULL OR HEAD_TOKEN<=0)", site);
        return "{\"rps\":" + rps + ",\"rpsCatalogHash\":" + q(catalogHash(c)) + ",\"otherSites\":" + other + ",\"site\":" + own + ",\"templateIds\":" + ids + ",\"activeCount\":" + active + ",\"uncommittedHeadCount\":" + orphan + "}";
    }
    static String catalogHash(Connection c) throws Exception {
        stage = "snapshot-rps-catalog";
        MessageDigest d=MessageDigest.getInstance("SHA-256");
        String sql="SELECT t.name,c.column_id,c.name,ty.name,c.max_length,c.precision,c.scale,c.is_nullable,c.collation_name,dc.definition,ic.seed_value,ic.increment_value,ic.last_value FROM sys.tables t JOIN sys.schemas s ON s.schema_id=t.schema_id JOIN sys.columns c ON c.object_id=t.object_id JOIN sys.types ty ON ty.user_type_id=c.user_type_id LEFT JOIN sys.default_constraints dc ON dc.object_id=c.default_object_id LEFT JOIN sys.identity_columns ic ON ic.object_id=c.object_id AND ic.column_id=c.column_id WHERE s.name='dbo' AND t.name LIKE 'RPS[_]%' ORDER BY t.name,c.column_id";
        try(Statement st=c.createStatement();ResultSet rs=st.executeQuery(sql)){int columns=rs.getMetaData().getColumnCount();while(rs.next()){for(int i=1;i<=columns;i++){String v=rs.getString(i);if(v==null)d.update((byte)0);else{d.update((byte)1);feed(d,v.getBytes(StandardCharsets.UTF_8));}}}}
        return hex(d.digest());
    }
    static long count(Connection c, String sql, String site) throws Exception { try(PreparedStatement ps=c.prepareStatement(sql)) { ps.setString(1,site); try(ResultSet rs=ps.executeQuery()) {rs.next();return rs.getLong(1);} } }
    static String cleanup(Connection c, Path plan, String site) throws Exception {
        stage = "cleanup-plan";
        Set<String> tids = new HashSet<>(), drafts = new HashSet<>();
        for (String l : Files.readAllLines(plan, StandardCharsets.UTF_8).subList(1, Files.readAllLines(plan, StandardCharsets.UTF_8).size())) {
            if (l.isBlank()) continue;
            String[] parts=l.split("=",2); if(parts.length!=2) throw new IllegalArgumentException("Malformed plan");
            String id=UUID.fromString(parts[1]).toString();
            if(parts[0].equals("T")) tids.add(id); else if(parts[0].equals("C")) drafts.add(id); else throw new IllegalArgumentException("Unknown plan entry");
        }
        if(tids.size()>20 || drafts.size()>80) throw new IllegalStateException("Cleanup cap exceeded");
        c.setAutoCommit(false); c.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
        try {
            stage = "cleanup-ownership";
            Set<String> actual=new HashSet<>();
            try(PreparedStatement ps=c.prepareStatement("SELECT TEMPLATE_ID,CREATED_BY,ACTIVE_REVISION_ID,ACTIVE_TOKEN FROM dbo.TPS_DOC_TEMPLATE WITH(UPDLOCK,HOLDLOCK) WHERE SITE_ID=?")) {
                ps.setString(1,site); try(ResultSet rs=ps.executeQuery()) {while(rs.next()) {
                    actual.add(rs.getString(1).toLowerCase(Locale.ROOT));
                    if(!ACTOR.equals(rs.getString(2)) || rs.getObject(3)!=null || rs.getLong(4)!=0) throw new IllegalStateException("Unexpected owner or active content");
                }}
            }
            if(!actual.equals(tids)) throw new IllegalStateException("Untracked templates; cleanup refused");
            for(String t : new String[]{"TPS_DOC_ACTIVATION","TPS_DOC_ASSET","TPS_DOC_REVISION_ASSET","TPS_DOC_IMPORT"}) if(count(c,"SELECT COUNT_BIG(*) FROM dbo.["+t+"] WHERE SITE_ID=?",site)!=0) throw new IllegalStateException("Unexpected dependent content");
            List<String> receipts=new ArrayList<>();
            try(PreparedStatement ps=c.prepareStatement("SELECT RECEIPT_ID,ACTOR_ID,OPERATION,SCOPE_KIND,SCOPE_ID FROM dbo.TPS_DOC_REQUEST WITH(UPDLOCK,HOLDLOCK) WHERE SITE_ID=?")) {
                ps.setString(1,site); try(ResultSet rs=ps.executeQuery()) {while(rs.next()) {
                    String scope=rs.getString(5).toLowerCase(Locale.ROOT);
                    boolean own = ACTOR.equals(rs.getString(2)) && (("CREATE".equals(rs.getString(3)) && "CLIENT_DRAFT".equals(rs.getString(4)) && drafts.contains(scope)) || ("SAVE".equals(rs.getString(3)) && "TEMPLATE".equals(rs.getString(4)) && tids.contains(scope)));
                    if(!own) throw new IllegalStateException("Untracked receipt; cleanup refused"); receipts.add(rs.getString(1));
                }}
            }
            for(String id:tids) {
                stage = "cleanup-owned-template";
                update(c,"UPDATE dbo.TPS_DOC_TEMPLATE SET HEAD_REVISION_ID=NULL WHERE SITE_ID=? AND TEMPLATE_ID=? AND ACTIVE_REVISION_ID IS NULL",site,id);
                List<String> revisions=new ArrayList<>();
                try(PreparedStatement ps=c.prepareStatement("SELECT REVISION_ID,CREATED_BY FROM dbo.TPS_DOC_REVISION WHERE SITE_ID=? AND TEMPLATE_ID=? ORDER BY REVISION_NO DESC")) {ps.setString(1,site);ps.setString(2,id);try(ResultSet rs=ps.executeQuery()){while(rs.next()){if(!ACTOR.equals(rs.getString(2))) throw new IllegalStateException("Unexpected revision owner");revisions.add(rs.getString(1));}}}
                for(String rid:revisions) {try(PreparedStatement ps=c.prepareStatement("DELETE FROM dbo.TPS_DOC_REVISION WHERE SITE_ID=? AND TEMPLATE_ID=? AND REVISION_ID=?")){ps.setString(1,site);ps.setString(2,id);ps.setString(3,rid);if(ps.executeUpdate()!=1)throw new IllegalStateException("Revision cleanup conflict");}}
                update(c,"DELETE FROM dbo.TPS_DOC_TEMPLATE WHERE SITE_ID=? AND TEMPLATE_ID=?",site,id);
            }
            stage = "cleanup-owned-receipts";
            for(String id:receipts) update(c,"DELETE FROM dbo.TPS_DOC_REQUEST WHERE SITE_ID=? AND RECEIPT_ID=?",site,id);
            stage = "cleanup-verify";
            for(String t:DOC) if(count(c,"SELECT COUNT_BIG(*) FROM dbo.["+t+"] WHERE SITE_ID=?",site)!=0) throw new IllegalStateException("Cleanup incomplete");
            c.commit(); return "{\"cleaned\":true,\"templates\":"+tids.size()+",\"receipts\":"+receipts.size()+"}";
        } catch(Exception e) {c.rollback();throw e;} finally {c.setAutoCommit(true);}
    }
    static void update(Connection c,String sql,String site,String id)throws Exception{try(PreparedStatement ps=c.prepareStatement(sql)){ps.setString(1,site);ps.setString(2,id);if(ps.executeUpdate()!=1)throw new IllegalStateException("Cleanup conflict");}}
}
