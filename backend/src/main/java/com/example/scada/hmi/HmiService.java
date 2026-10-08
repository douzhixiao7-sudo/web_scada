package com.example.scada.hmi;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.scada.auth.AuthUser;
import com.example.scada.auth.CurrentUserHolder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class HmiService {
    private static final long CONFIG_ID = 1L;
    private static final int MAX_DOCUMENT_BYTES = 1_000_000;
    private static final Pattern ITEM_ID = Pattern.compile("[a-zA-Z0-9-]+");
    private static final Set<String> KINDS = Set.of("value", "lamp", "button", "text");

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public HmiService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public HmiConfigResponse getDraft() {
        return jdbcTemplate.queryForObject("""
                select c.*, r.version_no published_version
                from scada_hmi_config c
                left join scada_hmi_revision r on r.id = c.published_revision_id
                where c.id = ?
                """, (rs, rowNum) -> mapConfig(rs), CONFIG_ID);
    }

    @Transactional
    public HmiConfigResponse saveDraft(HmiDocumentRequest request) {
        if (request == null) throw new IllegalArgumentException("组态文档不能为空");
        String json = validateAndSerialize(request.document());
        Long expected = request.expectedDraftVersion();
        int updated = expected == null
                ? jdbcTemplate.update("update scada_hmi_config set draft_json = ?, draft_version = draft_version + 1, updated_by = ? where id = ?", json, actor(), CONFIG_ID)
                : jdbcTemplate.update("update scada_hmi_config set draft_json = ?, draft_version = draft_version + 1, updated_by = ? where id = ? and draft_version = ?", json, actor(), CONFIG_ID, expected);
        if (updated == 0) throw new IllegalArgumentException("草稿已被其他会话修改，请刷新后再保存");
        audit("HMI_DRAFT_SAVE", "保存组态草稿");
        return getDraft();
    }

    @Transactional
    public HmiRevisionResponse publish(HmiDocumentRequest request) {
        jdbcTemplate.queryForObject("select id from scada_hmi_config where id = ? for update", Long.class, CONFIG_ID);
        HmiConfigResponse saved = saveDraft(request);
        Integer next = jdbcTemplate.queryForObject("select coalesce(max(version_no), 0) + 1 from scada_hmi_revision where config_id = ?", Integer.class, CONFIG_ID);
        int version = next == null ? 1 : next;
        jdbcTemplate.update("insert into scada_hmi_revision(config_id, version_no, content_json, published_by) values (?, ?, ?, ?)", CONFIG_ID, version, serialize(saved.document()), actor());
        Long revisionId = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
        jdbcTemplate.update("update scada_hmi_config set published_revision_id = ? where id = ?", revisionId, CONFIG_ID);
        audit("HMI_PUBLISH", "发布组态版本 V" + version);
        return getRevision(revisionId);
    }

    public HmiRevisionResponse getPublished() {
        Long id = jdbcTemplate.queryForObject("select published_revision_id from scada_hmi_config where id = ?", Long.class, CONFIG_ID);
        if (id == null) throw new IllegalArgumentException("尚未发布组态版本");
        return getRevision(id);
    }

    public List<HmiRevisionResponse> listVersions() {
        return jdbcTemplate.query("""
                select r.*, (r.id = c.published_revision_id) current_revision
                from scada_hmi_revision r join scada_hmi_config c on c.id = r.config_id
                where r.config_id = ? order by r.version_no desc limit 50
                """, (rs, rowNum) -> mapRevision(rs), CONFIG_ID);
    }

    @Transactional
    public HmiConfigResponse restore(Long id) {
        HmiRevisionResponse revision = getRevision(id);
        jdbcTemplate.update("update scada_hmi_config set draft_json = ?, draft_version = draft_version + 1, updated_by = ? where id = ?", serialize(revision.document()), actor(), CONFIG_ID);
        audit("HMI_RESTORE", "恢复组态版本 V" + revision.version() + " 到草稿");
        return getDraft();
    }

    private HmiRevisionResponse getRevision(Long id) {
        List<HmiRevisionResponse> rows = jdbcTemplate.query("""
                select r.*, (r.id = c.published_revision_id) current_revision
                from scada_hmi_revision r join scada_hmi_config c on c.id = r.config_id
                where r.id = ? and r.config_id = ?
                """, (rs, rowNum) -> mapRevision(rs), id, CONFIG_ID);
        if (rows.isEmpty()) throw new IllegalArgumentException("组态版本不存在");
        return rows.getFirst();
    }

    private HmiConfigResponse mapConfig(ResultSet rs) throws SQLException {
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        return new HmiConfigResponse(parse(rs.getString("draft_json")), rs.getLong("draft_version"),
                nullableLong(rs, "published_revision_id"), nullableInt(rs, "published_version"), rs.getString("updated_by"), updatedAt.toInstant());
    }

    private HmiRevisionResponse mapRevision(ResultSet rs) throws SQLException {
        return new HmiRevisionResponse(rs.getLong("id"), rs.getInt("version_no"), parse(rs.getString("content_json")),
                rs.getString("published_by"), rs.getTimestamp("created_at").toInstant(), rs.getBoolean("current_revision"));
    }

    private String validateAndSerialize(JsonNode document) {
        if (document == null || !document.isObject() || document.path("version").asInt(-1) != 1 || !document.path("items").isArray()) {
            throw new IllegalArgumentException("组态文档格式无效");
        }
        JsonNode items = document.path("items");
        if (items.size() > 1000) throw new IllegalArgumentException("组态组件不能超过 1000 个");
        for (JsonNode item : items) validateItem(item);
        String json = serialize(document);
        if (json.getBytes(StandardCharsets.UTF_8).length > MAX_DOCUMENT_BYTES) throw new IllegalArgumentException("组态文档不能超过 1 MB");
        return json;
    }

    private void validateItem(JsonNode item) {
        String id = item.path("id").asText("");
        String kind = item.path("kind").asText("");
        String label = item.path("label").asText("");
        double x = number(item, "x"), y = number(item, "y"), width = number(item, "width"), height = number(item, "height");
        if (!ITEM_ID.matcher(id).matches() || !KINDS.contains(kind) || label.length() > 80 || width < 40 || height < 40 || x < 0 || y < 0 || x + width > 1200 || y + height > 720) {
            throw new IllegalArgumentException("组态组件格式无效");
        }
        JsonNode binding = item.get("binding");
        if (binding != null && (!binding.isObject() || binding.path("deviceId").asLong(0) <= 0 ||
                (!binding.path("pointId").isNull() && binding.path("pointId").asLong(0) <= 0) ||
                binding.path("decimals").asInt(-1) < 0 || binding.path("decimals").asInt(7) > 6 ||
                binding.path("staleSeconds").asInt(0) < 5 || binding.path("staleSeconds").asInt(3601) > 3600)) {
            throw new IllegalArgumentException("组态点位绑定格式无效");
        }
    }

    private double number(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isNumber() || !Double.isFinite(value.asDouble())) throw new IllegalArgumentException("组态组件坐标无效");
        return value.asDouble();
    }

    private JsonNode parse(String json) {
        try { return objectMapper.readTree(json); }
        catch (JacksonException ex) { throw new IllegalStateException("数据库中的组态文档损坏", ex); }
    }

    private String serialize(JsonNode document) {
        try { return objectMapper.writeValueAsString(document); }
        catch (JacksonException ex) { throw new IllegalArgumentException("组态文档无法序列化", ex); }
    }

    private Long nullableLong(ResultSet rs, String name) throws SQLException {
        long value = rs.getLong(name);
        return rs.wasNull() ? null : value;
    }

    private Integer nullableInt(ResultSet rs, String name) throws SQLException {
        int value = rs.getInt(name);
        return rs.wasNull() ? null : value;
    }

    private String actor() {
        AuthUser user = CurrentUserHolder.user();
        return user == null ? "system" : user.username();
    }

    private void audit(String action, String detail) {
        jdbcTemplate.update("insert into sys_audit_log(actor, action, detail) values (?, ?, ?)", actor(), action, detail);
    }
}
