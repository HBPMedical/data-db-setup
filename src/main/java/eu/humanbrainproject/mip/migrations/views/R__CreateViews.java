package eu.humanbrainproject.mip.migrations.views;

import eu.humanbrainproject.mip.migrations.MigrationConfiguration;
import org.apache.commons.lang3.StringUtils;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.migration.MigrationChecksumProvider;
import org.flywaydb.core.api.migration.MigrationInfoProvider;
import org.flywaydb.core.api.migration.jdbc.JdbcMigration;
import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;

import java.io.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.CRC32;

@SuppressWarnings("unused")
public class R__CreateViews implements JdbcMigration, MigrationInfoProvider, MigrationChecksumProvider {

    private static final Logger LOG = Logger.getLogger("Create views");

    private MigrationConfiguration config = new MigrationConfiguration(this.getClass());
    private final Map<String, Properties> viewProperties = new HashMap<>();

    @Override
    public boolean isUndo() {
        return false;
    }

    public void migrate(Connection connection) throws Exception {
        String[] views = getViews();
        try {

            connection.setAutoCommit(false);

            for (String view: views) {
                LOG.info("Creating view " + view + "...");
                createView(connection, view);
            }

            connection.commit();

        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Cannot create views for data", e);
            throw e;
        }
    }

    private void createView(Connection connection, String view) throws IOException, SQLException {
        Map<String, Object> scopes = new HashMap<>();
        int i = 0;
        for (String table : getTables(view)) {
            Properties tableProperties = config.getColumnsProperties(table);

            String tableName = tableProperties.getProperty("__TABLE", table);
            List<String> columns = config.getColumns(table);
            List<String> ids = config.getIdColumns(table);

            final Table templateValue = new Table(tableName, columns, ids);
            scopes.put("table" + (++i), templateValue);
        }

        Properties viewProperties = getViewProperties(view);
        String viewName = viewProperties.getProperty("__VIEW", view);
        String viewColumnsStr = viewProperties.getProperty("__COLUMNS", "");
        List<String> viewColumns = Arrays.asList(StringUtils.split(viewColumnsStr, ","));

        scopes.put("view", new Table(viewName, viewColumns, new ArrayList<>(0)));

        StringWriter writer = new StringWriter();
        MustacheFactory mf = new DefaultMustacheFactory();
        Mustache mustache = mf.compile(new InputStreamReader(getViewTemplateResource(view)), view + ".mustache.sql");
        mustache.execute(writer, scopes);
        writer.flush();

        String createViewSql = writer.toString();

        try {
            connection.createStatement().execute(createViewSql);
        } catch (SQLException e) {
            LOG.severe("Cannot execute the following SQL statement: \n" + createViewSql);
            throw e;
        }

    }

    private String[] getViews() {
        String viewsStr = System.getenv("VIEWS");
        if (viewsStr == null || "".equals(viewsStr.trim())) {
            return new String[0];
        }
        return viewsStr.trim().split(",");
    }

    private String[] getTables(String viewName) throws IOException {
        return getViewProperties(viewName).getProperty("__TABLES").split(",");
    }

    private Properties getViewProperties(String viewName) throws IOException {
        Properties viewProperties = this.viewProperties.get(viewName);
        if (viewProperties == null) {
            viewProperties = new Properties();
            InputStream viewResource = getViewResource(viewName);
            viewProperties.load(viewResource);
            this.viewProperties.put(viewName, viewProperties);
        }
        return viewProperties;
    }

    private InputStream getViewResource(String viewName) {
        String propertiesFile = (viewName == null) ? "view.properties" : viewName + "_view.properties";
        if (!config.existsConfigResource(propertiesFile) && getViews().length == 1) {
            propertiesFile = "view.properties" ;
        }
        if (!config.existsConfigResource(propertiesFile)) {
            throw new IllegalStateException("Cannot load resource for view " + viewName + " from /config/" +
                    propertiesFile + ". Check VIEWS environment variable and contents of the jar");
        }
        return config.getConfigResource(propertiesFile);
    }

    private InputStream getViewTemplateResource(String viewName) throws IOException {
        Properties viewProperties = getViewProperties(viewName);
        String sqlTemplateFile = viewProperties.getProperty("__SQL_TEMPLATE");

        if (sqlTemplateFile == null) {
            sqlTemplateFile = (viewName == null) ? "view.mustache.sql" : viewName + "_view.mustache.sql";
            if (!config.existsConfigResource(sqlTemplateFile) && getViews().length == 1) {
                sqlTemplateFile = "view.mustache.sql";
            }
        }
        if (!config.existsConfigResource(sqlTemplateFile)) {
            throw new IllegalStateException("Cannot load resource for view " + viewName + " from /config/" +
                    sqlTemplateFile + ". Check VIEWS environment variable and contents of the jar");
        }
        return config.getConfigResource(sqlTemplateFile);
    }

    @Override
    public Integer getChecksum() {
        String[] views = getViews();
        int checksum = 0;
        for (String view: views) {
            try {
                checksum += computeChecksum(view);
            } catch (RuntimeException e) {
                LOG.log(Level.SEVERE, "Cannot compute checksum", e);
            }
        }
        return checksum;
    }

    private int computeChecksum(String view) {
        final CRC32 crc32 = new CRC32();

        // Use the name of the view
        byte[] bytes = view.getBytes();
        crc32.update(bytes, 0, bytes.length);

        // Use the values in the dataset
        try {
            InputStream viewResource = getViewResource(view);
            crcForResource(crc32, viewResource);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Cannot read data from " + view + "_view.properties", e);
        }

        try {
            InputStream viewTemplateResource = getViewTemplateResource(view);
            crcForResource(crc32, viewTemplateResource);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Cannot read data from " + view + "_view.mustache.sql", e);
        }

        // CRC for tables
        try {
            for (String table : getTables(view)) {
                InputStream tableResource = config.getColumnsResource(table);
                crcForResource(crc32, tableResource);
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Cannot read table properties", e);
        }

        return (int) crc32.getValue();
    }

    private void crcForResource(CRC32 crc32, InputStream resource) throws IOException {
        byte[] data = new byte[1024];
        int read;
        while ((read = resource.read(data)) > 0) {
            crc32.update(data, 0, read);
        }
    }

    @Override
    public MigrationVersion getVersion() {
        return null;
    }

    @Override
    public String getDescription() {
        String[] views = getViews();
        return "Create view" + (views.length > 1 ? "s " : " ") + StringUtils.join(views, ',');
    }

    static class Table {

        private final String name;
        private final List<String> columns;
        private final List<String> ids;

        Table(String name, List<String> columns, List<String> ids) {

            this.name = name;
            this.columns = columns;
            this.ids = ids;
        }

        public String getName() {
            return name;
        }

        public String getColumns() {
            return '"' + StringUtils.join(columns, "\",\"") + '"';
        }

        public String getColumnsNoId() {
            List<String> cols = new ArrayList<>(columns);
            cols.removeAll(ids);
            return '"' + StringUtils.join(cols, "\",\"") + '"';
        }

        public String getIds() {
            return '"' + StringUtils.join(ids, "\",\"") + '"';
        }

        public String getQualifiedColumns() {
            List<String> cols = new ArrayList<>(columns);
            cols.replaceAll(s -> "\"" + name + "\".\"" + s + "\"");
            return StringUtils.join(cols, ',');
        }

        public String getQualifiedColumnsNoId() {
            List<String> cols = new ArrayList<>(columns);
            cols.removeAll(ids);
            cols.replaceAll(s -> "\"" + name + "\".\"" + s + "\"");
            return StringUtils.join(cols, ',');
        }

        public String getQualifiedId() {
            if (ids.size() != 1) {
                throw new IllegalStateException("Table " + name + " does not have one column used as id");
            }
            return "\"" + name + "\".\"" + ids.get(0) + "\"";
        }

    }
}
